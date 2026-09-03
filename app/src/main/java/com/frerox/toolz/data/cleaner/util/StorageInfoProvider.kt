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

package com.frerox.toolz.data.cleaner.util

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager
import com.frerox.toolz.data.cleaner.StorageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageInfoProvider @Inject constructor(@ApplicationContext private val context: Context) {
    fun getStorageInfo(cleanableBytes: Long = 0): StorageInfo = try {
        val info = queryViaStorageStatsManager() ?: queryViaStatFs()
        (info ?: StorageInfo(totalBytes = 0L, usedBytes = 0L, freeBytes = 0L, cleanableBytes = 0L))
            .copy(cleanableBytes = cleanableBytes)
    } catch (_: Exception) { StorageInfo(totalBytes = 0L, usedBytes = 0L, freeBytes = 0L, cleanableBytes = cleanableBytes) }
    /** Primary: whole-volume totals via StorageStatsManager (API 26+). Null on any failure. */
    private fun queryViaStorageStatsManager(): StorageInfo? = try {
        val stats = context.getSystemService(StorageStatsManager::class.java) ?: return null
        val total = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
        val free = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
        if (total <= 0) return null
        StorageInfo(totalBytes = total, usedBytes = (total - free).coerceAtLeast(0), freeBytes = free, cleanableBytes = 0L)
    } catch (_: Exception) { null }
    /** Fallback: StatFs on the app-accessible external files dir (scoped-storage safe), else internal. */
    private fun queryViaStatFs(): StorageInfo? = try {
        // Prefer app-accessible external files dir (scoped-storage safe), fallback to legacy path.
        val base: java.io.File = context.getExternalFilesDirs(null).firstOrNull { it != null } ?: context.filesDir
        val stat = StatFs(base.path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        StorageInfo(totalBytes = total, usedBytes = (total - free).coerceAtLeast(0), freeBytes = free, cleanableBytes = 0L)
    } catch (_: Exception) { null }
    fun refresh(cleanableBytes: Long = 0): StorageInfo = getStorageInfo(cleanableBytes)
}
