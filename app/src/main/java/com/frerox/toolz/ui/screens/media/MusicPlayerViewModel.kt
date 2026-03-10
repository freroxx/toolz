package com.frerox.toolz.ui.screens.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.ListenableFuture
import javax.inject.Inject

enum class SortOrder {
    TITLE, ARTIST, RECENT
}

data class MusicUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val favoriteTracks: List<MusicTrack> = emptyList(),
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val progress: Long = 0L,
    val duration: Long = 0L,
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
    val hapticIntensity: Float = 0.5f,
    val pipEnabled: Boolean = false
)

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    val player: ExoPlayer,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var isPlayerServiceStarted = false
    private var mediaController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    
    private var equalizer: Equalizer? = null
    private var shakeDetector: ShakeDetector? = null
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    init {
        // Configure Audio Attributes for proper Audio Focus handling and System integration
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        
        player.setAudioAttributes(audioAttributes, true)
        player.setHandleAudioBecomingNoisy(true)

        connectToMediaController()

        viewModelScope.launch {
            settingsRepository.showMusicVisualizer.collect { show ->
                _uiState.update { it.copy(showVisualizer = show) }
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
            settingsRepository.hapticIntensity.collect { intensity ->
                _uiState.update { it.copy(hapticIntensity = intensity) }
            }
        }
        
        viewModelScope.launch {
            settingsRepository.musicPipEnabled.collect { enabled ->
                _uiState.update { it.copy(pipEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            combine(
                repository.allTracks,
                repository.allPlaylists,
                repository.favoriteTracks,
                _uiState.map { it.sortOrder }.distinctUntilChanged()
            ) { tracks, playlists, favorites, sortOrder ->
                val sortedBase = when (sortOrder) {
                    SortOrder.TITLE -> tracks.sortedBy { it.title }
                    SortOrder.ARTIST -> tracks.sortedBy { it.artist ?: "Unknown" }
                    SortOrder.RECENT -> tracks.reversed()
                }

                // Prioritize tracks with thumbnails and real artists
                val prioritizedTracks = sortedBase.sortedWith(
                    compareByDescending<MusicTrack> { it.thumbnailUri != null }
                        .thenByDescending { it.artist != null && it.artist != "Unknown Artist" && it.artist != "<unknown>" }
                )
                
                val folders = prioritizedTracks.groupBy { track ->
                    val uri = track.uri.toUri()
                    uri.path?.substringBeforeLast("/")?.substringAfterLast("/") ?: "Internal Storage"
                }

                _uiState.update { 
                    it.copy(
                        tracks = prioritizedTracks, 
                        playlists = playlists, 
                        favoriteTracks = favorites,
                        folders = folders,
                        currentTrack = prioritizedTracks.find { t -> t.uri == (player.currentMediaItem?.mediaId ?: it.currentTrack?.uri) },
                        isPlaying = player.isPlaying,
                        duration = player.duration.coerceAtLeast(0L),
                        isShuffleOn = player.shuffleModeEnabled,
                        repeatMode = player.repeatMode
                    ) 
                }
            }.collect()
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startProgressUpdate()
                    startPlayerService()
                    initEqualizer()
                    performHapticFeedback()
                } else {
                    stopProgressUpdate()
                    performHapticFeedback()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.mediaId ?: mediaItem?.requestMetadata?.mediaUri?.toString()
                val track = _uiState.value.tracks.find { it.uri == uri }
                _uiState.update { 
                    it.copy(
                        currentTrack = track,
                        duration = player.duration.coerceAtLeast(0L)
                    ) 
                }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                     performHapticFeedback()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _uiState.update { it.copy(duration = player.duration.coerceAtLeast(0L)) }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }
                performHapticFeedback()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.update { it.copy(isShuffleOn = shuffleModeEnabled) }
                performHapticFeedback()
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                super.onPlayerError(error)
                player.prepare()
            }
        })
        
        // Ensure progress is correct even if already playing
        if (player.isPlaying) {
            startProgressUpdate()
        }
    }

    private fun performHapticFeedback() {
        viewModelScope.launch {
            if (settingsRepository.hapticFeedback.first()) {
                val intensityValue = uiState.value.hapticIntensity
                val amplitude = (intensityValue * 255).toInt().coerceIn(1, 255)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(20)
                }
            }
        }
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
                        // Skip individual preset if it fails
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
            // Silently fail to avoid crashing the whole viewmodel
        }
    }

    private fun startPlayerService() {
        if (isPlayerServiceStarted) return
        val intent = Intent(context, MusicPlayerService::class.java)
        try {
            // Use startService instead of startForegroundService for MediaSessionService.
            // MediaSessionService manages its own foreground state and will promote itself
            // when playback starts. This avoids the ForegroundServiceDidNotStartInTimeException.
            context.startService(intent)
            isPlayerServiceStarted = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun connectToMediaController(onConnected: (() -> Unit)? = null) {
        startPlayerService()

        val existing = mediaControllerFuture
        if (existing != null) {
            if (onConnected != null) {
                existing.addListener({
                    if (mediaController != null) onConnected()
                }, ContextCompat.getMainExecutor(context))
            }
            return
        }

        val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture = future

        future.addListener({
            try {
                mediaController = future.get()
                onConnected?.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
                mediaControllerFuture = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun runWhenMediaSessionReady(action: () -> Unit) {
        if (mediaController != null) {
            action()
            return
        }
        connectToMediaController(action)
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(progress = player.currentPosition) }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun scanMusic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.scanDeviceForMusic()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun addCustomFolder(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.scanCustomFolder(uri)
            _uiState.update { it.copy(isLoading = false) }
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
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTracks = emptySet(), isSelectionMode = false) }
    }

    fun playTrack(track: MusicTrack, tracks: List<MusicTrack> = _uiState.value.tracks) {
        // Wait for the media session connection to avoid race conditions where playback starts
        // before Android recognizes an active MediaSessionService.
        runWhenMediaSessionReady {
            val mediaItems = tracks.map { t ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist ?: "Unknown Artist")
                    .setAlbumTitle(t.album ?: "Unknown Album")
                    .setDisplayTitle(t.title)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC) // Required for wavy bar on Android 16/17
                    .setIsPlayable(true)
                    .setArtworkUri(t.thumbnailUri?.toUri()) // Required for color matching
                    .build()

                MediaItem.Builder()
                    .setMediaId(t.uri)
                    .setUri(t.uri.toUri())
                    .setMediaMetadata(metadata)
                    .build()
            }

            val startIndex = tracks.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
            player.stop()
            player.clearMediaItems()
            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()
        }
    }

    fun playPlaylist(playlist: Playlist) {
        val tracks = _uiState.value.tracks.filter { it.uri in playlist.trackUris }
        if (tracks.isEmpty()) return
        playTrack(tracks.first(), tracks)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            // Resume after pause should wait for media-session readiness to keep notification
            // dynamic media content reliable on newer Android versions.
            runWhenMediaSessionReady {
                player.play()
            }
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _uiState.update { it.copy(currentTrack = null, isPlaying = false, progress = 0L) }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
        _uiState.update { it.copy(progress = position) }
    }

    fun skipNext() = player.seekToNext()
    fun skipPrevious() = player.seekToPrevious()

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun toggleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerMinutes = minutes) }
        if (minutes != null) {
            sleepTimerJob = viewModelScope.launch {
                delay(minutes * 60 * 1000L)
                fadeOutAndStop()
            }
        }
    }

    private suspend fun fadeOutAndStop() {
        var volume = 1.0f
        while (volume > 0) {
            volume -= 0.05f
            player.volume = volume.coerceAtLeast(0f)
            delay(100)
        }
        player.pause()
        player.volume = 1.0f
        _uiState.update { it.copy(sleepTimerMinutes = null) }
    }

    fun setArtShape(shape: String) {
        viewModelScope.launch {
            settingsRepository.setMusicArtShape(shape)
        }
    }

    fun toggleRotation() {
        viewModelScope.launch {
            settingsRepository.setMusicRotationEnabled(!_uiState.value.rotationEnabled)
        }
    }
    
    fun togglePipEnabled() {
        viewModelScope.launch {
            settingsRepository.setMusicPipEnabled(!_uiState.value.pipEnabled)
        }
    }

    fun createPlaylist(name: String, thumbnailUri: String? = null) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name, thumbnailUri = thumbnailUri))
        }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: MusicTrack) {
        viewModelScope.launch {
            val newList = (playlist.trackUris + track.uri).distinct()
            repository.updatePlaylist(playlist.copy(trackUris = newList))
        }
    }

    fun addSelectedTracksToPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val newList = (playlist.trackUris + _uiState.value.selectedTracks).distinct()
            repository.updatePlaylist(playlist.copy(trackUris = newList))
            clearSelection()
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, trackUri: String) {
        viewModelScope.launch {
            val newList = playlist.trackUris - trackUri
            repository.updatePlaylist(playlist.copy(trackUris = newList))
        }
    }
    
    fun updatePlaylistThumbnail(playlist: Playlist, uri: Uri) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(thumbnailUri = uri.toString()))
        }
    }

    fun createPlaylistWithTracks(name: String, trackUris: List<String>) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name, trackUris = trackUris))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun deleteTrack(track: MusicTrack) {
        viewModelScope.launch {
            repository.deleteTrack(track)
        }
    }
    
    fun toggleFavorite(track: MusicTrack) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        stopShakeDetection()
        sleepTimerJob?.cancel()
        equalizer?.release()
        mediaController?.release()
        mediaController = null
        mediaControllerFuture = null
    }
}
