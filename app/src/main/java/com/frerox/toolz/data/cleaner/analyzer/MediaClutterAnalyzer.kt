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
import com.frerox.toolz.data.cleaner.MediaEntry
import com.frerox.toolz.data.cleaner.MediaType
import com.frerox.toolz.data.cleaner.engine.CleanScanConfig
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import com.frerox.toolz.data.cleaner.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaClutterAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId="media_clutter"
    override val categoryName="Media Clutter"
    override val categoryIcon="Collections"
    override val description="Screenshots, old downloads, WhatsApp/Telegram media"
    override val isSafeToClean=false
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        val entries=mutableListOf<MediaEntry>()
        val now=System.currentTimeMillis()
        val oldThr=now - config.oldDownloadDays*24*60*60*1000L
        val dirs=listOf(File(root,"DCIM/Screenshots") to MediaType.SCREENSHOT, File(root,"Pictures/Screenshots") to MediaType.SCREENSHOT, File(root,"Download") to MediaType.DOWNLOAD, File(root,"Downloads") to MediaType.DOWNLOAD, File(root,"WhatsApp/Media") to MediaType.WHATSAPP, File(root,"Telegram/Telegram Images") to MediaType.TELEGRAM, File(root,"DCIM/Camera") to MediaType.DCIM)
        for ((dir,type) in dirs) {
            if (!isActive()) break
            if (!dir.exists()||!dir.isDirectory) continue
            if (exclusions.any { dir.absolutePath.contains(it) }) continue
            progress("Media — ${dir.name}")
            dir.walkTopDown().maxDepth(3).forEach { f ->
                if (!isActive()) return@forEach
                if (!f.isFile) return@forEach
                if (exclusions.any { f.absolutePath.contains(it) }) return@forEach
                val sz=f.length(); val lm=f.lastModified()
                val isOld=lm<oldThr
                val isLargeVideo=f.extension.lowercase() in setOf("mp4","mkv","mov") && sz>50*1024*1024L
                val isScreenshotOld=type==MediaType.SCREENSHOT && lm < now - 7*24*60*60*1000L
                if (isOld || isLargeVideo || isScreenshotOld || type==MediaType.WHATSAPP || type==MediaType.TELEGRAM) {
                    val ext=f.extension.lowercase()
                    entries.add(MediaEntry(f.name, f.absolutePath, sz, lm, ext, type, isSelected=false, thumbnailUri=FileUtils.getMediaStoreUri(context, f.absolutePath, ext)))
                    if (entries.size>=config.maxMediaFiles) return@forEach
                }
            }
            if (entries.size>=config.maxMediaFiles) break
        }
        val sorted=entries.sortedByDescending { it.sizeBytes }.take(config.maxMediaFiles)
        val items=sorted.map { CleanItem.MediaFile(it) }
        val total=sorted.sumOf { it.sizeBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, 0L, isSafeToClean, description=description)
    }
}
