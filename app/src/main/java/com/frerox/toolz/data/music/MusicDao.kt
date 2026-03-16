package com.frerox.toolz.data.music

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_tracks")
    fun getAllTracks(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks WHERE isFavorite = 1")
    fun getFavoriteTracks(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks ORDER BY lastPlayed DESC LIMIT 50")
    fun getRecentlyPlayed(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks ORDER BY playCount DESC LIMIT 50")
    fun getMostPlayed(): Flow<List<MusicTrack>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: MusicTrack): Long

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

    @Query("SELECT * FROM music_tracks WHERE path = :path")
    suspend fun getTrackByPath(path: String): MusicTrack?

    @Query("SELECT * FROM music_tracks WHERE (title = :title AND artist = :artist AND duration BETWEEN :duration - 2000 AND :duration + 2000) OR path = :path LIMIT 1")
    suspend fun findDuplicate(title: String, artist: String?, duration: Long, path: String?): MusicTrack?

    @Query("UPDATE music_tracks SET playCount = playCount + 1, lastPlayed = :timestamp WHERE uri = :uri")
    suspend fun incrementPlayCount(uri: String, timestamp: Long)
}
