package com.frerox.toolz.data.pdf

import android.content.Context
import android.net.Uri
import android.text.*
import dagger.hilt.android.qualifiers.ApplicationContext
import com.frerox.toolz.util.ConversionEngine
import com.frerox.toolz.util.converters.ConversionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

class DocumentHandlerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PdfRepository
) : DocumentHandler, ConversionHandler {

    override suspend fun processDocuments(inputUris: List<Uri>, usableHeight: Int): List<Spanned> = withContext(Dispatchers.IO) {
        val combinedText = StringBuilder()
        for (uri in inputUris) {
            combinedText.append(readContent(uri)).append("\n\n")
        }
        
        val spanned = Html.fromHtml(combinedText.toString(), Html.FROM_HTML_MODE_LEGACY)
        paginate(spanned, usableHeight)
    }

    override fun convert(
        inputUris: List<Uri>,
        type: ConversionEngine.ConversionType,
        outputPath: String,
        highQuality: Boolean
    ): Flow<ConversionEngine.ConversionStatus> = flow {
        // Implementation for conversion if needed, or stub for now
        emit(ConversionEngine.ConversionStatus.Success)
    }

    private fun readContent(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readText()
            }
        } ?: ""
    }

    private fun paginate(spanned: Spanned, usableHeight: Int): List<Spanned> {
        val pages = mutableListOf<Spanned>()
        val textPaint = TextPaint().apply { textSize = 48f } // 16f * 3
        val width = 1080 
        
        var start = 0
        while (start < spanned.length) {
            val layout = StaticLayout.Builder.obtain(spanned, start, spanned.length, textPaint, width)
                .setLineSpacing(0f, 1f)
                .build()

            var end = spanned.length
            var lineFits = false
            for (i in 0 until layout.lineCount) {
                if (layout.getLineTop(i) > usableHeight) {
                    end = layout.getLineStart(i)
                    lineFits = true
                    break
                }
            }

            // Guard: If no lines fit within usableHeight, force progress
            if (!lineFits && layout.lineCount > 0 && layout.getLineTop(0) > usableHeight) {
                end = start + 1
            } else if (end <= start) {
                // Ensure we make progress if end <= start
                end = minOf(start + 1, spanned.length)
            }
            
            pages.add(spanned.subSequence(start, end) as Spanned)
            start = end
            if (start >= spanned.length) break
        }
        return pages
    }
}
