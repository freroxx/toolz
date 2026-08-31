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
import com.frerox.toolz.data.cleaner.engine.CleanScanConfig
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmptyDirAnalyzer @Inject constructor() : CleanerAnalyzer {
    override val categoryId="empty_dirs"
    override val categoryName="Empty Folders"
    override val categoryIcon="FolderOff"
    override val description="Empty directories safe to remove"
    override val isSafeToClean=true
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        val entries=mutableListOf<EmptyDirEntry>()
        var scanned=0
        root.walkTopDown().onEnter { dir -> if (!isActive()) return@onEnter false; if (dir.name.startsWith(".") && !config.includeHidden) return@onEnter false; true }.forEach { f ->
            if (!isActive()) return@forEach
            scanned++; if (scanned%5000==0) progress("Empty folders — $scanned")
            if (exclusions.any { f.absolutePath.contains(it) }) return@forEach
            if (f.isDirectory && f!=root) {
                val list=f.list()
                if (list!=null && list.isEmpty()) {
                    entries.add(EmptyDirEntry(f.absolutePath, f.name, isSelected=true))
                    if (entries.size>=config.maxEmptyDirs) return@forEach
                }
            }
        }
        val items=entries.take(config.maxEmptyDirs).map { CleanItem.EmptyDir(it) }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, 0L, 0L, isSafeToClean, description=description)
    }
}
