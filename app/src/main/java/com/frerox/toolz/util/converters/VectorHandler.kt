package com.frerox.toolz.util.converters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.caverock.androidsvg.SVG
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VectorHandler @Inject constructor(
    @ApplicationContext private val context: Context
) : ConversionHandler {

    override fun convert(
        inputUris: List<Uri>,
        type: ConversionEngine.ConversionType,
        outputPath: String,
        highQuality: Boolean
    ): Flow<ConversionEngine.ConversionStatus> = flow {
        if (inputUris.isEmpty()) {
            emit(ConversionEngine.ConversionStatus.Error("No input files provided"))
            return@flow
        }

        try {
            emit(ConversionEngine.ConversionStatus.Progress(0))
            val inputUri = inputUris.first()
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw Exception("Could not open input stream")

            val svg = inputStream.use { SVG.getFromInputStream(it) }
            
            // Handle cases where document dimensions might be missing or invalid
            val docWidth = if (svg.documentWidth > 0) svg.documentWidth else svg.documentViewBox.width()
            val docHeight = if (svg.documentHeight > 0) svg.documentHeight else svg.documentViewBox.height()
            
            val baseWidth = if (docWidth > 0) docWidth else 512f
            val baseHeight = if (docHeight > 0) docHeight else 512f

            when (type) {
                ConversionEngine.ConversionType.SVG_TO_PNG,
                ConversionEngine.ConversionType.SVG_TO_JPG -> {
                    // Increase resolution for high quality
                    val scale = if (highQuality) 2f else 1f
                    val width = (baseWidth * scale).toInt()
                    val height = (baseHeight * scale).toInt()

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    
                    if (type == ConversionEngine.ConversionType.SVG_TO_JPG) {
                        canvas.drawColor(Color.WHITE)
                    }
                    
                    canvas.scale(scale, scale)
                    svg.renderToCanvas(canvas)

                    val format = if (type == ConversionEngine.ConversionType.SVG_TO_PNG) {
                        Bitmap.CompressFormat.PNG
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }
                    val quality = if (highQuality) 100 else 80

                    File(outputPath).outputStream().use { out ->
                        bitmap.compress(format, quality, out)
                    }
                    bitmap.recycle()
                }
                ConversionEngine.ConversionType.SVG_TO_PDF -> {
                    val pdfDocument = PdfDocument()
                    // PDF coordinates are typically in points (1/72 inch). 
                    // SVG coordinates are often pixels. We'll treat them 1:1 for simplicity
                    // unless we want to do specific scaling.
                    val pageInfo = PdfDocument.PageInfo.Builder(baseWidth.toInt(), baseHeight.toInt(), 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    
                    svg.renderToCanvas(page.canvas)
                    pdfDocument.finishPage(page)

                    File(outputPath).outputStream().use { out ->
                        pdfDocument.writeTo(out)
                    }
                    pdfDocument.close()
                }
                else -> throw Exception("Unsupported conversion type: $type")
            }

            emit(ConversionEngine.ConversionStatus.Progress(100))
            emit(ConversionEngine.ConversionStatus.Success(outputPath))
        } catch (e: Exception) {
            emit(ConversionEngine.ConversionStatus.Error("Vector conversion failed: ${e.localizedMessage}"))
        }
    }
}
