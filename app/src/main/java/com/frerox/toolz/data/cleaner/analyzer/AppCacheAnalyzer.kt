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

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import com.frerox.toolz.data.cleaner.AppCacheEntry
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.CleanItem
import com.frerox.toolz.data.cleaner.engine.CleanScanConfig
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCacheAnalyzer @Inject constructor(@ApplicationContext private val context: Context) : CleanerAnalyzer {
    override val categoryId="app_cache"
    override val categoryName="App Cache"
    override val categoryIcon="Cached"
    override val description="Hidden caches that can be cleared per app"
    override val isSafeToClean=true
    override suspend fun analyze(root: File, installedPackages: Set<String>, progress: (String)->Unit, exclusions: Set<String>, isActive: ()->Boolean, config: CleanScanConfig): CleanCategory {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CleanCategory(categoryId, categoryName, categoryIcon, emptyList(),0,0,isSafeToClean, description=description)
        val pm=context.packageManager
        val statsMgr=context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager ?: return CleanCategory(categoryId, categoryName, categoryIcon, emptyList(),0,0,isSafeToClean, description=description)
        val installed=pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val entries=mutableListOf<AppCacheEntry>()
        var scanned=0
        for (app in installed) {
            if (!isActive()) break
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM)!=0) continue
            if (app.packageName==context.packageName) continue
            if (exclusions.any { it.contains(app.packageName) }) continue
            scanned++; if (scanned%25==0) progress("App cache — $scanned/${installed.size}")
            try {
                val uuid=if (app.storageUuid==StorageManager.UUID_DEFAULT) StorageManager.UUID_DEFAULT else app.storageUuid
                val stats=statsMgr.queryStatsForPackage(uuid, app.packageName, android.os.Process.myUserHandle())
                val cache=stats.cacheBytes
                if (cache>5*1024*1024L) {
                    val label=pm.getApplicationLabel(app).toString()
                    val icon=try { pm.getApplicationIcon(app) } catch(_:Exception){ null }
                    entries.add(AppCacheEntry(app.packageName, label, cache, cache, icon, isSelected=true))
                }
            } catch(_:Exception){}
            if (entries.size>80) break
        }
        val sorted=entries.sortedByDescending { it.cacheBytes }
        val items=sorted.map { CleanItem.AppCache(it) }
        val total=sorted.sumOf { it.cacheBytes }
        val sel=sorted.filter { it.isSelected }.sumOf { it.cacheBytes }
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, sel, isSafeToClean, description=description)
    }
}
