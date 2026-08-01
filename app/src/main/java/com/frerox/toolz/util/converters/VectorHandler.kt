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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

/**
 * Handles SVG → raster / PDF conversions using AndroidSVG.
 *
 * Supports: SVG_TO_PNG, SVG_TO_JPG, SVG_TO_WEBP, SVG_TO_PDF
 */
@Singleton
class VectorHandler @Inject constructor(
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
            val svg = loadSvg(inputUri)

            val docW = if (svg.documentWidth > 0) svg.documentWidth
                       else if (svg.documentViewBox != null) svg.documentViewBox.width()
                       else 512f
            val docH = if (svg.documentHeight > 0) svg.documentHeight
                       else if (svg.documentViewBox != null) svg.documentViewBox.height()
                       else 512f

            emit(ConversionEngine.ConversionStatus.Progress(25))

            when (type) {
                ConversionEngine.ConversionType.SVG_TO_PNG,
                ConversionEngine.ConversionType.SVG_TO_JPG,
                ConversionEngine.ConversionType.SVG_TO_WEBP -> {
                    val scale = if (highQuality) 4f else 2f
                    val width = (docW * scale).toInt().coerceAtLeast(1)
                    val height = (docH * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)

                    // White background for non-PNG formats
                    if (type != ConversionEngine.ConversionType.SVG_TO_PNG) {
                        canvas.drawColor(Color.WHITE)
                    }

                    svg.renderToCanvas(canvas, android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat()))

                    emit(ConversionEngine.ConversionStatus.Progress(70))

                    val (format, quality) = when (type) {
                        ConversionEngine.ConversionType.SVG_TO_PNG ->
                            Bitmap.CompressFormat.PNG to 100

                        ConversionEngine.ConversionType.SVG_TO_WEBP ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                                Bitmap.CompressFormat.WEBP_LOSSLESS to 100
                            else
                                @Suppress("DEPRECATION")
                                Bitmap.CompressFormat.WEBP to (if (highQuality) 90 else 75)

                        else -> // SVG_TO_JPG
                            Bitmap.CompressFormat.JPEG to (if (highQuality) 95 else 80)
                    }

                    File(outputPath).outputStream().use { out ->
                        bitmap.compress(format, quality, out)
                    }
                    bitmap.recycle()
                }

                ConversionEngine.ConversionType.SVG_TO_PDF -> {
                    val pdfDocument = PdfDocument()
                    val pageW = docW.toInt().coerceAtLeast(1)
                    val pageH = docH.toInt().coerceAtLeast(1)
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
                    val page = pdfDocument.startPage(pageInfo)

                    // White background
                    page.canvas.drawColor(Color.WHITE)
                    svg.renderToCanvas(page.canvas, android.graphics.RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()))
                    pdfDocument.finishPage(page)

                    File(outputPath).outputStream().use { pdfDocument.writeTo(it) }
                    pdfDocument.close()
                }

                else -> throw IllegalArgumentException("Unsupported SVG conversion: $type")
            }

            emit(ConversionEngine.ConversionStatus.Progress(100))
            emit(ConversionEngine.ConversionStatus.Success(outputPath))
        } catch (e: Exception) {
            emit(ConversionEngine.ConversionStatus.Error("Vector conversion failed: ${e.localizedMessage}"))
        }
    }

    private fun loadSvg(uri: Uri): SVG {
        return if (uri.scheme == "file") {
            File(uri.path!!).inputStream().use { SVG.getFromInputStream(it) }
        } else {
            context.contentResolver.openInputStream(uri)?.use { SVG.getFromInputStream(it) }
                ?: throw Exception("Could not open SVG input stream")
        }
    }
}
