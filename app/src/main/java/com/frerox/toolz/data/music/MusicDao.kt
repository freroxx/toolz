package com.frerox.toolz.data.music

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_tracks")
    fun getAllTracks(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks WHERE isFavorite = 1")
    fun getFavoriteTracks(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT 100")
    fun getRecentlyPlayed(): Flow<List<MusicTrack>>

    @Query("SELECT * FROM music_tracks WHERE playCount > 0 ORDER BY playCount DESC LIMIT 100")
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

    @Query("SELECT * FROM music_tracks WHERE sourceUrl = :sourceUrl")
    suspend fun getTrackBySourceUrl(sourceUrl: String): MusicTrack?

    @Query("SELECT * FROM music_tracks WHERE path = :path")
    suspend fun getTrackByPath(path: String): MusicTrack?

    @Query("SELECT * FROM music_tracks WHERE (title = :title AND artist = :artist AND duration BETWEEN :duration - 2000 AND :duration + 2000) OR path = :path LIMIT 1")
    suspend fun findDuplicate(title: String, artist: String?, duration: Long, path: String?): MusicTrack?

    @Query("UPDATE music_tracks SET playCount = playCount + 1, lastPlayed = :timestamp WHERE uri = :uri")
    suspend fun incrementPlayCount(uri: String, timestamp: Long)

    @Query("UPDATE music_tracks SET playCount = playCount + 1, lastPlayed = :timestamp WHERE sourceUrl = :sourceUrl")
    suspend fun incrementPlayCountBySourceUrl(sourceUrl: String, timestamp: Long)

    @Query("SELECT * FROM music_tracks")
    suspend fun getAllTracksSync(): List<MusicTrack>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsSync(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<MusicTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<Playlist>)
}
