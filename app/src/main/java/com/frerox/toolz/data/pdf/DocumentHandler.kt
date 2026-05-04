package com.frerox.toolz.data.pdf

import android.net.Uri
import android.text.Spanned
import com.frerox.toolz.util.converters.ConversionHandler

interface DocumentHandler : ConversionHandler {
    suspend fun processDocuments(inputUris: List<Uri>, usableHeight: Int): List<Spanned>
}
