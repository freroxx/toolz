package com.frerox.toolz.data.cleaner

import android.graphics.drawable.Drawable

/**
 * Represents the current state of the file cleaning scan engine.
 */
sealed class ScanState {
    object Idle : ScanState()

    data class Scanning(
        val currentCategory: String = "",
        val filesScanned: Int = 0,
        val foundSize: Long = 0L,
        val progress: Float = 0f // 0f..1f
    ) : ScanState()

    data class Results(
        val categories: List<CleanCategory>,
        val totalCleanableBytes: Long,
        val filesScanned: Int
    ) : ScanState()

    data class Cleaning(
        val progress: Float, // 0f..1f
        val currentFile: String = ""
    ) : ScanState()

    data class Done(val result: CleanResult) : ScanState()

    data class Error(val message: String) : ScanState()
}

/**
 * Categorized cleanable items for a better UX breakdown.
 */
data class CleanCategory(
    val id: String,
    val name: String,
    val icon: String, // Icon name or resource ID
    val items: List<CleanItem>,
    val totalSize: Long,
    val isSafeToClean: Boolean = false,
    val isExpanded: Boolean = false
)

sealed class CleanItem {
    data class Duplicate(val group: DuplicateGroup) : CleanItem()
    data class Corpse(val entry: CorpseEntry) : CleanItem()
    data class GenericFile(val file: FileEntry) : CleanItem()
    data class UnusedApp(val entry: UnusedAppEntry) : CleanItem()
}

/**
 * A group of files that are true duplicates (matching size + partial SHA-256 hash).
 */
data class DuplicateGroup(
    val hash: String,
    val sizeBytes: Long,
    val files: List<DuplicateFile>
)

data class DuplicateFile(
    val path: String,
    val lastModified: Long,
    val isSelected: Boolean = false
)

/**
 * A leftover directory from an uninstalled app.
 */
data class CorpseEntry(
    val packageName: String,
    val path: String,
    val sizeBytes: Long,
    val type: CorpseType,
    val isSelected: Boolean = true
)

enum class CorpseType { DATA, OBB }

/**
 * Generic file entry for large files, temp files, etc.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val isSelected: Boolean = false,
    val thumbnailUri: String? = null
)

/**
 * Represents an app that hasn't been used in a long time.
 */
data class UnusedAppEntry(
    val packageName: String,
    val appName: String,
    val sizeBytes: Long,
    val lastUsed: Long,
    val icon: Drawable? = null,
    val isSelected: Boolean = false
)

/**
 * Device storage breakdown.
 */
data class StorageInfo(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val cleanableBytes: Long = 0L
)

/**
 * Result after a cleaning operation.
 */
data class CleanResult(
    val freedBytes: Long = 0L,
    val deletedCount: Int = 0,
    val failedCount: Int = 0
)
