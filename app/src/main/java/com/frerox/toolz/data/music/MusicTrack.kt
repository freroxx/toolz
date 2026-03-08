package com.frerox.toolz.data.music

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_tracks")
data class MusicTrack(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val thumbnailUri: String? = null
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val thumbnailUri: String? = null,
    val trackUris: List<String> = emptyList() // Stored as a converter
)
