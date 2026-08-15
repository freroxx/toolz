/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.util.converters

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Html
import android.text.StaticLayout
import android.text.TextPaint
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.text.TextContentRenderer
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Handles text / markup → PDF / HTML / TXT conversions:
 *
 *  - TEXT_TO_PDF  — plain text → A4 PDF
 *  - MD_TO_PDF    — Markdown → HTML → A4 PDF
 *  - MD_TO_HTML   — Markdown → HTML file
 *  - MD_TO_TXT    — Markdown → plain text (stripped markdown syntax)
 *  - HTML_TO_PDF  — HTML (Jsoup-cleaned) → A4 PDF
 */
@Singleton
class DocumentHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConversionHandler {

    override fun convert(
        inputUris: List<Uri>,
        type: ConversionEngine.ConversionType,
        outputPath: String,
        highQuality: Boolean,
    ): Flow<ConversionEngine.ConversionStatus> = flow {
        emit(ConversionEngine.ConversionStatus.Progress(0))
        try {
            val inputUri = inputUris.first()
            val rawContent = readText(inputUri)

            emit(ConversionEngine.ConversionStatus.Progress(20))

            when (type) {
                ConversionEngine.ConversionType.MD_TO_TXT -> {
                    // Use commonmark's TextContentRenderer to strip markdown syntax cleanly
                    val parser = Parser.builder().build()
                    val document = parser.parse(rawContent)
                    val renderer = TextContentRenderer.builder().build()
                    val plainText = renderer.render(document)
                    File(outputPath).writeText(plainText)
                    emit(ConversionEngine.ConversionStatus.Progress(100))
                    emit(ConversionEngine.ConversionStatus.Success(outputPath))
                    return@flow
                }

                ConversionEngine.ConversionType.MD_TO_HTML -> {
                    val html = markdownToHtml(rawContent)
                    // Wrap in a full HTML document with basic styling
                    val fullHtml = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; 
           max-width: 800px; margin: 40px auto; padding: 0 20px; line-height: 1.6; }
    code { background: #f4f4f4; padding: 2px 6px; border-radius: 3px; }
    pre { background: #f4f4f4; padding: 16px; border-radius: 6px; overflow-x: auto; }
    blockquote { border-left: 4px solid #ccc; margin: 0; padding-left: 16px; color: #666; }
    img { max-width: 100%; }
  </style>
</head>
<body>
$html
</body>
</html>"""
                    File(outputPath).writeText(fullHtml)
                    emit(ConversionEngine.ConversionStatus.Progress(100))
                    emit(ConversionEngine.ConversionStatus.Success(outputPath))
                    return@flow
                }

                else -> {
                    // PDF paths
                    val htmlContent = when (type) {
                        ConversionEngine.ConversionType.MD_TO_PDF -> markdownToHtml(rawContent)
                        ConversionEngine.ConversionType.HTML_TO_PDF -> cleanHtml(rawContent)
                        else -> rawContent.replace("\n", "<br>")  // TEXT_TO_PDF
                    }

                    emit(ConversionEngine.ConversionStatus.Progress(40))
                    renderHtmlToPdf(htmlContent, outputPath, highQuality)
                    emit(ConversionEngine.ConversionStatus.Progress(100))
                    emit(ConversionEngine.ConversionStatus.Success(outputPath))
                }
            }
        } catch (e: Exception) {
            emit(ConversionEngine.ConversionStatus.Error("Document conversion failed: ${e.localizedMessage}"))
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun readText(uri: Uri): String {
        return if (uri.scheme == "file") {
            File(uri.path!!).readText()
        } else {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""
        }
    }

    private fun markdownToHtml(markdown: String): String {
        val parser = Parser.builder().build()
        val document = parser.parse(markdown)
        val renderer = HtmlRenderer.builder().build()
        return renderer.render(document)
    }

    private fun cleanHtml(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script, style, noscript").remove()
        return doc.body().html()
    }

    private fun renderHtmlToPdf(html: String, outputPath: String, highQuality: Boolean) {
        val pdfDocument = PdfDocument()

        val pageW = 595
        val pageH = 842
        val margin = 48
        val textWidth = pageW - 2 * margin
        val usableH = pageH - 2 * margin

        val bodyPaint = TextPaint().apply {
            textSize = if (highQuality) 13f else 11f
            color = Color.DKGRAY
            isAntiAlias = true
        }

        val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)

        val staticLayout = StaticLayout.Builder
            .obtain(spanned, 0, spanned.length, bodyPaint, textWidth)
            .setLineSpacing(2f, 1.15f)
            .build()

        val totalHeight = staticLayout.height
        val pageCount = ceil(totalHeight.toDouble() / usableH).toInt().coerceAtLeast(1)

        try {
            for (i in 0 until pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)

                canvas.save()
                canvas.translate(margin.toFloat(), margin.toFloat())
                canvas.clipRect(0, 0, textWidth, usableH)
                canvas.translate(0f, -(i * usableH).toFloat())
                staticLayout.draw(canvas)
                canvas.restore()

                val pageNumPaint = Paint().apply {
                    textSize = 9f
                    color = Color.LTGRAY
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    "${i + 1} / $pageCount",
                    (pageW / 2).toFloat(),
                    (pageH - margin / 2).toFloat(),
                    pageNumPaint,
                )

                pdfDocument.finishPage(page)
            }

            FileOutputStream(File(outputPath)).use { pdfDocument.writeTo(it) }
        } finally {
            pdfDocument.close()
        }
    }
}
