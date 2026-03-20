package com.frerox.toolz.data.music

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_tracks")
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
    val dateAdded: Long = java.lang.System.currentTimeMillis(),
    // AI Cache Fields
    val aiLyrics: String? = null,
    val aiArtistVitals: String? = null,
    val aiSongMeaning: String? = null,
    val aiRecommendationsJson: String? = null, // Store list of recommendations as JSON string
    val lastAiSync: Long = 0L
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
