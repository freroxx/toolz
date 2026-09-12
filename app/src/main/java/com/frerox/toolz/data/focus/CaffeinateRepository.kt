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

package com.frerox.toolz.data.focus

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null
)

@Singleton
class CaffeinateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val caffeinateDao: CaffeinateDao
) {
    private val TAG = "CaffeinateRepo"

    val allApps: Flow<List<CaffeinateApp>> = caffeinateDao.getAllApps()

    /**
     * Returns all user-installed apps sorted alphabetically by label.
     * Skips pure system apps (keeps updated system apps like Maps/Chrome).
     */
    suspend fun getInstalledUserApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val isUpdatedSystem = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                !isSystem || isUpdatedSystem
            }
            .mapNotNull { appInfo ->
                try {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = try { pm.getApplicationIcon(appInfo.packageName) } catch (e: Exception) { null }
                    InstalledAppInfo(
                        packageName = appInfo.packageName,
                        label = label,
                        icon = icon
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load app info for ${appInfo.packageName}", e)
                    null
                }
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun updateAppAutoEnable(packageName: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        try {
            val existing = caffeinateDao.getAllAppsSync().find { it.packageName == packageName }
            if (existing != null) {
                caffeinateDao.updateApp(existing.copy(isAutoEnabled = isEnabled))
            } else {
                val info = pm.getApplicationInfo(packageName, 0)
                val name = pm.getApplicationLabel(info).toString()
                caffeinateDao.insertApps(listOf(CaffeinateApp(packageName = packageName, appName = name, category = "", isAutoEnabled = isEnabled)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateAppAutoEnable failed for $packageName", e)
        }
    }

    suspend fun syncAutoPkgsToRoom(pkgs: Set<String>) = withContext(Dispatchers.IO) {
        try {
            val current = caffeinateDao.getAllAppsSync()
            val pm = context.packageManager
            val updatedList = current.map { app ->
                app.copy(isAutoEnabled = pkgs.contains(app.packageName))
            }.toMutableList()

            // Also insert any new pkgs not in Room yet
            val existingPkgs = current.map { it.packageName }.toSet()
            for (pkg in pkgs) {
                if (!existingPkgs.contains(pkg)) {
                    try {
                        val info = pm.getApplicationInfo(pkg, 0)
                        val name = pm.getApplicationLabel(info).toString()
                        updatedList.add(CaffeinateApp(packageName = pkg, appName = name, category = "", isAutoEnabled = true))
                    } catch (_: Exception) {}
                }
            }
            caffeinateDao.insertApps(updatedList)
        } catch (e: Exception) {
            Log.e(TAG, "syncAutoPkgsToRoom failed", e)
        }
    }

    suspend fun getAutoEnabledPackages(): Set<String> {
        return caffeinateDao.getAutoEnabledApps().map { it.packageName }.toSet()
    }
}
