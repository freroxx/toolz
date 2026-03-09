package com.frerox.toolz.ui.screens.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder {
    TITLE, ARTIST, RECENT
}

data class MusicUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sortOrder: SortOrder = SortOrder.RECENT,
    val isLoading: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val folders: Map<String, List<MusicTrack>> = emptyMap()
)

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val repository: MusicRepository,
    val player: ExoPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    init {
        // Configure ExoPlayer for music
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, true)

        viewModelScope.launch {
            combine(
                repository.allTracks,
                repository.allPlaylists,
                _uiState.map { it.sortOrder }.distinctUntilChanged()
            ) { tracks, playlists, sortOrder ->
                val sortedTracks = when (sortOrder) {
                    SortOrder.TITLE -> tracks.sortedBy { it.title }
                    SortOrder.ARTIST -> tracks.sortedBy { it.artist ?: "" }
                    SortOrder.RECENT -> tracks.reversed()
                }
                
                val folders = sortedTracks.groupBy { track ->
                    val uri = Uri.parse(track.uri)
                    uri.path?.substringBeforeLast("/")?.substringAfterLast("/") ?: "Internal Storage"
                }

                _uiState.update { it.copy(tracks = sortedTracks, playlists = playlists, folders = folders) }
            }.collect()
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.mediaId
                val track = _uiState.value.tracks.find { it.uri == uri }
                _uiState.update { 
                    it.copy(
                        currentTrack = track,
                        duration = player.duration.coerceAtLeast(0L)
                    ) 
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _uiState.update { it.copy(duration = player.duration.coerceAtLeast(0L)) }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.update { it.copy(isShuffleOn = shuffleModeEnabled) }
            }
        })
        
        // Initial scan
        scanMusic()
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

    fun playTrack(track: MusicTrack, tracks: List<MusicTrack> = _uiState.value.tracks) {
        val mediaItems = tracks.map { t ->
            MediaItem.Builder()
                .setMediaId(t.uri)
                .setUri(t.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setArtworkUri(t.thumbnailUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }
        
        val startIndex = tracks.indexOf(track).coerceAtLeast(0)
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    fun playPlaylist(playlist: Playlist) {
        val tracks = _uiState.value.tracks.filter { it.uri in playlist.trackUris }
        if (tracks.isEmpty()) return
        playTrack(tracks.first(), tracks)
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
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

    fun createPlaylist(name: String, thumbnailUri: String? = null) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name, thumbnailUri = thumbnailUri))
        }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: MusicTrack) {
        viewModelScope.launch {
            val newList = (playlist.trackUris + track.uri).distinct()
            repository.createPlaylist(playlist.copy(trackUris = newList))
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, trackUri: String) {
        viewModelScope.launch {
            val newList = playlist.trackUris - trackUri
            repository.createPlaylist(playlist.copy(trackUris = newList))
        }
    }
    
    fun updatePlaylistThumbnail(playlist: Playlist, uri: Uri) {
        viewModelScope.launch {
            repository.createPlaylist(playlist.copy(thumbnailUri = uri.toString()))
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

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        sleepTimerJob?.cancel()
    }
}
