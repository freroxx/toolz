/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Int): Playlist?

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

    @Query("SELECT * FROM music_tracks WHERE sourceUrl = :sourceUrl ORDER BY CASE WHEN path IS NULL THEN 1 ELSE 0 END LIMIT 1")
    suspend fun getTrackBySourceUrl(sourceUrl: String): MusicTrack?

    @Query("SELECT * FROM music_tracks WHERE path = :path")
    suspend fun getTrackByPath(path: String): MusicTrack?

    @Query("SELECT * FROM music_tracks WHERE (title = :title AND artist = :artist AND duration BETWEEN :duration - 2000 AND :duration + 2000) OR path = :path LIMIT 1")
    suspend fun findDuplicate(title: String, artist: String?, duration: Long, path: String?): MusicTrack?

    @Query("UPDATE music_tracks SET playCount = playCount + 1, lastPlayed = :timestamp WHERE uri = :uri")
    suspend fun incrementPlayCount(uri: String, timestamp: Long)

    @Query("UPDATE music_tracks SET playCount = playCount + 1, lastPlayed = :timestamp WHERE sourceUrl = :sourceUrl")
    suspend fun incrementPlayCountBySourceUrl(sourceUrl: String, timestamp: Long)

    @Query("DELETE FROM music_tracks WHERE uri = :uri")
    suspend fun deleteTrackByUri(uri: String)

    @Query("SELECT * FROM music_tracks")
    suspend fun getAllTracksSync(): List<MusicTrack>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<Playlist>)
    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsSync(): List<Playlist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<MusicTrack>)

    // P0-01 fix: atomic URI migration + playlist repair in a single transaction.
    // Crash between deleteTrackByUri and insertTrack or playlist updates left
    // playlists referencing a deleted URI — now either all succeed or none.
    @Transaction
    suspend fun atomicMigrateUri(oldUri: String, newTrack: MusicTrack) {
        deleteTrackByUri(oldUri)
        insertTrack(newTrack)
        val playlists = getAllPlaylistsSync()
        playlists.forEach { pl ->
            if (pl.trackUris.contains(oldUri)) {
                updatePlaylist(pl.copy(trackUris = pl.trackUris.map { if (it == oldUri) newTrack.uri else it }))
            }
        }
    }

    @Transaction
    suspend fun atomicDeleteTrackAndCleanPlaylists(track: MusicTrack) {
        deleteTrack(track)
        val playlists = getAllPlaylistsSync()
        playlists.forEach { pl ->
            if (pl.trackUris.contains(track.uri)) {
                updatePlaylist(pl.copy(trackUris = pl.trackUris - track.uri))
            }
        }
    }
}
