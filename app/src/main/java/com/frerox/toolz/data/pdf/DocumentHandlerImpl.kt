package com.frerox.toolz.data.pdf

import android.content.Context
import android.net.Uri
import android.text.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

class DocumentHandlerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PdfRepository
) : DocumentHandler {

    override suspend fun processDocuments(inputUris: List<Uri>, usableHeight: Int): List<Spanned> = withContext(Dispatchers.IO) {
        val combinedText = StringBuilder()
        for (uri in inputUris) {
            combinedText.append(readContent(uri)).append("\n\n")
        }
        
        val spanned = Html.fromHtml(combinedText.toString(), Html.FROM_HTML_MODE_LEGACY)
        paginate(spanned, usableHeight)
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
            for (i in 0 until layout.lineCount) {
                if (layout.getLineTop(i) > usableHeight) {
                    end = layout.getLineStart(i)
                    break
                }
            }
            
            // Safety: ensure we make progress
            if (end <= start) {
                // If we can't fit even one line, take one character/span-boundary or break
                end = minOf(start + 1, spanned.length)
            }
            
            pages.add(spanned.subSequence(start, end) as Spanned)
            start = end
            if (start == spanned.length) break
        }
        return pages
    }
}
