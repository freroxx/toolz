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
class SystemJunkAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId = "system_junk"
    override val categoryName = "System Junk"
    override val categoryIcon = "DeleteSweep"
    override val description = "Temporary, log and cache files safe to remove"
    override val isSafeToClean = true
    private val junkExtensions = setOf("log","tmp","temp","cache","chk","dmp","crash","part","crdownload","old","bak","thumb","db-journal","tombstone","exo","fb_temp","thumbdata","logcat","hprof","stacktrace")
    private val junkPatterns = listOf("cache","cached","temp","tmp","logs",".thumbnails","lost+found","BugReport","diagnostics","crash_reports","UnityAdsCache","GmsCoreConfigCache","fb_temp",".exo","vungle.cache","fresco_cache","video_cache","image_cache",".Fabric","leakcanary","bugly","crashlytics",".glide","code_cache")
    private val docExt = setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","rtf","csv","json","xml","html")
    private val mediaExt = setOf("mp4","mkv","avi","mov","webm","flv","mp3","wav","m4a","ogg","flac","jpg","jpeg","png","gif","webp","bmp","heic")
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        val entries = mutableListOf<FileEntry>()
        var scanned=0
        root.walkTopDown().onEnter { dir -> if (!isActive()) return@onEnter false; val n=dir.name; if (n.startsWith(".") && n!=".thumbnails" && !config.includeHidden) return@onEnter false; true }.forEach { file ->
            if (!isActive()) return@forEach; scanned++; if (scanned%3000==0) progress("System Junk — $scanned files")
            if (exclusions.any { file.absolutePath.contains(it) }) return@forEach
            if (file.isFile) {
                val ext=file.extension.lowercase(); val abs=file.absolutePath; val isMedia=mediaExt.contains(ext); val isDoc=docExt.contains(ext)
                val isJunkExt=junkExtensions.contains(ext); val isJunkPath=junkPatterns.any { abs.contains(it, ignoreCase=true) }
                if ((isJunkExt || isJunkPath) && !isMedia && !isDoc) {
                    if (file.length() in 1..500_000_000L) entries.add(file.toEntry(true))
                }
            }
            if (entries.size>800) return@forEach
        }
        val top=entries.sortedByDescending { it.sizeBytes }.take(500)
        val items=top.map { CleanItem.GenericFile(it) }
        val total=top.sumOf { it.sizeBytes }
        val selected=items.sumOf { (it as CleanItem.GenericFile).let { f -> if (f.file.isSelected) f.file.sizeBytes else 0L } }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, selected, isSafeToClean, description=description)
    }
    private fun File.toEntry(sel:Boolean): FileEntry {
        val ext=extension.lowercase()
        return FileEntry(name, absolutePath, length(), lastModified(), ext, sel, FileUtils.getMediaStoreUri(context, absolutePath, ext))
    }
}
