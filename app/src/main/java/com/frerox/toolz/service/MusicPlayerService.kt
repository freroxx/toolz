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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
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
import androidx.core.app.NotificationCompat
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

        // FGS safeguard: every startForegroundService() call MUST be followed by
        // startForeground() within the system timeout, or the system raises
        // "Context.startForegroundService() did not then call
        // Service.startForeground()" ANR. Media3's MediaSessionService only goes
        // foreground when playback is active (playlist non-empty + playing), so
        // non-playback commands (favorite/shuffle/repeat/warm-up, or an async
        // restore that hasn't finished yet) never promoted -> ANR. We promote
        // synchronously on every onStartCommand with a lightweight placeholder
        // and demote right after if still idle; Media3 re-promotes with the real
        // media notification as soon as playback starts.
        private const val FGS_NOTIFICATION_ID = 1101
        private const val FGS_CHANNEL_ID = "music_playback"
    }

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var widgetCorrectionJob: Job? = null
    private var statePersistenceJob: Job? = null
    private var placeholderDemoteJob: Job? = null
    private var isPlaceholderActive = false

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
            runCatching {
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
                        // Audio becoming noisy usually handles this, but be safe.
                        // Guard isPlaying so a stray disconnect can't pause a
                        // fresh play() that raced the broadcast.
                        if (player.isPlaying) {
                            savePlaybackState()
                            player.pause()
                        }
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        // Wired/BT output lost (earphones unplugged, BT dropped):
                        // persist the exact position first so pocket-resume is exact.
                        if (player.isPlaying) {
                            savePlaybackState()
                            player.pause()
                        }
                    }
                    else -> Unit
                }
            }.onFailure {
                Log.w("MusicPlayerService", "headsetReceiver failed", it)
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
                // Media3 now owns the foreground with the real media notification.
                // Drop our transient placeholder so "Preparing music…" never sticks
                // and never competes with (or suppresses) the real notification.
                cancelPlaceholderKeepForeground()
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
                // Playback stopped/paused: if our placeholder is still up (e.g. a
                // restore that never started), drop it now. Never touch Media3's
                // own paused notification — only clean up OUR id.
                if (isPlaceholderActive) demoteForegroundIfIdle()
                // Only abandon if not expecting auto-resume (transient/duck pause)
                // and not currently ducking (still playing at low volume)
                if (!shouldResumeOnFocusGain && !isDucking) {
                    abandonAudioFocus()
                }
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateWidget()
            // Async restore path: player may hit READY+playing slightly after
            // onIsPlayingChanged. Belt-and-suspenders: whenever we are actually
            // playing, the placeholder must be gone so the real media
            // notification is the only thing the user sees.
            if (playbackState == Player.STATE_READY) {
                runCatching { if (player.isPlaying) cancelPlaceholderKeepForeground() }
            }
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
            // Catalog streams are set with the watch URL first and resolved to a
            // direct stream async in onMediaItemTransition. If the player errors
            // before that resolve lands, the URL was never playable — retry the
            // resolve in place instead of skipping a healthy queue item (which
            // made catalog taps look dead and left the notification empty).
            runCatching {
                val current = player.currentMediaItem
                if (current != null &&
                    (current.mediaMetadata.extras?.getBoolean("is_catalog") ?: false) &&
                    (current.localConfiguration?.uri == null ||
                        current.localConfiguration?.uri.toString() == current.mediaId)
                ) {
                    resolveCatalogTrack(current, retryPlay = true)
                    return
                }
            }
            // User-first: skip the dead file silently so background playback
            // never stalls on one moved/deleted song. No toast, no scan loop.
            runCatching {
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                    player.prepare()
                    if (!player.isPlaying) player.play()
                } else {
                    player.pause()
                }
            }.onFailure {
                Log.w("MusicPlayerService", "error-skip failed", it)
            }
        }
    }

    private fun resolveCatalogTrack(item: MediaItem, retryPlay: Boolean = false) {
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
                                val wasCurrent = i == player.currentMediaItemIndex
                                player.replaceMediaItem(i, updatedItem)
                                // If this is the current item and we got here via an
                                // error-retry (or it is still the active item), make
                                // sure the new stream actually loads instead of
                                // sitting in error/idle with no notification.
                                if (wasCurrent && (retryPlay || player.playWhenReady || player.isPlaying)) {
                                    runCatching {
                                        player.prepare()
                                        if (!player.isPlaying) player.play()
                                    }
                                }
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
                        runCatching {
                            if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                            else if (player.hasNextMediaItem()) player.seekToNext()
                            else if (player.repeatMode == Player.REPEAT_MODE_ALL) player.seekTo(0, 0L)
                        }
                        return true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        runCatching {
                            if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                            else if (player.currentPosition > 3_000) player.seekTo(0L)
                            else if (player.hasPreviousMediaItem()) player.seekToPrevious()
                        }
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
            return buildResumptionFuture()
        }

        // Media3 1.11 calls this 3-arg overload (not the deprecated 2-arg one)
        // when the process is dead and a headset/BT play button is pressed days
        // later. Without it, resumption silently fails and the user must reopen
        // Toolz manually — the exact regression reported. Both overloads share
        // the same restore path so resume works on every Media3 version.
        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playedFromSearch: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return buildResumptionFuture()
        }
    }

    /**
     * Shared playback-resumption future used by both onPlaybackResumption
     * overloads. Reads the last-played DataStore checkpoint (uri + position +
     * queue), re-resolves deleted/re-indexed tracks, and returns the items with
     * the exact saved start position so the system can resume without Toolz
     * ever coming to the foreground.
     */
    private fun buildResumptionFuture(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val setter = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        serviceScope.launch {
            runCatching {
                val uri = settingsRepository.musicLastPlayedUri.first()
                val pos = settingsRepository.musicLastPlayedPosition.first().coerceAtLeast(0L)
                val queueJson = settingsRepository.musicLastPlayedQueue.first()

                if (uri != null) {
                    val saved = readPersistedQueue(queueJson)
                    var items = resolveQueueTracks(saved)
                    if (items.isEmpty()) {
                        val single = musicRepository.getTrackByUri(uri)
                            ?: musicRepository.getTrackBySourceUrl(uri)
                        if (single != null) items = mutableListOf(single.toMediaItem())
                    }
                    // Last resort: never return empty when the library still has
                    // songs — fall back to the most recent track so a headset
                    // PLAY press days later always does something.
                    if (items.isEmpty()) {
                        val fallback = runCatching {
                            musicRepository.getAllTracksSyncForBackfill().firstOrNull()
                        }.getOrNull()
                        if (fallback != null) items = mutableListOf(fallback.toMediaItem())
                    }
                    if (items.isEmpty()) {
                        setter.setException(Exception("No playable tracks"))
                        return@launch
                    }
                    var startIndex = items.indexOfFirst { it.mediaId == uri }.coerceAtLeast(0)
                        .coerceIn(0, items.size - 1)
                    setter.set(MediaSession.MediaItemsWithStartPosition(items, startIndex, pos))
                } else {
                    setter.setException(Exception("No last played track"))
                }
            }.onFailure {
                runCatching { setter.setException(it as? Exception ?: Exception(it)) }
            }
        }
        return setter
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
        // User-first timing: 400ms grouping + 250ms settle keeps single-press
        // snappy (~250ms) while double/triple (next/prev) still register.
        // The old 500ms + 350ms made every earphone press feel dead.
        if (now - lastMediaButtonClickTime > 400) {
            mediaButtonClickCount = 1
        } else {
            mediaButtonClickCount++
        }
        lastMediaButtonClickTime = now

        mediaButtonCheckJob?.cancel()
        mediaButtonCheckJob = serviceScope.launch {
            delay(250)
            runCatching {
                when (mediaButtonClickCount) {
                    1 -> {
                        if (player.mediaItemCount == 0) {
                            restorePlaybackState(autoPlay = true)
                        } else {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    }
                    2 -> {
                        if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                        else if (player.hasNextMediaItem()) player.seekToNext()
                        else player.play()
                    }
                    3 -> {
                        if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                        else if (player.hasPreviousMediaItem()) player.seekToPrevious()
                        else player.play()
                    }
                    else -> Unit
                }
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

    /**
     * Synchronously satisfy the startForegroundService() contract. Must do
     * zero suspend/blocking work — just channel + placeholder notification.
     *
     * Only posts when a real FGS start needs satisfying: a non-null widget /
     * media-button action while NOT already playing. Warm-up starts (null
     * action from MainActivity/ViewModel via plain startService) never need
     * foreground, and posting a placeholder for them is what caused the
     * flickering/stuck "Preparing music…" notification on every app open.
     */
    private fun ensureForegroundForStartCommand(action: String?) {
        try {
            if (action == null) return
            if (runCatching { player.isPlaying }.getOrDefault(false)) return
            if (isPlaceholderActive) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(
                        FGS_CHANNEL_ID,
                        "Music playback",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Keeps music playing in the background"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                    }
                )
            }
            val contentIntent = PendingIntent.getActivity(
                this, 2001,
                Intent(this, MainActivity::class.java).apply {
                    putExtra("navigate_to", "music_player")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(this, FGS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Preparing music…")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FGS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(FGS_NOTIFICATION_ID, notification)
            }
            isPlaceholderActive = true
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "ensureForeground failed", e)
        }
    }

    /**
     * Remove ONLY our transient placeholder (id 1101), keeping Media3's own
     * media notification / foreground state untouched so the real player
     * notification always shows up once playback starts.
     */
    private fun cancelPlaceholderKeepForeground() {
        if (!isPlaceholderActive) return
        isPlaceholderActive = false
        placeholderDemoteJob?.cancel()
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(FGS_NOTIFICATION_ID)
        }
    }

    /**
     * Drop the placeholder when playback isn't active. Only ever touches OUR
     * id and only exits foreground if WE are the ones holding it (flag set).
     * Uses REMOVE (not DETACH) so the placeholder can't linger as a stuck
     * regular notification after demotion.
     */
    private fun demoteForegroundIfIdle() {
        try {
            if (!isPlaceholderActive) return
            val playing = runCatching { player.isPlaying }.getOrDefault(false)
            if (playing) {
                // Media3 owns foreground now — just remove our id.
                cancelPlaceholderKeepForeground()
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            runCatching {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(FGS_NOTIFICATION_ID)
            }
            isPlaceholderActive = false
            placeholderDemoteJob?.cancel()
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "demoteForeground failed", e)
        }
    }

    private fun scheduleDemoteIfIdle(delayMs: Long) {
        placeholderDemoteJob?.cancel()
        if (!isPlaceholderActive) return
        placeholderDemoteJob = serviceScope.launch {
            kotlinx.coroutines.delay(delayMs)
            demoteForegroundIfIdle()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ANR fix: promote synchronously BEFORE any async restore/widget work,
        // but only when a real action needs it (see helper — warm-ups skip).
        val action = intent?.action
        ensureForegroundForStartCommand(action)
        try {
            when (action) {
                ACTION_TOGGLE_PLAY -> {
                    if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                    else if (player.isPlaying) player.pause() else player.play()
                }
                ACTION_SKIP_NEXT -> {
                    if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                    else if (player.hasNextMediaItem()) player.seekToNext()
                    else if (player.repeatMode == Player.REPEAT_MODE_ALL) player.seekTo(0, 0L)
                    else Unit
                }
                ACTION_SKIP_PREV -> {
                    if (player.mediaItemCount == 0) restorePlaybackState(autoPlay = true)
                    else if (player.currentPosition > 3_000) player.seekTo(0L)
                    else if (player.hasPreviousMediaItem()) player.seekToPrevious()
                    else Unit
                }
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
                val dur = try { player.duration } catch (_: Exception) { -1L }
                if (pos >= 0 && (dur <= 0 || pos <= dur)) {
                    runCatching { player.seekTo(pos) }
                }
            }
                else -> Unit
            }
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "onStartCommand failed for $action", e)
        }
        updateWidget()
        val superResult = try {
            super.onStartCommand(intent, flags, startId)
        } catch (e: Exception) {
            Log.w("MusicPlayerService", "super.onStartCommand failed", e)
            START_STICKY
        }
        // Don't hold the placeholder when idle. Playback-triggering actions may
        // resolve asynchronously (restore from DataStore), so give them a short
        // grace window before demoting — if playback starts in that window,
        // onIsPlayingChanged cancels the placeholder and Media3's real media
        // notification takes over. Non-playback actions demote immediately.
        val needsGrace = action == ACTION_TOGGLE_PLAY ||
            action == ACTION_SKIP_NEXT ||
            action == ACTION_SKIP_PREV ||
            action == ACTION_SEEK_TO_QUEUE_INDEX
        val playingNow = runCatching { player.isPlaying }.getOrDefault(false)
        if (playingNow) {
            cancelPlaceholderKeepForeground()
        } else if (needsGrace) {
            scheduleDemoteIfIdle(1_500L)
        } else {
            demoteForegroundIfIdle()
        }
        return superResult
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
        val currentItem = try { player.currentMediaItem } catch (_: Exception) { return }
            ?: return
        val position = try { player.currentPosition } catch (_: Exception) { return }
        val uri = currentItem.mediaId
        if (uri.isBlank()) return

        // Persist rich entries (uri + stableId + sourceUrl) so a MediaStore
        // re-index (content:// ID change) can still re-resolve. Reader stays
        // backward-compatible with the legacy List<String> format.
        val entries = mutableListOf<Map<String, String?>>()
        for (i in 0 until try { player.mediaItemCount } catch (_: Exception) { 0 }) {
            runCatching {
                val item = player.getMediaItemAt(i)
                val extras = item.mediaMetadata.extras
                entries.add(
                    mapOf(
                        "uri" to item.mediaId,
                        "stable_id" to extras?.getString("stable_id"),
                        "source_url" to extras?.getString("source_url")
                    )
                )
            }
        }
        val queueJson = runCatching {
            moshi.adapter<List<Map<String, String?>>>(
                Types.newParameterizedType(
                    List::class.java,
                    Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                )
            ).toJson(entries)
        }.getOrNull() ?: runCatching {
            moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
                .toJson(entries.mapNotNull { it["uri"] })
        }.getOrNull()

        serviceScope.launch {
            runCatching { settingsRepository.setMusicLastPlayedState(uri, position, queueJson) }
        }
    }

    /** Reads both the new rich queue format and the legacy List<String> format. */
    private suspend fun readPersistedQueue(queueJson: String?): List<Triple<String, String?, String?>> {
        if (queueJson.isNullOrBlank()) return emptyList()
        // New: List<Map<uri, stable_id, source_url>>
        runCatching {
            val adapter = moshi.adapter<List<Map<String, String?>>>(
                Types.newParameterizedType(
                    List::class.java,
                    Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                )
            )
            val parsed = adapter.fromJson(queueJson)
            if (parsed != null) {
                val mapped = parsed.mapNotNull { m ->
                    val u = m["uri"] ?: return@mapNotNull null
                    Triple(u, m["stable_id"], m["source_url"])
                }
                // Distinguish from legacy: rich saves always contain the keys,
                // legacy parses as garbage — only accept if at least one entry
                // actually carried metadata or the raw json looks like objects.
                if (mapped.isNotEmpty() && queueJson.trimStart().startsWith("[")) {
                    // If entries have no metadata at all, fall through to legacy parse
                    // to avoid misreading; otherwise accept.
                    if (mapped.any { it.second != null || it.third != null } || queueJson.contains("stable_id") || queueJson.contains("source_url")) {
                        return mapped
                    }
                } else if (mapped.isNotEmpty()) {
                    return mapped
                }
            }
        }
        // Legacy: List<String>
        runCatching {
            val uris = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
                .fromJson(queueJson) ?: emptyList()
            return uris.map { Triple(it, null, null) }
        }
        return emptyList()
    }

    private suspend fun resolveQueueTracks(
        saved: List<Triple<String, String?, String?>>
    ): List<MediaItem> {
        if (saved.isEmpty()) return emptyList()
        val resolved = mutableListOf<MediaItem>()
        var missing = 0
        for ((uri, stableId, sourceUrl) in saved) {
            val track = musicRepository.resolveTrackForPlayback(uri, sourceUrl, stableId)
            if (track != null) resolved.add(track.toMediaItem())
            else missing++
        }
        if (missing > 0) {
            Log.w("MusicPlayerService", "restore resolved ${resolved.size}/${saved.size} (pruned $missing deleted)")
        }
        return resolved
    }

    private fun restorePlaybackState(autoPlay: Boolean = false) {
        serviceScope.launch {
            val uri = settingsRepository.musicLastPlayedUri.first()
            val position = settingsRepository.musicLastPlayedPosition.first().coerceAtLeast(0L)
            val queueJson = settingsRepository.musicLastPlayedQueue.first()

            if (uri != null) {
                // Resolve the last track itself with stable fallbacks first so we
                // always have at least one playable item even if the queue is stale.
                val lastTrack = musicRepository.getTrackByUri(uri)
                    ?: musicRepository.getTrackBySourceUrl(uri)
                val saved = readPersistedQueue(queueJson)
                var items = resolveQueueTracks(saved)
                if (items.isEmpty() && lastTrack != null) {
                    items = mutableListOf(lastTrack.toMediaItem())
                }
                if (items.isEmpty()) {
                    // Best for the user: never leave the player empty when the
                    // library still has songs. Fall back to the most recent
                    // library track so a headset PLAY press does something.
                    // If the library itself is empty (fresh install / permission
                    // revoked), stay empty silently — nothing to play.
                    val fallback = runCatching {
                        musicRepository.getAllTracksSyncForBackfill().firstOrNull()
                    }.getOrNull()
                    if (fallback != null) {
                        Log.w("MusicPlayerService", "restore queue empty, falling back to most recent library track")
                        items = mutableListOf(fallback.toMediaItem())
                    }
                }
                if (items.isEmpty()) {
                    Log.w("MusicPlayerService", "restorePlaybackState: nothing playable, staying empty")
                    return@launch
                }

                val safePosition = position
                withContext(Dispatchers.Main) {
                    runCatching {
                        var startIndex = items.indexOfFirst { it.mediaId == uri }.coerceAtLeast(0)
                        // If the exact URI is gone (re-index), the resolved list
                        // holds the same song under a new URI — anchor on the
                        // last track's current URI instead of index 0 guess.
                        if (startIndex == 0 && lastTrack != null && items.none { it.mediaId == uri }) {
                            startIndex = items.indexOfFirst { it.mediaId == lastTrack.uri }.coerceAtLeast(0)
                        }
                        startIndex = startIndex.coerceIn(0, items.size - 1)
                        // Don't clobber a queue the user just built while we
                        // were resolving (tap raced restore): if the player
                        // gained items meanwhile, keep them.
                        if (player.mediaItemCount != 0) {
                            Log.w("MusicPlayerService", "restore skipped: player gained queue during resolve")
                            if (autoPlay && !player.isPlaying) player.play()
                            return@withContext
                        }
                        player.setMediaItems(items, startIndex, safePosition)
                        player.prepare()
                        if (autoPlay) player.play()
                    }.onFailure {
                        Log.e("MusicPlayerService", "restorePlaybackState failed", it)
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
        // Rich format (uri + stable_id + source_url), backward-compatible reader.
        runCatching {
            val item = try { player.currentMediaItem } catch (_: Exception) { null }
            if (item != null) {
                val uri = item.mediaId
                val pos = try { player.currentPosition } catch (_: Exception) { -1L }
                if (uri.isNotBlank() && pos >= 0) {
                    val entries = mutableListOf<Map<String, String?>>()
                    runCatching {
                        for (i in 0 until player.mediaItemCount) {
                            val it = player.getMediaItemAt(i)
                            val extras = it.mediaMetadata.extras
                            entries.add(
                                mapOf(
                                    "uri" to it.mediaId,
                                    "stable_id" to extras?.getString("stable_id"),
                                    "source_url" to extras?.getString("source_url")
                                )
                            )
                        }
                    }
                    val queueJson = runCatching {
                        moshi.adapter<List<Map<String, String?>>>(
                            Types.newParameterizedType(
                                List::class.java,
                                Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                            )
                        ).toJson(entries)
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
        placeholderDemoteJob?.cancel()
        isPlaceholderActive = false
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(FGS_NOTIFICATION_ID)
        }
        serviceScope.cancel()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}