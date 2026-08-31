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

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.frerox.toolz.data.cleaner.StorageInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageInfoProvider @Inject constructor(@ApplicationContext private val context: Context) {
    fun getStorageInfo(cleanableBytes: Long = 0): StorageInfo = try {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        StorageInfo(totalBytes = total, usedBytes = total - free, freeBytes = free, cleanableBytes = cleanableBytes)
    } catch (_: Exception) { StorageInfo(cleanableBytes = cleanableBytes) }
    fun refresh(cleanableBytes: Long = 0): StorageInfo = getStorageInfo(cleanableBytes)
}
