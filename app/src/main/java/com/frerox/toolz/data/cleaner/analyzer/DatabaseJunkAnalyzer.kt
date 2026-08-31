package com.frerox.toolz.data.cleaner.analyzer

import android.content.Context
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.FileEntry
import com.frerox.toolz.data.cleaner.engine.CleanScanConfig
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseJunkAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId = "db_junk"
    override val categoryName = "Database Junk"
    override val categoryIcon = "Storage"
    override val description = "Journal/WAL leftovers safe to vacuum"
    override val isSafeToClean = true
    private val dbExt = setOf("db-journal","db-wal","db-shm","tombstone")
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        val entries = mutableListOf<FileEntry>()
        var scanned = 0
        root.walkTopDown().onEnter { dir -> if (!isActive()) return@onEnter false; if (dir.name.startsWith(".") && !config.includeHidden) return@onEnter false; if (exclusions.any { dir.absolutePath.contains(it) }) return@onEnter false; true }.forEach { file ->
            if (!isActive()) return@forEach
            scanned++; if (scanned % 3000 == 0) progress("Database Junk — $scanned")
            if (!file.isFile) return@forEach
            if (exclusions.any { file.absolutePath.contains(it) }) return@forEach
            val ext = file.extension.lowercase()
            val name = file.name.lowercase()
            if (dbExt.contains(ext) || name.contains("journal") || name.contains("tombstone") || ext == "wal" || ext == "shm") {
                if (file.length() in 1..100_000_000L) entries.add(file.toEntry(true))
                if (entries.size >= 300) return@forEach
            }
        }
        val top = entries.sortedByDescending { it.sizeBytes }.take(300)
        val items = top.map { CleanItem.GenericFile(it) }
        val total = top.sumOf { it.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, total, isSafeToClean, description = description, requiresShizuku = false)
    }
    private fun File.toEntry(sel: Boolean): FileEntry {
        val ext = extension.lowercase()
        return FileEntry(name, absolutePath, length(), lastModified(), ext, sel, FileUtils.getMediaStoreUri(context, absolutePath, ext))
    }
}
