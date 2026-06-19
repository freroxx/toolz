package com.frerox.toolz.data.pdf

import android.content.Context
import android.net.Uri
import android.text.*
import dagger.hilt.android.qualifiers.ApplicationContext
import com.frerox.toolz.util.ConversionEngine
import com.frerox.toolz.util.converters.ConversionHandler
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
        emit(ConversionEngine.ConversionStatus.Progress(0))
        try {
            if (type == ConversionEngine.ConversionType.MD_TO_HTML || type == ConversionEngine.ConversionType.MD_TO_TXT) {
                // Combine documents
                val combinedText = StringBuilder()
                for (uri in inputUris) {
                    combinedText.append(readContent(uri)).append("\n\n")
                }

                if (type == ConversionEngine.ConversionType.MD_TO_HTML) {
                    val parser = org.commonmark.parser.Parser.builder().build()
                    val document = parser.parse(combinedText.toString())
                    val renderer = org.commonmark.renderer.html.HtmlRenderer.builder().build()
                    val html = renderer.render(document)
                    java.io.FileOutputStream(java.io.File(outputPath)).use { out ->
                        out.write(html.toByteArray())
                    }
                } else {
                    // MD to TXT: just save as text
                    java.io.FileOutputStream(java.io.File(outputPath)).use { out ->
                        out.write(combinedText.toString().toByteArray())
                    }
                }
            } else {
                val a4Width = 595   // A4 in points at 72dpi
                val a4Height = 842
                val margin = 48
                val usableHeight = a4Height - 2 * margin

                val pages = processDocuments(inputUris, usableHeight)
                val pdfDocument = android.graphics.pdf.PdfDocument()

                for ((index, pageSpanned) in pages.withIndex()) {
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(a4Width, a4Height, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    val textPaint = android.text.TextPaint().apply { textSize = 16f }
                    val layout = android.text.StaticLayout.Builder.obtain(pageSpanned, 0, pageSpanned.length, textPaint, a4Width - 2 * margin)
                        .setLineSpacing(0f, 1f)
                        .build()

                    canvas.save()
                    canvas.translate(margin.toFloat(), margin.toFloat())
                    layout.draw(canvas)
                    canvas.restore()

                    pdfDocument.finishPage(page)
                    emit(ConversionEngine.ConversionStatus.Progress(((index + 1).toFloat() / pages.size * 100).toInt()))
                }

                java.io.FileOutputStream(java.io.File(outputPath)).use { out ->
                    pdfDocument.writeTo(out)
                }
                pdfDocument.close()
            }
            
            emit(ConversionEngine.ConversionStatus.Progress(100))
            emit(ConversionEngine.ConversionStatus.Success(outputPath))
        } catch (e: Exception) {
            emit(ConversionEngine.ConversionStatus.Error("Document conversion failed: ${e.localizedMessage}"))
        }
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

            // Safety Guard: If no lines fit within usableHeight, force progress
            // Even if the line is taller than usableHeight, we must include at least one character
            // or we will loop infinitely.
            if (!lineFits) {
                // If it all fits, we are done.
                // If it doesn't fit, we must take at least one character.
                end = if (layout.lineCount > 0 && layout.getLineTop(0) > usableHeight) {
                    // Line is taller than usableHeight, force inclusion of at least this line
                    layout.getLineEnd(0).coerceAtLeast(start + 1)
                } else {
                    spanned.length
                }
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
