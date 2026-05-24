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
