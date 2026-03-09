package com.frerox.toolz.data.music

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_tracks")
    fun getAllTracks(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks WHERE isFavorite = 1")
    fun getFavoriteTracks(): Flow<List<MusicTrack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: MusicTrack)

    @Update
    suspend fun updateTrack(track: MusicTrack)

    @Delete
    suspend fun deleteTrack(track: MusicTrack)

    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist)

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)
    
    @Query("SELECT * FROM music_tracks WHERE uri = :uri")
    suspend fun getTrackByUri(uri: String): MusicTrack?
}
