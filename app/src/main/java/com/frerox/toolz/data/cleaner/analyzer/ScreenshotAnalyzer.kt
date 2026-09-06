/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.analyzer

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.MediaEntry
import com.frerox.toolz.data.cleaner.MediaType
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) : CleanerAnalyzer {
    override val categoryId = "screenshots"
    override val categoryName = "Screenshots"
    override val categoryIcon = "Screenshot"
    override val description = "Review and delete screenshots taking up storage space"
    override val isSafeToClean = false

    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        val storeHits = try { queryMediaStore(ctx) } catch (_: Exception) { emptyList() }
        val entries = if (storeHits.isNotEmpty()) storeHits.toMutableList() else indexFallback(index, ctx).toMutableList()
        // Sort newest first by default
        val sorted = entries.sortedByDescending { it.lastModified }.take(ctx.config.maxMediaFiles)
        val items = sorted.map { CleanItem.MediaFile(it) }
        val total = sorted.sumOf { it.sizeBytes }
        return CleanCategory(
            categoryId, categoryName, categoryIcon, items, total, 0L, isSafeToClean,
            description = description, truncatedCount = (entries.size - sorted.size).coerceAtLeast(0)
        )
    }

    private fun queryMediaStore(ctx: ScanCtx): List<MediaEntry> {
        val out = mutableListOf<MediaEntry>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        try {
            context.contentResolver.query(collection, proj, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iRel = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                var rows = 0
                while (c.moveToNext()) {
                    if (!ctx.isActive()) break
                    if (++rows > 30_000) break
                    if (out.size >= ctx.config.maxMediaFiles * 2) break
                    val name = try { c.getString(iName) } catch (_: Exception) { continue } ?: continue
                    val rel = try { c.getString(iRel) } catch (_: Exception) { "" } ?: ""
                    val size = try { c.getLong(iSize) } catch (_: Exception) { 0L }
                    val mod = try { c.getLong(iDate) * 1000L } catch (_: Exception) { 0L }

                    if (!isScreenshot(rel, name)) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val id = try { c.getLong(iId) } catch (_: Exception) { continue }
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val path = "/storage/emulated/0/$rel$name"
                    out.add(MediaEntry(name, path, size, mod, ext, MediaType.SCREENSHOT, isSelected = false, thumbnailUri = uri))
                }
            }
        } catch (_: Exception) {}
        return out
    }

    private fun indexFallback(index: FileIndex, ctx: ScanCtx): List<MediaEntry> {
        val out = mutableListOf<MediaEntry>()
        for (f in index.files) {
            if (!ctx.isActive()) break
            if (!isScreenshot(f.parentDir, f.name)) continue
            out.add(
                MediaEntry(
                    name = f.name,
                    path = f.path,
                    sizeBytes = f.size,
                    lastModified = f.lastModified,
                    extension = f.ext,
                    type = MediaType.SCREENSHOT,
                    isSelected = false,
                    thumbnailUri = FileUtils.getMediaStoreUri(context, f.path, f.ext)
                )
            )
            if (out.size >= ctx.config.maxMediaFiles * 2) break
        }
        return out
    }

    companion object {
        fun isScreenshot(relativeOrDir: String, fileName: String = ""): Boolean {
            val p = relativeOrDir.lowercase()
            val f = fileName.lowercase()
            return p.contains("screenshot") ||
                f.contains("screenshot") ||
                f.startsWith("screen_") ||
                f.startsWith("screencap") ||
                f.startsWith("screenshot_")
        }
    }
}
