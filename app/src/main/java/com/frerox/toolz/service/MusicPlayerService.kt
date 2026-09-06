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
import com.frerox.toolz.data.music.toMediaItem
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
        const val ACTION_TOGGLE_SHUFFLE = "com.frerox.toolz.action.TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT = "com.frerox.toolz.action.CYCLE_REPEAT"
        const val ACTION_SEEK_TO_POSITION = "com.frerox.toolz.action.SEEK_TO_POSITION"

        // How many upcoming tracks the widget's "Up Next" queue shows.
        // Bounded deliberately: Glance's RemoteViews-backed LazyColumn has
        // real per-row overhead, and nobody scans an 40-deep widget queue
        // anyway — the next handful is what's actually useful at a glance.
        private const val MAX_QUEUE_ROWS = 8

        // P-Revamp: Realtime correction every 2s for true live progress
        // instead of 12s drift correction. The widget interpolates position
        // between pushes, but 12s made scrub/seek feel laggy and queue
        // updates stale. 2s keeps bar smooth and queue fresh with minimal
        // battery impact (Glance throttles per widgetId).
        private const val PROGRESS_CORRECTION_INTERVAL_MS = 2_000L

        // Pocket-resume persistence: while playing we checkpoint the exact
        // position every 5s (was 30s — up to 30s of drift if the process died).
        // Pauses/seeks/task-removed/destroy also checkpoint immediately, so the
        // last song resumes at the exact second even with the phone in a pocket.
        private const val POSITION_PERSIST_INTERVAL_MS = 5_000L
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
    private var audioFocusDucking = false
    private var isDucking = false
    private var shouldResumeOnFocusGain = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (!audioFocusEnabled) return@OnAudioFocusChangeListener
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (isDucking) {
                    player.volume = 1.0f
                    isDucking = false
                }
                if (shouldResumeOnFocusGain) {
                    shouldResumeOnFocusGain = false
                    if (!player.isPlaying) player.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                shouldResumeOnFocusGain = false
                if (isDucking) {
                    player.volume = 1.0f
                    isDucking = false
                }
                if (player.isPlaying) player.pause()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                shouldResumeOnFocusGain = player.isPlaying
                if (player.isPlaying) player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (audioFocusDucking) {
                    if (!isDucking && player.isPlaying) {
                        player.volume = 0.2f
                        isDucking = true
                    }
                } else {
                    shouldResumeOnFocusGain = player.isPlaying
                    if (player.isPlaying) player.pause()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (!audioFocusEnabled) return false
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            val frameworkAttrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(frameworkAttrs)
                .setWillPauseWhenDucked(!audioFocusDucking)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
            am.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                am.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusListener)
        }
        if (isDucking) {
            player.volume = 1.0f
            isDucking = false
        }
    }

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
                    } else if (state == 0) { // Unplugged — checkpoint exact second, then pause
                        savePlaybackState()
                        if (player.isPlaying) player.pause()
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = false)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    // Audio becoming noisy usually handles this, but we can be safe
                    savePlaybackState()
                    if (player.isPlaying) player.pause()
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Wired/BT output lost (earphones unplugged, BT dropped):
                    // persist the exact position first so pocket-resume is exact.
                    savePlaybackState()
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
                if (audioFocusEnabled) {
                    val granted = requestAudioFocus()
                    // If focus not granted, pause playback to respect system
                    if (!granted) {
                        player.pause()
                    }
                }
            } else {
                stopWidgetCorrectionLoop()
                stopStatePersistenceLoop()
                unregisterShakeListener()
                savePlaybackState()
                // Only abandon if not expecting auto-resume (transient/duck pause)
                // and not currently ducking (still playing at low volume)
                if (!shouldResumeOnFocusGain && !isDucking) {
                    abandonAudioFocus()
                }
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
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Seeks (user scrub, skip, BT jump) must checkpoint immediately —
            // otherwise a kill before the next 5s tick resumes from a stale second.
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT ||
                reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                reason == Player.DISCONTINUITY_REASON_SKIP
            ) {
                savePlaybackState()
            }
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateWidget() }
        override fun onRepeatModeChanged(repeatMode: Int) { updateWidget() }

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

                // P0-05 fix: validate index still holds same mediaId (queue may have mutated during resolve)
                for (i in 0 until player.mediaItemCount) {
                    if (player.getMediaItemAt(i).mediaId == item.mediaId) {
                        // Double-check before replacing — bound-check + id check on Main
                        withContext(Dispatchers.Main) {
                            if (i < player.mediaItemCount && player.getMediaItemAt(i).mediaId == item.mediaId) {
                                player.replaceMediaItem(i, updatedItem)
                            } else {
                                Log.w("MusicPlayerService", "Skipping stale catalog resolve: queue mutated")
                            }
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerService", "Catalog resolve failed", e)
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

        // P0-07 fix: specify receiver export flag on Tiramisu+ (required for dynamic receivers)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(headsetReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(headsetReceiver, filter)
        }
        
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
                    // Pocket / lockscreen / BT-remote resume without opening Toolz:
                    // single PLAY/PAUSE keys must also wake + restore the last song.
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        resumeLastIfEmptyOrPlay()
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        savePlaybackState()
                        if (player.isPlaying) player.pause()
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                        else player.seekToNext()
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                        else player.seekToPrevious()
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

    // Shared pocket-resume path: if the process was killed, the queue is empty —
    // restore the last song at its exact saved second and play. Otherwise toggle.
    private fun resumeLastIfEmptyOrPlay() {
        if (player.mediaItemCount == 0) {
            restorePlaybackState(autoPlay = true)
        } else {
            if (player.isPlaying) player.pause() else player.play()
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
            ) { enabled, ducking -> enabled to ducking }.collect { (enabled, ducking) ->
                val wasEnabled = audioFocusEnabled
                val oldDucking = audioFocusDucking
                val needsAttrUpdate = wasEnabled != enabled || oldDucking != ducking
                audioFocusEnabled = enabled
                audioFocusDucking = ducking

                if (needsAttrUpdate) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build()
                    // Disable ExoPlayer automatic handling; we manage focus manually
                    player.setAudioAttributes(audioAttributes, false)
                }

                if (!enabled) {
                    // Smart focus OFF: restore volume, clear resume flag, abandon focus,
                    // and keep playing (music mixes with other apps).
                    if (isDucking) {
                        player.volume = 1.0f
                        isDucking = false
                    }
                    shouldResumeOnFocusGain = false
                    abandonAudioFocus()
                } else {
                    // Smart focus ON
                    if (isDucking && !ducking) {
                        // Was ducking (playing quietly), but ducking is now disabled
                        // -> restore volume and pause instead, remembering to resume on gain
                        player.volume = 1.0f
                        isDucking = false
                        shouldResumeOnFocusGain = player.isPlaying
                        if (player.isPlaying) player.pause()
                    } else if (!isDucking && ducking && shouldResumeOnFocusGain && !player.isPlaying) {
                        // Was paused due to a previous CAN_DUCK that could not duck (paused),
                        // and ducking is now enabled -> resume at duck volume immediately
                        // if the transient focus is still held. We attempt to resume
                        // ducking; if focus is still held by another app, the system
                        // will keep us ducked via the listener, otherwise we play at full.
                        player.volume = 0.2f
                        isDucking = true
                        shouldResumeOnFocusGain = false
                        player.play()
                    }
                    // Re-request focus with updated willPauseWhenDucked if currently
                    // playing, ducking, or waiting to resume (so the pending request
                    // reflects the new ducking preference for future transient losses).
                    if (player.isPlaying || isDucking || shouldResumeOnFocusGain) {
                        requestAudioFocus()
                    } else if (!wasEnabled || oldDucking != ducking) {
                        // Enabled while idle or ducking pref changed while idle:
                        // ensure no stale duck volume.
                        if (isDucking) {
                            player.volume = 1.0f
                            isDucking = false
                        }
                        // If we were idle and just enabled focus, make sure volume is clean
                        if (!wasEnabled) {
                            player.volume = 1.0f
                            isDucking = false
                        }
                    }
                }
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
            ACTION_TOGGLE_SHUFFLE -> {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
            }
            ACTION_CYCLE_REPEAT -> {
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
            ACTION_SEEK_TO_POSITION -> {
                val pos = intent.getLongExtra(com.frerox.toolz.widget.glance.MusicActionCallback.EXTRA_POSITION_MS, -1L)
                if (pos >= 0) player.seekTo(pos)
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
                delay(POSITION_PERSIST_INTERVAL_MS) // Checkpoint exact second every 5s while playing
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
                        if (tracks.size < uris.size) {
                            Log.w("MusicPlayerService", "restorePlaybackState pruned ${uris.size - tracks.size} missing tracks (deleted)")
                        }
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

            // P2-03 fix: Palette work off Main (was blocking serviceScope/Main)
            if (forceBitmapRefresh || artUri != lastTrackUri || artShape != lastShape || cachedProcessedBitmap == null) {
                var bitmap = if (artUri != null) loadBitmap(artUri) else null
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
                }
                bitmap?.let { bmp ->
                    cachedProcessedBitmap = withContext(Dispatchers.Default) { processThumbnail(bmp, artShape) }
                    lastTrackUri = artUri
                    lastShape = artShape

                    // Extract accent color off Main
                    val color = withContext(Dispatchers.Default) {
                        val palette = Palette.from(bmp).generate()
                        palette.getVibrantColor(palette.getMutedColor(Color.BLUE))
                    }
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
                queue = queueSnapshot,
                isShuffleOn = player.shuffleModeEnabled,
                repeatMode = player.repeatMode
            )
        }
    }

    private fun processThumbnail(bitmap: Bitmap, shape: String): Bitmap {
        val size = minOf(bitmap.width, bitmap.height).coerceAtMost(256)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)

        when (shape) {
            "CIRCLE" -> {
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(bitmap, null, rect, paint)
            }
            "SQUIRCLE", "SQUARE_ROUNDED" -> {
                // Squircle: larger radius than square (0.32f vs 0.2f) for superellipse feel
                val cornerRadius = size * 0.32f
                canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), cornerRadius, cornerRadius, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(bitmap, null, rect, paint)
            }
            else -> {
                val cornerRadius = size * 0.2f
                canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), cornerRadius, cornerRadius, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(bitmap, null, rect, paint)
            }
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
        // Save state one last time — swiping the app away must not lose the second.
        savePlaybackState()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Best-effort synchronous checkpoint: the async savePlaybackState() posts to
        // serviceScope, which we cancel below — so capture the exact position first
        // and persist it blocking (short timeout) before teardown.
        runCatching {
            val item = try { player.currentMediaItem } catch (_: Exception) { null }
            if (item != null) {
                val uri = item.mediaId
                val pos = try { player.currentPosition } catch (_: Exception) { -1L }
                if (pos >= 0) {
                    val queueUris = mutableListOf<String>()
                    runCatching {
                        for (i in 0 until player.mediaItemCount) {
                            queueUris.add(player.getMediaItemAt(i).mediaId)
                        }
                    }
                    val queueJson = runCatching {
                        moshi.adapter<List<String>>(
                            Types.newParameterizedType(List::class.java, String::class.java)
                        ).toJson(queueUris)
                    }.getOrNull()
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.withTimeoutOrNull(1_500L) {
                            settingsRepository.setMusicLastPlayedState(uri, pos, queueJson)
                        }
                    }
                }
            }
        }
        runCatching { unregisterReceiver(headsetReceiver) }
        unregisterShakeListener()
        abandonAudioFocus()
        try { player.volume = 1.0f } catch (_: Exception) {}
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}