/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.trash

import android.content.Context
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Real trash: move file/dir into app-private trash before delete → restorable + auto-expiry. */
@Singleton
class TrashManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: CleanerTrashDao
) {
    fun trashRoot(): File = File(context.filesDir, "cleaner_trash").apply { mkdirs() }

    /** Move to trash. V3: same-volume rename fast-path, else copy+delete with quota guard. */
    suspend fun moveToTrash(absPath: String, type: String, sizeBytes: Long): String? = withContext(Dispatchers.IO) {
        try {
            if (!FileUtils.isSafeToDelete(absPath)) return@withContext null
            val src = File(absPath)
            if (!src.exists()) return@withContext null
            val dest = File(trashRoot(), "${UUID.randomUUID()}_${src.name.take(60)}")
            // Fast path: same volume rename
            val moved = try { src.renameTo(dest) } catch (_: Exception) { false }
            if (moved && dest.exists()) {
                record(absPath, dest.absolutePath, sizeBytes, type)
                return@withContext dest.absolutePath
            }
            // Cross-volume (shared → internal): quota guard then copy+delete.
            // Directories are size-verified after copy (best-effort).
            try {
                val free = trashRoot().freeSpace
                val need = if (src.isDirectory) FileUtils.calculateDirSize(src) else src.length()
                if (need <= 0L || need > free - 256 * 1024 * 1024L) return@withContext null // keep 256MB headroom
                if (src.isDirectory) src.copyRecursively(dest, overwrite = true) else src.copyTo(dest, overwrite = true)
                if (!dest.exists()) return@withContext null
                // Verify size before deleting source.
                val okSize = if (src.isDirectory) {
                    need <= 0L || FileUtils.calculateDirSize(dest) >= need
                } else dest.length() == src.length()
                if (!okSize) { try { dest.deleteRecursively() } catch (_: Exception) {}; return@withContext null }
                val deleted = try { if (src.isDirectory) src.deleteRecursively() else src.delete() } catch (_: Exception) { false }
                if (!deleted && src.exists()) { try { dest.deleteRecursively() } catch (_: Exception) {}; return@withContext null }
                record(absPath, dest.absolutePath, sizeBytes, type)
                return@withContext dest.absolutePath
            } catch (_: Exception) { try { dest.deleteRecursively() } catch (_: Exception) {}; return@withContext null }
        } catch (_: Exception) { null }
    }

    private suspend fun record(original: String, trash: String, size: Long, type: String) {
        try {
            trashDao.insert(CleanerTrashEntity(originalPath = original, trashPath = trash, sizeBytes = size, type = type))
        } catch (_: Exception) {}
    }

    suspend fun restoreAll(): Int = withContext(Dispatchers.IO) {
        var restored = 0
        try {
            for (e in trashDao.getAll()) {
                try {
                    // Ghost rows (recorded without a trash copy, e.g. direct deletes)
                    // can never be restored — drop them instead of leaking.
                    val tp = e.trashPath ?: run { trashDao.deleteById(e.id); continue }
                    val src = File(tp); if (!src.exists()) { trashDao.deleteById(e.id); continue }
                    val dest = File(e.originalPath)
                    try { dest.parentFile?.mkdirs() } catch (_: Exception) {}
                    if (tryRestore(src, dest)) {
                        trashDao.deleteById(e.id)
                        notifyMediaScan(dest.absolutePath)
                        restored++
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        restored
    }

    /** Rename fast-path, copy fallback, rename-on-conflict. Never overwrites user data. */
    private fun tryRestore(src: File, dest: File): Boolean {
        try {
            if (!dest.exists()) {
                if (src.renameTo(dest) && !src.exists()) return true
                // Cross-volume: copy back, verify, then drop the trash copy.
                return try {
                    if (src.isDirectory) src.copyRecursively(dest, overwrite = false)
                    else src.copyTo(dest, overwrite = false)
                    val ok = dest.exists() &&
                        (if (src.isDirectory) FileUtils.calculateDirSize(dest) >= FileUtils.calculateDirSize(src) else dest.length() == src.length())
                    if (ok) { src.deleteRecursively(); true } else { try { dest.deleteRecursively() } catch (_: Exception) {}; false }
                } catch (_: Exception) { false }
            }
            // Destination reappeared (recreated by an app) — restore beside it.
            val sibling = File(dest.parentFile, dest.name + " (restored)")
            if (sibling.exists()) return false
            if (src.renameTo(sibling) && !src.exists()) return true
            return try {
                if (src.isDirectory) src.copyRecursively(sibling, overwrite = false)
                else src.copyTo(sibling, overwrite = false)
                sibling.exists()
            } catch (_: Exception) { false }
        } catch (_: Exception) { return false }
    }

    suspend fun purgeExpired(): Int = withContext(Dispatchers.IO) {
        var purged = 0
        try {
            val expired = trashDao.getExpired()
            for (e in expired) {
                try {
                    e.trashPath?.let { File(it).deleteRecursively() }
                    trashDao.deleteById(e.id); purged++
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        purged
    }

    suspend fun getTrashEntries(): List<CleanerTrashEntity> = withContext(Dispatchers.IO) {
        try { trashDao.getAll() } catch (_: Exception) { emptyList() }
    }

    suspend fun getTrashTotalBytes(): Long = withContext(Dispatchers.IO) {
        try {
            trashDao.getAll().sumOf { it.sizeBytes }
        } catch (_: Exception) { 0L }
    }

    suspend fun restoreItem(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val entry = trashDao.getAll().firstOrNull { it.id == id } ?: return@withContext false
            val tp = entry.trashPath ?: run { trashDao.deleteById(id); return@withContext false }
            val src = File(tp)
            if (!src.exists()) { trashDao.deleteById(id); return@withContext false }
            val dest = File(entry.originalPath)
            dest.parentFile?.mkdirs()
            if (tryRestore(src, dest)) {
                trashDao.deleteById(id)
                notifyMediaScan(dest.absolutePath)
                true
            } else false
        } catch (_: Exception) { false }
    }

    private fun notifyMediaScan(path: String) {
        try {
            android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (_: Exception) {}
    }

    suspend fun deletePermanently(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val entry = trashDao.getAll().firstOrNull { it.id == id } ?: return@withContext false
            entry.trashPath?.let { File(it).deleteRecursively() }
            trashDao.deleteById(id)
            true
        } catch (_: Exception) { false }
    }

    suspend fun restoreItems(ids: Set<Long>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (id in ids) {
            if (restoreItem(id)) count++
        }
        count
    }

    suspend fun deletePermanently(ids: Set<Long>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (id in ids) {
            if (deletePermanently(id)) count++
        }
        count
    }

    suspend fun emptyTrash(): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val all = trashDao.getAll()
            count = all.size
            for (e in all) {
                try { e.trashPath?.let { File(it).deleteRecursively() } } catch (_: Exception) {}
            }
            trashDao.clearAll()
            try { trashRoot().listFiles()?.forEach { it.deleteRecursively() } } catch (_: Exception) {}
        } catch (_: Exception) {}
        count
    }
}
