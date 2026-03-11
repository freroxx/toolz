package com.frerox.toolz.data.pdf

import android.net.Uri

data class PdfFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModified: Long
)
