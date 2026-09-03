/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.analyzer

import android.content.Context
import com.frerox.toolz.data.cleaner.ApkEntry
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId = "apk"
    override val categoryName = "Installer packages"
    override val categoryIcon = "Android"
    override val description = "APK and bundle files — stale ones are safe to remove"
    override val isSafeToClean = false
    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        val heap = java.util.PriorityQueue<ApkEntry>(ctx.config.maxApkFiles.coerceAtLeast(10), compareBy { it.sizeBytes })
        var hits = 0
        val pm = context.packageManager
        for (f in index.files) {
            if (!ctx.isActive()) break
            if (f.ext != "apk" && f.ext != "apks" && f.ext != "xapk") continue
            hits++
            // Only plain APKs can be parsed; bundles are flagged by extension alone.
            var pkg: String? = null; var ver: String? = null
            if (f.ext == "apk") {
                try {
                    val info = pm.getPackageArchiveInfo(f.path, 0)
                    pkg = info?.packageName; ver = info?.versionName
                } catch (_: Exception) {}
            }
            val e = ApkEntry(f.name, f.path, f.size, f.lastModified, pkg, ver, isSelected = false)
            if (heap.size < ctx.config.maxApkFiles) heap.add(e)
            else if (f.size > (heap.peek()?.sizeBytes ?: 0L)) { heap.poll(); heap.add(e) }
        }
        val sorted = heap.sortedByDescending { it.sizeBytes }
        val stale = sorted.count { it.packageName == null || !ctx.installed.contains(it.packageName) }
        val items = sorted.map { CleanItem.ApkFile(it) }
        val total = sorted.sumOf { it.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, 0L, isSafeToClean,
            description = "$stale of ${sorted.size} look stale (not installed) — review before deleting",
            truncatedCount = (hits - sorted.size).coerceAtLeast(0))
    }
}
