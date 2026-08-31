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
import com.frerox.toolz.data.cleaner.ApkEntry
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.engine.CleanScanConfig
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId="apk"
    override val categoryName="APK Files"
    override val categoryIcon="Android"
    override val description="Installer packages — often outdated"
    override val isSafeToClean=false
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        val collected=mutableListOf<ApkEntry>()
        var scanned=0
        root.walkTopDown().onEnter { dir -> if (!isActive()) return@onEnter false; if (dir.name.startsWith(".") && !config.includeHidden) return@onEnter false; if (exclusions.any { dir.absolutePath.contains(it) }) return@onEnter false; true }.forEach { file ->
            if (!isActive()) return@forEach
            scanned++; if (scanned%4000==0) progress("APKs — $scanned")
            if (!file.isFile) return@forEach
            if (exclusions.any { file.absolutePath.contains(it) }) return@forEach
            if (file.extension.lowercase()=="apk") {
                val pm=context.packageManager
                var pkg:String?=null; var ver:String?=null
                try { val info=pm.getPackageArchiveInfo(file.absolutePath,0); pkg=info?.packageName; ver=info?.versionName } catch(_:Exception){}
                collected.add(ApkEntry(file.name, file.absolutePath, file.length(), file.lastModified(), pkg, ver, isSelected=false))
                if (collected.size>=config.maxApkFiles) return@forEach
            }
        }
        val sorted=collected.sortedByDescending { it.sizeBytes }.take(config.maxApkFiles)
        val items=sorted.map { CleanItem.ApkFile(it) }
        val total=sorted.sumOf { it.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, 0L, isSafeToClean, description=description)
    }
}
