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

package com.frerox.toolz.data.cleaner.analyzer

import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.EmptyDirEntry
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmptyDirAnalyzer @Inject constructor() : CleanerAnalyzer {
    override val categoryId = "empty_dirs"
    override val categoryName = "Empty Folders"
    override val categoryIcon = "FolderOff"
    override val description = "Empty directories safe to remove"
    override val isSafeToClean = true

    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        // Truly-empty dirs come from the shared crawl — no walks.
        val entries = mutableListOf<EmptyDirEntry>()
        for (dir in index.emptyDirs) {
            if (!ctx.isActive()) break
            if (!dir.startsWith(index.root)) continue
            val name = dir.substringAfterLast('/').trimEnd('/')
            if (name.startsWith(".") && !ctx.config.includeHidden) continue
            if (FileUtils.isExcluded(dir, ctx.exclusions)) continue
            entries.add(EmptyDirEntry(dir, name.ifEmpty { dir }, isSelected = true))
            if (entries.size >= ctx.config.maxEmptyDirs) break
        }
        val items = entries.map { CleanItem.EmptyDir(it) }
        // empty dirs have 0 size but we show count; size 0
        return CleanCategory(categoryId, categoryName, categoryIcon, items, 0L, 0L, isSafeToClean,
            description = description,
            emptyHint = if (entries.isEmpty()) "No empty folders — storage is tidy" else null)
    }
}
