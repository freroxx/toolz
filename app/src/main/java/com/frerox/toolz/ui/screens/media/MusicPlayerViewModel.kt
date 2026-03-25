package com.frerox.toolz.ui.screens.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.MusicPlayerService
import com.frerox.toolz.util.VibrationManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SortOrder {
    TITLE, ARTIST, RECENT
}

data class MusicUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val favoriteTracks: List<MusicTrack> = emptyList(),
    val recentlyPlayed: List<MusicTrack> = emptyList(),
    val mostPlayed: List<MusicTrack> = emptyList(),
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sortOrder: SortOrder = SortOrder.RECENT,
    val isLoading: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val folders: Map<String, List<MusicTrack>> = emptyMap(),
    val selectedTracks: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val showVisualizer: Boolean = true,
    val artShape: String = "CIRCLE",
    val rotationEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val hapticIntensity: Float = 0.5f,
    val pipEnabled: Boolean = false,
    val sleepTimerActive: Boolean = false,
    val sleepTimerRemaining: Long? = null,
    val queue: List<MusicTrack> = emptyList(),
    val performanceMode: Boolean = false,
    val playbackPosition: Long = 0L,
    val duration: Long = 0L
)

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    val vibrationManager: VibrationManager,
    val player: ExoPlayer,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _sliderPosition = MutableStateFlow<Long?>(null)
    val sliderPosition = _sliderPosition.asStateFlow()

    private val _showSleepTimer = MutableStateFlow(false)
    val showSleepTimer = _showSleepTimer.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: (() -> Unit)? = null
    
    private var equalizer: Equalizer? = null
    private var shakeDetector: ShakeDetector? = null
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private var currentQueueUris: List<String> = emptyList()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdate()
                startPlayerService()
                initEqualizer()
                vibrationManager.vibrateClick()
            } else {
                stopProgressUpdate()
                vibrationManager.vibrateClick()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uri = mediaItem?.mediaId ?: mediaItem?.requestMetadata?.mediaUri?.toString()
            var track = _uiState.value.tracks.find { it.uri == uri }
            
            // Handle external tracks not in the main library
            if (track == null && mediaItem != null) {
                val metadata = mediaItem.mediaMetadata
                track = MusicTrack(
                    uri = uri ?: "",
                    title = metadata.title?.toString() ?: "External Audio",
                    artist = metadata.artist?.toString() ?: "Unknown",
                    album = metadata.albumTitle?.toString() ?: "Unknown",
                    duration = player.duration.coerceAtLeast(0L),
                    thumbnailUri = metadata.artworkUri?.toString()
                )
            }

            val dur = player.duration.coerceAtLeast(0L)
            _uiState.update { it.copy(currentTrack = track, duration = dur) }
            _duration.value = dur
            
            val currentUri = player.currentMediaItem?.mediaId
            if (currentUri != null) {
                viewModelScope.launch {
                    repository.incrementPlayCount(currentUri)
                }
            }
            
            updateQueue()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                 vibrationManager.vibrateClick()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val dur = player.duration.coerceAtLeast(0L)
                _uiState.update { it.copy(duration = dur) }
                _duration.value = dur
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
            vibrationManager.vibrateClick()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(isShuffleOn = shuffleModeEnabled) }
            vibrationManager.vibrateClick()
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            super.onPlayerError(error)
            player.prepare()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            updateQueue()
        }
    }

    private fun updateQueue() {
        viewModelScope.launch(Dispatchers.Main) {
            val p: Player = controller ?: player
            val currentTracks = _uiState.value.tracks
            
            val trackMap = currentTracks.associateBy { it.uri }
            val queueTracks = mutableListOf<MusicTrack>()
            val uris = mutableListOf<String>()
            
            for (i in 0 until p.mediaItemCount) {
                val mediaItem = p.getMediaItemAt(i)
                uris.add(mediaItem.mediaId)
                val track = trackMap[mediaItem.mediaId] ?: run {
                    val meta = mediaItem.mediaMetadata
                    MusicTrack(
                        uri = mediaItem.mediaId,
                        title = meta.title?.toString() ?: "External Audio",
                        artist = meta.artist?.toString() ?: "Unknown",
                        album = meta.albumTitle?.toString() ?: "Unknown",
                        duration = 0L
                    )
                }
                queueTracks.add(track)
            }
            
            currentQueueUris = uris
            _uiState.update { it.copy(queue = queueTracks) }
        }
    }

    private data class MusicData(
        val tracks: List<MusicTrack>,
        val playlists: List<Playlist>,
        val favorites: List<MusicTrack>,
        val recent: List<MusicTrack>,
        val most: List<MusicTrack>
    )

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        
        player.setAudioAttributes(audioAttributes, true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(playerListener)

        connectToMediaController()

        viewModelScope.launch {
            settingsRepository.showMusicVisualizer.collect { show ->
                _uiState.update { it.copy(showVisualizer = show) }
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
            }
        }

        viewModelScope.launch {
            settingsRepository.musicShakeToSkip.collect { enabled ->
                if (enabled) {
                    startShakeDetection()
                } else {
                    stopShakeDetection()
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.musicEqualizerPreset.collect { preset ->
                applyEqualizerPreset(preset)
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
            settingsRepository.musicPipEnabled.collect { enabled ->
                _uiState.update { it.copy(pipEnabled = enabled) }
            }
        }

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

            combinedFlow.combine(_uiState.map { it.sortOrder }.distinctUntilChanged()) { data, sortOrder ->
                val sortedBase = when (sortOrder) {
                    SortOrder.TITLE -> data.tracks.sortedBy { it.title }
                    SortOrder.ARTIST -> data.tracks.sortedBy { it.artist ?: "Unknown" }
                    SortOrder.RECENT -> data.tracks.reversed()
                }

                val prioritizedTracks = sortedBase.sortedWith(
                    compareByDescending<MusicTrack> { it.thumbnailUri != null }
                        .thenByDescending { it.artist != null && it.artist != "Unknown Artist" && it.artist != "<unknown>" }
                )
                
                val folders = prioritizedTracks.groupBy { track ->
                    val uri = track.uri.toUri()
                    uri.path?.substringBeforeLast("/")?.substringAfterLast("/") ?: "Internal Storage"
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            tracks = prioritizedTracks, 
                            playlists = data.playlists.sortedByDescending { it.createdAt }, 
                            favoriteTracks = data.favorites,
                            recentlyPlayed = data.recent,
                            mostPlayed = data.most,
                            folders = folders,
                            currentTrack = prioritizedTracks.find { t -> t.uri == (player.currentMediaItem?.mediaId ?: state.currentTrack?.uri) } ?: state.currentTrack,
                            isPlaying = player.isPlaying,
                            isShuffleOn = player.shuffleModeEnabled,
                            repeatMode = player.repeatMode,
                            duration = player.duration.coerceAtLeast(0L)
                        ) 
                    }
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
            }.collect()
        }
        
        if (player.isPlaying) {
            startProgressUpdate()
        }
    }

    private fun connectToMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(playerListener)
                val dur = controller?.duration?.coerceAtLeast(0L) ?: 0L
                _uiState.update { it.copy(
                    isPlaying = controller?.isPlaying ?: false,
                    isShuffleOn = controller?.shuffleModeEnabled ?: false,
                    repeatMode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF,
                    currentTrack = _uiState.value.tracks.find { t -> t.uri == controller?.currentMediaItem?.mediaId } ?: it.currentTrack,
                    duration = dur
                )}
                _duration.value = dur
                updateQueue()
                pendingAction?.invoke()
                pendingAction = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startShakeDetection() {
        if (shakeDetector == null) {
            shakeDetector = ShakeDetector {
                if (player.isPlaying) {
                    skipNext()
                }
            }
        }
        sensorManager.registerListener(
            shakeDetector,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI
        )
    }

    private fun stopShakeDetection() {
        shakeDetector?.let { sensorManager.unregisterListener(it) }
    }

    @UnstableApi
    private fun initEqualizer() {
        if (equalizer == null && player.audioSessionId != 0) {
            try {
                equalizer = Equalizer(0, player.audioSessionId).apply {
                    enabled = true
                }
                viewModelScope.launch {
                    applyEqualizerPreset(settingsRepository.musicEqualizerPreset.first())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun applyEqualizerPreset(preset: String) {
        val eq = equalizer ?: return
        try {
            val numPresets = eq.numberOfPresets
            if (numPresets > 0) {
                for (i in 0 until numPresets.toInt()) {
                    try {
                        val presetName = eq.getPresetName(i.toShort())
                        if (presetName.equals(preset, ignoreCase = true)) {
                            eq.usePreset(i.toShort())
                            return
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            
            if (preset == "Bass Boost") {
                for (i in 0 until eq.numberOfBands.toInt()) {
                    try {
                        val freq = eq.getCenterFreq(i.toShort())
                        if (freq < 500000) {
                            eq.setBandLevel(i.toShort(), 1000.toShort())
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun startPlayerService() {
        val intent = Intent(context, MusicPlayerService::class.java)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                if (_sliderPosition.value == null) {
                    val p: Player = controller ?: player
                    val currentPos = p.currentPosition
                    _uiState.update { it.copy(playbackPosition = currentPos) }
                    if (_playbackPosition.value != currentPos) {
                        _playbackPosition.value = currentPos
                    }
                }
                delay(if (_uiState.value.performanceMode) 500 else 100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun onSliderChange(position: Long) {
        _sliderPosition.value = position
    }

    fun onSliderChangeFinished() {
        _sliderPosition.value?.let { pos ->
            val p: Player = controller ?: player
            p.seekTo(pos)
            _uiState.update { it.copy(playbackPosition = pos) }
            _playbackPosition.value = pos
            vibrationManager.vibrateClick()
        }
        _sliderPosition.value = null
    }

    fun scanMusic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.scanDeviceForMusic()
            _uiState.update { it.copy(isLoading = false) }
            vibrationManager.vibrateSuccess()
        }
    }

    fun addCustomFolder(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.scanCustomFolder(uri)
            _uiState.update { it.copy(isLoading = false) }
            vibrationManager.vibrateSuccess()
        }
    }

    fun toggleTrackSelection(uri: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedTracks.contains(uri)) {
                state.selectedTracks - uri
            } else {
                state.selectedTracks + uri
            }
            state.copy(
                selectedTracks = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
        vibrationManager.vibrateClick()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTracks = emptySet(), isSelectionMode = false) }
        vibrationManager.vibrateClick()
    }

    fun playTrack(track: MusicTrack, tracks: List<MusicTrack> = _uiState.value.tracks) {
        startPlayerService()
        
        if (controller == null) {
            pendingAction = { playTrack(track, tracks) }
            connectToMediaController()
            return
        }

        vibrationManager.vibrateClick()
        
        viewModelScope.launch(Dispatchers.Default) {
            val trackUris = tracks.map { it.uri }
            val isSameQueue = trackUris == currentQueueUris
            
            val mediaItems = tracks.map { t -> t.toMediaItem() }
            val startIndex = tracks.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
            
            withContext(Dispatchers.Main) {
                val p: Player = controller ?: player
                if (isSameQueue) {
                    val index = trackUris.indexOf(track.uri)
                    if (index != -1) {
                        p.seekTo(index, 0L)
                        p.play()
                        return@withContext
                    }
                }
                
                p.stop()
                p.setMediaItems(mediaItems, startIndex, 0L)
                p.prepare()
                p.play()
            }
        }
    }

    fun playUri(uri: Uri) {
        startPlayerService()
        
        if (controller == null) {
            pendingAction = { playUri(uri) }
            connectToMediaController()
            return
        }

        vibrationManager.vibrateClick()
        
        var title = "External Audio"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    title = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()

        val p: Player = controller ?: player
        p.stop()
        p.setMediaItem(mediaItem)
        p.prepare()
        p.play()
    }

    fun addToQueue(track: MusicTrack) {
        val p: Player = controller ?: player
        p.addMediaItem(track.toMediaItem())
        vibrationManager.vibrateClick()
    }

    fun playNext(track: MusicTrack) {
        val p: Player = controller ?: player
        val nextIndex = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
        p.addMediaItem(nextIndex, track.toMediaItem())
        vibrationManager.vibrateClick()
    }

    private fun MusicTrack.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist ?: "Unknown Artist")
            .setAlbumTitle(album ?: "Unknown Album")
            .setDisplayTitle(title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setArtworkUri(thumbnailUri?.toUri())
            .build()

        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri.toUri())
            .setMediaMetadata(metadata)
            .build()
    }

    fun playPlaylist(playlist: Playlist) {
        val tracks = playlist.trackUris.mapNotNull { uri -> 
            _uiState.value.tracks.find { it.uri == uri }
        }
        if (tracks.isEmpty()) return
        
        val p: Player = controller ?: player
        p.shuffleModeEnabled = true
        _uiState.update { it.copy(isShuffleOn = true) }
        
        playTrack(tracks.random(), tracks)
        vibrationManager.vibrateSuccess()
    }

    fun togglePlayPause() {
        startPlayerService()
        val p: Player = controller ?: player
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
        vibrationManager.vibrateClick()
    }

    fun stop() {
        val p: Player = controller ?: player
        p.stop()
        p.clearMediaItems()
        _uiState.update { it.copy(currentTrack = null, isPlaying = false, playbackPosition = 0L, duration = 0L) }
        _playbackPosition.value = 0L
        _duration.value = 0L
        vibrationManager.vibrateClick()
    }

    fun seekTo(position: Long) {
        val p: Player = controller ?: player
        p.seekTo(position)
        _uiState.update { it.copy(playbackPosition = position) }
        _playbackPosition.value = position
        vibrationManager.vibrateClick()
    }

    fun skipNext() {
        val p: Player = controller ?: player
        if (p.hasNextMediaItem()) {
            p.seekToNext()
        } else if (p.repeatMode == Player.REPEAT_MODE_ALL) {
            p.seekTo(0, 0)
        }
        vibrationManager.vibrateClick()
    }
    
    fun skipPrevious() {
        val p: Player = controller ?: player
        if (p.currentPosition > 3000) {
            p.seekTo(0)
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPrevious()
        }
        vibrationManager.vibrateClick()
    }

    fun toggleShuffle() {
        val p: Player = controller ?: player
        val newState = !p.shuffleModeEnabled
        p.shuffleModeEnabled = newState
        _uiState.update { it.copy(isShuffleOn = newState) }
        
        if (newState) {
            vibrationManager.vibrateSuccess()
        } else {
            vibrationManager.vibrateClick()
        }
    }

    fun toggleRepeat() {
        val p: Player = controller ?: player
        val newMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        p.repeatMode = newMode
        _uiState.update { it.copy(repeatMode = newMode) }
        
        if (newMode != Player.REPEAT_MODE_OFF) {
            vibrationManager.vibrateSuccess()
        } else {
            vibrationManager.vibrateClick()
        }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        vibrationManager.vibrateClick()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(
            sleepTimerMinutes = minutes, 
            sleepTimerActive = minutes != null,
            sleepTimerRemaining = minutes?.let { it * 60 * 1000L }
        ) }
        
        if (minutes != null) {
            val endTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
            sleepTimerJob = viewModelScope.launch {
                while (System.currentTimeMillis() < endTime) {
                    val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                    _uiState.update { it.copy(sleepTimerRemaining = remaining) }
                    delay(1000)
                }
                fadeOutAndStop()
            }
        }
        vibrationManager.vibrateClick()
    }

    fun toggleSleepTimerDialog() {
        _showSleepTimer.update { !it }
        vibrationManager.vibrateClick()
    }

    private suspend fun fadeOutAndStop() {
        var volume = 1.0f
        while (volume > 0) {
            volume -= 0.05f
            player.volume = volume.coerceAtLeast(0f)
            delay(100)
        }
        val p: Player = controller ?: player
        p.pause()
        player.volume = 1.0f
        _uiState.update { it.copy(sleepTimerMinutes = null, sleepTimerActive = false, sleepTimerRemaining = null) }
        vibrationManager.vibrateLongClick()
    }

    fun setArtShape(shape: String) {
        viewModelScope.launch {
            settingsRepository.setMusicArtShape(shape)
            vibrationManager.vibrateClick()
        }
    }

    fun toggleRotation() {
        viewModelScope.launch {
            settingsRepository.setMusicRotationEnabled(!_uiState.value.rotationEnabled)
            vibrationManager.vibrateClick()
        }
    }
    
    fun togglePipEnabled() {
        viewModelScope.launch {
            settingsRepository.setMusicPipEnabled(!_uiState.value.pipEnabled)
            vibrationManager.vibrateClick()
        }
    }

    fun createPlaylist(name: String, thumbnailUri: String? = null) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name, thumbnailUri = thumbnailUri))
            vibrationManager.vibrateSuccess()
        }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: MusicTrack) {
        viewModelScope.launch {
            val newList = (playlist.trackUris + track.uri).distinct()
            repository.updatePlaylist(playlist.copy(trackUris = newList))
            vibrationManager.vibrateClick()
        }
    }

    fun addSelectedTracksToPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val newList = (playlist.trackUris + _uiState.value.selectedTracks).distinct()
            repository.updatePlaylist(playlist.copy(trackUris = newList))
            clearSelection()
            vibrationManager.vibrateSuccess()
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, trackUri: String) {
        viewModelScope.launch {
            val newList = playlist.trackUris - trackUri
            repository.updatePlaylist(playlist.copy(trackUris = newList))
            vibrationManager.vibrateClick()
        }
    }
    
    fun updatePlaylistThumbnail(playlist: Playlist, uri: Uri) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(thumbnailUri = uri.toString()))
            vibrationManager.vibrateClick()
        }
    }

    fun createPlaylistWithTracks(name: String, trackUris: List<String>) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name, trackUris = trackUris))
            vibrationManager.vibrateSuccess()
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            vibrationManager.vibrateClick()
        }
    }

    fun deleteTrack(track: MusicTrack) {
        viewModelScope.launch {
            repository.deleteTrack(track)
            vibrationManager.vibrateClick()
        }
    }
    
    fun toggleFavorite(track: MusicTrack) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
            vibrationManager.vibrateClick()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        stopShakeDetection()
        sleepTimerJob?.cancel()
        equalizer?.release()
        player.removeListener(playerListener)
        controller?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controller = null
    }
}
