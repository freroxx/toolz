package com.frerox.toolz.data.pdf

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_annotations")
data class PdfAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileUri: String,
    val pageIndex: Int,
    val type: AnnotationType,
    val data: String, // JSON or encoded path data
    val color: Int,
    val thickness: Float,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String? = null
)

enum class AnnotationType {
    PEN, HIGHLIGHTER, TEXT, STAMP, SHAPE
}
