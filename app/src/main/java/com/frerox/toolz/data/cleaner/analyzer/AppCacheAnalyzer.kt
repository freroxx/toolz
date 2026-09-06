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
import com.frerox.toolz.data.cleaner.access.AccessGate
import com.frerox.toolz.data.cleaner.engine.FileIndex
import com.frerox.toolz.data.cleaner.engine.ScanCtx
import com.frerox.toolz.data.cleaner.engine.CleanerAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCacheAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessGate: AccessGate
) : CleanerAnalyzer {
    override val categoryId="app_cache"
    override val categoryName="App caches"
    override val categoryIcon="Cached"
    override val description="Per-app caches — external cleared directly, internal via automation or system settings"
    override val isSafeToClean=true
    override suspend fun analyze(index: FileIndex, ctx: ScanCtx): CleanCategory {
        val root = ctx.root
        val exclusions = ctx.exclusions
        val isActive = ctx.isActive
        val progress = ctx.progress
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CleanCategory(categoryId, categoryName, categoryIcon, emptyList(),0,0,isSafeToClean, description=description,
            blockedReason="Needs Android 8+", skippedCount=0)
        // V3: never silent-zero — usage access is mandatory for real sizes
        if (!accessGate.usageAccessGranted()) {
            // Still report measurable external caches so the category is useful pre-grant
            val extOnly = externalCacheFallback(root, exclusions, isActive, progress)
            val items = extOnly.map { CleanItem.AppCache(it) }
            val total = extOnly.sumOf { it.cacheBytes }
            return CleanCategory(categoryId, categoryName, categoryIcon, items, total, total, isSafeToClean,
                description="Grant Usage Access for full per-app sizes — showing external caches only",
                blockedReason="Usage Access not granted — internal cache sizes hidden",
                blockedFixLabel="Open settings", skippedCount=0)
        }
        val pm=context.packageManager
        val statsMgr=context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return CleanCategory(categoryId, categoryName, categoryIcon, emptyList(),0,0,isSafeToClean, description=description,
                blockedReason="Storage stats unavailable on this device", blockedFixLabel=null)
        val installed=try { pm.getInstalledApplications(PackageManager.GET_META_DATA) } catch (_: Exception) { emptyList() }
        val entries=mutableListOf<AppCacheEntry>()
        var scanned=0; var denied=0
        for (app in installed) {
            if (!isActive()) break
            if (!isManageableUserApp(app, pm, context.packageName)) continue
            if (exclusions.contains(app.packageName)) continue
            scanned++; if (scanned%25==0) progress("App cache — $scanned/${installed.size}")
            try {
                val uuid=if (app.storageUuid==StorageManager.UUID_DEFAULT) StorageManager.UUID_DEFAULT else app.storageUuid
                val stats=statsMgr.queryStatsForPackage(uuid, app.packageName, android.os.Process.myUserHandle())
                val cache=stats.cacheBytes
                if (cache>2*1024*1024L) {
                    val label=try { pm.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                    entries.add(AppCacheEntry(app.packageName, label, cache, cache, null, isSelected=true))
                }
            } catch (se: SecurityException) { denied++ }
            catch (_: Exception) { denied++ }
            if (entries.size>=120) break
        }
        val sorted=entries.sortedByDescending { it.cacheBytes }
        val items=sorted.map { CleanItem.AppCache(it) }
        val total=sorted.sumOf { it.cacheBytes }
        val sel=sorted.filter { it.isSelected }.sumOf { it.cacheBytes }
        // Routine per-package query failures (other profiles, restricted pkgs) are normal —
        // only surface a banner when NOTHING could be measured, never alongside real results.
        return CleanCategory(categoryId, categoryName, categoryIcon, items, total, sel, isSafeToClean, description=description,
            blockedReason=if (items.isEmpty() && denied>0) "Couldn't read per-app cache stats" else null,
            blockedFixLabel=if (items.isEmpty() && denied>0) "Open settings" else null,
            skippedCount=if (items.isEmpty()) denied else 0)
    }

    /** Pre-grant fallback: measure external Android/data+media caches directly (no permission beyond storage). */
    private fun externalCacheFallback(root: File, exclusions: Set<String>, isActive: () -> Boolean, progress: (String) -> Unit): List<AppCacheEntry> {
        val out = mutableListOf<AppCacheEntry>()
        val pm = context.packageManager
        val extBase = File(root, "Android/data")
        val dirs = try { extBase.listFiles()?.filter { it.isDirectory } ?: emptyList() } catch (_: Exception) { emptyList() }
        var n = 0
        for (d in dirs) {
            if (!isActive()) break
            if (exclusions.contains(d.name)) continue
            val appInfo = try { pm.getApplicationInfo(d.name, 0) } catch (_: Exception) { null }
            if (appInfo == null || !isManageableUserApp(appInfo, pm, context.packageName)) continue
            val cache = File(d, "cache")
            if (!cache.exists()) continue
            var sz = 0L
            try { sz = com.frerox.toolz.data.cleaner.util.FileUtils.calculateDirSize(cache) } catch (_: Exception) {}
            if (sz > 2*1024*1024L) {
                val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { d.name }
                out.add(AppCacheEntry(d.name, label, sz, sz, null, isSelected=true))
            }
            if (++n % 40 == 0) progress("App cache (external) — $n")
            if (out.size >= 60) break
        }
        return out.sortedByDescending { it.cacheBytes }
    }

    companion object {
        fun isManageableUserApp(app: ApplicationInfo, pm: PackageManager, myPackageName: String): Boolean {
            if (app.packageName == myPackageName) return false
            if (!app.enabled) return false
            if (app.packageName == "android" ||
                app.packageName.startsWith("com.android.overlay") ||
                app.packageName.startsWith("android.auto_generated_rro_") ||
                app.packageName.startsWith("com.android.providers.settings") ||
                app.packageName.startsWith("com.android.keychain")
            ) return false
            val isUserApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLaunchIntent = try { pm.getLaunchIntentForPackage(app.packageName) != null } catch (_: Exception) { false }
            return isUserApp || isUpdatedSystem || hasLaunchIntent
        }
    }
}
