package com.frerox.toolz.data.pdf

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_metadata")
data class PdfMetadata(
    @PrimaryKey val uri: String,
    val isPinned: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis(),
    val ocrContent: String? = null, // Plain text for search
    val structuredOcrData: String? = null // JSON of blocks with coordinates
)
