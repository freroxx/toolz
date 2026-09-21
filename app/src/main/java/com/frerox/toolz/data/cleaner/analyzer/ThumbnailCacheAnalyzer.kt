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
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailCacheAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) : CleanerAnalyzer {
    override val categoryId = "thumb_cache"
    override val categoryName = "Thumbnail Cache"
    override val categoryIcon = "Image"
    override val description = "Cached thumbnails that can be regenerated"
    override val isSafeToClean = true

    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        // Index-only: files living under a .thumbnails dir or the Photos cache.
        val candidates = mutableListOf<FileEntry>()
        for (f in index.files) {
            if (!ctx.isActive()) break
            if (candidates.size >= 400) break
            val lp = f.path.lowercase()
            val inThumbDir = "/.thumbnails/" in lp || lp.endsWith("/.thumbnails") ||
                "com.google.android.apps.photos/cache" in lp
            if (!inThumbDir) continue
            if (FileUtils.isExcluded(f.path, ctx.exclusions)) continue
            candidates.add(
                FileEntry(f.name, f.path, f.size, f.lastModified, f.ext, true,
                    FileUtils.getMediaStoreUri(context, f.path, f.ext))
            )
        }
        // keep sorted by size
        val top = candidates.sortedByDescending { it.sizeBytes }.take(300)
        val items = top.map { CleanItem.GenericFile(it) }
        val total = top.sumOf { it.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, total, isSafeToClean,
            description = description,
            emptyHint = if (top.isEmpty()) "No thumbnail cache found" else null)
    }
}
