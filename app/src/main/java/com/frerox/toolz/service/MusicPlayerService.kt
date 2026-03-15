package com.frerox.toolz.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.widget.MusicWidgetProvider
import com.frerox.toolz.widget.WidgetUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.sqrt

@UnstableApi
@AndroidEntryPoint
class MusicPlayerService : MediaSessionService(), SensorEventListener {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var widgetUpdateJob: Job? = null
    private var currentRotation = 0f
    
    private var sensorManager: SensorManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private val SHAKE_THRESHOLD = 12f
    private var lastShakeTime: Long = 0
    private var isShakeRegistered = false

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "music_player")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateWidget()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateWidget()
                if (isPlaying) {
                    startWidgetTimer()
                    observeShakeSetting()
                } else {
                    stopWidgetTimer()
                    unregisterShakeListener()
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateWidget()
            }
        })
        
        if (player.isPlaying) {
            startWidgetTimer()
            observeShakeSetting()
        }
        updateWidget()
    }

    private fun observeShakeSetting() {
        serviceScope.launch {
            settingsRepository.musicShakeToSkip.collectLatest { enabled ->
                if (enabled && player.isPlaying) {
                    registerShakeListener()
                } else {
                    unregisterShakeListener()
                }
            }
        }
    }

    private fun registerShakeListener() {
        if (isShakeRegistered) return
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager?.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
            isShakeRegistered = true
        }
    }

    private fun unregisterShakeListener() {
        sensorManager?.unregisterListener(this)
        isShakeRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt(x * x + y * y + z * z)
        val delta = currentAcceleration - lastAcceleration
        acceleration = acceleration * 0.9f + delta

        if (acceleration > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > 1000) {
                lastShakeTime = now
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "TOGGLE_PLAY" -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            "SKIP_NEXT" -> player.seekToNext()
            "SKIP_PREV" -> player.seekToPrevious()
        }
        updateWidget()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startWidgetTimer() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = serviceScope.launch {
            while (isActive) {
                val rotationEnabled = settingsRepository.musicRotationEnabled.first()
                if (player.isPlaying && rotationEnabled) {
                    currentRotation = (currentRotation + 5f) % 360f
                }
                updateWidget()
                delay(500) // Update faster for smoother rotation and progress
            }
        }
    }

    private fun stopWidgetTimer() {
        widgetUpdateJob?.cancel()
        updateWidget()
    }

    private fun updateWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, MusicWidgetProvider::class.java)
        val views = RemoteViews(packageName, R.layout.music_widget)

        // Apply custom themes and opacity
        WidgetUtils.applyTheme(this, views, settingsRepository)

        val currentItem = player.currentMediaItem
        if (currentItem != null) {
            views.setTextViewText(R.id.widget_music_title, currentItem.mediaMetadata.title ?: "Unknown Title")
            views.setTextViewText(R.id.widget_music_artist, currentItem.mediaMetadata.artist ?: "Unknown Artist")
            
            val duration = player.duration
            val position = player.currentPosition
            val progress = if (duration > 0) (position * 100 / duration).toInt() else 0
            views.setProgressBar(R.id.widget_music_progress, 100, progress, false)
            
            views.setImageViewResource(R.id.widget_music_play_pause, 
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)

            serviceScope.launch {
                val artShape = settingsRepository.musicArtShape.first()
                val rotationEnabled = settingsRepository.musicRotationEnabled.first()
                val artUri = currentItem.mediaMetadata.artworkUri
                
                var bitmap = if (artUri != null) loadBitmap(artUri.toString()) else null
                
                if (bitmap == null) {
                    // Fallback icon
                    bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
                }

                bitmap?.let {
                    val processedBitmap = processThumbnail(it, artShape)
                    views.setImageViewBitmap(R.id.widget_music_album_art, processedBitmap)
                    if (player.isPlaying && rotationEnabled) {
                        views.setFloat(R.id.widget_music_album_art, "setRotation", currentRotation)
                    } else {
                        views.setFloat(R.id.widget_music_album_art, "setRotation", 0f)
                    }
                }
                
                setupIntents(views)
                appWidgetManager.updateAppWidget(componentName, views)
            }
        } else {
            views.setTextViewText(R.id.widget_music_title, "NOT PLAYING")
            views.setTextViewText(R.id.widget_music_artist, "Tap to open Toolz")
            views.setProgressBar(R.id.widget_music_progress, 100, 0, false)
            views.setImageViewResource(R.id.widget_music_play_pause, android.R.drawable.ic_media_play)
            views.setImageViewResource(R.id.widget_music_album_art, R.drawable.ic_music_note)
            views.setFloat(R.id.widget_music_album_art, "setRotation", 0f)
            setupIntents(views)
            appWidgetManager.updateAppWidget(componentName, views)
        }
    }

    private fun processThumbnail(bitmap: Bitmap, shape: String): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)

        if (shape == "CIRCLE") {
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
        } else {
            // Rounded Square
            val cornerRadius = size * 0.2f
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), cornerRadius, cornerRadius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
        }
        return output
    }

    private fun setupIntents(views: RemoteViews) {
        val playIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "TOGGLE_PLAY" }
        views.setOnClickPendingIntent(R.id.widget_music_play_pause, PendingIntent.getBroadcast(this, 1, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val nextIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "SKIP_NEXT" }
        views.setOnClickPendingIntent(R.id.widget_music_next, PendingIntent.getBroadcast(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val prevIntent = Intent(this, MusicWidgetProvider::class.java).apply { action = "SKIP_PREV" }
        views.setOnClickPendingIntent(R.id.widget_music_prev, PendingIntent.getBroadcast(this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val appIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "music_player")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_music_album_art, appPendingIntent)
        views.setOnClickPendingIntent(R.id.widget_text_container, appPendingIntent)
    }

    private suspend fun loadBitmap(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(this@MusicPlayerService)
            val request = ImageRequest.Builder(this@MusicPlayerService)
                .data(uri)
                .size(300, 300)
                .allowHardware(false)
                .build()
            val result = loader.execute(request).image
            result?.toBitmap()
        } catch (e: Exception) {
            null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        unregisterShakeListener()
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
