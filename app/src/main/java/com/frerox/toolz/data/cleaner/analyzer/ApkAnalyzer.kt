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
            val extLower = f.ext.lowercase()
            if (extLower != "apk" && extLower != "apks" && extLower != "xapk") continue
            hits++
            // Only plain APKs can be parsed; bundles are flagged by extension alone.
            var pkg: String? = null
            var ver: String? = null
            var installedVer: String? = null
            var isRedundant = false
            if (extLower == "apk") {
                try {
                    val info = pm.getPackageArchiveInfo(f.path, 0)
                    pkg = info?.packageName
                    ver = info?.versionName
                    if (pkg != null && ctx.installed.contains(pkg)) {
                        try {
                            val installedInfo = pm.getPackageInfo(pkg, 0)
                            installedVer = installedInfo.versionName
                            val installedCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                installedInfo.longVersionCode
                            } else {
                                @Suppress("DEPRECATION") installedInfo.versionCode.toLong()
                            }
                            val apkCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                info?.longVersionCode ?: 0L
                            } else {
                                @Suppress("DEPRECATION") (info?.versionCode ?: 0).toLong()
                            }
                            if (installedCode >= apkCode && apkCode > 0L) {
                                isRedundant = true
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }
            // Auto-select redundant APKs (app already installed with same or newer version)
            val e = ApkEntry(
                name = f.name,
                path = f.path,
                sizeBytes = f.size,
                lastModified = f.lastModified,
                packageName = pkg,
                versionName = ver,
                isSelected = isRedundant,
                installedVersionName = installedVer,
                isRedundant = isRedundant
            )
            if (heap.size < ctx.config.maxApkFiles) heap.add(e)
            else if (f.size > (heap.peek()?.sizeBytes ?: 0L)) { heap.poll(); heap.add(e) }
        }
        val sorted = heap.sortedByDescending { it.sizeBytes }
        val stale = sorted.count { it.packageName == null || !ctx.installed.contains(it.packageName) }
        val redundant = sorted.count { it.isRedundant }
        val items = sorted.map { CleanItem.ApkFile(it) }
        val total = sorted.sumOf { it.sizeBytes }
        val selected = sorted.filter { it.isSelected }.sumOf { it.sizeBytes }
        val desc = when {
            redundant > 0 && stale > 0 -> "$redundant redundant (installed) • $stale not installed"
            redundant > 0 -> "$redundant redundant installer(s) (already installed)"
            else -> "$stale of ${sorted.size} look stale (not installed) — review before deleting"
        }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, selected, isSafeToClean = false,
            description = desc,
            truncatedCount = (hits - sorted.size).coerceAtLeast(0))
    }
}
