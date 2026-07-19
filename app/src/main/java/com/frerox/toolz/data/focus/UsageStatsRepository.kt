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
        val events = usageStatsManager.queryEvents(startMs, endMs)

        while (events.hasNextEvent()) {
            val ev = UsageEvents.Event()
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    resumeTime[ev.packageName] = ev.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val start = resumeTime[ev.packageName]
                    if (start != null) {
                        durations[ev.packageName] = (durations[ev.packageName] ?: 0L) + (ev.timeStamp - start)
                        resumeTime.remove(ev.packageName)
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
        val stats = try {
            usageStatsManager.queryAndAggregateUsageStats(startMs, endMs)
        } catch (e: Exception) {
            emptyMap()
        }
        
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
        return try {
            val stats = usageStatsManager.queryAndAggregateUsageStats(startMs, endMs)
            stats.values.sumOf { it.totalTimeInForeground }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Robust aggregate usage for a specific package today.
     */
    fun queryPackageUsageToday(packageName: String): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startMs = calendar.timeInMillis
        val endMs = System.currentTimeMillis()

        return try {
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
            stats.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun isExcluded(packageName: String): Boolean {
        if (packageName in EXCLUDED_PACKAGES) return true
        if (EXCLUDED_PREFIXES.any { packageName.startsWith(it) }) return true
        return false
    }
}
