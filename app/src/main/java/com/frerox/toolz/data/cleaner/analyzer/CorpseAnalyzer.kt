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
import com.frerox.toolz.data.cleaner.CorpseEntry
import com.frerox.toolz.data.cleaner.CorpseType
import com.frerox.toolz.data.cleaner.engine.CleanScanConfig
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorpseAnalyzer @Inject constructor() : CleanerAnalyzer {
    override val categoryId="corpse"
    override val categoryName="App Leftovers"
    override val categoryIcon="AutoDelete"
    override val description="Folders left by uninstalled apps"
    override val isSafeToClean=true
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        val entries=mutableListOf<CorpseEntry>()
        val paths=listOf("Android/data","Android/obb","Android/media","Android/obj")
        for (p in paths) {
            if (!isActive()) break
            progress("Leftovers — $p")
            val base=File(root,p)
            if (!base.exists()||!base.isDirectory) continue
            base.listFiles()?.forEach { dir ->
                if (!isActive()) return@forEach
                if (!dir.isDirectory) return@forEach
                val name=dir.name
                if (name.startsWith("com.android.")||name.startsWith("com.google.android.")) return@forEach
                if (exclusions.any { dir.absolutePath.contains(it) }) return@forEach
                if (!installedPackages.contains(name)) {
                    val size=FileUtils.calculateDirSize(dir)
                    if (size>0) {
                        val type=when { p.contains("obb")->CorpseType.OBB; p.contains("media")->CorpseType.MEDIA; else->CorpseType.DATA }
                        entries.add(CorpseEntry(name, dir.absolutePath, size, type, isSelected=true))
                    }
                }
            }
        }
        val sorted=entries.sortedByDescending { it.sizeBytes }
        val items=sorted.map { CleanItem.Corpse(it) }
        val total=sorted.sumOf { it.sizeBytes }
        val selected=items.sumOf { (it as CleanItem.Corpse).let { c-> if(c.entry.isSelected) c.entry.sizeBytes else 0L } }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, selected, isSafeToClean, description=description)
    }
}
