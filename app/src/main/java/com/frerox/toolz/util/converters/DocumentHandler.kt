package com.frerox.toolz.util.converters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Html
import android.text.StaticLayout
import android.text.TextPaint
import com.frerox.toolz.util.ConversionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentHandler @Inject constructor(
    private val context: Context
) : ConversionHandler {

    override fun convert(
        inputUris: List<Uri>,
        type: ConversionEngine.ConversionType,
        outputPath: String,
        highQuality: Boolean
    ): Flow<ConversionEngine.ConversionStatus> = flow {
        emit(ConversionEngine.ConversionStatus.Progress(0))

        try {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val textPaint = TextPaint().apply {
                textSize = 14f
                color = android.graphics.Color.BLACK
            }

            // Simple handling for now (assuming 1 input for simple case)
            val inputUri = inputUris.first()
            val content = context.contentResolver.openInputStream(inputUri)?.bufferedReader().use { it?.readText() } ?: ""
            
            val htmlContent = if (type.name.contains("MD")) {
                val parser = Parser.builder().build()
                val document = parser.parse(content)
                val renderer = HtmlRenderer.builder().build()
                renderer.render(document)
            } else {
                content // Text
            }

            val spanned = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_LEGACY)
            
            // Logic for pagination (rough)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            val staticLayout = StaticLayout.Builder.obtain(
                spanned, 0, spanned.length, textPaint, canvas.width
            ).build()
            
            staticLayout.draw(canvas)
            pdfDocument.finishPage(page)

            pdfDocument.writeTo(FileOutputStream(File(outputPath)))
            pdfDocument.close()
            
            emit(ConversionEngine.ConversionStatus.Success(outputPath))
        } catch (e: Exception) {
            emit(ConversionEngine.ConversionStatus.Error(e.localizedMessage ?: "Unknown error"))
        }
    }
}
