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

package com.frerox.toolz.data.cleaner.util

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File

object FileUtils {
    fun calculateDirSize(dir: File): Long {
        var size = 0L
        try {
            var count = 0
            for (f in dir.walkTopDown().onEnter { d -> !d.name.startsWith(".") || d == dir }) {
                if (f.isFile) { size += f.length(); count++ }
                if (count > 50_000) break
            }
        } catch (_: Exception) {}
        return size
    }
    fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()
    fun isImageExt(ext: String) = extOf(ext) in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif")
    fun isVideoExt(ext: String) = extOf(ext) in setOf("mp4","mkv","avi","mov","webm","flv")
    fun isAudioExt(ext: String) = extOf(ext) in setOf("mp3","wav","m4a","ogg","flac","aac")
    /** Allowlist-based safety gate: only files under app-accessible external storage. */
    fun isSafeToDelete(path: String, rootHint: String? = null): Boolean {
        if (path.isBlank()) return false
        val normalized = try { File(path).canonicalPath } catch (_: Exception) { return false }
        // Hard blocks — system / device nodes, never cleanable
        val blockedPrefixes = listOf("/system", "/vendor", "/proc", "/dev", "/sys", "/apex", "/acct", "/config", "/mnt", "/data", "/cache")
        if (blockedPrefixes.any { normalized == it || normalized.startsWith("$it/") }) return false
        if (normalized == "/" || normalized == "/storage" || normalized == "/storage/emulated" ||
            normalized == "/storage/emulated/0" || normalized == "/sdcard") return false
        // Allowlist: must live under shared external storage root
        val roots = mutableListOf("/storage/emulated/0")
        if (rootHint != null) {
            try { roots.add(File(rootHint).canonicalPath) } catch (_: Exception) {}
        }
        if (roots.none { normalized == it || normalized.startsWith("$it/") }) return false
        // Refuse traversal leftovers / sentinel files
        val name = File(normalized).name
        if (name == ".nomedia" || name == ".gitkeep") return false
        return true
    }
    /** Directory variant of the allowlist gate: same checks as [isSafeToDelete],
     * plus the path must be an existing directory, or a non-existent path whose
     * parent is an existing directory (creation target). */
    fun isSafeDir(path: String, rootHint: String? = null): Boolean {
        if (!isSafeToDelete(path, rootHint)) return false
        return try {
            val f = File(path)
            if (f.exists()) f.isDirectory else (f.parentFile?.isDirectory == true)
        } catch (_: Exception) { false }
    }
    /** Prefix-match exclusion (exact or child). Substring matching is intentionally NOT used. */
    fun isExcluded(path: String, exclusions: Set<String>): Boolean {
        if (exclusions.isEmpty()) return false
        var p = path
        try { p = File(path).canonicalPath } catch (_: Exception) {}
        return exclusions.any { ex ->
            if (ex.isBlank()) return@any false
            var e = ex
            try { e = File(ex).canonicalPath } catch (_: Exception) {}
            p == e || p.startsWith("$e/")
        }
    }
    fun getMediaStoreUri(context: Context, path: String, ext: String): String? {
        // For images/videos, return file path directly for Coil (faster, Q+ compatible). MediaStore DATA is deprecated.
        val lower = ext.lowercase()
        if (lower in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif","mp4","mkv","avi","mov","webm","flv")) {
            return path
        }
        // For other types try MediaStore query via DISPLAY_NAME fallback, but return null to show icon
        if (lower == "pdf") return null
        return try {
            val collection = when (lower) {
                "mp3","wav","m4a","ogg","flac","aac" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> return null
            }
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(File(path).name), null)?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)).toString() else null }
        } catch (_: Exception) { null }
    }
}
