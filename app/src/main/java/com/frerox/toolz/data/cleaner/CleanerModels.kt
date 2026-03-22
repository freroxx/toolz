package com.frerox.toolz.data.cleaner

/**
 * Represents the current state of the file cleaning scan engine.
 */
sealed class ScanState {
    object Idle : ScanState()

    data class Scanning(
        val currentPath: String = "",
        val filesScanned: Int = 0,
        val duplicatesFound: Int = 0,
        val corpsesFound: Int = 0
    ) : ScanState()

    data class Results(
        val duplicateGroups: List<DuplicateGroup>,
        val corpseEntries: List<CorpseEntry>,
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
    val isSelected: Boolean = false // newer files auto-selected for deletion
)

/**
 * A leftover directory from an uninstalled app.
 */
data class CorpseEntry(
    val packageName: String,
    val path: String,
    val sizeBytes: Long,
    val type: CorpseType,
    val isSelected: Boolean = false
)

enum class CorpseType { DATA, OBB }

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
