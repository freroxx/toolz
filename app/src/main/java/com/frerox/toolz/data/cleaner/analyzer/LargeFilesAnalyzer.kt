/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.analyzer

import android.content.Context
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.FileEntry
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LargeFilesAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId = "large"
    override val categoryName = "Large files"
    override val categoryIcon = "Straighten"
    override val description = "Your biggest files — review before deleting"
    override val isSafeToClean = false
    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        val thr = ctx.config.largeThresholdBytes
        // Largest always wins: bounded min-heap over the shared index, zero I/O.
        val heap = java.util.PriorityQueue<FileEntry>(ctx.config.maxLargeFiles.coerceAtLeast(10), compareBy { it.sizeBytes })
        var hits = 0
        for (f in index.files) {
            if (!ctx.isActive()) break
            if (f.size >= thr) {
                hits++
                val e = f.toEntry(false)
                if (heap.size < ctx.config.maxLargeFiles) heap.add(e)
                else if (f.size > (heap.peek()?.sizeBytes ?: 0L)) { heap.poll(); heap.add(e) }
            }
        }
        val sorted = heap.sortedByDescending { it.sizeBytes }
        val items = sorted.map { CleanItem.GenericFile(it) }
        val total = sorted.sumOf { it.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, 0L, isSafeToClean,
            description = description, truncatedCount = (hits - sorted.size).coerceAtLeast(0))
    }
    private fun com.frerox.toolz.data.cleaner.engine.IndexedFile.toEntry(sel: Boolean): FileEntry =
        FileEntry(name, path, size, lastModified, ext, sel, FileUtils.getMediaStoreUri(context, path, ext))
}
