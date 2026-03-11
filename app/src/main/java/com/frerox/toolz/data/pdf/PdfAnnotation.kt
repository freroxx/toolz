package com.frerox.toolz.data.pdf

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_annotations")
data class PdfAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileUri: String,
    val pageIndex: Int,
    val type: String, // "PEN", "HIGHLIGHTER"
    val data: String, // JSON or encoded path data
    val color: Int,
    val thickness: Float
)
