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

package com.frerox.toolz.data.cleaner.engine

import com.frerox.toolz.data.cleaner.CleanCategory
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

interface CleanerAnalyzer {
    val categoryId: String
    val categoryName: String
    val categoryIcon: String
    val description: String
    val isSafeToClean: Boolean
    suspend fun analyze(
        root: File,
        installedPackages: Set<String>,
        progress: (String) -> Unit,
        exclusions: Set<String>,
        isActive: () -> Boolean,
        config: CleanScanConfig
    ): CleanCategory
}
