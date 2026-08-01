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

package com.frerox.toolz.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceSpecHelper {

    data class DeviceSpecs(
        val totalRamGb: Double,
        val isLowRamDevice: Boolean,
        val androidVersion: Int,
        val recommendPerformanceMode: Boolean
    )

    fun getDeviceSpecs(context: Context): DeviceSpecs {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val isLowRamDevice = activityManager.isLowRamDevice
        
        // Recommend performance mode if RAM < 4GB or if system flags it as low ram
        val recommendPerformanceMode = totalRamGb < 4.0 || isLowRamDevice

        return DeviceSpecs(
            totalRamGb = totalRamGb,
            isLowRamDevice = isLowRamDevice,
            androidVersion = Build.VERSION.SDK_INT,
            recommendPerformanceMode = recommendPerformanceMode
        )
    }
}
