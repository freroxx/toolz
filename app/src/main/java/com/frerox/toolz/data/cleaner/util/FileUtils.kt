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
        try { dir.walkTopDown().filter { it.isFile }.forEach { size += it.length() } } catch (_: Exception) {}
        return size
    }
    fun isImageExt(ext: String) = ext.lowercase() in setOf("jpg","jpeg","png","gif","webp","bmp","heic","heif")
    fun isVideoExt(ext: String) = ext.lowercase() in setOf("mp4","mkv","avi","mov","webm","flv")
    fun isAudioExt(ext: String) = ext.lowercase() in setOf("mp3","wav","m4a","ogg","flac","aac")
    fun isSafeToDelete(path: String): Boolean {
        if (path.isBlank()) return false
        val normalized = File(path).canonicalPath
        if (normalized == "/" || normalized == "/storage" || normalized == "/storage/emulated" || normalized == "/storage/emulated/0") return false
        if (normalized.startsWith("/system") || normalized.startsWith("/vendor") || normalized.startsWith("/proc")) return false
        return true
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
