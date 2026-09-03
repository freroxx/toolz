/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.analyzer

import android.content.Context
import android.util.Log
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.FileEntry
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.IndexedFile
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemJunkAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId = "system_junk"
    override val categoryName = "Junk files"
    override val categoryIcon = "DeleteSweep"
    override val description = "Logs, temp files, crash reports and stale caches — your files are never touched"
    override val isSafeToClean = true
    private val packLogExt = setOf("log","logcat","stacktrace","dmp","hprof","crash")
    private val packTempExt = setOf("tmp","temp","part","crdownload","chk","old","bak")
    private val packCacheExt = setOf("cache","exo","fb_temp","thumbdata","thumb")
    private val cacheDirNames = setOf("cache","code_cache",".cache","tmp",".tmp","temp",".temp","logs","log","crash_reports","crashlytics","bugly","leakcanary","fresco_cache","glide","image_cache","video_cache",".fabric","diagnostics","bugreport","bugreports")
    private val userDirs = setOf("dcim","pictures","movies","music","documents","download","downloads","whatsapp","telegram")

    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        val entries = ArrayList<FileEntry>(512)
        var denied = 0
        // Orphan companion DBs need a sibling check against the index.
        val byParent = HashMap<String, MutableMap<String, Long>>(1024)
        // Thumbnail-orphan check needs live originals — built lazily, index-only.
        var liveBasenames: Set<String>? = null
        fun thumbOrphan(f: IndexedFile): Boolean {
            var set = liveBasenames
            if (set == null) {
                set = thumbBasenames(index)
                liveBasenames = set
            }
            return !set.contains(f.name.substringBeforeLast('.').lowercase())
        }
        for (f in index.files) {
            if (!ctx.isActive()) break
            try {
                val abs = f.path
                if (f.name == ".nomedia" || f.name == ".gitkeep") continue
                if (FileUtils.isExcluded(abs, ctx.exclusions)) continue
                if (f.size !in 1..500_000_000L) continue
                // Legacy thumbnail caches: only orphans whose original is gone.
                if (f.parentDir.substringAfterLast('/').lowercase() == ".thumbnails") {
                    if (thumbOrphan(f)) entries.add(f.toEntry(true))
                    continue
                }
                val hit = when {
                    f.ext in packLogExt -> inCacheDir(abs) || abs.lowercase().contains("crash")
                    f.ext in packTempExt -> true
                    f.ext in packCacheExt -> inCacheDir(abs)
                    f.ext in setOf("db-journal","tombstone") -> inCacheDir(abs)
                    isOrphanDbCompanion(f, byParent, index) -> true
                    else -> false
                }
                if (!hit) continue
                if (inUserDir(abs) && f.ext !in packLogExt && !inCacheDir(abs)) continue
                entries.add(f.toEntry(true))
            } catch (e: Exception) { Log.w("SystemJunk", "skip ${f.path}", e); denied++ }
            if (entries.size >= 800) break
        }
        val top = entries.sortedByDescending { it.sizeBytes }.take(400)
        val items = top.map { CleanItem.GenericFile(it) }
        val total = top.sumOf { it.sizeBytes }
        val selected = items.sumOf { (it as CleanItem.GenericFile).let { g -> if (g.file.isSelected) g.file.sizeBytes else 0L } }
        val blocked = if (top.isEmpty() && denied > 0) "Some junk locations couldn't be read" else null
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, selected, isSafeToClean,
            description = description, truncatedCount = (entries.size - top.size).coerceAtLeast(0),
            skippedCount = denied, blockedReason = blocked, blockedFixLabel = null,
            emptyHint = if (top.isEmpty() && blocked == null) "No junk found — system looks clean" else null)
    }

    /** Basenames (no ext) of live, non-cache files — thumbnail originals. Index-only. */
    private fun thumbBasenames(index: FileIndex): Set<String> =
        index.files.asSequence()
            .filter { !inCacheDir(it.path) && it.parentDir.substringAfterLast('/').lowercase() != ".thumbnails" }
            .mapTo(HashSet()) { it.name.substringBeforeLast('.').lowercase() }

    /** Orphan `-journal/-wal/-shm` older than 7d whose main DB is missing or 0-byte. Index-only. */
    private fun isOrphanDbCompanion(f: IndexedFile, byParent: MutableMap<String, MutableMap<String, Long>>, index: FileIndex): Boolean {
        val lower = f.name.lowercase()
        val isCompanion = lower.endsWith("-journal") || lower.endsWith(".db-journal") ||
            lower.endsWith("-wal") || lower.endsWith(".db-wal") ||
            lower.endsWith("-shm") || lower.endsWith(".db-shm")
        if (!isCompanion) return false
        if (f.lastModified > System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) return false
        val siblings = byParent.getOrPut(f.parentDir) {
            index.files.asSequence().filter { it.parentDir == f.parentDir }.associateTo(HashMap()) { it.name to it.size }
        }
        val main = f.name.substringBeforeLast("-")
        val mainSize = siblings[main] ?: siblings["$main.db"]
        return mainSize == null || mainSize == 0L
    }

    private fun inCacheDir(abs: String): Boolean {
        val lower = abs.lowercase()
        return lower.split('/').any { it in cacheDirNames || it == ".thumbnails" || it.endsWith("cache") }
    }
    private fun inUserDir(abs: String): Boolean {
        val segs = abs.lowercase().split('/')
        return segs.size > 4 && segs[3] in userDirs
    }
    private fun IndexedFile.toEntry(sel: Boolean): FileEntry =
        FileEntry(name, path, size, lastModified, ext, sel, FileUtils.getMediaStoreUri(context, path, ext))
}
