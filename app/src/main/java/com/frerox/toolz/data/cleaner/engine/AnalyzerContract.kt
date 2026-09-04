/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.cleaner.engine

import java.io.File

data class CleanScanConfig(
    val largeThresholdBytes: Long = 50 * 1024 * 1024L,
    val duplicateMinSize: Long = 1024L,
    val oldDownloadDays: Int = 30,
    val includeHidden: Boolean = false,
    val maxLargeFiles: Int = 100,
    val maxEmptyDirs: Int = 500,
    val maxDuplicatesGroups: Int = 200,
    val maxApkFiles: Int = 100,
    val maxMediaFiles: Int = 150
)

/** Everything an analyzer needs besides the shared index. */
data class ScanCtx(
    val root: File,
    val installed: Set<String>,
    val exclusions: Set<String>,
    val config: CleanScanConfig,
    val isActive: () -> Boolean,
    val progress: (String) -> Unit,
    // Gates snapshot at scan start; analyzers must NEVER tell the user to grant
    // something already granted — banner only for still-missing capabilities.
    val allFilesGranted: Boolean = true,
    val shizukuUsable: Boolean = false
)

interface CleanerAnalyzer {
    val categoryId: String
    val categoryName: String
    val categoryIcon: String
    val description: String
    val isSafeToClean: Boolean
    /** Pure-ish: read ONLY from [index]; no filesystem walks. Hashing/manifest parses allowed. */
    suspend fun analyze(index: FileIndex, ctx: ScanCtx): com.frerox.toolz.data.cleaner.CleanCategory
}
