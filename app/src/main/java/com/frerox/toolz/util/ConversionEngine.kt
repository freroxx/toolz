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

package com.frerox.toolz.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.frerox.toolz.util.converters.ArchiveHandler
import com.frerox.toolz.util.converters.ConversionHandler
import com.frerox.toolz.util.converters.DocumentHandler
import com.frerox.toolz.util.converters.ImageDocumentHandler
import com.frerox.toolz.util.converters.MediaHandler
import com.frerox.toolz.util.converters.VectorHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaHandler: MediaHandler,
    private val vectorHandler: VectorHandler,
    private val documentHandler: DocumentHandler,
    private val imageDocumentHandler: ImageDocumentHandler,
    private val archiveHandler: ArchiveHandler,
) {

    enum class ConversionType(
        val extension: String,
        val category: String,
        val label: String,
        val isPopular: Boolean = false,
        /** MIME type prefixes that can be used as input for this conversion. */
        val inputMimes: List<String> = emptyList(),
    ) {
        // ── Video → Video ─────────────────────────────────────────────────────
        VIDEO_TO_MP4("mp4", "Videos", "Video → MP4", true, listOf("video")),
        VIDEO_TO_MKV("mkv", "Videos", "Video → MKV", true, listOf("video")),
        VIDEO_TO_MOV("mov", "Videos", "Video → MOV", true, listOf("video")),
        VIDEO_TO_AVI("avi", "Videos", "Video → AVI", true, listOf("video")),
        VIDEO_TO_WEBM("webm", "Videos", "Video → WebM", true, listOf("video")),
        VIDEO_TO_FLV("flv", "Videos", "Video → FLV", false, listOf("video")),
        VIDEO_TO_WMV("wmv", "Videos", "Video → WMV", true, listOf("video")),
        VIDEO_TO_3GP("3gp", "Videos", "Video → 3GP", false, listOf("video")),
        VIDEO_TO_MPEG("mpeg", "Videos", "Video → MPEG", false, listOf("video")),
        VIDEO_TO_OGV("ogv", "Videos", "Video → OGV", false, listOf("video")),
        VIDEO_TO_M4V("m4v", "Videos", "Video → M4V", false, listOf("video")),
        VIDEO_TO_TS("ts", "Videos", "Video → TS", false, listOf("video")),
        VIDEO_TO_M2TS("m2ts", "Videos", "Video → M2TS", false, listOf("video")),
        VIDEO_TO_DV("dv", "Videos", "Video → DV", false, listOf("video")),

        // ── Video → Animated ──────────────────────────────────────────────────
        VIDEO_TO_GIF("gif", "Animations", "Video → GIF", true, listOf("video")),
        VIDEO_TO_WEBP("webp", "Animations", "Video → WebP", true, listOf("video")),

        // ── Video → Audio ─────────────────────────────────────────────────────
        VIDEO_TO_MP3("mp3", "Audio", "Video → MP3", true, listOf("video")),
        VIDEO_TO_WAV("wav", "Audio", "Video → WAV", true, listOf("video")),
        VIDEO_TO_AAC("aac", "Audio", "Video → AAC", true, listOf("video")),
        VIDEO_TO_FLAC("flac", "Audio", "Video → FLAC", true, listOf("video")),
        VIDEO_TO_M4A("m4a", "Audio", "Video → M4A", true, listOf("video")),
        VIDEO_TO_OGG("ogg", "Audio", "Video → OGG", false, listOf("video")),
        VIDEO_TO_OPUS("opus", "Audio", "Video → Opus", false, listOf("video")),
        VIDEO_TO_WMA("wma", "Audio", "Video → WMA", false, listOf("video")),
        VIDEO_TO_AIFF("aiff", "Audio", "Video → AIFF", false, listOf("video")),

        // ── Audio → Audio ─────────────────────────────────────────────────────
        AUDIO_TO_MP3("mp3", "Audio", "Audio → MP3", true, listOf("audio")),
        AUDIO_TO_WAV("wav", "Audio", "Audio → WAV", true, listOf("audio")),
        AUDIO_TO_AAC("aac", "Audio", "Audio → AAC", true, listOf("audio")),
        AUDIO_TO_M4A("m4a", "Audio", "Audio → M4A", true, listOf("audio")),
        AUDIO_TO_FLAC("flac", "Audio", "Audio → FLAC", true, listOf("audio")),
        AUDIO_TO_OGG("ogg", "Audio", "Audio → OGG", true, listOf("audio")),
        AUDIO_TO_OPUS("opus", "Audio", "Audio → Opus", false, listOf("audio")),
        AUDIO_TO_WMA("wma", "Audio", "Audio → WMA", false, listOf("audio")),
        AUDIO_TO_AMR("amr", "Audio", "Audio → AMR", false, listOf("audio")),
        AUDIO_TO_AIFF("aiff", "Audio", "Audio → AIFF", false, listOf("audio")),
        AUDIO_TO_MKA("mka", "Audio", "Audio → MKA", false, listOf("audio")),
        AUDIO_TO_AC3("ac3", "Audio", "Audio → AC3", false, listOf("audio")),
        AUDIO_TO_MP2("mp2", "Audio", "Audio → MP2", false, listOf("audio")),

        // ── Image → Image ─────────────────────────────────────────────────────
        IMAGE_TO_JPG("jpg", "Images", "Image → JPG", true, listOf("image")),
        IMAGE_TO_PNG("png", "Images", "Image → PNG", true, listOf("image")),
        IMAGE_TO_WEBP("webp", "Images", "Image → WebP", true, listOf("image")),
        IMAGE_TO_GIF("gif", "Animations", "Image → GIF", true, listOf("image")),
        IMAGE_TO_BMP("bmp", "Images", "Image → BMP", true, listOf("image")),
        IMAGE_TO_TIFF("tiff", "Images", "Image → TIFF", false, listOf("image")),
        IMAGE_TO_ICO("ico", "Images", "Image → ICO", true, listOf("image")),
        IMAGE_TO_HEIF("heif", "Images", "Image → HEIF", true, listOf("image")),
        IMAGE_TO_AVIF("avif", "Images", "Image → AVIF", true, listOf("image")),
        IMAGE_TO_TGA("tga", "Images", "Image → TGA", false, listOf("image")),
        IMAGE_TO_PPM("ppm", "Images", "Image → PPM", false, listOf("image")),

        // ── Image / PDF ───────────────────────────────────────────────────────
        IMAGE_TO_PDF("pdf", "Documents", "Image → PDF", true, listOf("image")),
        PDF_TO_PNG("png", "Images", "PDF → PNG", true, listOf("application/pdf")),
        PDF_TO_JPG("jpg", "Images", "PDF → JPG", true, listOf("application/pdf")),
        PDF_TO_WEBP("webp", "Images", "PDF → WebP", false, listOf("application/pdf")),

        // ── Text / Document → PDF / HTML / TXT ────────────────────────────────
        TEXT_TO_PDF("pdf", "Documents", "Text → PDF", true, listOf("text/plain")),
        MD_TO_PDF("pdf", "Documents", "Markdown → PDF", true, listOf("text/markdown", "text/plain", "text/x-markdown")),
        MD_TO_HTML("html", "Documents", "Markdown → HTML", true, listOf("text/markdown", "text/plain", "text/x-markdown")),
        MD_TO_TXT("txt", "Documents", "Markdown → TXT", false, listOf("text/markdown", "text/plain", "text/x-markdown")),
        HTML_TO_PDF("pdf", "Documents", "HTML → PDF", false, listOf("text/html")),
        HTML_TO_TXT("txt", "Documents", "HTML → TXT", false, listOf("text/html")),
        TXT_TO_HTML("html", "Documents", "Text → HTML", false, listOf("text/plain")),

        // ── Archives ──────────────────────────────────────────────────────────
        XAPK_TO_APK("apk", "Archives", "XAPK → APK", true, listOf("application/x-xapk", "application/xapk", "application/zip", "application/vnd.android.package-archive")),
        APK_TO_ZIP("zip", "Archives", "APK → ZIP", false, listOf("application/vnd.android.package-archive")),
        ZIP_TO_APK("apk", "Archives", "ZIP → APK", false, listOf("application/zip", "application/x-zip-compressed")),
        XAPK_TO_ZIP("zip", "Archives", "XAPK → ZIP", false, listOf("application/x-xapk", "application/xapk")),

        // ── Vector ────────────────────────────────────────────────────────────
        SVG_TO_PNG("png", "Images", "SVG → PNG", true, listOf("image/svg+xml")),
        SVG_TO_JPG("jpg", "Images", "SVG → JPG", true, listOf("image/svg+xml")),
        SVG_TO_WEBP("webp", "Images", "SVG → WebP", false, listOf("image/svg+xml")),
        SVG_TO_PDF("pdf", "Documents", "SVG → PDF", true, listOf("image/svg+xml")),
    }

    sealed class ConversionStatus {
        data class Progress(val percentage: Int) : ConversionStatus()
        data class Success(val outputPath: String) : ConversionStatus()
        data class Error(val message: String) : ConversionStatus()
    }

    /**
     * Routes the conversion to the correct handler based on the [ConversionType],
     * creates a properly named output file in the public Downloads/Toolz directory,
     * and ensures the temp input file is cleaned up after conversion.
     */
    fun routeConversion(
        inputUri: Uri,
        type: ConversionType,
        highQuality: Boolean = true,
        batchIndex: Int = -1,
    ): Flow<ConversionStatus> {
        val tempExt = resolveInputExtension(inputUri)
        val tempFile = File(context.cacheDir, "input_temp_${System.currentTimeMillis()}.$tempExt")

        return flow {
            try {
                val copied = try {
                    context.contentResolver.openInputStream(inputUri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    true
                } catch (e: Exception) {
                    emit(ConversionStatus.Error("Could not read input file: ${e.localizedMessage}"))
                    return@flow
                }
                if (!copied) {
                    emit(ConversionStatus.Error("Could not read input file"))
                    return@flow
                }

                val outputFile = buildOutputFile(type, batchIndex)
                val handler: ConversionHandler = pickHandler(type)

                handler.convert(
                    inputUris = listOf(Uri.fromFile(tempFile)),
                    type = type,
                    outputPath = outputFile.absolutePath,
                    highQuality = highQuality,
                ).collect { status -> emit(status) }
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }
    }

    /** Same as [routeConversion] but accepts multiple input URIs (batch merge where supported). */
    fun routeBatchConversion(
        inputUris: List<Uri>,
        type: ConversionType,
        highQuality: Boolean = true,
    ): Flow<ConversionStatus> {
        if (inputUris.size == 1) return routeConversion(inputUris.first(), type, highQuality)

        val tempFiles = mutableListOf<File>()
        return flow {
            try {
                for ((i, uri) in inputUris.withIndex()) {
                    val tempExt = resolveInputExtension(uri)
                    val tempFile = File(context.cacheDir, "input_temp_${System.currentTimeMillis()}_$i.$tempExt")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempFiles.add(tempFile)
                }

                val outputFile = buildOutputFile(type)
                val handler: ConversionHandler = pickHandler(type)

                handler.convert(
                    inputUris = tempFiles.map { Uri.fromFile(it) },
                    type = type,
                    outputPath = outputFile.absolutePath,
                    highQuality = highQuality,
                ).collect { status -> emit(status) }
            } finally {
                tempFiles.forEach { if (it.exists()) it.delete() }
            }
        }
    }

    private fun pickHandler(type: ConversionType): ConversionHandler = when {
        type.name.startsWith("SVG_") -> vectorHandler
        type == ConversionType.IMAGE_TO_PDF ||
        type == ConversionType.PDF_TO_PNG ||
        type == ConversionType.PDF_TO_JPG ||
        type == ConversionType.PDF_TO_WEBP -> imageDocumentHandler
        type == ConversionType.TEXT_TO_PDF ||
        type == ConversionType.MD_TO_PDF ||
        type == ConversionType.MD_TO_HTML ||
        type == ConversionType.MD_TO_TXT ||
        type == ConversionType.HTML_TO_PDF ||
        type == ConversionType.HTML_TO_TXT ||
        type == ConversionType.TXT_TO_HTML -> documentHandler
        type.category == "Archives" -> archiveHandler
        else -> mediaHandler
    }

    private fun buildOutputFile(type: ConversionType, batchIndex: Int = -1): File {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(File(base, "Toolz"), type.category).also { it.mkdirs() }
        val suffix = if (batchIndex >= 0) "_${batchIndex + 1}" else ""
        return File(dir, "TOOLZ_${System.currentTimeMillis()}$suffix.${type.extension}")
    }

    /** Normalises HEIC → HEIF so our MIME matching works uniformly. */
    private fun normalizeHeicMime(mime: String) =
        if (mime == "image/heic") "image/heif" else mime

    fun resolveInputExtension(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)?.let { normalizeHeicMime(it) } ?: ""
        if (mime.isNotBlank()) {
            val ext = mimeToExtension(mime)
            if (ext != "bin") return ext
        }
        val displayName = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (idx >= 0) cursor.getString(idx) else null
            }
        } catch (_: Exception) { null }
        if (displayName != null && displayName.contains(".")) {
            return displayName.substringAfterLast(".").lowercase()
        }
        return uri.lastPathSegment?.substringAfterLast(".")?.lowercase() ?: "tmp"
    }

    fun mimeToExtension(mime: String): String = when {
        mime.startsWith("video/mp4") -> "mp4"
        mime.startsWith("video/x-matroska") -> "mkv"
        mime.startsWith("video/quicktime") -> "mov"
        mime.startsWith("video/x-msvideo") -> "avi"
        mime.startsWith("video/webm") -> "webm"
        mime.startsWith("video/") -> "mp4"
        mime.startsWith("audio/mpeg") || mime == "audio/mp3" -> "mp3"
        mime == "audio/wav" || mime == "audio/x-wav" -> "wav"
        mime.startsWith("audio/aac") -> "aac"
        mime.startsWith("audio/flac") || mime == "audio/x-flac" -> "flac"
        mime.startsWith("audio/ogg") || mime == "application/ogg" -> "ogg"
        mime.startsWith("audio/opus") || mime == "audio/x-opus" -> "opus"
        mime.startsWith("audio/amr") || mime == "audio/3gpp" -> "amr"
        mime.startsWith("audio/aiff") || mime == "audio/x-aiff" -> "aiff"
        mime.startsWith("audio/mp4") || mime.startsWith("audio/x-m4a") || mime.startsWith("audio/m4a") -> "m4a"
        mime.startsWith("audio/") -> "mp3"
        mime == "image/jpeg" || mime == "image/jpg" -> "jpg"
        mime == "image/png" -> "png"
        mime == "image/webp" -> "webp"
        mime == "image/gif" -> "gif"
        mime == "image/bmp" -> "bmp"
        mime == "image/heif" || mime == "image/heic" -> "heif"
        mime == "image/svg+xml" -> "svg"
        mime == "image/avif" -> "avif"
        mime.startsWith("image/") -> "jpg"
        mime == "application/pdf" -> "pdf"
        mime == "application/xapk" || mime == "application/x-xapk" -> "xapk"
        mime == "text/html" -> "html"
        mime.startsWith("text/markdown") || mime == "text/x-markdown" -> "md"
        mime.startsWith("text/") -> "txt"
        else -> "bin"
    }
}
