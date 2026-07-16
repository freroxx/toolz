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
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.widget.WidgetUpdateManager
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.EXTRA_QUEUE_INDEX
import com.frerox.toolz.widget.glance.QueueTrackInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class MusicPlayerService : MediaSessionService(), SensorEventListener {

    companion object {
        // Service-facing action names. The widget receiver maps its own
        // broadcast actions (MUSIC_ACTION_*) onto these — kept as constants
        // here rather than duplicated string literals in both files, so
        // renaming or adding one can't silently drift out of sync.
        const val ACTION_TOGGLE_PLAY = "com.frerox.toolz.action.TOGGLE_PLAY"
        const val ACTION_SKIP_NEXT = "com.frerox.toolz.action.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.frerox.toolz.action.SKIP_PREV"
        const val ACTION_TOGGLE_FAVORITE = "com.frerox.toolz.action.TOGGLE_FAVORITE"
        const val ACTION_SEEK_TO_QUEUE_INDEX = "com.frerox.toolz.action.SEEK_TO_QUEUE_INDEX"

        // How many upcoming tracks the widget's "Up Next" queue shows.
        // Bounded deliberately: Glance's RemoteViews-backed LazyColumn has
        // real per-row overhead, and nobody scans an 40-deep widget queue
        // anyway — the next handful is what's actually useful at a glance.
        private const val MAX_QUEUE_ROWS = 8

        // Correction interval while playing. The widget no longer needs a
        // per-second push to *look* live — it interpolates position on its
        // own between updates (see liveProgressFraction) — this loop exists
        // purely to correct drift from playback speed changes, seeks made
        // outside the widget, or buffering stalls. 12s keeps drift
        // imperceptible without re-rendering the widget 12x more than
        // necessary.
        private const val PROGRESS_CORRECTION_INTERVAL_MS = 12_000L
    }

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var widgetCorrectionJob: Job? = null

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
    private var lastAccentColor: String? = null

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var catalogRepository: com.frerox.toolz.data.catalog.CatalogRepository

    @Inject
    lateinit var widgetUpdateManager: WidgetUpdateManager

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var vibrationManager: com.frerox.toolz.util.VibrationManager

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
                startWidgetCorrectionLoop()
                observeShakeSetting()
            } else {
                stopWidgetCorrectionLoop()
                unregisterShakeListener()
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateWidget()
        }
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            // The queue itself can change shape (tracks added/removed/
            // reordered) without a media item transition — re-push so the
            // widget's Up Next list doesn't go stale.
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
            this, 2001, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        player.addListener(playerListener)

        if (player.isPlaying) {
            startWidgetCorrectionLoop()
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
                    vibrationManager.vibrateSuccess()
                    player.seekToNext()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            ACTION_SKIP_NEXT -> player.seekToNext()
            ACTION_SKIP_PREV -> player.seekToPrevious()
            ACTION_SEEK_TO_QUEUE_INDEX -> {
                val index = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1)
                if (index in 0 until player.mediaItemCount) {
                    player.seekTo(index, 0L)
                    if (!player.isPlaying) player.play()
                }
            }
            ACTION_TOGGLE_FAVORITE -> {
                serviceScope.launch {
                    val currentMediaId = player.currentMediaItem?.mediaId
                    if (currentMediaId != null) {
                        val track = musicRepository.getTrackByUri(currentMediaId)
                        if (track != null) {
                            musicRepository.toggleFavorite(track)
                            updateWidget()
                        }
                    }
                }
            }
        }
        updateWidget()
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Periodic drift correction while playing — NOT a "make the bar move"
     * timer. The widget derives its own live position between pushes (see
     * MusicWidgetSupport.liveProgressFraction), so this only needs to run
     * often enough to catch drift, not every second. Replaces the previous
     * 1s poll loop, cutting widget re-renders by roughly 12x during
     * continuous playback while the bar looks equally live.
     */
    private fun startWidgetCorrectionLoop() {
        widgetCorrectionJob?.cancel()
        widgetCorrectionJob = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_CORRECTION_INTERVAL_MS)
                updateWidget()
            }
        }
    }

    private fun stopWidgetCorrectionLoop() {
        widgetCorrectionJob?.cancel()
        updateWidget()
    }

    private fun buildQueueSnapshot(): List<QueueTrackInfo> {
        if (player.mediaItemCount == 0) return emptyList()
        val currentIndex = player.currentMediaItemIndex
        val upcoming = (currentIndex + 1) until player.mediaItemCount
        return upcoming.take(MAX_QUEUE_ROWS).map { index ->
            val item = player.getMediaItemAt(index)
            QueueTrackInfo(
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString() ?: "Unknown",
                artist = item.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                queueIndex = index
            )
        }
    }

    private fun updateWidget(forceBitmapRefresh: Boolean = false) {
        val currentItem = player.currentMediaItem
        val mediaId = currentItem?.mediaId
        val queueSnapshot = buildQueueSnapshot()
        val capturedAtElapsedMs = SystemClock.elapsedRealtime()
        val positionMs = player.currentPosition
        val durationMs = player.duration.coerceAtLeast(0L)
        val isPlayingNow = player.isPlaying

        serviceScope.launch {
            val artShape = settingsRepository.musicArtShape.first()
            val artUri = currentItem?.mediaMetadata?.artworkUri?.toString()
            val title = currentItem?.mediaMetadata?.title?.toString() ?: "Not Playing"
            val artist = currentItem?.mediaMetadata?.artist?.toString() ?: "Tap to open Toolz"
            val album = currentItem?.mediaMetadata?.albumTitle?.toString()

            // Load & persist art bitmap to a file for Glance to read
            if (forceBitmapRefresh || artUri != lastTrackUri || artShape != lastShape || cachedProcessedBitmap == null) {
                var bitmap = if (artUri != null) loadBitmap(artUri) else null
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
                }
                bitmap?.let {
                    cachedProcessedBitmap = processThumbnail(it, artShape)
                    lastTrackUri = artUri
                    lastShape = artShape

                    // Extract accent color
                    val palette = Palette.from(it).generate()
                    val color = palette.getVibrantColor(palette.getMutedColor(Color.BLUE))
                    lastAccentColor = String.format("#%06X", 0xFFFFFF and color)
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

            val isFavorite = if (mediaId != null) {
                musicRepository.getTrackByUri(mediaId)?.isFavorite ?: false
            } else false

            val nextTitle = queueSnapshot.firstOrNull()?.title

            // Push state to Glance DataStore using the WidgetUpdateManager
            widgetUpdateManager.updateMusicWidget(
                title = title,
                artist = artist,
                album = album,
                positionMs = positionMs,
                durationMs = durationMs,
                capturedAtElapsedMs = capturedAtElapsedMs,
                isPlaying = isPlayingNow,
                hasNext = player.hasNextMediaItem(),
                hasPrev = player.hasPreviousMediaItem(),
                accentColor = lastAccentColor,
                artShape = artShape,
                artFilePath = artFilePath,
                isFavorite = isFavorite,
                nextTitle = nextTitle,
                queue = queueSnapshot
            )
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