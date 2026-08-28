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

package com.frerox.toolz.ui.screens.media

import com.frerox.toolz.BuildConfig
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.audiofx.Equalizer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.lifecycle.asFlow
import androidx.work.WorkManager
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.data.music.toMediaItem

import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.MusicPlayerService
import com.frerox.toolz.util.MusicVisualizerManager
import com.frerox.toolz.util.OfflineManager
import com.frerox.toolz.util.OfflineState
import com.frerox.toolz.util.VibrationManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// P1-02: MusicUiState/QueueEntry/SortOrder extracted to MusicUiState.kt

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    val repository          : MusicRepository,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val offlineManager    : OfflineManager,
    val vibrationManager          : VibrationManager,
    val player                    : ExoPlayer,
    private val visualizerManager : MusicVisualizerManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()
    // P2-01 fix: sliced flows so sections can collect only what they need without full-screen recomposition
    val currentTrackFlow = uiState.map { it.currentTrack }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isPlayingFlow = uiState.map { it.isPlaying }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _sliderPosition = MutableStateFlow<Long?>(null)
    val sliderPosition = _sliderPosition.asStateFlow()

    private val _showSleepTimer = MutableStateFlow(false)
    val showSleepTimer = _showSleepTimer.asStateFlow()

    private var progressJob       : Job? = null
    private var sleepTimerJob     : Job? = null
    private var controllerFuture  : ListenableFuture<MediaController>? = null
    private var controller        : MediaController? = null
    private var pendingAction     : (() -> Unit)? = null
    private var equalizer         : Equalizer?  = null

    // Reused across every onFftDataCapture callback (fires up to ~60x/sec)
    // to avoid allocating fresh FloatArrays on the audio capture thread.
    // `spectrumScratch` holds the in-progress computation; `spectrumBufA/B`
    // are alternated as the published result so the buffer currently held
    // by `_visualizerData.value` (being read on the UI thread) is never the
    // same object this thread is about to write into next.

    // P1-05 fix: shake-to-skip is now owned solely by MusicPlayerService (single sensor listener).
    // ViewModel no longer registers its own accelerometer listener to avoid double-skip and
    // conflicting thresholds (service uses single-shake 15f, ShakeDetector required triple-shake).
    private val _visualizerData = MutableStateFlow(FloatArray(0))
    val visualizerData = _visualizerData.asStateFlow()

    private val _equalizerPresets = MutableStateFlow<List<String>>(emptyList())
    val equalizerPresets = _equalizerPresets.asStateFlow()

    private var currentQueueUris: List<String> = emptyList()


    private fun hapticClick() { if (!_uiState.value.isKaraokeActive) vibrationManager.vibrateClick() }
    private fun hapticSuccess() { if (!_uiState.value.isKaraokeActive) vibrationManager.vibrateSuccess() }
    private fun hapticTick() { if (!_uiState.value.isKaraokeActive) vibrationManager.vibrateTick() }

    // ─────────────────────────────────────────────────────────────────────────
    // Player listener
    // ─────────────────────────────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdate()
                startPlayerService()
                if (_uiState.value.showVisualizer) startVisualizer()
                initEqualizer()
                hapticClick()
            } else {
                stopProgressUpdate()
                // FIX: Only stop visualizer if we are NOT playWhenReady. 
                // This keeps bars from freezing/clipping during a rebuffer blip.
                if (!_uiState.value.playWhenReady) stopVisualizer()
                hapticClick()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _uiState.update { it.copy(playWhenReady = playWhenReady) }
        }

        /**
         * Called when the player transitions to a new media item.
         *
         * FIX: The original code never cleared `isResolvingCatalog` here.
         * When skipping to the next song, ExoPlayer goes through
         * STATE_BUFFERING before STATE_READY, and the catalog resolve state
         * was never reset, leaving the UI on an infinite loading screen.
         *
         * We now clear `isResolvingCatalog = false` here so the loading
         * spinner immediately hides when a new item starts loading.
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Immediately clear catalog resolve state – the player now owns the item.
            _uiState.update { it.copy(isResolvingCatalog = false) }

            // Invalidate the session ID on track change to force a re-attach
            // when the player becomes ready, and clear current data.

            _visualizerData.value = FloatArray(0)

            val uri       = mediaItem?.mediaId ?: mediaItem?.requestMetadata?.mediaUri?.toString()
            val metadata  = mediaItem?.mediaMetadata
            val sourceUrl = metadata?.extras?.getString("source_url")

            viewModelScope.launch {
                var track = repository.getTrackByUri(uri ?: "")

                if (track == null && sourceUrl != null) {
                    track = repository.getTrackBySourceUrl(sourceUrl)
                }

                // Persist ephemeral external tracks so they appear in Recently Played.
                if (track == null && mediaItem != null) {
                    val ephUri = uri ?: ""
                    val ephTitle = metadata?.title?.toString() ?: "External Audio"
                    val ephArtist = metadata?.artist?.toString() ?: "Unknown"
                    track = MusicTrack(
                        uri         = ephUri,
                        title       = ephTitle,
                        artist      = ephArtist,
                        album       = metadata?.albumTitle?.toString()  ?: "Unknown",
                        duration    = player.duration.coerceAtLeast(0L),
                        thumbnailUri = metadata?.artworkUri?.toString() ?: "",
                        sourceUrl   = sourceUrl,
                        lastPlayed  = System.currentTimeMillis(),
                        stableId    = (sourceUrl ?: ephUri).hashCode().toString() + "_" + ephTitle.hashCode()
                    )
                    repository.insertTrack(track)
                } else if (track != null) {
                    val updated = track.copy(
                        lastPlayed = System.currentTimeMillis(),
                        duration   = if (player.duration > 0 && track.duration <= 0)
                            player.duration else track.duration
                    )
                    repository.updateTrack(updated)
                    track = updated
                }

                val dur = player.duration.coerceAtLeast(0L)
                _uiState.update { it.copy(currentTrack = track, duration = dur) }
                _duration.value = dur

                if (track != null) repository.incrementPlayCount(track)
                updateQueue()
            }
        }

        /**
         * FIX: The original code only updated duration on STATE_READY, but
         * never cleared `isResolvingCatalog` on STATE_BUFFERING or STATE_READY
         * when arriving from a next-song skip.
         *
         * Now:
         *  - STATE_BUFFERING → clear resolve flag, show a slim loading indicator
         *    via `isLoading` only if we have no current track yet.
         *  - STATE_READY     → always clear resolve flag + update duration.
         *  - STATE_ENDED     → clear everything cleanly.
         */
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    // If we somehow still show a "resolving" overlay, clear it –
                    // the player has accepted the item and is buffering normally.
                    _uiState.update { it.copy(isResolvingCatalog = false) }
                }

                Player.STATE_READY -> {
                    val dur = player.duration.coerceAtLeast(0L)
                    _uiState.update { it.copy(duration = dur, isResolvingCatalog = false) }
                    _duration.value = dur

                    // Persist duration if it was previously unknown
                    val currentTrack = _uiState.value.currentTrack
                    if (currentTrack != null && dur > 0 && currentTrack.duration <= 0) {
                        val updated = currentTrack.copy(duration = dur)
                        viewModelScope.launch { repository.updateTrack(updated) }
                        _uiState.update { it.copy(currentTrack = updated) }
                    }

                    // Extra safety net for the visualizer: if a skip or transition left it
                    // unattached or attached to a stale session, the track
                    // becoming READY is the clearest possible signal to sync
                    // right now.
                    if (_uiState.value.showVisualizer && player.isPlaying) {
                        startVisualizer()
                    }
                }

                Player.STATE_ENDED -> {
                    _uiState.update { it.copy(isResolvingCatalog = false, isPlaying = false) }
                }

                Player.STATE_IDLE -> {
                    _uiState.update { it.copy(isResolvingCatalog = false) }
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
            hapticClick()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(isShuffleOn = shuffleModeEnabled) }
            hapticClick()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            super.onPlayerError(error)
            Log.e("MusicPlayerViewModel", "Playback error: ${error.errorCodeName}", error)
            _uiState.update { it.copy(isResolvingCatalog = false, isPlaying = false, isLoading = false) }

            // Auto-refresh/heal library if file was moved or not found
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.scanDeviceForMusic() }
            }

            if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
            ) {
                // P0-03 fix: only re-prepare for transient live-window/timeout, don't nuke queue.
                player.prepare()
            } else {
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Playback error: ${error.localizedMessage ?: "File moved or unplayable. Skipping."}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    // P0-03: preserve queue — skip to next if possible instead of clearing everything.
                    val p: Player = controller ?: player
                    if (p.hasNextMediaItem()) {
                        p.seekToNext()
                        p.prepare()
                    } else {
                        // No next item: just pause, keep current queue intact for retry.
                        p.pause()
                    }
                }
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            updateQueue()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queue helper
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateQueue() {
        viewModelScope.launch(Dispatchers.Main) {
            val p: Player = controller ?: player
            val trackMap  = _uiState.value.tracks.associateBy { it.uri }
            val entries   = mutableListOf<QueueEntry>()
            val uris      = mutableListOf<String>()
            val timeline  = p.currentTimeline
            val window    = androidx.media3.common.Timeline.Window()

            for (i in 0 until p.mediaItemCount) {
                val item = p.getMediaItemAt(i)
                uris.add(item.mediaId)
                val stableId = if (!timeline.isEmpty && i < timeline.windowCount) {
                    timeline.getWindow(i, window).uid.toString()
                } else "${item.mediaId}_$i"

                val track = trackMap[item.mediaId] ?: run {
                    val meta = item.mediaMetadata
                    val tTitle = meta.title?.toString() ?: "External Audio"
                    val tArtist = meta.artist?.toString() ?: "Unknown"
                    MusicTrack(
                        uri    = item.mediaId,
                        title  = tTitle,
                        artist = tArtist,
                        album  = meta.albumTitle?.toString() ?: "Unknown",
                        duration = 0L,
                        stableId = item.mediaId.hashCode().toString() + "_" + tTitle.hashCode()
                    )
                }
                entries.add(QueueEntry(id = stableId, track = track))
            }

            currentQueueUris = uris
            // P0-02 fix: detect silently dropped tracks (uris not in trackMap but also not external)
            val missingCount = uris.count { uri ->
                // external audio stubs are expected missing; real missing = was in previous queue but now no track
                trackMap[uri] == null && uri.startsWith("content://")
            }
            val warning = if (missingCount > 0) {
                android.util.Log.w("MusicPlayerVM", "Queue pruned $missingCount missing tracks (deleted/moved)")
                "$missingCount track(s) unavailable — removed from queue"
            } else null
            _uiState.update { it.copy(queue = entries, currentQueueIndex = p.currentMediaItemIndex.coerceAtLeast(0), queueWarning = warning) }
        }
    }

    fun consumeQueueWarning() {
        _uiState.update { it.copy(queueWarning = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data aggregation
    // ─────────────────────────────────────────────────────────────────────────

    private data class MusicData(
        val tracks   : List<MusicTrack>,
        val playlists: List<Playlist>,
        val favorites: List<MusicTrack>,
        val recent   : List<MusicTrack>,
        val most     : List<MusicTrack>
    )

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, false)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(playerListener)
        // The Visualizer must be attached to the *current* audio session id.
        // ExoPlayer can (re)allocate a new session id on track changes, on
        // audio-focus recovery, or a few hundred ms after playback actually
        // starts — any of which used to leave the FFT listener silently
        // attached to a stale/invalid session, which is the root cause of
        // "the visualizer sometimes just stops reacting". Reacting to this
        // callback and re-arming the Visualizer whenever it fires keeps it
        // permanently in sync with whatever session is actually playing.
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                if (_uiState.value.showVisualizer && player.isPlaying) {
                    restartVisualizer()
                }
            }
        })

        connectToMediaController()

        observeSettings()
        observeLibrary()

        // Start real-time MediaStore live observer
        repository.startLiveObserver(viewModelScope)

        if (player.isPlaying) startProgressUpdate()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.showMusicVisualizer.collect { show ->
                _uiState.update { it.copy(showVisualizer = show) }
                // Use _uiState.value.isPlaying for thread safety
                if (show && _uiState.value.isPlaying) startVisualizer() else if (!show) stopVisualizer()
            }
        }
        viewModelScope.launch {
            settingsRepository.performanceMode.collect { perf ->
                _uiState.update { it.copy(performanceMode = perf) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicArtShape.collect { shape ->
                _uiState.update { it.copy(artShape = shape) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicRotationEnabled.collect { enabled ->
                _uiState.update { it.copy(rotationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicPlaybackSpeed.collect { speed ->
                player.playbackParameters = PlaybackParameters(speed)
                _uiState.update { it.copy(playbackSpeed = speed) }
            }
        }
        // P1-05: shake handled by service — no ViewModel sensor registration needed.
        viewModelScope.launch {
            settingsRepository.musicEqualizerPreset.collect { preset ->
                _uiState.update { it.copy(equalizerPreset = preset) }
                applyEqualizerPreset(preset)
            }
        }
        viewModelScope.launch {
            settingsRepository.musicCustomEqualizer.collect { data ->
                if (data.isNotBlank()) {
                    val gains = data.split(",").mapNotNull { it.toFloatOrNull() }
                    if (gains.size >= 5) {
                        _uiState.update { it.copy(customEqualizerGains = gains) }
                        if (_uiState.value.equalizerPreset == "Custom") applyCustomEqualizer(gains)
                    }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicVisualizerSensitivity.collect { sens ->
                _uiState.update { it.copy(visualizerSensitivity = sens) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicVisualizerAutoSensitivity.collect { enabled ->
                _uiState.update { it.copy(visualizerAutoSensitivity = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.catalogStreamQuality.collect { quality ->
                _uiState.update { it.copy(catalogStreamQuality = quality) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hapticFeedback.collect { enabled ->
                _uiState.update { it.copy(hapticEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hapticIntensity.collect { intensity ->
                _uiState.update { it.copy(hapticIntensity = intensity) }
            }
        }
        viewModelScope.launch {
            settingsRepository.karaokeEnabled.collect { enabled ->
                _uiState.update { it.copy(karaokeEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicPipEnabled.collect { enabled ->
                _uiState.update { it.copy(pipEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            offlineManager.offlineState.collect { state ->
                _uiState.update { it.copy(isOnline = state == OfflineState.ONLINE) }
            }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch(Dispatchers.Default) {
            val combinedFlow = combine(
                repository.allTracks,
                repository.allPlaylists,
                repository.favoriteTracks,
                repository.recentlyPlayed,
                repository.mostPlayed
            ) { tracks, playlists, favorites, recent, most ->
                MusicData(tracks, playlists, favorites, recent, most)
            }

            combinedFlow.combine(
                combine(
                    _uiState.map { it.sortOrder }.distinctUntilChanged(),
                    _uiState.map { it.isOnline  }.distinctUntilChanged()
                ) { sort, online -> sort to online }
            ) { data, (sortOrder, isOnline) ->

                // De-duplicate: prefer offline (path != null) copies
                val deduped = data.tracks
                    .groupBy { "${it.title}|${it.artist}" }
                    .map { (_, group) -> group.find { it.path != null } ?: group.first() }

                val visible = if (!isOnline) deduped.filter { it.path != null } else deduped

                val sorted = when (sortOrder) {
                    SortOrder.TITLE  -> visible.sortedBy { it.title }
                    SortOrder.ARTIST -> visible.sortedBy { it.artist ?: "Unknown" }
                    SortOrder.RECENT -> visible.reversed()
                }.sortedWith(
                    compareByDescending<MusicTrack> { it.thumbnailUri != null }
                        .thenByDescending { it.artist != null && it.artist != "Unknown Artist" && it.artist != "<unknown>" }
                )

                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val toolzPath = File(downloadsDir, "Toolz").absolutePath
                val folders = sorted.groupBy { track ->
                    if (track.album == "Toolz Downloads" ||
                        (track.path != null && track.path.startsWith(toolzPath))
                    ) "Toolz Downloads"
                    else {
                        val p = track.path ?: track.uri.toUri().path ?: ""
                        if (p.contains("/")) p.substringBeforeLast("/").substringAfterLast("/").ifEmpty { "Internal Storage" }
                        else "Internal Storage"
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val currentMediaId = player.currentMediaItem?.mediaId
                        val newCurrentTrack = data.tracks.find { it.uri == currentMediaId || (it.sourceUrl != null && it.sourceUrl == currentMediaId) }
                            ?: state.currentTrack

                        state.copy(
                            tracks          = sorted,
                            playlists       = data.playlists.sortedByDescending { it.createdAt },
                            favoriteTracks  = data.favorites,
                            recentlyPlayed  = data.recent,
                            mostPlayed      = data.most,
                            folders         = folders,
                            currentTrack    = newCurrentTrack,
                            isPlaying       = player.isPlaying,
                            playWhenReady   = player.playWhenReady,
                            isShuffleOn     = player.shuffleModeEnabled,
                            repeatMode      = player.repeatMode,
                            duration        = player.duration.coerceAtLeast(0L)
                        )
                    }
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
            }.collect()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MediaController
    // ─────────────────────────────────────────────────────────────────────────

    private fun connectToMediaController() {
        val token = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            runCatching {
                controller = controllerFuture?.get()

                // Every queue/move/remove/clear call in this ViewModel targets
                // `controller ?: player` once connected, so `controller` is
                // the Player instance that actually fires timeline/media-item
                // events from here on. `playerListener` was previously only
                // ever attached to the local `player` (see init{}), so once
                // `controller` took over, onTimelineChanged (→ updateQueue()),
                // onIsPlayingChanged, and onMediaItemTransition all went silent
                // for controller-driven changes — including every reorder,
                // remove, and clear fired from the Up Next sheet. Attaching
                // the listener here is what makes the sheet (and playback
                // state generally) actually reflect controller-driven changes.
                controller?.addListener(playerListener)

                val dur = controller?.duration?.coerceAtLeast(0L) ?: 0L
                val currentMediaItem = controller?.currentMediaItem
                val mediaId = currentMediaItem?.mediaId ?: currentMediaItem?.requestMetadata?.mediaUri?.toString()
                val metadata = currentMediaItem?.mediaMetadata
                val sourceUrl = metadata?.extras?.getString("source_url")

                _uiState.update {
                    it.copy(
                        isPlaying    = controller?.isPlaying ?: false,
                        isShuffleOn  = controller?.shuffleModeEnabled ?: false,
                        repeatMode   = controller?.repeatMode ?: Player.REPEAT_MODE_OFF,
                        duration     = dur
                    )
                }

                // Properly restore current track even if it's an online catalog track
                if (mediaId != null) {
                    viewModelScope.launch {
                        var track = repository.getTrackByUri(mediaId)
                        if (track == null && sourceUrl != null) {
                            track = repository.getTrackBySourceUrl(sourceUrl)
                        }
                        
                        // If still null, it might be an ephemeral track not yet persisted or a catalog track
                        if (track == null && metadata != null) {
                            val cTitle = metadata.title?.toString() ?: "External Audio"
                            track = MusicTrack(
                                uri = mediaId,
                                title = cTitle,
                                artist = metadata.artist?.toString() ?: "Unknown",
                                album = metadata.albumTitle?.toString() ?: "Unknown",
                                duration = dur,
                                thumbnailUri = metadata.artworkUri?.toString() ?: "",
                                sourceUrl = sourceUrl,
                                lastPlayed = System.currentTimeMillis(),
                                stableId = (sourceUrl ?: mediaId).hashCode().toString() + "_" + cTitle.hashCode()
                            )
                        }

                        _uiState.update { it.copy(currentTrack = track) }
                    }
                }

                _duration.value = dur
                updateQueue()
                pendingAction?.invoke()
                pendingAction = null
            }.onFailure { it.printStackTrace() }
        }, MoreExecutors.directExecutor())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Equalizer
    // ─────────────────────────────────────────────────────────────────────────

    private fun initEqualizer() {
        if (equalizer != null || player.audioSessionId == 0) return
        runCatching {
            equalizer = Equalizer(0, player.audioSessionId).apply { enabled = true }
            val presets = (0 until (equalizer?.numberOfPresets?.toInt() ?: 0))
                .mapNotNull { equalizer?.getPresetName(it.toShort()) }
                .toMutableList()
            listOf("Bass Boost", "Vocal Booster", "Treble Booster", "Electronic")
                .forEach { if (!presets.contains(it)) presets.add(it) }
            _equalizerPresets.value = presets.distinct()
            viewModelScope.launch { applyEqualizerPreset(settingsRepository.musicEqualizerPreset.first()) }
        }
    }

    private fun applyEqualizerPreset(preset: String) {
        if (preset == "Custom") { applyCustomEqualizer(_uiState.value.customEqualizerGains); return }
        val eq = equalizer ?: return
        runCatching {
            for (i in 0 until eq.numberOfPresets.toInt()) {
                if (eq.getPresetName(i.toShort()).equals(preset, ignoreCase = true)) {
                    eq.usePreset(i.toShort()); return
                }
            }
            // Custom-mapped presets
            val numBands = eq.numberOfBands.toInt()
            when (preset) {
                "Bass Boost" -> {
                    for (i in 0 until numBands) {
                        val f = eq.getCenterFreq(i.toShort())
                        eq.setBandLevel(i.toShort(), (if (f < 500_000) 1000 else 0).toShort())
                    }
                }
                "Vocal Booster" -> {
                    for (i in 0 until numBands) {
                        val f = eq.getCenterFreq(i.toShort())
                        eq.setBandLevel(i.toShort(), (if (f in 500_000..3_000_000) 800 else -200).toShort())
                    }
                }
                "Treble Booster" -> {
                    for (i in 0 until numBands) {
                        val f = eq.getCenterFreq(i.toShort())
                        eq.setBandLevel(i.toShort(), (if (f > 3_000_000) 1000 else 0).toShort())
                    }
                }
                "Electronic" -> {
                    for (i in 0 until numBands) {
                        val f = eq.getCenterFreq(i.toShort())
                        val lv = when {
                            f < 250_000         -> 800
                            f in 250_000..1_000_000 -> 0
                            f > 4_000_000       -> 600
                            else                -> 200
                        }
                        eq.setBandLevel(i.toShort(), lv.toShort())
                    }
                }
            }
        }
    }

    private fun applyCustomEqualizer(gains: List<Float>) {
        val eq = equalizer ?: return
        runCatching {
            gains.forEachIndexed { i, gain ->
                if (i < eq.numberOfBands) {
                    eq.setBandLevel(i.toShort(), (gain * 1500).toInt().coerceIn(-1500, 1500).toShort())
                }
            }
        }
    }

    fun setCustomEqualizerGain(band: Int, gain: Float) {
        val gains = _uiState.value.customEqualizerGains.toMutableList()
        if (band !in gains.indices) return
        gains[band] = gain
        _uiState.update { it.copy(customEqualizerGains = gains) }
        viewModelScope.launch { settingsRepository.setMusicCustomEqualizer(gains.joinToString(",")) }
        if (_uiState.value.equalizerPreset == "Custom") applyCustomEqualizer(gains)
    }

    fun setEqualizerPreset(preset: String) {
        viewModelScope.launch {
            settingsRepository.setMusicEqualizerPreset(preset)
            _uiState.update { it.copy(equalizerPreset = preset) }
            applyEqualizerPreset(preset)
            hapticClick()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Visualizer
    // ─────────────────────────────────────────────────────────────────────────

    // Waiting for ExoPlayer to allocate a real, attachable audio session id.
    // Right after `play()` (or right after a skip, while the next track is
    // still buffering) `audioSessionId` can read 0 for an unpredictable
    // amount of time — a network fetch on the new track can easily take
    // longer than a short fixed retry burst. The previous version gave up
    // after ~600ms and never tried again, which is exactly why skipping to
    // a track that took a moment to load left the visualizer permanently
    // frozen. Retrying for as long as it's still relevant (visualizer not
    // yet attached, and still supposed to be showing) fixes that without
    // risking an infinite loop, since it stops the moment either condition
    // flips.
    private var visualizerAttachJob: kotlinx.coroutines.Job? = null

    private fun startVisualizer() {
        if (!_uiState.value.showVisualizer || _uiState.value.isKaraokeActive) {
            if (BuildConfig.DEBUG) android.util.Log.d("MusicVisualizer", "startVisualizer: skipped, showVisualizer=${_uiState.value.showVisualizer} isKaraokeActive=${_uiState.value.isKaraokeActive}")
            return
        }

        visualizerAttachJob?.cancel()
        visualizerAttachJob = viewModelScope.launch(Dispatchers.Default) {
            val emptySpectrum = FloatArray(64)
            var lastSpectrum = FloatArray(64)
            
            while (currentCoroutineContext().isActive && _uiState.value.showVisualizer) {
                // FIX: Use _uiState.value.isPlaying for thread-safe access from background coroutine.
                if (_uiState.value.isPlaying) {
                    val spectrum = visualizerManager.getSpectrum()
                    
                    // Implement "Gravity" for smoother decay and instant peaks.
                    // We execute this EVERY frame while playing, even if spectrum is null,
                    // to ensure bars don't "freeze" during tiny data gaps.
                    val smoothed = FloatArray(64)
                    for (i in 0 until 64) {
                        val target = spectrum?.get(i) ?: 0f
                        val prev = lastSpectrum[i]
                        // Rise instantly to catch transients, fall with gravity (0.78f) for aggressive, rhythmic motion.
                        smoothed[i] = if (target > prev) target else prev * 0.78f
                    }
                    lastSpectrum = smoothed
                    _visualizerData.value = smoothed
                } else {
                    // Decay to zero when paused
                    val current = _visualizerData.value
                    if (current.isNotEmpty() && current.any { it > 0.01f }) {
                        val decayed = current.map { it * 0.8f }.toFloatArray()
                        _visualizerData.value = decayed
                        lastSpectrum = decayed
                    } else if (current.isNotEmpty()) {
                        _visualizerData.value = emptySpectrum
                        lastSpectrum = emptySpectrum
                    }
                }
                delay(16L) // ~60fps for high-performance live reaction
            }
        }
    }



    /** Tears down and re-creates the Visualizer against whatever session id is current. */
    private fun restartVisualizer() {
        detachVisualizer()
        startVisualizer()
    }





    private fun detachVisualizer() {
        visualizerAttachJob?.cancel()
        visualizerAttachJob = null
    }

    private fun stopVisualizer() {
        detachVisualizer()
        _visualizerData.value = FloatArray(0)
    }

    fun setVisualizerSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            settingsRepository.setMusicVisualizerSensitivity(sensitivity)
            _uiState.update { it.copy(visualizerSensitivity = sensitivity) }
        }
    }

    fun setVisualizerAutoSensitivity(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMusicVisualizerAutoSensitivity(enabled)
            _uiState.update { it.copy(visualizerAutoSensitivity = enabled) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service
    // ─────────────────────────────────────────────────────────────────────────

    // startService() is a binder IPC call — cheap in isolation, but it was
    // previously fired on *every* play()/skipNext()/skipPrevious()/playTrack()
    // tap, including while the service was already running. That's wasted
    // main-thread work stacking up right in the middle of a tap-to-transport-
    // action path, which is exactly where a dropped frame is most visible.
    // The service only needs to be told to start once per session; guard
    // it so repeated taps don't repeat the IPC.
    private var playerServiceStarted = false
    private fun startPlayerService() {
        if (playerServiceStarted) return
        runCatching {
            context.startService(Intent(context, MusicPlayerService::class.java))
            playerServiceStarted = true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Progress polling
    // ─────────────────────────────────────────────────────────────────────────

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                if (_sliderPosition.value == null) {
                    val p: Player = controller ?: player
                    val pos = p.currentPosition.coerceAtLeast(0)
                    // Only write to the dedicated `_playbackPosition` flow, not
                    // into `_uiState`. `MusicUiState` is one big object collected
                    // once at the top of the whole screen (`val state by
                    // viewModel.uiState.collectAsState()`), so copying it here
                    // was forcing a full-screen recomposition on every tick —
                    // up to 60/sec with fast seeking, lyric sync, or karaoke
                    // active — even though the scrubber already reads position
                    // from this same flow directly via a scoped
                    // collectAsStateWithLifecycle further down the tree. The
                    // `playbackPosition`/`duration` fields on MusicUiState are
                    // kept for callers that only need an occasional snapshot,
                    // not as the hot-path source of truth.
                    _playbackPosition.value = pos

                    val dur = p.duration
                    if (_uiState.value.isKaraokeActive && dur > 0 && pos >= dur - 800 && p.isPlaying) {
                        p.pause()
                    }
                }
                val isSynced = _uiState.value.currentTrack?.aiLyrics?.contains("[0") == true
                val interval = when {
                    _uiState.value.performanceMode         -> 500L
                    _uiState.value.fastSeeking || isSynced || _uiState.value.isKaraokeActive -> 16L
                    else                                   -> 100L
                }
                delay(interval)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        _visualizerData.value = FloatArray(0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Playback controls
    // ─────────────────────────────────────────────────────────────────────────

    fun onSliderChange(position: Long)         { _sliderPosition.value = position }
    fun onSliderChangeFinished() {
        _sliderPosition.value?.let { pos ->
            val p: Player = controller ?: player
            p.seekTo(pos)
            _uiState.update { it.copy(playbackPosition = pos) }
            _playbackPosition.value = pos
            hapticClick()
        }
        _sliderPosition.value = null
    }

    fun refreshLibraryOnOpen() {
        if (repository.hasAudioPermission()) {
            scanMusic()
        }
    }

    fun refreshLibrarySilent() {
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.hasAudioPermission()) {
                repository.scanDeviceForMusic()
            }
        }
    }

    fun scanMusic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.scanDeviceForMusic()
            repository.fixAllThumbnails()
            _uiState.update { it.copy(isLoading = false) }
            hapticSuccess()
        }
    }

    fun fixThumbnails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.fixAllThumbnails()
            _uiState.update { it.copy(isLoading = false) }
            hapticSuccess()
        }
    }

    fun addCustomFolder(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.scanCustomFolder(uri)
            _uiState.update { it.copy(isLoading = false) }
            hapticSuccess()
        }
    }

    fun toggleTrackSelection(uri: String) {
        _uiState.update { state ->
            val sel = if (state.selectedTracks.contains(uri)) state.selectedTracks - uri
            else                                    state.selectedTracks + uri
            state.copy(selectedTracks = sel, isSelectionMode = sel.isNotEmpty())
        }
        hapticClick()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTracks = emptySet(), isSelectionMode = false) }
    }

    fun playTrack(track: MusicTrack, tracks: List<MusicTrack> = _uiState.value.tracks) {
        startPlayerService()

        if (controller == null) {
            pendingAction = { playTrack(track, tracks) }
            connectToMediaController()
            return
        }

        hapticClick()

        viewModelScope.launch {
            // Check if it's an online track that needs resolution
            if (track.path == null && track.sourceUrl != null && !track.uri.startsWith("content://") && !track.uri.startsWith("file://")) {
                _uiState.update { it.copy(isResolvingCatalog = true) }
                try {
                    val quality = settingsRepository.catalogStreamQuality.first()
                    val resolvedUrl = withContext(Dispatchers.IO) {
                        catalogRepository.resolveAudioStream(track.sourceUrl, quality)
                    }
                    val resolvedTrack = track.copy(uri = resolvedUrl)
                    val resolvedTracks = tracks.map { if (it.uri == track.uri) resolvedTrack else it }
                    executePlay(resolvedTrack, resolvedTracks)
                } catch (e: Exception) {
                    _uiState.update { it.copy(isResolvingCatalog = false) }
                    // Show error toast or similar
                } finally {
                    _uiState.update { it.copy(isResolvingCatalog = false) }
                }
            } else {
                executePlay(track, tracks)
            }
        }
    }

    private suspend fun executePlay(track: MusicTrack, tracks: List<MusicTrack>) {
        viewModelScope.launch(Dispatchers.Default) {
            val trackUris  = tracks.map { it.uri }
            val isSameQueue = trackUris == currentQueueUris
            val mediaItems  = tracks.map { t -> t.toMediaItem() }
            val startIndex  = tracks.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)

            withContext(Dispatchers.Main) {
                val p: Player = controller ?: player
                if (isSameQueue) {
                    val idx = trackUris.indexOf(track.uri)
                    if (idx != -1) { p.seekTo(idx, 0L); p.play(); return@withContext }
                }
                p.stop()
                p.setMediaItems(mediaItems, startIndex, 0L)
                p.prepare()
                p.play()
            }
        }
    }

    fun playUri(
        uri       : Uri,
        title     : String?  = null,
        artist    : String?  = null,
        thumbUrl  : String?  = null,
        sourceUrl : String?  = null
    ) {
        startPlayerService()
        if (controller == null) {
            pendingAction = { playUri(uri, title, artist, thumbUrl, sourceUrl) }
            connectToMediaController()
            return
        }
        hapticClick()

        viewModelScope.launch {
            var displayTitle = title ?: "External Audio"
            if (title == null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1 && cursor.moveToFirst()) displayTitle = cursor.getString(idx)
                        }
                    }
                }
            }

            val metaBuilder = MediaMetadata.Builder()
                .setTitle(displayTitle).setDisplayTitle(displayTitle)
                .setArtist(artist ?: "Unknown Artist")
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsPlayable(true)
            thumbUrl?.let { metaBuilder.setArtworkUri(Uri.parse(it)) }
            sourceUrl?.let {
                metaBuilder.setExtras(android.os.Bundle().apply { putString("source_url", it) })
            }

            val item = MediaItem.Builder()
                .setMediaId(uri.toString()).setUri(uri)
                .setMediaMetadata(metaBuilder.build()).build()

            withContext(Dispatchers.Main) {
                val p: Player = controller ?: player
                p.stop()
                p.setMediaItem(item)
                p.prepare()
                p.play()
            }
        }
    }

    fun addToQueue(track: MusicTrack) {
        val p: Player = controller ?: player
        p.addMediaItem(track.toMediaItem())
        hapticClick()
    }

    fun playNext(track: MusicTrack) {
        val p: Player    = controller ?: player
        val nextIndex = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
        p.addMediaItem(nextIndex, track.toMediaItem())
        hapticClick()
    }

    /**
     * FIX: `playCatalogTracks` now clears `isResolvingCatalog` in the
     * `finally` block unconditionally, so the spinner can never get stuck
     * even if an exception is thrown mid-resolution.
     */
    fun playCatalogTracks(tracks: List<CatalogTrack>, startIndex: Int = 0) {
        startPlayerService()
        if (controller == null) {
            pendingAction = { playCatalogTracks(tracks, startIndex) }
            connectToMediaController()
            return
        }
        hapticClick()

        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingCatalog = true) }
            try {
                val items = tracks.map { track ->
                    val localTrack = withContext(Dispatchers.IO) {
                        repository.getTrackBySourceUrl(track.sourceUrl)
                    }
                    val playableUri = if (localTrack != null && localTrack.uri.isNotBlank()) {
                        localTrack.uri
                    } else {
                        track.sourceUrl
                    }

                    val meta = MediaMetadata.Builder()
                        .setTitle(track.title).setArtist(track.artist)
                        .setAlbumTitle("YouTube Catalog").setDisplayTitle(track.title)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsPlayable(true)
                        .setArtworkUri(track.thumbnailUrl?.toUri())
                        .setExtras(android.os.Bundle().apply {
                            putString("source_url", track.sourceUrl)
                            putBoolean("is_catalog", true)
                        }).build()

                    MediaItem.Builder()
                        .setMediaId(track.sourceUrl).setUri(playableUri.toUri())
                        .setMediaMetadata(meta).build()
                }

                withContext(Dispatchers.Main) {
                    val p: Player = controller ?: player
                    p.stop(); p.setMediaItems(items, startIndex, 0L); p.prepare(); p.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Always clear – onPlaybackStateChanged will also clear it once
                // STATE_BUFFERING / STATE_READY fires, but this is the safety net.
                _uiState.update { it.copy(isResolvingCatalog = false) }
            }
        }
    }

    fun addToQueue(track: CatalogTrack) {
        val p: Player = controller ?: player
        p.addMediaItem(track.toMediaItem())
        hapticClick()
    }

    fun playNext(track: CatalogTrack) {
        val p: Player = controller ?: player
        val idx = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
        p.addMediaItem(idx, track.toMediaItem())
        hapticClick()
    }

    /**
     * FIX: `setResolvingCatalog` is now only called from the UI to signal that
     * URL resolution has started. Once the player accepts the item (via
     * `onMediaItemTransition` or `onPlaybackStateChanged`), the flag is cleared
     * automatically, so the caller no longer needs to call `setResolvingCatalog(false)`.
     */
    fun setResolvingCatalog(resolving: Boolean) {
        _uiState.update { it.copy(isResolvingCatalog = resolving) }
    }

    fun enqueueCatalogTrack(track: CatalogTrack, playNext: Boolean) {
        viewModelScope.launch {
            val localTrack = withContext(Dispatchers.IO) {
                repository.getTrackBySourceUrl(track.sourceUrl)
            }
            val item = if (localTrack != null && localTrack.uri.isNotBlank()) {
                localTrack.toMediaItem()
            } else {
                track.toMediaItem()
            }
            withContext(Dispatchers.Main) {
                val p: Player = controller ?: player
                if (playNext) {
                    val idx = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
                    p.addMediaItem(idx, item)
                } else {
                    p.addMediaItem(item)
                }
                if (!p.isPlaying && !p.playWhenReady) p.prepare()
                hapticClick()
            }
        }
    }

    // P1-06: MediaItem mapping now consolidated in data/music/MediaItemMapper.kt

    // ─────────────────────────────────────────────────────────────────────────
    // Playlist helpers
    // ─────────────────────────────────────────────────────────────────────────

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        val tracks = playlist.trackUris.mapNotNull { uri -> _uiState.value.tracks.find { it.uri == uri } }
        if (tracks.isEmpty()) return
        val p: Player = controller ?: player
        
        if (shuffle) {
            p.shuffleModeEnabled = true
            _uiState.update { it.copy(isShuffleOn = true) }
            playTrack(tracks.random(), tracks)
        } else {
            p.shuffleModeEnabled = false
            _uiState.update { it.copy(isShuffleOn = false) }
            playTrack(tracks.first(), tracks)
        }
        hapticSuccess()
    }

    fun togglePlayPause() {
        startPlayerService()
        val p: Player = controller ?: player
        if (p.isPlaying) p.pause() else p.play()
        hapticClick()
    }

    private var fadeJob: Job? = null
    private fun fadeVolume(toVolume: Float, duration: Long, onEnd: () -> Unit = {}) {
        fadeJob?.cancel()
        fadeJob = viewModelScope.launch {
            val startVolume = player.volume
            val steps = 15
            val interval = duration / steps
            val delta = (toVolume - startVolume) / steps
            for (i in 1..steps) {
                delay(interval)
                player.volume = (startVolume + delta * i).coerceIn(0f, 1f)
            }
            player.volume = toVolume
            onEnd()
        }
    }

    fun play()  {
        startPlayerService()
        val p: Player = controller ?: player
        if (!p.isPlaying) {
            // Act first, decorate after: the transport call fires immediately
            // on this same frame so tapping play is instant. The volume ramp
            // is a purely cosmetic fade-in layered on top afterward — it no
            // longer gates when playback actually starts.
            p.volume = 0f
            p.play()
            if (!_uiState.value.isMutedByAi) {
                fadeVolume(1f, 100)
            } else {
                p.volume = 0f
            }
        }
        hapticClick()
    }
    fun pause() {
        val p: Player = controller ?: player
        if (p.isPlaying) {
            // Pause immediately — do not wait on a fade to actually stop
            // audio/update state. The fade below is a quick cosmetic volume
            // dip that runs fully after the fact and resets volume for the
            // next play(), it never blocks the pause itself.
            p.pause()
            if (!_uiState.value.isMutedByAi) {
                fadeVolume(0f, 80) {
                    p.volume = 1f
                }
            }
        }
        hapticClick()
    }

    fun stop() {
        val p: Player = controller ?: player
        p.stop(); p.clearMediaItems()
        _uiState.update { it.copy(currentTrack = null, isPlaying = false, playbackPosition = 0L, duration = 0L, isResolvingCatalog = false) }
        _playbackPosition.value = 0L; _duration.value = 0L
        hapticClick()
    }

    fun seekTo(position: Long) {
        val p: Player = controller ?: player
        p.seekTo(position)
        _uiState.update { it.copy(playbackPosition = position) }
        _playbackPosition.value = position
        hapticClick()
    }

    fun setVolume(volume: Float) {
        fadeJob?.cancel()
        val p: Player = controller ?: player
        p.volume = volume
    }

    fun setMutedByAi(muted: Boolean) {
        _uiState.update { it.copy(isMutedByAi = muted) }
        setVolume(if (muted) 0f else 1f)
    }

    fun updateKaraokeStats(count: Int, avgScore: Int) {
        _uiState.update { it.copy(karaokeSessionsCount = count, karaokeAvgScore = avgScore) }
    }

    fun skipNext() {
        val p: Player = controller ?: player
        // Skip immediately — ExoPlayer's own transition already gives a clean
        // cut, so there's no reason to hold the actual seek hostage behind a
        // 100ms fade. The fade now runs as a quick decorative dip *after* the
        // real skip has already happened, so the tap feels instant.
        if (p.hasNextMediaItem()) p.seekToNext()
        else if (p.repeatMode == Player.REPEAT_MODE_ALL) p.seekTo(0, 0)
        hapticClick()

        if (!_uiState.value.isMutedByAi) {
            val target = p.volume
            p.volume = 0f
            fadeVolume(target.takeIf { it > 0f } ?: 1f, 120)
        } else {
            p.volume = 0f
        }
    }

    fun skipPrevious() {
        val p: Player = controller ?: player
        if (p.currentPosition > 3_000) p.seekTo(0)
        else if (p.hasPreviousMediaItem()) p.seekToPrevious()
        hapticClick()

        if (!_uiState.value.isMutedByAi) {
            val target = p.volume
            p.volume = 0f
            fadeVolume(target.takeIf { it > 0f } ?: 1f, 120)
        } else {
            p.volume = 0f
        }
    }

    fun toggleShuffle() {
        val p: Player = controller ?: player
        val new = !p.shuffleModeEnabled
        p.shuffleModeEnabled = new
        _uiState.update { it.copy(isShuffleOn = new) }
        if (new) hapticSuccess() else hapticClick()
    }

    fun toggleRepeat() {
        val p: Player = controller ?: player
        val new = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else                   -> Player.REPEAT_MODE_OFF
        }
        p.repeatMode = new
        _uiState.update { it.copy(repeatMode = new) }
        if (new != Player.REPEAT_MODE_OFF) hapticSuccess() else hapticClick()
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        hapticClick()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _uiState.update {
            it.copy(
                sleepTimerMinutes   = minutes,
                sleepTimerActive    = minutes != null,
                sleepTimerRemaining = minutes?.let { m -> m * 60_000L }
            )
        }
        if (minutes != null) {
            val end = System.currentTimeMillis() + minutes * 60_000L
            sleepTimerJob = viewModelScope.launch {
                while (System.currentTimeMillis() < end) {
                    _uiState.update { it.copy(sleepTimerRemaining = (end - System.currentTimeMillis()).coerceAtLeast(0)) }
                    delay(1_000)
                }
                fadeOutAndStop()
            }
        }
        hapticClick()
    }

    fun toggleSleepTimerDialog() { _showSleepTimer.update { !it }; hapticClick() }

    private suspend fun fadeOutAndStop() {
        var vol = 1f
        while (vol > 0) { vol -= 0.05f; player.volume = vol.coerceAtLeast(0f); delay(100) }
        val p: Player = controller ?: player
        p.pause(); player.volume = 1f
        _uiState.update { it.copy(sleepTimerMinutes = null, sleepTimerActive = false, sleepTimerRemaining = null) }
        vibrationManager.vibrateLongClick()
    }

    fun toggleShowVisualizer() {
        viewModelScope.launch {
            settingsRepository.setShowMusicVisualizer(!_uiState.value.showVisualizer)
            hapticClick()
        }
    }

    fun toggleKaraokeMode() {
        _uiState.update {
            val newActive = !it.isKaraokeActive
            if (newActive) { 
                val p: Player = controller ?: player
                if (p.isPlaying) p.pause()
                stopVisualizer()
            }
            it.copy(isKaraokeActive = newActive)
        }
        hapticClick()
    }

    fun setKaraokeMode(enabled: Boolean) {
        _uiState.update {
            if (enabled && !it.isKaraokeActive) {
                val p: Player = controller ?: player
                if (p.isPlaying) p.pause()
            }
            it.copy(isKaraokeActive = enabled)
        }
        hapticClick()
    }

    fun incrementKaraokeSingCount(track: MusicTrack) {
        viewModelScope.launch { repository.incrementKaraokeSingCount(track.uri) }
    }

    fun setArtShape(shape: String) {
        viewModelScope.launch { settingsRepository.setMusicArtShape(shape); hapticClick() }
    }

    fun toggleRotation() {
        viewModelScope.launch { settingsRepository.setMusicRotationEnabled(!_uiState.value.rotationEnabled); hapticClick() }
    }

    fun togglePipEnabled() {
        viewModelScope.launch { settingsRepository.setMusicPipEnabled(!_uiState.value.pipEnabled); hapticClick() }
    }

    fun createPlaylist(name: String, thumbnailUri: String? = null) {
        viewModelScope.launch { repository.createPlaylist(Playlist(name = name, thumbnailUri = thumbnailUri)); hapticSuccess() }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: MusicTrack) {
        viewModelScope.launch { 
            // Ensure the track is in the database if it's an online track being added to a playlist
            if (track.path == null && track.sourceUrl != null) {
                repository.insertTrack(track)
            }
            // Fetch latest playlist state from repository to avoid erasing other songs
            val latestPlaylist = repository.getPlaylistById(playlist.id) ?: playlist
            val updatedUris = (latestPlaylist.trackUris + track.uri).distinct()
            repository.updatePlaylist(latestPlaylist.copy(trackUris = updatedUris))
            hapticClick() 
        }
    }

    fun addSelectedTracksToPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val latestPlaylist = repository.getPlaylistById(playlist.id) ?: playlist
            val updatedUris = (latestPlaylist.trackUris + _uiState.value.selectedTracks).distinct()
            repository.updatePlaylist(latestPlaylist.copy(trackUris = updatedUris))
            clearSelection(); hapticSuccess()
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, trackUri: String) {
        viewModelScope.launch { repository.updatePlaylist(playlist.copy(trackUris = playlist.trackUris - trackUri)); hapticClick() }
    }

    fun updatePlaylistThumbnail(playlist: Playlist, uri: Uri) {
        viewModelScope.launch {
            try {
                // Copy selected image to internal storage for persistence
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = "playlist_cover_${playlist.id}_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
                
                inputStream?.use { input ->
                    java.io.FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                
                val internalUri = Uri.fromFile(file)
                repository.updatePlaylist(playlist.copy(thumbnailUri = internalUri.toString()))
                hapticClick()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createPlaylistWithTracks(name: String, trackUris: List<String>) {
        viewModelScope.launch { repository.createPlaylist(Playlist(name = name, trackUris = trackUris)); hapticSuccess() }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { repository.deletePlaylist(playlist); hapticClick() }
    }

    fun deleteTrack(track: MusicTrack) {
        viewModelScope.launch { repository.deleteTrack(track); hapticClick() }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.setMusicPlaybackSpeed(speed)
            val p: Player = controller ?: player
            p.playbackParameters = PlaybackParameters(speed)
            _uiState.update { it.copy(playbackSpeed = speed) }
            hapticClick()
        }
    }

    fun setCatalogStreamQuality(quality: String) {
        viewModelScope.launch {
            settingsRepository.setCatalogStreamQuality(quality)
            _uiState.update { it.copy(catalogStreamQuality = quality) }

            val currentTrack = _uiState.value.currentTrack
            if (currentTrack != null && currentTrack.isOnlineCatalogTrack()) {
                refreshCurrentCatalogStream(currentTrack, quality)
            } else {
                hapticClick()
            }
        }
    }

    fun toggleMusicSettings() { _uiState.update { it.copy(showMusicSettings = !it.showMusicSettings) }; hapticClick() }

    fun seekToQueueIndex(index: Int) {
        val p: Player = controller ?: player
        if (index in 0 until p.mediaItemCount) {
            p.seekTo(index, 0L)
            p.play()
            hapticClick()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val p: Player = controller ?: player
        if (fromIndex in 0 until p.mediaItemCount && toIndex in 0 until p.mediaItemCount) {
            p.moveMediaItem(fromIndex, toIndex); hapticTick()
        }
    }

    fun removeQueueItem(index: Int) {
        val p: Player = controller ?: player
        if (index in 0 until p.mediaItemCount) { p.removeMediaItem(index); hapticClick() }
    }

    fun clearQueue()  { val p: Player = controller ?: player; p.clearMediaItems(); hapticClick() }
    fun toggleFavorite(track: MusicTrack) { 
        viewModelScope.launch { 
            val isFav = !track.isFavorite
            // Optimistic update
            _uiState.update { state ->
                if (state.currentTrack?.uri == track.uri) {
                    state.copy(currentTrack = state.currentTrack?.copy(isFavorite = isFav))
                } else state
            }
            repository.toggleFavorite(track)
            hapticClick() 
        } 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        stopVisualizer()
        sleepTimerJob?.cancel()
        runCatching { repository.stopLiveObserver() }
        runCatching { equalizer?.release() }
        player.removeListener(playerListener)
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }

    private suspend fun refreshCurrentCatalogStream(track: MusicTrack, quality: String) {
        val sourceUrl = track.sourceUrl ?: return
        val streamUrl = try {
            catalogRepository.resolveAudioStream(sourceUrl, quality)
        } catch (e: Exception) {
            hapticClick()
            return
        }

        val p: Player = controller ?: player
        val index = p.currentMediaItemIndex
        if (index < 0) {
            hapticClick()
            return
        }

        val wasPlaying = p.isPlaying
        val resumePosition = p.currentPosition.coerceAtLeast(0L)
        val metadata = p.currentMediaItem?.mediaMetadata ?: MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist ?: "Unknown Artist")
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setArtworkUri(track.thumbnailUri?.toUri())
            .setExtras(android.os.Bundle().apply {
                putString("source_url", sourceUrl)
                putBoolean("is_catalog", true)
            })
            .build()

        val replacement = MediaItem.Builder()
            .setMediaId(streamUrl)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(metadata)
            .build()

        withContext(Dispatchers.Main) {
            p.replaceMediaItem(index, replacement)
            p.prepare()
            p.seekTo(index, resumePosition)
            if (wasPlaying) p.play() else p.pause()
        }
        hapticClick()
    }

    private fun MusicTrack.isOnlineCatalogTrack(): Boolean {
        return sourceUrl != null && path == null
    }
}
