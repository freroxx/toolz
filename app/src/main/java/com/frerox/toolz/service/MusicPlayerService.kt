package com.frerox.toolz.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import com.frerox.toolz.widget.glance.MusicGlanceWidget
import com.frerox.toolz.widget.glance.MusicWidgetState
import com.frerox.toolz.widget.glance.MusicWidgetStateDefinition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
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
    private var shakeThreshold = 15f // Increased default
    private var lastShakeTime: Long = 0
    private var isShakeRegistered = false

    private var cachedProcessedBitmap: Bitmap? = null
    private var lastTrackUri: String? = null
    private var lastShape: String? = null

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var catalogRepository: com.frerox.toolz.data.catalog.CatalogRepository

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateWidget(forceBitmapRefresh = true)
            
            // Check if we need to resolve the stream URL for catalog tracks
            mediaItem?.let { item ->
                val isCatalog = item.mediaMetadata.extras?.getBoolean("is_catalog") ?: false
                if (isCatalog && (item.localConfiguration?.uri == null || item.localConfiguration?.uri.toString() == item.mediaId)) {
                    resolveCatalogTrack(item)
                }
            }
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
    }

    private fun resolveCatalogTrack(item: MediaItem) {
        serviceScope.launch {
            try {
                val sourceUrl = item.mediaMetadata.extras?.getString("source_url") ?: return@launch
                val streamUrl = catalogRepository.resolveAudioStream(sourceUrl)
                
                if (streamUrl != null) {
                    val updatedItem = item.buildUpon()
                        .setUri(Uri.parse(streamUrl))
                        .build()
                    
                    // Replace the item in the player
                    for (i in 0 until player.mediaItemCount) {
                        if (player.getMediaItemAt(i).mediaId == item.mediaId) {
                            player.replaceMediaItem(i, updatedItem)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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

        player.addListener(playerListener)
        
        if (player.isPlaying) {
            startWidgetTimer()
            observeShakeSetting()
        }
        updateWidget(forceBitmapRefresh = true)
    }

    private fun observeShakeSetting() {
        serviceScope.launch {
            combine(
                settingsRepository.musicShakeToSkip,
                settingsRepository.musicShakeSensitivity
            ) { enabled, sensitivity ->
                enabled to sensitivity
            }.collectLatest { (enabled, sensitivity) ->
                // Sensitivity 0.0 -> 35f (hard), 1.0 -> 8f (easy)
                shakeThreshold = 35f - (sensitivity * 27f)
                
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
        currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val delta = currentAcceleration - lastAcceleration
        acceleration = acceleration * 0.9f + delta

        if (acceleration > shakeThreshold) {
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
                delay(500) 
            }
        }
    }

    private fun stopWidgetTimer() {
        widgetUpdateJob?.cancel()
        updateWidget()
    }

    private fun updateWidget(forceBitmapRefresh: Boolean = false) {
        val currentItem = player.currentMediaItem

        serviceScope.launch {
            val artShape = settingsRepository.musicArtShape.first()
            val artUri   = currentItem?.mediaMetadata?.artworkUri?.toString()
            val title    = currentItem?.mediaMetadata?.title?.toString() ?: "Not Playing"
            val artist   = currentItem?.mediaMetadata?.artist?.toString() ?: "Tap to open Toolz"

            val duration = player.duration
            val position = player.currentPosition
            val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

            // Load & persist art bitmap to a file for Glance to read
            if (forceBitmapRefresh || artUri != lastTrackUri || artShape != lastShape || cachedProcessedBitmap == null) {
                var bitmap = if (artUri != null) loadBitmap(artUri) else null
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
                }
                bitmap?.let {
                    cachedProcessedBitmap = processThumbnail(it, artShape)
                    lastTrackUri = artUri
                    lastShape    = artShape
                }
            }

            // Save bitmap to internal storage so Glance can load it
            val artFilePath = cachedProcessedBitmap?.let { bmp ->
                try {
                    val file = File(filesDir, "widget_art.png")
                    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 85, it) }
                    file.absolutePath
                } catch (_: Exception) { null }
            }

            // Push state to Glance DataStore using the correct API
            val glanceIds = GlanceAppWidgetManager(this@MusicPlayerService)
                .getGlanceIds(MusicGlanceWidget::class.java)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(this@MusicPlayerService, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[MusicWidgetState.KEY_TITLE]    = title
                        this[MusicWidgetState.KEY_ARTIST]   = artist
                        this[MusicWidgetState.KEY_PROGRESS] = progress
                        this[MusicWidgetState.KEY_PLAYING]  = player.isPlaying
                        this[MusicWidgetState.KEY_ART_SHAPE]= artShape
                        if (artFilePath != null) this[MusicWidgetState.KEY_ART_PATH] = artFilePath
                    }
                }
            }
            MusicGlanceWidget().updateAll(this@MusicPlayerService)
        }
    }

    private fun processThumbnail(bitmap: Bitmap, shape: String): Bitmap {
        val size = minOf(bitmap.width, bitmap.height).coerceAtMost(256)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)

        if (shape == "CIRCLE") {
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, null, rect, paint)
        } else {
            val cornerRadius = size * 0.2f
            canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), cornerRadius, cornerRadius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, null, rect, paint)
        }
        return output
    }

    // setupIntents no longer needed — Glance handles click actions declaratively

    private suspend fun loadBitmap(uri: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(this@MusicPlayerService)
            val request = ImageRequest.Builder(this@MusicPlayerService)
                .data(uri)
                .size(256, 256)
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
