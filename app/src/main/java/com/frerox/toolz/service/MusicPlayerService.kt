/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.service

import android.util.Log

import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.widget.WidgetUpdateManager
import com.frerox.toolz.widget.glance.MusicActionCallback.Companion.EXTRA_QUEUE_INDEX
import com.frerox.toolz.widget.glance.QueueTrackInfo
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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
    private var statePersistenceJob: Job? = null

    private var sensorManager: SensorManager? = null
    private var audioManager: AudioManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var shakeThreshold = 15f // Increased default
    private var lastShakeTime: Long = 0
    private var isShakeRegistered = false

    private var audioFocusEnabled = true
    private var audioFocusDucking = true
    private var isDucking = false

    private var cachedProcessedBitmap: Bitmap? = null
    private var lastTrackUri: String? = null
    private var lastShape: String? = null
    private var lastAccentColor: String? = null

    // Multi-tap detection for earphone center button
    private var lastMediaButtonClickTime: Long = 0
    private var mediaButtonClickCount = 0
    private var mediaButtonCheckJob: Job? = null

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

    @Inject
    lateinit var moshi: Moshi

    private val headsetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1) { // Plugged
                        if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = false)
                    } else if (state == 0) { // Unplugged
                        if (player.isPlaying) player.pause()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = false)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // Audio becoming noisy usually handles this, but we can be safe
                    if (player.isPlaying) player.pause()
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateWidget(forceBitmapRefresh = true)
            savePlaybackState()

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
                startStatePersistenceLoop()
                observeShakeSetting()
            } else {
                stopWidgetCorrectionLoop()
                stopStatePersistenceLoop()
                unregisterShakeListener()
                savePlaybackState()
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
            savePlaybackState()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            super.onPlayerError(error)
            Log.e("MusicPlayerService", "Playback error in background: ${error.errorCodeName}", error)
        }
    }

    private fun resolveCatalogTrack(item: MediaItem) {
        serviceScope.launch {
            try {
                val sourceUrl = item.mediaMetadata.extras?.getString("source_url") ?: return@launch
                val streamUrl = catalogRepository.resolveAudioStream(sourceUrl)

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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            .setCallback(CustomMediaSessionCallback())
            .build()

        player.addListener(playerListener)

        observeAudioFocusSettings()

        if (player.isPlaying) {
            startWidgetCorrectionLoop()
            startStatePersistenceLoop()
            observeShakeSetting()
        }
        updateWidget(forceBitmapRefresh = true)

        // Register headset and bluetooth receivers
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(headsetReceiver, filter)
        
        // Restore last state if empty
        if (player.mediaItemCount == 0) {
            restorePlaybackState(autoPlay = false)
        }
    }

    @OptIn(UnstableApi::class)
    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent?.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_HEADSETHOOK,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        handleMediaButtonClick()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.accept(
                androidx.media3.session.SessionCommands.EMPTY,
                androidx.media3.common.Player.Commands.Builder().addAllCommands().build()
            )
        }

        @Deprecated("Use onPlaybackResumption(MediaSession, ControllerInfo, Bundle) instead")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val setter = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val uri = settingsRepository.musicLastPlayedUri.first()
                val pos = settingsRepository.musicLastPlayedPosition.first()
                val queueJson = settingsRepository.musicLastPlayedQueue.first()
                
                if (uri != null) {
                    val track = musicRepository.getTrackByUri(uri)
                    if (track != null) {
                        val items = mutableListOf<MediaItem>()
                        if (queueJson != null) {
                            val uris = try {
                                moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
                                    .fromJson(queueJson) ?: emptyList()
                            } catch (e: Exception) { emptyList() }
                            
                            val tracks = uris.mapNotNull { musicRepository.getTrackByUri(it) }
                            items.addAll(tracks.map { it.toMediaItem() })
                        } else {
                            items.add(track.toMediaItem())
                        }
                        
                        val startIndex = items.indexOfFirst { it.mediaId == uri }.coerceAtLeast(0)
                        setter.set(MediaSession.MediaItemsWithStartPosition(items, startIndex, pos))
                    } else {
                        setter.setException(Exception("Track not found"))
                    }
                } else {
                    setter.setException(Exception("No last played track"))
                }
            }
            return setter
        }
    }

    private fun handleMediaButtonClick() {
        val now = System.currentTimeMillis()
        if (now - lastMediaButtonClickTime > 500) {
            mediaButtonClickCount = 1
        } else {
            mediaButtonClickCount++
        }
        lastMediaButtonClickTime = now

        mediaButtonCheckJob?.cancel()
        mediaButtonCheckJob = serviceScope.launch {
            delay(350)
            when (mediaButtonClickCount) {
                1 -> {
                    if (player.mediaItemCount == 0) {
                        restorePlaybackState(autoPlay = true)
                    } else {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                }
                2 -> player.seekToNext()
                3 -> player.seekToPrevious()
            }
            mediaButtonClickCount = 0
        }
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

    private fun observeAudioFocusSettings() {
        serviceScope.launch {
            combine(
                settingsRepository.musicAudioFocus,
                settingsRepository.musicAudioFocusDucking
            ) { enabled, ducking -> enabled to ducking }.collectLatest { (enabled, ducking) ->
                audioFocusEnabled = enabled
                audioFocusDucking = ducking
                
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
                
                // If enabled, we let ExoPlayer handle it, but we'll also use a custom listener
                // for the ducking behavior if needed, OR we just trust ExoPlayer's default
                // which is to duck if usage is MEDIA.
                // However, to satisfy "Smart", we'll implement the listener to respect the 'ducking' setting.
                player.setAudioAttributes(audioAttributes, enabled)
            }
        }
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
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
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

    private fun startStatePersistenceLoop() {
        statePersistenceJob?.cancel()
        statePersistenceJob = serviceScope.launch {
            while (isActive) {
                delay(30_000L) // Save state every 30s while playing
                savePlaybackState()
            }
        }
    }

    private fun stopStatePersistenceLoop() {
        statePersistenceJob?.cancel()
        savePlaybackState()
    }

    private fun savePlaybackState() {
        val currentItem = player.currentMediaItem ?: return
        val position = player.currentPosition
        val uri = currentItem.mediaId
        
        val queueUris = mutableListOf<String>()
        for (i in 0 until player.mediaItemCount) {
            queueUris.add(player.getMediaItemAt(i).mediaId)
        }
        val queueJson = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
            .toJson(queueUris)

        serviceScope.launch {
            settingsRepository.setMusicLastPlayedState(uri, position, queueJson)
        }
    }

    private fun restorePlaybackState(autoPlay: Boolean = false) {
        serviceScope.launch {
            val uri = settingsRepository.musicLastPlayedUri.first()
            val position = settingsRepository.musicLastPlayedPosition.first()
            val queueJson = settingsRepository.musicLastPlayedQueue.first()

            if (uri != null) {
                val track = musicRepository.getTrackByUri(uri)
                if (track != null) {
                    val items = mutableListOf<MediaItem>()
                    if (queueJson != null) {
                        val uris = try {
                            moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
                                .fromJson(queueJson) ?: emptyList()
                        } catch (e: Exception) { emptyList() }
                        
                        val tracks = uris.mapNotNull { musicRepository.getTrackByUri(it) }
                        items.addAll(tracks.map { it.toMediaItem() })
                    } else {
                        items.add(track.toMediaItem())
                    }

                    withContext(Dispatchers.Main) {
                        val startIndex = items.indexOfFirst { it.mediaId == uri }.coerceAtLeast(0)
                        player.setMediaItems(items, startIndex, position)
                        player.prepare()
                        if (autoPlay) player.play()
                    }
                }
            }
        }
    }

    private fun MusicTrack.toMediaItem(): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title).setArtist(artist ?: "Unknown Artist")
            .setAlbumTitle(album ?: "Unknown Album").setDisplayTitle(title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsPlayable(true)
            .setArtworkUri(thumbnailUri?.let { Uri.parse(it) })
            .apply { sourceUrl?.let { setExtras(Bundle().apply { putString("source_url", it) }) } }
            .build()
        val playableUri = when {
            path != null && File(path).exists() -> Uri.fromFile(File(path)).toString()
            uri.startsWith("content://") || uri.startsWith("file://") -> uri
            path != null && (path.startsWith("content://") || path.startsWith("file://")) -> path
            path != null && path.startsWith("/") -> Uri.fromFile(File(path)).toString()
            else -> uri
        }
        val parsedUri = if (playableUri.startsWith("/")) Uri.fromFile(File(playableUri)) else Uri.parse(playableUri)
        return MediaItem.Builder()
            .setMediaId(uri).setUri(parsedUri).setMediaMetadata(meta).build()
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Save state one last time
        savePlaybackState()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(headsetReceiver) }
        unregisterShakeListener()
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}