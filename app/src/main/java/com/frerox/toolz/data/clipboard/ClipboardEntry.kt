package com.frerox.toolz.data.clipboard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_entries")
data class ClipboardEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var type: String = "TEXT", // TEXT, URL, COLOR, PHONE, OTP, EMAIL, MATHS, PERSONAL, CODE, ADDRESS
    val isPinned: Boolean = false,
    val previewUrl: String? = null,
    val sourceApp: String? = null,
    val summary: String? = null
)
