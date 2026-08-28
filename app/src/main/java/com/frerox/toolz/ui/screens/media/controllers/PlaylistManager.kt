/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.media.controllers

import android.content.Context
import android.net.Uri
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.util.VibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns playlist mutations: createPlaylist, addTrackToPlaylist,
 * addSelectedTracksToPlaylist, removeTrackFromPlaylist,
 * updatePlaylistThumbnail, createPlaylistWithTracks, deletePlaylist, etc.
 */
class PlaylistManager(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicUiState>,
    private val repository: MusicRepository,
    private val context: Context,
    private val vibrationManager: VibrationManager
) {
    private fun hapticClick() { if (!uiState.value.isKaraokeActive) vibrationManager.vibrateClick() }
    private fun hapticSuccess() { if (!uiState.value.isKaraokeActive) vibrationManager.vibrateSuccess() }

    fun createPlaylist(name: String, thumbnailUri: String? = null) {
        scope.launch { repository.createPlaylist(Playlist(name = name, thumbnailUri = thumbnailUri)); hapticSuccess() }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: MusicTrack) {
        scope.launch {
            if (track.path == null && track.sourceUrl != null) {
                repository.insertTrack(track)
            }
            val latestPlaylist = repository.getPlaylistById(playlist.id) ?: playlist
            val updatedUris = (latestPlaylist.trackUris + track.uri).distinct()
            repository.updatePlaylist(latestPlaylist.copy(trackUris = updatedUris))
            hapticClick()
        }
    }

    fun addSelectedTracksToPlaylist(playlist: Playlist) {
        scope.launch {
            val latestPlaylist = repository.getPlaylistById(playlist.id) ?: playlist
            val updatedUris = (latestPlaylist.trackUris + uiState.value.selectedTracks).distinct()
            repository.updatePlaylist(latestPlaylist.copy(trackUris = updatedUris))
            clearSelection()
            hapticSuccess()
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, trackUri: String) {
        scope.launch { repository.updatePlaylist(playlist.copy(trackUris = playlist.trackUris - trackUri)); hapticClick() }
    }

    fun updatePlaylistThumbnail(playlist: Playlist, uri: Uri) {
        scope.launch {
            try {
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
        scope.launch { repository.createPlaylist(Playlist(name = name, trackUris = trackUris)); hapticSuccess() }
    }

    fun deletePlaylist(playlist: Playlist) {
        scope.launch { repository.deletePlaylist(playlist); hapticClick() }
    }

    fun deleteTrack(track: MusicTrack) {
        scope.launch { repository.deleteTrack(track); hapticClick() }
    }

    fun toggleFavorite(track: MusicTrack) {
        scope.launch {
            val isFav = !track.isFavorite
            uiState.update { state ->
                if (state.currentTrack?.uri == track.uri) {
                    state.copy(currentTrack = state.currentTrack?.copy(isFavorite = isFav))
                } else state
            }
            repository.toggleFavorite(track)
            hapticClick()
        }
    }

    private fun clearSelection() {
        uiState.update { it.copy(selectedTracks = emptySet(), isSelectionMode = false) }
    }
}
