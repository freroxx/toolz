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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "music_tracks",
    indices = [
        Index(value = ["lastPlayed"]),
        Index(value = ["playCount"]),
        Index(value = ["sourceUrl"]),
        Index(value = ["path"]),
        Index(value = ["stableId"])
    ]
)
data class MusicTrack(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: Long = -1,
    val duration: Long,
    val thumbnailUri: String? = null,
    val isFavorite: Boolean = false,
    val lastPlayed: Long = 0L,
    val playCount: Int = 0,
    val path: String? = null,
    val sourceUrl: String? = null,
    val dateAdded: Long = java.lang.System.currentTimeMillis(),
    // Stable business key (hash of path|sourceUrl) — URI can change on re-index, this persists
    val stableId: String = "",
    // P3-14 per-track LRC offset in ms (−2000..+2000) for manual sync
    val lrcOffsetMs: Long = 0L,
    // AI Cache Fields
    val aiLyrics: String? = null,
    val aiArtistVitals: String? = null,
    val aiSongMeaning: String? = null,
    val aiRecommendationsJson: String? = null, // Store list of recommendations as JSON string
    val lastAiSync: Long = 0L,
    val karaokeSingCount: Int = 0
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val thumbnailUri: String? = null,
    val trackUris: List<String> = emptyList(),
    val isSystemPlaylist: Boolean = false,
    val createdAt: Long = java.lang.System.currentTimeMillis()
)
