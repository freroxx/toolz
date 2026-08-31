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
import com.frerox.toolz.data.cleaner.DuplicateFile
import com.frerox.toolz.data.cleaner.DuplicateGroup
import com.frerox.toolz.data.cleaner.engine.CleanScanConfig
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.HashUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateAnalyzer @Inject constructor() : CleanerAnalyzer {
    override val categoryId="dupes"
    override val categoryName="Duplicate Files"
    override val categoryIcon="FileCopy"
    override val description="Exact duplicates — keep oldest, delete others"
    override val isSafeToClean=false
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        val sizeMap=HashMap<Long, MutableList<File>>(4096)
        var scanned=0
        root.walkTopDown().onEnter { dir -> if (!isActive()) return@onEnter false; if (dir.name.startsWith(".") && !config.includeHidden) return@onEnter false; if (exclusions.any { dir.absolutePath.contains(it) }) return@onEnter false; true }.forEach { file ->
            if (!isActive()) return@forEach
            if (!file.isFile) return@forEach
            if (exclusions.any { file.absolutePath.contains(it) }) return@forEach
            val size=file.length()
            if (size<config.duplicateMinSize) return@forEach
            if (size>500*1024*1024L) return@forEach
            sizeMap.getOrPut(size){ mutableListOf() }.add(file)
            scanned++; if (scanned%4000==0) progress("Duplicate scan — $scanned files")
        }
        progress("Analyzing duplicates…")
        var filtered=sizeMap.filter { it.value.size>=2 }
        // name+size prefilter limit 1k files
        val totalFiles = filtered.values.sumOf { it.size }
        if (totalFiles > 1000) {
            val sortedBySize = filtered.entries.sortedByDescending { it.key }
            var acc = 0
            val limited = mutableMapOf<Long, MutableList<java.io.File>>()
            for ((sz, lst) in sortedBySize) {
                if (acc >= 1000) break
                limited[sz] = lst
                acc += lst.size
            }
            filtered = limited
        }
        val groups=mutableListOf<DuplicateGroup>()
        var processed=0
        for ((size, files) in filtered) {
            if (!isActive()) break
            val quickGroups=HashMap<String, MutableList<File>>()
            for (f in files) {
                val q=HashUtils.computeQuickHash(f) ?: continue
                quickGroups.getOrPut(q){ mutableListOf() }.add(f)
            }
            for ((_, pot) in quickGroups.filter { it.value.size>=2 }) {
                val fullGroups=HashMap<String, MutableList<File>>()
                for (f in pot) {
                    val fh=HashUtils.computeFullHash(f) ?: continue
                    fullGroups.getOrPut(fh){ mutableListOf() }.add(f)
                }
                for ((hash, dupes) in fullGroups.filter { it.value.size>=2 }) {
                    val sorted=dupes.sortedBy { it.lastModified() }
                    val dupFiles=sorted.mapIndexed { idx,file -> DuplicateFile(file.absolutePath, file.lastModified(), isSelected=idx>0) }
                    groups.add(DuplicateGroup(hash, size, dupFiles))
                    if (groups.size>=config.maxDuplicatesGroups) break
                }
                if (groups.size>=config.maxDuplicatesGroups) break
            }
            processed++; if (processed%50==0) progress("Hashing — $processed/${filtered.size}")
            if (groups.size>=config.maxDuplicatesGroups) break
        }
        val sortedGroups=groups.sortedByDescending { it.sizeBytes * it.files.size }
        val items=sortedGroups.map { CleanItem.Duplicate(it) }
        val total=sortedGroups.sumOf { it.sizeBytes * it.files.size }
        val sel=sortedGroups.sumOf { g -> (g.files.size-1).coerceAtLeast(0)*g.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, sel, isSafeToClean, description=description)
    }
}
