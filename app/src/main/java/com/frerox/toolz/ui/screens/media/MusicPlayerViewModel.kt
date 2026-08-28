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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.data.music.toMediaItem
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.MusicPlayerService
import com.frerox.toolz.ui.screens.media.controllers.EqualizerController
import com.frerox.toolz.ui.screens.media.controllers.PlaybackTransport
import com.frerox.toolz.ui.screens.media.controllers.PlaylistManager
import com.frerox.toolz.ui.screens.media.controllers.QueueManager
import com.frerox.toolz.ui.screens.media.controllers.SleepTimerManager
import com.frerox.toolz.ui.screens.media.controllers.VisualizerDelegate
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.File

// P1-02: MusicUiState/QueueEntry/SortOrder extracted to MusicUiState.kt

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    val repository: MusicRepository,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val offlineManager: OfflineManager,
    val vibrationManager: VibrationManager,
    val player: ExoPlayer,
    private val visualizerManager: MusicVisualizerManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()
    // P2-01 fix: sliced flows so sections can collect only what they need without full-screen recomposition
    val currentTrackFlow = uiState.map { it.currentTrack }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isPlayingFlow = uiState.map { it.isPlaying }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Controllers — thin facade delegates
    @Suppress("UNNECESSARY_LATEINIT")
    private lateinit var queueManager: QueueManager
    @Suppress("UNNECESSARY_LATEINIT")
    private lateinit var playbackTransport: PlaybackTransport
    @Suppress("UNNECESSARY_LATEINIT")
    private lateinit var equalizerController: EqualizerController
    @Suppress("UNNECESSARY_LATEINIT")
    private lateinit var visualizerDelegate: VisualizerDelegate
    @Suppress("UNNECESSARY_LATEINIT")
    private lateinit var playlistManager: PlaylistManager
    @Suppress("UNNECESSARY_LATEINIT")
    private lateinit var sleepTimerManager: SleepTimerManager

    private val _fallbackPlaybackPosition = MutableStateFlow(0L)
    private val _fallbackDuration = MutableStateFlow(0L)
    private val _fallbackSlider = MutableStateFlow<Long?>(null)
    private val _fallbackShowSleep = MutableStateFlow(false)
    private val _fallbackVisualizer = MutableStateFlow(FloatArray(0))
    private val _fallbackEqualizer = MutableStateFlow<List<String>>(emptyList())
    private val _fallbackQueue = MutableStateFlow<List<QueueEntry>>(emptyList())
    private val _fallbackQueueIndex = MutableStateFlow(0)

    // Exposed flows delegating to controllers (public API identical)
    val playbackPosition: StateFlow<Long> get() = if (::playbackTransport.isInitialized) playbackTransport.playbackPosition else _fallbackPlaybackPosition
    val duration: StateFlow<Long> get() = if (::playbackTransport.isInitialized) playbackTransport.duration else _fallbackDuration
    val sliderPosition: StateFlow<Long?> get() = if (::playbackTransport.isInitialized) playbackTransport.sliderPosition else _fallbackSlider
    val showSleepTimer: StateFlow<Boolean> get() = if (::sleepTimerManager.isInitialized) sleepTimerManager.showSleepTimer else _fallbackShowSleep
    val visualizerData: StateFlow<FloatArray> get() = if (::visualizerDelegate.isInitialized) visualizerDelegate.visualizerData else _fallbackVisualizer
    val equalizerPresets: StateFlow<List<String>> get() = if (::equalizerController.isInitialized) equalizerController.equalizerPresets else _fallbackEqualizer

    // Additional queue flows as per spec
    val queueFlow: StateFlow<List<QueueEntry>> get() = if (::queueManager.isInitialized) queueManager.queueFlow else _fallbackQueue
    val currentQueueIndexFlow: StateFlow<Int> get() = if (::queueManager.isInitialized) queueManager.currentQueueIndexFlow else _fallbackQueueIndex

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: (() -> Unit)? = null

    private fun hapticClick() { if (!_uiState.value.isKaraokeActive) vibrationManager.vibrateClick() }
    private fun hapticSuccess() { if (!_uiState.value.isKaraokeActive) vibrationManager.vibrateSuccess() }
    private fun hapticTick() { if (!_uiState.value.isKaraokeActive) vibrationManager.vibrateTick() }

    // ─────────────────────────────────────────────────────────────────────────
    // Player listener — delegates to controllers
    // ─────────────────────────────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                playbackTransport.startProgressUpdate()
                startPlayerService()
                if (_uiState.value.showVisualizer) visualizerDelegate.startVisualizer()
                equalizerController.initEqualizer()
                hapticClick()
            } else {
                playbackTransport.stopProgressUpdate()
                if (!_uiState.value.playWhenReady) visualizerDelegate.stopVisualizer()
                hapticClick()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            _uiState.update { it.copy(playWhenReady = playWhenReady) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _uiState.update { it.copy(isResolvingCatalog = false) }
            visualizerDelegate.clearVisualizerData()

            val uri = mediaItem?.mediaId ?: mediaItem?.requestMetadata?.mediaUri?.toString()
            val metadata = mediaItem?.mediaMetadata
            val sourceUrl = metadata?.extras?.getString("source_url")

            viewModelScope.launch {
                var track = repository.getTrackByUri(uri ?: "")

                if (track == null && sourceUrl != null) {
                    track = repository.getTrackBySourceUrl(sourceUrl)
                }

                if (track == null && mediaItem != null) {
                    val ephUri = uri ?: ""
                    val ephTitle = metadata?.title?.toString() ?: "External Audio"
                    val ephArtist = metadata?.artist?.toString() ?: "Unknown"
                    track = MusicTrack(
                        uri = ephUri,
                        title = ephTitle,
                        artist = ephArtist,
                        album = metadata?.albumTitle?.toString() ?: "Unknown",
                        duration = player.duration.coerceAtLeast(0L),
                        thumbnailUri = metadata?.artworkUri?.toString() ?: "",
                        sourceUrl = sourceUrl,
                        lastPlayed = System.currentTimeMillis(),
                        stableId = (sourceUrl ?: ephUri).hashCode().toString() + "_" + ephTitle.hashCode()
                    )
                    repository.insertTrack(track)
                } else if (track != null) {
                    val updated = track.copy(
                        lastPlayed = System.currentTimeMillis(),
                        duration = if (player.duration > 0 && track.duration <= 0)
                            player.duration else track.duration
                    )
                    repository.updateTrack(updated)
                    track = updated
                }

                val dur = player.duration.coerceAtLeast(0L)
                _uiState.update { it.copy(currentTrack = track, duration = dur) }
                playbackTransport.updateDuration(dur)

                if (track != null) repository.incrementPlayCount(track)
                queueManager.updateQueue()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update { it.copy(isResolvingCatalog = false) }
                }

                Player.STATE_READY -> {
                    val dur = player.duration.coerceAtLeast(0L)
                    _uiState.update { it.copy(duration = dur, isResolvingCatalog = false) }
                    playbackTransport.updateDuration(dur)

                    val currentTrack = _uiState.value.currentTrack
                    if (currentTrack != null && dur > 0 && currentTrack.duration <= 0) {
                        val updated = currentTrack.copy(duration = dur)
                        viewModelScope.launch { repository.updateTrack(updated) }
                        _uiState.update { it.copy(currentTrack = updated) }
                    }

                    if (_uiState.value.showVisualizer && player.isPlaying) {
                        visualizerDelegate.startVisualizer()
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

            viewModelScope.launch(Dispatchers.IO) {
                runCatching { repository.scanDeviceForMusic() }
            }

            if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
            ) {
                player.prepare()
            } else {
                viewModelScope.launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Playback error: ${error.localizedMessage ?: "File moved or unplayable. Skipping."}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    val p: Player = controller ?: player
                    if (p.hasNextMediaItem()) {
                        p.seekToNext()
                        p.prepare()
                    } else {
                        p.pause()
                    }
                }
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            queueManager.updateQueue()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data aggregation
    // ─────────────────────────────────────────────────────────────────────────

    private data class MusicData(
        val tracks: List<MusicTrack>,
        val playlists: List<Playlist>,
        val favorites: List<MusicTrack>,
        val recent: List<MusicTrack>,
        val most: List<MusicTrack>
    )

    init {
        // Initialize delegates first so init logic can use them
        queueManager = QueueManager(viewModelScope, _uiState, player, { controller }, vibrationManager)
        playbackTransport = PlaybackTransport(
            scope = viewModelScope,
            uiState = _uiState,
            player = player,
            controllerProvider = { controller },
            repository = repository,
            catalogRepository = catalogRepository,
            settingsRepository = settingsRepository,
            context = context,
            vibrationManager = vibrationManager,
            queueManager = queueManager,
            onStartService = { startPlayerService() }
        )
        equalizerController = EqualizerController(viewModelScope, _uiState, player, settingsRepository, vibrationManager)
        visualizerDelegate = VisualizerDelegate(viewModelScope, _uiState, visualizerManager, settingsRepository)
        playlistManager = PlaylistManager(viewModelScope, _uiState, repository, context, vibrationManager)
        sleepTimerManager = SleepTimerManager(viewModelScope, _uiState, player, { controller }, vibrationManager)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, false)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(playerListener)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                if (_uiState.value.showVisualizer && player.isPlaying) {
                    visualizerDelegate.restartVisualizer()
                }
            }
        })

        connectToMediaController()

        observeSettings()
        observeLibrary()

        repository.startLiveObserver(viewModelScope)

        if (player.isPlaying) playbackTransport.startProgressUpdate()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.showMusicVisualizer.collect { show ->
                _uiState.update { it.copy(showVisualizer = show) }
                if (show && _uiState.value.isPlaying) visualizerDelegate.startVisualizer() else if (!show) visualizerDelegate.stopVisualizer()
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
        viewModelScope.launch {
            settingsRepository.musicEqualizerPreset.collect { preset ->
                _uiState.update { it.copy(equalizerPreset = preset) }
                equalizerController.applyEqualizerPreset(preset)
            }
        }
        viewModelScope.launch {
            settingsRepository.musicCustomEqualizer.collect { data ->
                if (data.isNotBlank()) {
                    val gains = data.split(",").mapNotNull { it.toFloatOrNull() }
                    if (gains.size >= 5) {
                        _uiState.update { it.copy(customEqualizerGains = gains) }
                        if (_uiState.value.equalizerPreset == "Custom") equalizerController.applyCustomEqualizer(gains)
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
                visualizerManager.setAutoSensitivity(enabled)
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
        viewModelScope.launch {
            settingsRepository.musicDownloadedOnlyFilter.collect { enabled ->
                _uiState.update { it.copy(downloadedOnlyFilter = enabled) }
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
                    _uiState.map { it.isOnline }.distinctUntilChanged(),
                    _uiState.map { it.downloadedOnlyFilter }.distinctUntilChanged()
                ) { sort, online, downloadedOnly -> Triple(sort, online, downloadedOnly) }
            ) { data, (sortOrder, isOnline, downloadedOnly) ->

                val deduped = data.tracks
                    .groupBy { "${it.title}|${it.artist}" }
                    .map { (_, group) -> group.find { it.path != null } ?: group.first() }

                var visible = if (!isOnline) deduped.filter { it.path != null } else deduped
                if (downloadedOnly) visible = visible.filter { it.path != null || it.album == "Toolz Downloads" }

                val sorted = when (sortOrder) {
                    SortOrder.TITLE -> visible.sortedBy { it.title }
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
                            tracks = sorted,
                            playlists = data.playlists.sortedByDescending { it.createdAt },
                            favoriteTracks = data.favorites,
                            recentlyPlayed = data.recent,
                            mostPlayed = data.most,
                            folders = folders,
                            currentTrack = newCurrentTrack,
                            isPlaying = player.isPlaying,
                            playWhenReady = player.playWhenReady,
                            isShuffleOn = player.shuffleModeEnabled,
                            repeatMode = player.repeatMode,
                            duration = player.duration.coerceAtLeast(0L)
                        )
                    }
                    playbackTransport.updateDuration(player.duration.coerceAtLeast(0L))
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
                controller?.addListener(playerListener)

                val dur = controller?.duration?.coerceAtLeast(0L) ?: 0L
                val currentMediaItem = controller?.currentMediaItem
                val mediaId = currentMediaItem?.mediaId ?: currentMediaItem?.requestMetadata?.mediaUri?.toString()
                val metadata = currentMediaItem?.mediaMetadata
                val sourceUrl = metadata?.extras?.getString("source_url")

                _uiState.update {
                    it.copy(
                        isPlaying = controller?.isPlaying ?: false,
                        isShuffleOn = controller?.shuffleModeEnabled ?: false,
                        repeatMode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF,
                        duration = dur
                    )
                }

                if (mediaId != null) {
                    viewModelScope.launch {
                        var track = repository.getTrackByUri(mediaId)
                        if (track == null && sourceUrl != null) {
                            track = repository.getTrackBySourceUrl(sourceUrl)
                        }

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

                playbackTransport.updateDuration(dur)
                queueManager.updateQueue()
                pendingAction?.invoke()
                pendingAction = null
            }.onFailure { it.printStackTrace() }
        }, MoreExecutors.directExecutor())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Service
    // ─────────────────────────────────────────────────────────────────────────

    private var playerServiceStarted = false
    private fun startPlayerService() {
        if (playerServiceStarted) return
        runCatching {
            context.startService(Intent(context, MusicPlayerService::class.java))
            playerServiceStarted = true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Facade delegations — Queue
    // ─────────────────────────────────────────────────────────────────────────

    fun consumeQueueWarning() = queueManager.consumeQueueWarning()
    fun seekToQueueIndex(index: Int) = queueManager.seekToQueueIndex(index)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = queueManager.moveQueueItem(fromIndex, toIndex)
    fun removeQueueItem(index: Int) = queueManager.removeQueueItem(index)
    fun undoRemoveQueueItem() = queueManager.undoRemove()
    fun hasUndoQueue() = queueManager.hasUndo()
    fun shuffleRemaining() = queueManager.shuffleRemaining()
    fun clearQueue() = queueManager.clearQueue()

    // ─────────────────────────────────────────────────────────────────────────
    // Facade delegations — Visualizer / Equalizer
    // ─────────────────────────────────────────────────────────────────────────

    fun setVisualizerSensitivity(sensitivity: Float) = visualizerDelegate.setVisualizerSensitivity(sensitivity)
    fun setVisualizerAutoSensitivity(enabled: Boolean) = visualizerDelegate.setVisualizerAutoSensitivity(enabled)

    fun setCustomEqualizerGain(band: Int, gain: Float) = equalizerController.setCustomEqualizerGain(band, gain)
    fun setEqualizerPreset(preset: String) = equalizerController.setEqualizerPreset(preset)

    // ─────────────────────────────────────────────────────────────────────────
    // Facade delegations — Playback Transport
    // ─────────────────────────────────────────────────────────────────────────

    fun onSliderChange(position: Long) = playbackTransport.onSliderChange(position)
    fun onSliderChangeFinished() = playbackTransport.onSliderChangeFinished()

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
            else state.selectedTracks + uri
            state.copy(selectedTracks = sel, isSelectionMode = sel.isNotEmpty())
        }
        hapticClick()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTracks = emptySet(), isSelectionMode = false) }
    }

    fun playTrack(track: MusicTrack, tracks: List<MusicTrack> = _uiState.value.tracks) {
        if (controller == null) {
            pendingAction = { playTrack(track, tracks) }
            connectToMediaController()
            return
        }
        playbackTransport.playTrack(track, tracks)
    }

    fun playUri(
        uri: Uri,
        title: String? = null,
        artist: String? = null,
        thumbUrl: String? = null,
        sourceUrl: String? = null
    ) {
        if (controller == null) {
            pendingAction = { playUri(uri, title, artist, thumbUrl, sourceUrl) }
            connectToMediaController()
            return
        }
        playbackTransport.playUri(uri, title, artist, thumbUrl, sourceUrl)
    }

    fun addToQueue(track: MusicTrack) = playbackTransport.addToQueue(track)

    fun playNext(track: MusicTrack) = playbackTransport.playNext(track)

    fun playCatalogTracks(tracks: List<CatalogTrack>, startIndex: Int = 0) {
        if (controller == null) {
            pendingAction = { playCatalogTracks(tracks, startIndex) }
            connectToMediaController()
            return
        }
        playbackTransport.playCatalogTracks(tracks, startIndex)
    }

    fun addToQueue(track: CatalogTrack) = playbackTransport.addToQueue(track)

    fun playNext(track: CatalogTrack) = playbackTransport.playNext(track)

    fun setResolvingCatalog(resolving: Boolean) = playbackTransport.setResolvingCatalog(resolving)

    fun enqueueCatalogTrack(track: CatalogTrack, playNext: Boolean) = playbackTransport.enqueueCatalogTrack(track, playNext)

    // ─────────────────────────────────────────────────────────────────────────
    // Playlist helpers — delegated to PlaylistManager
    // ─────────────────────────────────────────────────────────────────────────

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false) {
        val tracks = playlist.trackUris.mapNotNull { uri -> _uiState.value.tracks.find { it.uri == uri } }
        if (tracks.isEmpty()) return
        playbackTransport.playPlaylist(tracks, shuffle)
    }

    fun togglePlayPause() = playbackTransport.togglePlayPause()

    fun play() = playbackTransport.play()
    fun pause() = playbackTransport.pause()

    fun stop() = playbackTransport.stop()

    fun seekTo(position: Long) = playbackTransport.seekTo(position)

    fun setVolume(volume: Float) = playbackTransport.setVolume(volume)

    fun setMutedByAi(muted: Boolean) = playbackTransport.setMutedByAi(muted)

    fun updateKaraokeStats(count: Int, avgScore: Int) {
        _uiState.update { it.copy(karaokeSessionsCount = count, karaokeAvgScore = avgScore) }
    }

    fun skipNext() = playbackTransport.skipNext()

    fun skipPrevious() = playbackTransport.skipPrevious()

    fun toggleShuffle() = playbackTransport.toggleShuffle()

    fun toggleRepeat() = playbackTransport.toggleRepeat()

    fun setSortOrder(order: SortOrder) = playbackTransport.setSortOrder(order)
    fun toggleDownloadedOnlyFilter() {
        viewModelScope.launch { settingsRepository.setMusicDownloadedOnlyFilter(!_uiState.value.downloadedOnlyFilter); hapticClick() }
    }
    fun setLrcOffset(track: MusicTrack, offsetMs: Long) {
        viewModelScope.launch {
            val clamped = offsetMs.coerceIn(-2000L, 2000L)
            repository.updateTrack(track.copy(lrcOffsetMs = clamped))
            // also update uiState currentTrack if same
            _uiState.update { s -> if (s.currentTrack?.uri == track.uri) s.copy(currentTrack = s.currentTrack?.copy(lrcOffsetMs = clamped)) else s }
            hapticTick()
        }
    }

    fun setSleepTimer(minutes: Int?) = sleepTimerManager.setSleepTimer(minutes)

    fun toggleSleepTimerDialog() = sleepTimerManager.toggleSleepTimerDialog()

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
                visualizerDelegate.stopVisualizer()
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

    fun createPlaylist(name: String, thumbnailUri: String? = null) = playlistManager.createPlaylist(name, thumbnailUri)

    fun addTrackToPlaylist(playlist: Playlist, track: MusicTrack) = playlistManager.addTrackToPlaylist(playlist, track)

    fun addSelectedTracksToPlaylist(playlist: Playlist) = playlistManager.addSelectedTracksToPlaylist(playlist)

    fun removeTrackFromPlaylist(playlist: Playlist, trackUri: String) = playlistManager.removeTrackFromPlaylist(playlist, trackUri)

    fun updatePlaylistThumbnail(playlist: Playlist, uri: Uri) = playlistManager.updatePlaylistThumbnail(playlist, uri)

    fun createPlaylistWithTracks(name: String, trackUris: List<String>) = playlistManager.createPlaylistWithTracks(name, trackUris)

    fun deletePlaylist(playlist: Playlist) = playlistManager.deletePlaylist(playlist)

    fun deleteTrack(track: MusicTrack) = playlistManager.deleteTrack(track)

    fun setPlaybackSpeed(speed: Float) = playbackTransport.setPlaybackSpeed(speed)

    fun setCatalogStreamQuality(quality: String) {
        playbackTransport.setCatalogStreamQuality(quality) { q ->
            _uiState.value.currentTrack?.let { track ->
                playbackTransport.refreshCurrentCatalogStream(track, q) { (controller ?: player).currentMediaItem }
            }
        }
    }

    fun toggleMusicSettings() { _uiState.update { it.copy(showMusicSettings = !it.showMusicSettings) }; hapticClick() }
    fun setCacheSize(mb: Int) { viewModelScope.launch { settingsRepository.setMusicCacheSizeMb(mb); hapticClick() } }

    fun toggleFavorite(track: MusicTrack) = playlistManager.toggleFavorite(track)

    fun editTrackTags(
        track: MusicTrack,
        newTitle: String?,
        newArtist: String?,
        newAlbum: String?,
        newThumbnailUri: String?,
        newLyrics: String?,
        onDone: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.updateTrackTags(track.uri, newTitle, newArtist, newAlbum, newThumbnailUri, newLyrics)
                withContext(Dispatchers.Main) {
                    if (res != null) {
                        // Optimistic currentTrack refresh for immediate UI feedback
                        _uiState.update { s -> if (s.currentTrack?.uri == track.uri) s.copy(currentTrack = res) else s }
                        // If this track is currently playing, update its MediaItem metadata live
                        try {
                            val p: Player = controller ?: player
                            val idx = p.currentMediaItemIndex
                            if (idx != -1 && p.getMediaItemAt(idx).mediaId == track.uri) {
                                val pos = p.currentPosition
                                val wasPlaying = p.isPlaying
                                p.replaceMediaItem(idx, res.toMediaItem())
                                p.prepare()
                                p.seekTo(idx, pos)
                                if (wasPlaying) p.play()
                            }
                        } catch (_: Exception) {}
                        hapticSuccess()
                        onDone(true)
                    } else {
                        onDone(false)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerVM", "editTrackTags failed", e)
                withContext(Dispatchers.Main) { onDone(false) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        playbackTransport.onCleared()
        visualizerDelegate.onCleared()
        equalizerController.onCleared()
        sleepTimerManager.onCleared()
        playbackTransport.stopProgressUpdate()
        // visualizer already cleared via delegate
        runCatching { repository.stopLiveObserver() }
        player.removeListener(playerListener)
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }
}
