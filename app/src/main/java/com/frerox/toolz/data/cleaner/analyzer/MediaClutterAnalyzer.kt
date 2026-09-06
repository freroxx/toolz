/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.analyzer

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.MediaEntry
import com.frerox.toolz.data.cleaner.MediaType
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaClutterAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId = "media_clutter"
    override val categoryName = "Photos & videos to review"
    override val categoryIcon = "Collections"
    override val description = "Old screenshots, downloads and chat media — your camera roll is never touched"
    override val isSafeToClean = false

    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        // MediaStore first: free thumbnails, IDs and sizes with zero file I/O.
        val storeHits = try { queryMediaStore(ctx) } catch (_: Exception) { emptyList() }
        val entries = if (storeHits.isNotEmpty()) storeHits.toMutableList() else indexFallback(index, ctx).toMutableList()
        val sorted = entries.sortedByDescending { it.sizeBytes }.take(ctx.config.maxMediaFiles)
        val items = sorted.map { CleanItem.MediaFile(it) }
        val total = sorted.sumOf { it.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, 0L, isSafeToClean,
            description = description, truncatedCount = (entries.size - sorted.size).coerceAtLeast(0))
    }

    private data class Hit(
        val name: String, val path: String, val size: Long, val modified: Long,
        val ext: String, val type: MediaType, val thumb: String?
    )

    private fun queryMediaStore(ctx: ScanCtx): List<MediaEntry> {
        val out = mutableListOf<MediaEntry>()
        val now = System.currentTimeMillis()
        val oldThr = now - ctx.config.oldDownloadDays * 24 * 60 * 60 * 1000L
        val week = now - 7 * 24 * 60 * 60 * 1000L
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        for (collection in collections) {
            if (!ctx.isActive()) break
            var rows = 0
            try {
                context.contentResolver.query(collection, proj, null, null, null)?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val iRel = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    while (c.moveToNext()) {
                        if (!ctx.isActive()) break
                        if (++rows > 30_000) break
                        if (out.size >= ctx.config.maxMediaFiles * 3) break
                        val name = try { c.getString(iName) } catch (_: Exception) { continue } ?: continue
                        val rel = try { c.getString(iRel) } catch (_: Exception) { "" } ?: ""
                        val size = try { c.getLong(iSize) } catch (_: Exception) { 0L }
                        val mod = try { c.getLong(iDate) * 1000L } catch (_: Exception) { 0L }
                        val type = classifyMediaPath(rel) ?: continue
                        if (type == MediaType.DCIM || type == MediaType.SCREENSHOT) continue // DCIM untouched, screenshots have dedicated category
                        val ext = name.substringAfterLast('.', "").lowercase()
                        val isOld = mod in 1..<oldThr
                        val isVideo = collection == MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        val isLargeVideo = isVideo && size > 50 * 1024 * 1024L
                        val relLower = rel.lowercase()
                        val isSentClutter = type == MediaType.WHATSAPP &&
                            (relLower.contains("/sent") || relLower.contains("status"))
                        if (!(isOld || isLargeVideo || isSentClutter ||
                                (type == MediaType.TELEGRAM && (relLower.contains("cache") || relLower.contains("thumb") || isOld)))) continue
                        val id = try { c.getLong(iId) } catch (_: Exception) { continue }
                        val uri = ContentUris.withAppendedId(collection, id).toString()
                        val path = "/storage/emulated/0/$rel$name"
                        out.add(MediaEntry(name, path, size, mod, ext, type, isSelected = false, thumbnailUri = uri))
                    }
                }
            } catch (_: Exception) { }
        }
        return out
    }

    /** Index fallback when MediaStore is unavailable: same rules, zero walks. */
    private fun indexFallback(index: FileIndex, ctx: ScanCtx): List<MediaEntry> {
        val out = mutableListOf<MediaEntry>()
        val now = System.currentTimeMillis()
        val oldThr = now - ctx.config.oldDownloadDays * 24 * 60 * 60 * 1000L
        val week = now - 7 * 24 * 60 * 60 * 1000L
        for (f in index.files) {
            if (!ctx.isActive()) break
            val type = classifyMediaPath(f.parentDir) ?: continue
            if (type == MediaType.DCIM || type == MediaType.SCREENSHOT) continue
            val isOld = f.lastModified in 1..<oldThr
            val isLargeVideo = f.ext in setOf("mp4", "mkv", "mov") && f.size > 50 * 1024 * 1024L
            val lower = f.parentDir.lowercase()
            val isSentClutter = type == MediaType.WHATSAPP && (lower.contains("/sent") || lower.contains("status"))
            if (!(isOld || isLargeVideo || isSentClutter ||
                    (type == MediaType.TELEGRAM && (lower.contains("cache") || lower.contains("thumb") || isOld)))) continue
            out.add(MediaEntry(f.name, f.path, f.size, f.lastModified, f.ext, type,
                isSelected = false, thumbnailUri = FileUtils.getMediaStoreUri(context, f.path, f.ext)))
            if (out.size >= ctx.config.maxMediaFiles * 3) break
        }
        return out
    }

    companion object {
        /** Maps a MediaStore RELATIVE_PATH (or any dir path) to a clutter type. Pure — tested. */
        fun classifyMediaPath(relativeOrDir: String): MediaType? {
            val p = relativeOrDir.lowercase()
            return when {
                p.contains("screenshot") -> MediaType.SCREENSHOT
                p.contains("whatsapp") -> MediaType.WHATSAPP
                p.contains("telegram") -> MediaType.TELEGRAM
                p.contains("download") -> MediaType.DOWNLOAD
                p.contains("dcim/camera") || (p.contains("dcim") && !p.contains("screenshot")) -> MediaType.DCIM
                else -> null
            }
        }
    }
}
