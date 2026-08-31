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

import android.content.Context
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.FileEntry
import com.frerox.toolz.data.cleaner.engine.CleanScanConfig
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailCacheAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId="thumb_cache"
    override val categoryName="Thumbnail Cache"
    override val categoryIcon="Image"
    override val description="Cached thumbnails that can be regenerated"
    override val isSafeToClean=true
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        val candidates=mutableListOf<FileEntry>()
        val thumbDirs=listOf(File(root,"DCIM/.thumbnails"), File(root,"Pictures/.thumbnails"), File(root,".thumbnails")) + root.walkTopDown().maxDepth(4).filter { it.isDirectory && it.name==".thumbnails" }.toList()
        for (dir in thumbDirs.distinctBy { it.absolutePath }) {
            if (!isActive()) break
            if (!dir.exists()||!dir.isDirectory) continue
            if (exclusions.any { dir.absolutePath.contains(it) }) continue
            progress("Thumbnails — ${dir.name}")
            dir.walkTopDown().forEach { f ->
                if (!isActive()) return@forEach
                if (exclusions.any { f.absolutePath.contains(it) }) return@forEach
                if (f.isFile) {
                    candidates.add(f.toEntry(true))
                    if (candidates.size>400) return@forEach
                }
            }
        }
        val top=candidates.sortedByDescending { it.sizeBytes }.take(300)
        val items=top.map { CleanItem.GenericFile(it) }
        val total=top.sumOf { it.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, total, isSafeToClean, description=description)
    }
    private fun File.toEntry(sel:Boolean): FileEntry {
        val ext=extension.lowercase()
        return FileEntry(name, absolutePath, length(), lastModified(), ext, sel, FileUtils.getMediaStoreUri(context, absolutePath, ext))
    }
}
