package com.frerox.toolz.data.pdf

import android.net.Uri
import android.text.Spanned

interface DocumentHandler {
    suspend fun processDocuments(inputUris: List<Uri>, usableHeight: Int): List<Spanned>
}
