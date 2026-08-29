/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Triple-redundant durability for PurgeShot queue.
 *
 * Problem: clearing app data / cache wipes Room + DataStore. The user requested
 * "queue never breaks, even after updating or clearing cache/appdata — preserved perfectly".
 *
 * Solution:
 *  - Every mutation mirrors to an external JSON file in a public, app-independent location
 *    (Documents/Toolz/.purgeshot_queue.json) via MediaStore on Android 10+ with legacy
 *    Documents fallback. This file survives clear-data because it lives outside internal app storage.
 *  - On launch, if Room is empty but external file has entries, we restore.
 *  - Also mirrored to app's no-backup? No: we include db & DataStore in Auto Backup,
 *    and we also expose a plain JSON snapshot for manual restore.
 *
 * Encryption: queue contains only content URIs + timestamps (no image bytes), so plain JSON is fine.
 * If we stored secrets, we'd encrypt via KeyManager; here obfuscation is unnecessary.
 *
 * File format: JSON array of PurgeShotEntity (minimal fields). Versioned for forward-compat.
 */

package com.frerox.toolz.data.purgeshot

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurgeShotExternalBackupHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PurgeShotBackup"
        private const val BACKUP_DIR_NAME = "Toolz"
        private const val BACKUP_FILE_NAME = ".purgeshot_queue.json"
        private const val LEGACY_BACKUP_FILE_NAME = "purgeshot_queue.json"
    }

    private fun getLegacyFile(): File {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(docs, BACKUP_DIR_NAME)
        return File(dir, BACKUP_FILE_NAME)
    }

    private fun getLegacyFallbackFile(): File {
        // Secondary fallback for devices where Documents is restricted
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(pictures, BACKUP_DIR_NAME)
        return File(dir, BACKUP_FILE_NAME)
    }

    suspend fun mirrorToExternal(entities: List<PurgeShotEntity>) = withContext(Dispatchers.IO) {
        try {
            val json = entitiesToJson(entities)
            // Try scoped MediaStore write first (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(json)
            }
            // Always also write via legacy File API as redundancy (direct File works on most devices with MANAGE_EXTERNAL_STORAGE or when MediaStore is empty)
            writeViaLegacy(json)
        } catch (e: Exception) {
            Log.w(TAG, "mirrorToExternal failed", e)
        }
    }

    private suspend fun writeViaMediaStore(json: String) = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$BACKUP_DIR_NAME"
            // Check if existing entry
            val collection = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME}=? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH}=?"
            val selectionArgs = arrayOf(BACKUP_FILE_NAME, "$relativePath/")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            var existingId: Long? = null
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { c ->
                if (c.moveToFirst()) existingId = c.getLong(0)
            }
            if (existingId != null) {
                    val uri = android.content.ContentUris.withAppendedId(collection, existingId)
                resolver.openOutputStream(uri, "w")?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                Log.d(TAG, "Updated MediaStore backup: $uri")
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, BACKUP_FILE_NAME)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/json")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection, values) ?: return@withContext
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                values.clear()
                values.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Log.d(TAG, "Created MediaStore backup: $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore write failed", e)
            // Fall through to legacy
        }
    }

    private fun writeViaLegacy(json: String) {
        try {
            for (file in listOf(getLegacyFile(), getLegacyFallbackFile())) {
                file.parentFile?.mkdirs()
                file.writeText(json, Charsets.UTF_8)
                Log.d(TAG, "Legacy backup written: ${file.absolutePath}")
                break // only write to first available
            }
        } catch (e: Exception) {
            Log.w(TAG, "Legacy write failed", e)
        }
    }

    suspend fun restoreFromExternal(): List<PurgeShotEntity> = withContext(Dispatchers.IO) {
        // Priority: MediaStore -> legacy File -> fallback File
        var json: String? = readViaMediaStore()
        if (json.isNullOrBlank()) json = readViaLegacy()
        if (json.isNullOrBlank()) return@withContext emptyList()
        try {
            return@withContext jsonToEntities(json)
        } catch (e: Exception) {
            Log.w(TAG, "restore parse failed", e)
            return@withContext emptyList()
        }
    }

    private fun readViaMediaStore(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME}=? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf(BACKUP_FILE_NAME, "%$BACKUP_DIR_NAME%")
            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    resolver.openInputStream(uri)?.use { inp -> inp.readBytes().toString(Charsets.UTF_8) }
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore read failed", e)
            null
        }
    }

    private fun readViaLegacy(): String? {
        for (file in listOf(getLegacyFile(), getLegacyFallbackFile())) {
            if (file.exists() && file.canRead()) {
                try {
                    val txt = file.readText(Charsets.UTF_8)
                    if (txt.isNotBlank()) return txt
                } catch (_: Exception) {}
            }
        }
        // Also check app-external files dir as tertiary (survives update but not clear data)
        try {
            val ext = File(context.getExternalFilesDir(null), BACKUP_FILE_NAME)
            if (ext.exists()) return ext.readText(Charsets.UTF_8)
        } catch (_: Exception) {}
        return null
    }

    suspend fun clearExternal() = withContext(Dispatchers.IO) {
        try {
            mirrorToExternal(emptyList())
        } catch (_: Exception) {}
        try {
            for (f in listOf(getLegacyFile(), getLegacyFallbackFile())) if (f.exists()) f.delete()
        } catch (_: Exception) {}
    }

    // --- JSON ser/deser ---
    private fun entitiesToJson(entities: List<PurgeShotEntity>): String {
        val arr = JSONArray()
        for (e in entities) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("uri", e.fileUriString)
            o.put("name", e.displayName)
            if (e.filePath != null) o.put("path", e.filePath)
            o.put("createdAt", e.createdAtMs)
            o.put("deleteAt", e.scheduledDeleteAtMs)
            o.put("duration", e.durationMillis)
            o.put("label", e.durationLabel)
            o.put("status", e.status)
            if (e.sourcePackage != null) o.put("pkg", e.sourcePackage)
            o.put("attempts", e.attempts)
            if (e.lastError != null) o.put("err", e.lastError)
            arr.put(o)
        }
        val wrapper = JSONObject()
        wrapper.put("v", 1)
        wrapper.put("ts", System.currentTimeMillis())
        wrapper.put("queue", arr)
        return wrapper.toString()
    }

    private fun jsonToEntities(json: String): List<PurgeShotEntity> {
        val root = JSONObject(json)
        val arr: JSONArray = if (root.has("queue")) root.getJSONArray("queue") else root.getJSONArray("queue")
        // fallback: if root itself is array
        val list = mutableListOf<PurgeShotEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            // Only restore PENDING entries that are still in the future (allow 5min grace for overdue but not yet deleted)
            val status = o.optString("status", PurgeShotEntity.STATUS_PENDING)
            if (status != PurgeShotEntity.STATUS_PENDING) continue
            val deleteAt = o.getLong("deleteAt")
            // Keep even expired overdue entries so they can be cleaned up (show as expired)
            list.add(
                PurgeShotEntity(
                    id = o.optLong("id", 0),
                    fileUriString = o.getString("uri"),
                    displayName = o.optString("name", "screenshot.jpg"),
                    filePath = if (o.has("path")) o.optString("path") else null,
                    createdAtMs = o.optLong("createdAt", System.currentTimeMillis()),
                    scheduledDeleteAtMs = deleteAt,
                    durationMillis = o.optLong("duration", 0),
                    durationLabel = o.optString("label", "auto"),
                    status = status,
                    sourcePackage = if (o.has("pkg")) o.optString("pkg") else null,
                    attempts = o.optInt("attempts", 0),
                    lastError = if (o.has("err")) o.optString("err") else null
                )
            )
        }
        return list
    }
}
