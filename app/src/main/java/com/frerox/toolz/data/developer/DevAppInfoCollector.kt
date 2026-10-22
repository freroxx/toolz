/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Runtime snapshot of the current process. Shared by the Developer Mode
 * console and the always-on fast-report flow so both describe the device in
 * exactly the same words.
 */

package com.frerox.toolz.data.developer

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import com.frerox.toolz.BuildConfig
import java.util.Locale

object DevAppInfoCollector {

    fun collect(context: Context): DevAppInfo {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val max = runtime.maxMemory().coerceAtLeast(1L)
        val manufacturer = Build.MANUFACTURER
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            .trim()
        val model = Build.MODEL.trim()
        val device = when {
            manufacturer.isBlank() -> model
            model.isBlank() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        return DevAppInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            packageName = context.packageName,
            pid = Process.myPid(),
            processUptimeMs = (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
                .coerceAtLeast(0L),
            heapUsedBytes = used,
            heapMaxBytes = max,
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L),
            threads = Thread.activeCount(),
            heapUsedFraction = (used.toFloat() / max.toFloat()).coerceIn(0f, 1f),
            deviceLabel = device.ifBlank { "Unknown device" },
            androidLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            buildType = BuildConfig.BUILD_TYPE,
            densityDpi = context.resources.displayMetrics.densityDpi,
            debuggable = BuildConfig.DEBUG,
        )
    }
}
