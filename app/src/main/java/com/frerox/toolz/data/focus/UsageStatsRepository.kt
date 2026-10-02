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

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val pm = context.packageManager

    val usageStatsSettingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    private val EXCLUDED_PACKAGES = setOf(
        "android", "com.android.systemui", "com.android.settings",
        "com.google.android.packageinstaller", "com.android.phone",
        "com.android.server.telecom"
    )

    private val EXCLUDED_PREFIXES = setOf(
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oneplus.launcher",
        "com.huawei.android.launcher",
        "com.vivo.launcher",
        "com.oppo.launcher",
        "com.asus.launcher",
        "com.realme.launcher",
        "com.nothing.launcher",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.android.inputmethod.",
        "com.swiftkey.",
        "com.nuance."
    )

    fun hasUsageStatsPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun queryDailyByEvents(startMs: Long, endMs: Long): List<AppUsageInfo> {
        val durations = mutableMapOf<String, Long>()
        val resumeTime = mutableMapOf<String, Long>()
        // queryEvents() is a platform API: treat the result as nullable — a null
        // return (no data / service hiccup) must yield empty stats, never a crash.
        val events = usageStatsManager.queryEvents(startMs, endMs) ?: return emptyList()

        while (events.hasNextEvent()) {
            val ev = UsageEvents.Event()
            events.getNextEvent(ev)
            // packageName is a platform String: skip nulls before they reach map keys.
            val pkg = ev.packageName ?: continue
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    resumeTime[pkg] = ev.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = resumeTime[pkg]
                    if (start != null) {
                        durations[pkg] = (durations[pkg] ?: 0L) + (ev.timeStamp - start)
                        resumeTime.remove(pkg)
                    }
                }
            }
        }

        // Handle apps still in the foreground
        resumeTime.forEach { (pkg, start) ->
            durations[pkg] = (durations[pkg] ?: 0L) + (endMs - start)
        }

        return durations.mapNotNull { (pkg, time) ->
            if (isExcluded(pkg)) return@mapNotNull null
            
            val isToolz = pkg == context.packageName
            if (!isToolz && pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null

            val name = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                if (isToolz) "Toolz" else return@mapNotNull null
            }

            AppUsageInfo(
                packageName = pkg,
                appName = name,
                usageTimeMillis = time
            )
        }.sortedByDescending { it.usageTimeMillis }
    }

    fun queryWeeklyByAggregate(startMs: Long, endMs: Long): List<AppUsageInfo> {
        // queryAndAggregateUsageStats() returns null when permission is missing
        // or there is no data — a null map must yield empty stats, never an NPE
        // on the line below the try/catch.
        val stats = try {
            usageStatsManager.queryAndAggregateUsageStats(startMs, endMs)
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        
        return stats.mapNotNull { (pkg, usage) ->
            if (isExcluded(pkg)) return@mapNotNull null
            
            val isToolz = pkg == context.packageName
            if (!isToolz && pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null

            val name = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                if (isToolz) "Toolz" else return@mapNotNull null
            }

            AppUsageInfo(
                packageName = pkg,
                appName = name,
                usageTimeMillis = usage.totalTimeInForeground
            )
        }.sortedByDescending { it.usageTimeMillis }
    }

    /**
     * Queries the total foreground usage for ALL apps in a specific time range.
     * Useful for filling analytics chart bars.
     */
    fun queryTotalUsageInRange(startMs: Long, endMs: Long): Long {
        // Null map (no permission / no data) means zero usage — check explicitly
        // instead of relying on the catch below to convert the NPE.
        return try {
            val stats = usageStatsManager.queryAndAggregateUsageStats(startMs, endMs) ?: return 0L
            // Filter excluded packages so the headline total matches the visible list sum.
            stats.entries.filterNot { isExcluded(it.key) }.sumOf { it.value.totalTimeInForeground }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Robust aggregate usage for a specific package today.
     * Sums all daily buckets (queryUsageStats can return several rows per pkg).
     * Prefer [queryTodayUsageMap] when looking up several packages (one IPC).
     */
    fun queryPackageUsageToday(packageName: String): Long =
        queryTodayUsageMap()[packageName] ?: 0L

    /**
     * Batched today-usage lookup: ONE queryUsageStats call for all packages.
     * Replaces N x per-package queries (N binder scans) in weekly refresh.
     */
    fun queryTodayUsageMap(): Map<String, Long> {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startMs = calendar.timeInMillis
        val endMs = System.currentTimeMillis()

        return try {
            // queryUsageStats() returns null when there is no data — not an exception.
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs) ?: return emptyMap()
            val out = mutableMapOf<String, Long>()
            stats.forEach { out[it.packageName] = (out[it.packageName] ?: 0L) + it.totalTimeInForeground }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun isExcluded(packageName: String): Boolean {
        if (packageName in EXCLUDED_PACKAGES) return true
        if (EXCLUDED_PREFIXES.any { packageName.startsWith(it) }) return true
        return false
    }
}
