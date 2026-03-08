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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MusicUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val isLoading: Boolean = false
)

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val repository: MusicRepository,
    val player: ExoPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.allTracks, repository.allPlaylists) { tracks, playlists ->
                _uiState.update { it.copy(tracks = tracks, playlists = playlists) }
            }.collect()
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val uri = mediaItem?.mediaId
                val track = _uiState.value.tracks.find { it.uri == uri }
                _uiState.update { it.copy(currentTrack = track) }
            }
        })
    }

    fun playTrack(track: MusicTrack) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.uri)
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
            .build()
        
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipNext() = player.seekToNext()
    fun skipPrevious() = player.seekToPrevious()

    fun addTracks(uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                // In a real app, extract metadata using MediaMetadataRetriever
                val track = MusicTrack(
                    uri = uri.toString(),
                    title = uri.lastPathSegment ?: "Unknown",
                    artist = "Unknown Artist",
                    album = "Unknown Album",
                    duration = 0
                )
                repository.addTrack(track)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Player is provided by Hilt as Singleton/Activity scoped usually, 
        // but here we might want to release it if it's not managed by service
    }
}
