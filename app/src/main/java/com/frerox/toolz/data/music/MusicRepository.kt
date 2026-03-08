package com.frerox.toolz.data.music

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val musicDao: MusicDao,
    @ApplicationContext private val context: Context
) {
    val allTracks: Flow<List<MusicTrack>> = musicDao.getAllTracks()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun addTrack(track: MusicTrack) = musicDao.insertTrack(track)
    suspend fun deleteTrack(track: MusicTrack) = musicDao.deleteTrack(track)
    
    suspend fun createPlaylist(playlist: Playlist) = musicDao.insertPlaylist(playlist)
    suspend fun deletePlaylist(playlist: Playlist) = musicDao.deletePlaylist(playlist)

    fun scanFolder(folderUri: Uri): List<MusicTrack> {
        val tracks = mutableListOf<MusicTrack>()
        // Simplified scanning logic for the example
        // In a real app, use contentResolver with proper selection
        return tracks
    }
}
