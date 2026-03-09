package com.frerox.toolz.ui.screens.media

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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
    val isLoading: Boolean = false
)

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val repository: MusicRepository,
    val player: ExoPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()

    private var progressJob: Job? = null

    init {
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
                _uiState.update { it.copy(tracks = sortedTracks, playlists = playlists) }
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
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(progress = player.currentPosition) }
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun playTrack(track: MusicTrack) {
        val currentTracks = _uiState.value.tracks
        val mediaItems = currentTracks.map { t ->
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
        
        val startIndex = currentTracks.indexOf(track)
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    fun playPlaylist(playlist: Playlist) {
        val tracks = _uiState.value.tracks.filter { it.uri in playlist.trackUris }
        if (tracks.isEmpty()) return

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
        
        player.setMediaItems(mediaItems)
        player.prepare()
        player.play()
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

    fun addTracks(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            uris.forEach { uri ->
                val track = repository.extractMetadata(uri)
                repository.addTrack(track)
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteTrack(track: MusicTrack) {
        viewModelScope.launch {
            repository.deleteTrack(track)
        }
    }

    fun createPlaylist(name: String, trackUris: List<String>, thumbnailUri: String? = null) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name, trackUris = trackUris, thumbnailUri = thumbnailUri))
        }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: MusicTrack) {
        viewModelScope.launch {
            val newList = (playlist.trackUris + track.uri).distinct()
            repository.createPlaylist(playlist.copy(trackUris = newList))
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
    }
}
