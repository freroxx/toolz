package com.frerox.toolz.widget

import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
//  Screen Time Widget — RemoteViews-based, uses ScreenTimeWidgetDrawer
//  Shows a ring indicator of total screen time vs 4h daily goal,
//  plus a mini bar chart of the top 3 apps.
// ---------------------------------------------------------------------------

class ScreenTimeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(context, manager, id) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.screen_time_widget)

        val (totalMs, topApps) = loadTodayUsage(context)
        val dailyLimitMs      = 4L * 60 * 60 * 1000 // 4-hour default goal
        val progress          = (totalMs.toFloat() / dailyLimitMs).coerceIn(0f, 1f)

        val primaryColor = getPrimaryColor(context)
        val trackColor   = getTrackColor(context)
        val bgColor      = getSurfaceColor(context)
        val textColor    = getOnSurfaceColor(context)

        // Ring bitmap
        val ringBitmap = ScreenTimeWidgetDrawer.drawRing(
            progress   = progress,
            ringColor  = primaryColor,
            trackColor = trackColor,
            bgColor    = bgColor,
            sizePx     = 300,
        )
        views.setImageViewBitmap(R.id.st_ring_image, ringBitmap)

        // Center text
        val hours   = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val totalStr = when {
            hours > 0 -> "${hours}h ${minutes}m"
            else      -> "${minutes}m"
        }
        views.setTextViewText(R.id.st_total_time, totalStr)
        views.setTextColor(R.id.st_total_time, textColor)
        views.setTextViewText(R.id.st_label, "Screen Time")
        views.setTextColor(R.id.st_label, setAlpha(textColor, 0.6f))

        val pctStr = "${(progress * 100).roundToInt()}%"
        views.setTextViewText(R.id.st_percent, pctStr)
        views.setTextColor(R.id.st_percent, primaryColor)

        // Top 3 apps
        val appIds = listOf(
            Triple(R.id.app1_name, R.id.app1_time, R.id.app1_bar),
            Triple(R.id.app2_name, R.id.app2_time, R.id.app2_bar),
            Triple(R.id.app3_name, R.id.app3_time, R.id.app3_bar),
        )
        appIds.forEachIndexed { idx, (nameId, timeId, barId) ->
            val app = topApps.getOrNull(idx)
            if (app != null) {
                val (name, ms) = app
                val h = ms / 3_600_000
                val m = (ms % 3_600_000) / 60_000
                val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                val barPct  = if (totalMs > 0) ((ms * 100) / totalMs).toInt() else 0
                views.setTextViewText(nameId, name)
                views.setTextColor(nameId, textColor)
                views.setTextViewText(timeId, timeStr)
                views.setTextColor(timeId, setAlpha(textColor, 0.6f))
                views.setProgressBar(barId, 100, barPct.coerceIn(0, 100), false)
            } else {
                views.setTextViewText(nameId, "—")
                views.setTextViewText(timeId, "")
                views.setProgressBar(barId, 100, 0, false)
            }
        }

        // Tap → open Focus Flow
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigate_to", "focus_flow")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 77, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.st_root, pi)

        manager.updateAppWidget(id, views)
    }

    // ── Usage Stats ─────────────────────────────────────────────────────────

    private data class UsageResult(val totalMs: Long, val topApps: List<Pair<String, Long>>)

    private fun loadTodayUsage(context: Context): UsageResult {
        val statsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as? UsageStatsManager ?: return UsageResult(0L, emptyList())
        val pm  = context.packageManager
        val now = System.currentTimeMillis()
        val tz  = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis

        return try {
            val usageMap      = mutableMapOf<String, Long>()
            val lastEventTime = mutableMapOf<String, Long>()
            val isFg          = mutableMapOf<String, Boolean>()

            val events = statsManager.queryEvents(startTime, now)
            while (events.hasNextEvent()) {
                val ev = UsageEvents.Event()
                events.getNextEvent(ev)
                val pkg  = ev.packageName
                val time = ev.timeStamp
                when (ev.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        lastEventTime[pkg] = time
                        isFg[pkg] = true
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (isFg[pkg] == true) {
                            val diff = time - (lastEventTime[pkg] ?: startTime)
                            if (diff > 0) usageMap[pkg] = (usageMap[pkg] ?: 0L) + diff
                            isFg[pkg] = false
                        }
                    }
                }
            }
            // Add still-running foreground
            isFg.forEach { (pkg, fg) ->
                if (fg) {
                    val diff = now - (lastEventTime[pkg] ?: startTime)
                    if (diff > 0) usageMap[pkg] = (usageMap[pkg] ?: 0L) + diff
                }
            }

            val excluded = setOf("android", "com.android.systemui", context.packageName)
            val appList = usageMap.entries
                .filter { (pkg, ms) ->
                    ms > 5_000L &&
                    pkg !in excluded &&
                    pm.getLaunchIntentForPackage(pkg) != null
                }
                .mapNotNull { (pkg, ms) ->
                    val name = try {
                        pm.getApplicationLabel(
                            pm.getApplicationInfo(pkg, 0)
                        ).toString()
                    } catch (_: Exception) { return@mapNotNull null }
                    name to ms
                }
                .sortedByDescending { it.second }

            UsageResult(
                totalMs = appList.sumOf { it.second },
                topApps = appList.take(3),
            )
        } catch (_: Exception) {
            UsageResult(0L, emptyList())
        }
    }

    // ── Dynamic Colors (API 31+) ─────────────────────────────────────────────

    private fun getPrimaryColor(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getColor(android.R.color.system_accent1_400, context.theme)
        } else 0xFF6750A4.toInt()
    }

    private fun getTrackColor(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getColor(android.R.color.system_neutral1_700, context.theme)
        } else 0xFF49454F.toInt()
    }

    private fun getSurfaceColor(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getColor(android.R.color.system_neutral1_900, context.theme)
        } else 0xFF1C1B1F.toInt()
    }

    private fun getOnSurfaceColor(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getColor(android.R.color.system_neutral1_100, context.theme)
        } else 0xFFE6E1E5.toInt()
    }

    private fun setAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}

// ---------------------------------------------------------------------------
//  Ring/Arc drawing utility — pure Canvas, no Compose dependency
// ---------------------------------------------------------------------------

object ScreenTimeWidgetDrawer {
    fun drawRing(
        progress: Float,
        ringColor: Int,
        trackColor: Int,
        bgColor: Int,
        sizePx: Int,
    ): Bitmap {
        val bmp    = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = sizePx * 0.12f
        val inset  = stroke / 2f + sizePx * 0.03f

        // Background fill
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)

        // Track (full circle)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color       = trackColor
            style       = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap   = Paint.Cap.ROUND
        }
        canvas.drawArc(
            RectF(inset, inset, sizePx - inset, sizePx - inset),
            -90f, 360f, false, trackPaint,
        )

        // Progress arc
        if (progress > 0.01f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color       = ringColor
                style       = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap   = Paint.Cap.ROUND
            }
            canvas.drawArc(
                RectF(inset, inset, sizePx - inset, sizePx - inset),
                -90f, progress * 360f, false, progressPaint,
            )
        }
        return bmp
    }
}
