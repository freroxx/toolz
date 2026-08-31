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

package com.frerox.toolz.data.cleaner

import android.graphics.drawable.Drawable

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val currentCategory: String = "", val filesScanned: Int = 0, val foundSize: Long = 0L, val progress: Float = 0f) : ScanState()
    data class Results(val categories: List<CleanCategory>, val totalCleanableBytes: Long, val selectedBytes: Long, val filesScanned: Int) : ScanState()
    data class Cleaning(val progress: Float, val currentFile: String = "") : ScanState()
    data class Done(val result: CleanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

data class CleanCategory(
    val id: String,
    val name: String,
    val icon: String,
    val items: List<CleanItem>,
    val totalSize: Long,
    val selectedSize: Long,
    val isSafeToClean: Boolean = false,
    val isExpanded: Boolean = false,
    val description: String? = null,
    val requiresShizuku: Boolean = false
)

sealed class CleanItem {
    data class Duplicate(val group: DuplicateGroup) : CleanItem()
    data class Corpse(val entry: CorpseEntry) : CleanItem()
    data class GenericFile(val file: FileEntry) : CleanItem()
    data class UnusedApp(val entry: UnusedAppEntry) : CleanItem()
    data class AppCache(val entry: AppCacheEntry) : CleanItem()
    data class EmptyDir(val entry: EmptyDirEntry) : CleanItem()
    data class MediaFile(val entry: MediaEntry) : CleanItem()
    data class ApkFile(val entry: ApkEntry) : CleanItem()
}

data class DuplicateGroup(val hash: String, val sizeBytes: Long, val files: List<DuplicateFile>)
data class DuplicateFile(val path: String, val lastModified: Long, val isSelected: Boolean = false)
data class CorpseEntry(val packageName: String, val path: String, val sizeBytes: Long, val type: CorpseType, val isSelected: Boolean = true)
enum class CorpseType { DATA, OBB, MEDIA }
data class FileEntry(val name: String, val path: String, val sizeBytes: Long, val lastModified: Long, val extension: String, val isSelected: Boolean = false, val thumbnailUri: String? = null)
data class UnusedAppEntry(val packageName: String, val appName: String, val sizeBytes: Long, val lastUsed: Long, val icon: Drawable? = null, val isSelected: Boolean = false)
data class AppCacheEntry(val packageName: String, val appName: String, val sizeBytes: Long, val cacheBytes: Long, val icon: Drawable? = null, val isSelected: Boolean = true)
data class EmptyDirEntry(val path: String, val name: String, val isSelected: Boolean = true)
data class MediaEntry(val name: String, val path: String, val sizeBytes: Long, val lastModified: Long, val extension: String, val type: MediaType, val isSelected: Boolean = false, val thumbnailUri: String? = null)
enum class MediaType { SCREENSHOT, DOWNLOAD, WHATSAPP, TELEGRAM, DCIM, OTHER }
data class ApkEntry(val name: String, val path: String, val sizeBytes: Long, val lastModified: Long, val packageName: String? = null, val versionName: String? = null, val isSelected: Boolean = false)
data class StorageInfo(val totalBytes: Long = 0L, val usedBytes: Long = 0L, val freeBytes: Long = 0L, val cleanableBytes: Long = 0L)
data class CleanResult(val freedBytes: Long = 0L, val deletedCount: Int = 0, val failedCount: Int = 0)
