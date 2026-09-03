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
    data class Results(val categories: List<CleanCategory>, val totalCleanableBytes: Long, val selectedBytes: Long, val filesScanned: Int, val truncated: Boolean = false) : ScanState()
    data class Cleaning(val progress: Float, val currentFile: String = "") : ScanState()
    data class Done(val result: CleanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

/** S1.1.1: coarse UI phase derived from ScanState (no behavior change). */
enum class ScanPhase { IDLE, SCANNING, RESULTS, CLEANING, DONE, ERROR }

fun ScanState.phase(): ScanPhase = when (this) {
    is ScanState.Idle -> ScanPhase.IDLE
    is ScanState.Scanning -> ScanPhase.SCANNING
    is ScanState.Results -> ScanPhase.RESULTS
    is ScanState.Cleaning -> ScanPhase.CLEANING
    is ScanState.Done -> ScanPhase.DONE
    is ScanState.Error -> ScanPhase.ERROR
}

/** S1.1.1: per-category health without changing existing blocked fields. */
enum class CategoryCapability { READY, DEGRADED, BLOCKED }

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
    val requiresShizuku: Boolean = false,
    val truncatedCount: Int = 0,
    /** V3: never silent-empty — Blocked/Degraded carry why + fix action. */
    val blockedReason: String? = null,
    val blockedFixLabel: String? = null,
    val skippedCount: Int = 0,
    /** S1.1.1 additive only: capability + empty hint (defaults keep callers compiling). */
    val capability: CategoryCapability = CategoryCapability.READY,
    val emptyHint: String? = null
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

/** Per-item fix action for failed clean operations. */
enum class CleanFix { OPEN_APP_SETTINGS, OPEN_GATE_ALL_FILES, OPEN_GATE_USAGE, ENABLE_AUTO_CLEAR, OPEN_SHIZUKU_SETUP }

data class FailedItem(
    val key: String,
    val label: String,
    val reason: String,
    val fix: CleanFix? = null,
    val pkg: String? = null
)

data class CleanResult(
    val freedBytes: Long = 0L,
    val deletedCount: Int = 0,
    val failedCount: Int = 0,
    val trashedCount: Int = 0,
    /** V4 honest accounting: cleared vs nothing-to-do vs failed-with-reason. */
    val clearedCount: Int = 0,
    val alreadyCleanCount: Int = 0,
    val failedItems: List<FailedItem> = emptyList(),
    /** V4: truly-empty folders auto-removed during the clean (0 bytes, cosmetic). */
    val emptyDirsRemoved: Int = 0,
    /** S1.1.1 additive only: scan telemetry carried into result. */
    val scannedFiles: Int = 0,
    val scanMs: Long = 0L
)

/** Stable keys for LazyColumn + accessibility. */
fun CleanItem.stableId(): String = when (this) {
    is CleanItem.Duplicate -> "d_${group.hash}"
    is CleanItem.Corpse -> "c_${entry.path}"
    is CleanItem.GenericFile -> "f_${file.path}"
    is CleanItem.UnusedApp -> "u_${entry.packageName}"
    is CleanItem.AppCache -> "ac_${entry.packageName}"
    is CleanItem.EmptyDir -> "e_${entry.path}"
    is CleanItem.MediaFile -> "m_${entry.path}"
    is CleanItem.ApkFile -> "a_${entry.path}"
}
fun CleanItem.isSelected(): Boolean = when (this) {
    is CleanItem.Duplicate -> group.files.any { it.isSelected }
    is CleanItem.Corpse -> entry.isSelected
    is CleanItem.GenericFile -> file.isSelected
    is CleanItem.UnusedApp -> entry.isSelected
    is CleanItem.AppCache -> entry.isSelected
    is CleanItem.EmptyDir -> entry.isSelected
    is CleanItem.MediaFile -> entry.isSelected
    is CleanItem.ApkFile -> entry.isSelected
}
fun CleanItem.sizeBytes(): Long = when (this) {
    is CleanItem.Duplicate -> (group.files.size - 1).coerceAtLeast(0) * group.sizeBytes
    is CleanItem.Corpse -> entry.sizeBytes
    is CleanItem.GenericFile -> file.sizeBytes
    is CleanItem.UnusedApp -> entry.sizeBytes
    is CleanItem.AppCache -> entry.cacheBytes
    is CleanItem.EmptyDir -> 0L
    is CleanItem.MediaFile -> entry.sizeBytes
    is CleanItem.ApkFile -> entry.sizeBytes
}
/** S1.1.1 pure helpers (no IO). */
fun CleanCategory.selectedCount(): Int = items.count { it.isSelected() }
fun CleanCategory.isBlocked(): Boolean = blockedReason != null && items.isEmpty()
