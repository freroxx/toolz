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

package com.frerox.toolz.data.pdf

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderEngine: PdfRenderEngine,
    private val textEngine: PdfTextEngine
) {
    private val OCR_TARGET_WIDTH = 1600

    /**
     * Fast vault scan — NO per-file render. Thumbnails + page counts resolve
     * lazily in the UI via [renderEngine] so 100+ PDFs open in <1s.
     */
    suspend fun getPdfFiles(): List<PdfFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<PdfFile>()
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val args = arrayOf("application/pdf")
        val sort = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        try {
            context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
                val idC = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameC = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeC = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateC = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    try {
                        val id = c.getLong(idC)
                        val name = c.getString(nameC) ?: "Document.pdf"
                        val size = try { c.getLong(sizeC) } catch (_: Exception) { 0L }
                        val date = try { c.getLong(dateC) } catch (_: Exception) { 0L }
                        out.add(
                            PdfFile(
                                uri = ContentUris.withAppendedId(collection, id),
                                name = name,
                                size = size,
                                lastModified = date,
                                thumbnail = null,
                                pageCount = 0
                            )
                        )
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
        // App-private imports (SAF copies) — shown first-class alongside MediaStore.
        try {
            val dir = File(context.filesDir, "pdfs")
            if (dir.exists()) {
                dir.listFiles { f -> f.extension.equals("pdf", true) }?.forEach { f ->
                    try {
                        out.add(
                            PdfFile(
                                uri = Uri.fromFile(f),
                                name = f.name,
                                size = f.length(),
                                lastModified = f.lastModified() / 1000,
                                thumbnail = null,
                                pageCount = 0
                            )
                        )
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
        out
    }

    // ── Render passthrough (compat + new) ────────────────────────────────────

    suspend fun getPageCount(uri: Uri): Int = renderEngine.getPageCount(uri).coerceAtLeast(0)

    suspend fun getPageBitmap(uri: Uri, pageIndex: Int, scale: Float = 1f): Bitmap? {
        val width = when {
            scale <= 1.1f -> 1080
            scale <= 2f -> 1400
            else -> 1800
        }
        return renderEngine.renderPage(uri, pageIndex, width, highQuality = scale > 1.5f)
    }

    suspend fun getThumbnail(uri: Uri, widthPx: Int = 320): Bitmap? =
        renderEngine.renderThumbnail(uri, widthPx)

    suspend fun getPageSize(uri: Uri, pageIndex: Int): PdfRenderEngine.PageSize? =
        renderEngine.getPageSize(uri, pageIndex)

    suspend fun getOcrBitmap(uri: Uri, pageIndex: Int): Bitmap? =
        renderEngine.renderPage(uri, pageIndex, OCR_TARGET_WIDTH, highQuality = true)

    suspend fun getDocInfo(uri: Uri): PdfDocInfo? = try {
        textEngine.getDocInfo(uri)
    } catch (_: Exception) {
        null
    }

    // ── SAF import with persistable permission ───────────────────────────────

    /**
     * Best-effort persistable read grant. Returns false for URIs that don't
     * do persistable grants (MediaStore, app-private files) — those don't
     * need one, so false here is NOT a failure, just "nothing to persist".
     */
    fun ensurePersistableGrant(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        true
    } catch (_: Exception) {
        false
    }

    /** True when the bytes behind [uri] can actually be opened right now. */
    fun isReadable(uri: Uri): Boolean = try {
        if (uri.scheme == "file") {
            File(uri.path ?: "").canRead()
        } else {
            context.contentResolver.openInputStream(uri)?.close()
            true
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Persist [sourceUri] (from OpenDocument). Prefers a persistable grant;
     * falls back to an app-private copy so the doc survives reboot.
     * Returns the usable Uri, or null on failure.
     */
    suspend fun importPdf(sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        // 1) Try persistable grant — zero-copy path.
        try {
            context.contentResolver.takePersistableUriPermission(
                sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            // Verify readable.
            context.contentResolver.openInputStream(sourceUri)?.close()
            return@withContext sourceUri
        } catch (_: Exception) { }
        // 2) Copy into files/pdfs.
        try {
            val name = queryDisplayName(sourceUri) ?: "imported-${System.currentTimeMillis()}.pdf"
            val safe = name.takeLast(120).replace(Regex("[^A-Za-z0-9._-]+"), "_")
            val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
            val dest = File(dir, if (safe.endsWith(".pdf", true)) safe else "$safe.pdf")
            context.contentResolver.openInputStream(sourceUri)?.use { ins ->
                FileOutputStream(dest).use { out -> ins.copyTo(out) }
            } ?: return@withContext null
            Uri.fromFile(dest)
        } catch (_: Exception) {
            null
        }
    }

    fun queryDisplayName(uri: Uri): String? = try {
        if (uri.scheme == "file") return File(uri.path ?: "").name.takeIf { it.isNotBlank() }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    fun querySize(uri: Uri): Long = try {
        if (uri.scheme == "file") return File(uri.path ?: "").length()
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    } catch (_: Exception) {
        0L
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    suspend fun deletePdf(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") return@withContext File(uri.path ?: "").delete()
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun renamePdf(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return@withContext false)
                val clean = if (newName.endsWith(".pdf", true)) newName else "$newName.pdf"
                return@withContext f.renameTo(File(f.parent, clean))
            }
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, if (newName.endsWith(".pdf", true)) newName else "$newName.pdf")
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }
}
