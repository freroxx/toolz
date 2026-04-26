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
import android.util.Log
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.focus.UsageStatsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
//  Screen Time Widget — RemoteViews-based, uses ScreenTimeWidgetDrawer
//  Shows a ring indicator of total screen time vs 4h daily goal,
//  plus a mini bar chart of the top 3 apps.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun usageStatsRepository(): UsageStatsRepository
}

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
        val repository = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        ).usageStatsRepository()

        if (!repository.hasUsageStatsPermission()) {
            return UsageResult(0L, emptyList())
        }

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis

        val appList = repository.queryDailyByEvents(startTime, now)

        return UsageResult(
            totalMs = appList.sumOf { it.usageTimeMillis },
            topApps = appList.take(3).map { it.appName to it.usageTimeMillis },
        )
    }

    // ── Dynamic Colors (API 31+) ─────────────────────────────────────────────

    private fun getPrimaryColor(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getColor(android.R.color.system_accent1_200, context.theme)
        } else 0xFFD0BCFF.toInt()
    }

    private fun getTrackColor(context: Context): Int {
        return if (Build.VERSION.SDK_INT >= 31) {
            context.resources.getColor(android.R.color.system_neutral1_800, context.theme)
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
        val stroke = sizePx * 0.10f
        val inset  = stroke / 2f + sizePx * 0.04f

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
