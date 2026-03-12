package com.frerox.toolz.data.pdf

import android.graphics.Bitmap
import android.net.Uri

data class PdfFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val thumbnail: Bitmap? = null,
    val pageCount: Int = 0,
    val isPinned: Boolean = false,
    val ocrContent: String? = null
)
