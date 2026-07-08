package com.frerox.toolz.util.converters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.frerox.toolz.util.ConversionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles all Image ↔ PDF conversions natively via Android SDK:
 *
 *  - IMAGE_TO_PDF  — one or more bitmaps → single PDF (A4 portrait, images scaled to fit)
 *  - PDF_TO_PNG    — each page of a PDF → individual PNG files
 *  - PDF_TO_JPG    — each page of a PDF → individual JPEG files
 *  - PDF_TO_WEBP   — each page of a PDF → individual WebP files
 */
@Singleton
class ImageDocumentHandler @Inject constructor(
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
            when (type) {
                ConversionEngine.ConversionType.IMAGE_TO_PDF ->
                    imagesToPdf(inputUris, outputPath, highQuality)

                ConversionEngine.ConversionType.PDF_TO_PNG,
                ConversionEngine.ConversionType.PDF_TO_JPG,
                ConversionEngine.ConversionType.PDF_TO_WEBP -> {
                    val uriToProcess = inputUris.first()
                    val finalPath = pdfToImages(uriToProcess, outputPath, type, highQuality) { progress ->
                        // We can't emit inside a lambda but we model it through a collect
                    }
                    emit(ConversionEngine.ConversionStatus.Progress(100))
                    emit(ConversionEngine.ConversionStatus.Success(finalPath))
                    return@flow
                }

                else -> throw IllegalArgumentException("Unsupported type: $type")
            }
            emit(ConversionEngine.ConversionStatus.Progress(100))
            emit(ConversionEngine.ConversionStatus.Success(outputPath))
        } catch (e: Exception) {
            emit(ConversionEngine.ConversionStatus.Error("Conversion failed: ${e.localizedMessage}"))
        }
    }

    // ── IMAGE → PDF ──────────────────────────────────────────────────────────

    private fun imagesToPdf(
        inputUris: List<Uri>,
        outputPath: String,
        highQuality: Boolean,
    ) {
        val pdfDocument = PdfDocument()
        val a4Width = 595   // A4 in points at 72dpi
        val a4Height = 842
        val margin = 32

        try {
            for ((index, uri) in inputUris.withIndex()) {
                val bitmap = decodeBitmap(uri) ?: continue

                // Scale bitmap to fit A4 with margins
                val availW = (a4Width - 2 * margin).toFloat()
                val availH = (a4Height - 2 * margin).toFloat()
                val scaleW = availW / bitmap.width
                val scaleH = availH / bitmap.height
                val scale = minOf(scaleW, scaleH)

                val scaledW = (bitmap.width * scale).toInt()
                val scaledH = (bitmap.height * scale).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, highQuality)
                bitmap.recycle()

                val pageInfo = PdfDocument.PageInfo.Builder(a4Width, a4Height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // White background
                canvas.drawColor(android.graphics.Color.WHITE)

                // Centre on page
                val left = (a4Width - scaledW) / 2f
                val top = (a4Height - scaledH) / 2f
                canvas.drawBitmap(scaledBitmap, left, top, Paint(Paint.ANTI_ALIAS_FLAG))
                scaledBitmap.recycle()

                pdfDocument.finishPage(page)
            }

            FileOutputStream(File(outputPath)).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
        }
    }

    // ── PDF → IMAGE ──────────────────────────────────────────────────────────

    private fun pdfToImages(
        inputUri: Uri,
        outputPath: String,
        type: ConversionEngine.ConversionType,
        highQuality: Boolean,
        onProgress: (Int) -> Unit,
    ): String {
        val inputFile = File(inputUri.path ?: throw Exception("Invalid input file path"))
        if (!inputFile.exists()) throw Exception("Input file not found: ${inputFile.absolutePath}")

        val fd = android.os.ParcelFileDescriptor.open(
            inputFile,
            android.os.ParcelFileDescriptor.MODE_READ_ONLY
        )
        val renderer = PdfRenderer(fd)
        val pageCount = renderer.pageCount
        val scale = if (highQuality) 3f else 1.5f

        var actualOutputPath = outputPath
        val outputFile = File(outputPath)
        val outputDir = outputFile.parentFile ?: throw Exception("Invalid output directory")
        val baseName = outputFile.nameWithoutExtension
        val ext = type.extension

        if (pageCount > 1) {
            actualOutputPath = File(outputDir, "${baseName}.zip").absolutePath
        }

        try {
            val generatedFiles = mutableListOf<File>()

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bmpWidth = (page.width * scale).toInt()
                val bmpHeight = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageFile = if (pageCount == 1) {
                    outputFile
                } else {
                    File(outputDir, "${baseName}_page_${i + 1}.$ext")
                }

                val (format, quality) = when (type) {
                    ConversionEngine.ConversionType.PDF_TO_PNG ->
                        Bitmap.CompressFormat.PNG to 100

                    ConversionEngine.ConversionType.PDF_TO_WEBP ->
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                            Bitmap.CompressFormat.WEBP_LOSSLESS to 100
                        else
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP to (if (highQuality) 90 else 75)

                    else -> // PDF_TO_JPG
                        Bitmap.CompressFormat.JPEG to (if (highQuality) 95 else 80)
                }

                pageFile.outputStream().use { out ->
                    bitmap.compress(format, quality, out)
                }
                bitmap.recycle()
                generatedFiles.add(pageFile)

                // Half progress for rendering if zipping is required, full if single page
                val progressWeight = if (pageCount > 1) 0.5f else 1.0f
                onProgress((((i + 1).toFloat() / pageCount) * 100 * progressWeight).toInt())
            }

            if (pageCount > 1) {
                java.util.zip.ZipOutputStream(FileOutputStream(actualOutputPath)).use { zos ->
                    for ((index, file) in generatedFiles.withIndex()) {
                        val entry = java.util.zip.ZipEntry(file.name)
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                        file.delete() // Clean up individual image file
                        
                        onProgress(50 + (((index + 1).toFloat() / pageCount) * 50).toInt())
                    }
                }
            }
            
            return actualOutputPath
        } finally {
            renderer.close()
            fd.close()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            if (uri.scheme == "file") {
                android.graphics.BitmapFactory.decodeFile(uri.path)
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
