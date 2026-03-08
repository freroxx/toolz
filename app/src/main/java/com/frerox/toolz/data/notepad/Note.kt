package com.frerox.toolz.data.notepad

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val color: Int, // ARGB format
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val fontStyle: String = "SANS_SERIF", // "SANS_SERIF", "SERIF", "MONOSPACE"
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)
