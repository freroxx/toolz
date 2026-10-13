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

package com.frerox.toolz.widget.livingframes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameRenderer
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Builds and pushes widget RemoteViews.
 *
 * Model: a widget is pushed ONCE per change. For animated sets the RemoteViews
 * carry every frame inside a ViewFlipper and the launcher advances it on its
 * own clock — no foreground service, no per-frame Binder traffic, nothing
 * running while the launcher is hidden. Pushes only happen on: pin, setting
 * change, resize, slideshow tick.
 */
object LivingFramesWidgetHelper {

    const val ACTION_NEXT = "com.frerox.toolz.LIVING_FRAME_NEXT"
    const val ACTION_SLIDE_ADVANCE = "com.frerox.toolz.LIVING_FRAME_SLIDE"
    const val EXTRA_WIDGET_ID = "living_frame_widget_id"

    // -- widget geometry ------------------------------------------------------

    fun allWidgetIds(context: Context): IntArray = try {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, LivingFramesWidgetReceiver::class.java))
    } catch (_: Exception) { intArrayOf() }

    /** Widget size in dp as the launcher reports it (portrait: min width × max height). */
    fun widgetSizeDp(mgr: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
        val o = try { mgr.getAppWidgetOptions(appWidgetId) } catch (_: Exception) { null }
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        return (if (w > 0) w else 150) to (if (h > 0) h else 150)
    }

    fun widgetSizePx(context: Context, mgr: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
        val d = context.resources.displayMetrics.density
        val (w, h) = widgetSizeDp(mgr, appWidgetId)
        return (w * d).roundToInt().coerceIn(96, 1400) to (h * d).roundToInt().coerceIn(96, 1400)
    }

    /** Launcher grid cells, e.g. "4×2" (standard 70dp-cell rule). */
    fun cellLabel(context: Context, appWidgetId: Int): String {
        val (w, h) = widgetSizeDp(AppWidgetManager.getInstance(context), appWidgetId)
        return "${((w + 30) / 70).coerceAtLeast(1)}×${((h + 30) / 70).coerceAtLeast(1)}"
    }

    /** What the OS allows for one update is ~6 screens of pixels; we use 40% of it. */
    fun bitmapBudget(context: Context): Long {
        val dm = context.resources.displayMetrics
        return (6.0 * 4.0 * dm.widthPixels * dm.heightPixels * 0.4).toLong()
    }

    // -- layouts ------------------------------------------------------------------

    private fun flipperLayout(fps: Int): Int = when (fps) {
        1 -> R.layout.living_frames_widget_f1
        2 -> R.layout.living_frames_widget_f2
        4 -> R.layout.living_frames_widget_f4
        5 -> R.layout.living_frames_widget_f5
        6 -> R.layout.living_frames_widget_f6
        8 -> R.layout.living_frames_widget_f8
        10 -> R.layout.living_frames_widget_f10
        15 -> R.layout.living_frames_widget_f15
        20 -> R.layout.living_frames_widget_f20
        24 -> R.layout.living_frames_widget_f24
        30 -> R.layout.living_frames_widget_f30
        else -> R.layout.living_frames_widget_f12
    }

    // -- tap handling -------------------------------------------------------------------

    private fun tapIntent(context: Context, appWidgetId: Int, frame: LivingFrame): PendingIntent? {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val action = if (frame.tapAction == LivingFrameTapAction.NEXT_FRAME && !frame.isSlideshow)
            LivingFrameTapAction.OPEN_PREVIEW else frame.tapAction
        return when (action) {
            LivingFrameTapAction.NOTHING -> null
            LivingFrameTapAction.NEXT_FRAME -> PendingIntent.getBroadcast(
                context, appWidgetId,
                Intent(context, LivingFramesWidgetReceiver::class.java).apply {
                    this.action = ACTION_NEXT
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                }, flags
            )
            LivingFrameTapAction.OPEN_PREVIEW -> PendingIntent.getActivity(
                context, appWidgetId,
                Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.LivingWidgets.route)
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }, flags
            )
        }
    }

    private fun applyChrome(context: Context, views: RemoteViews, appWidgetId: Int, frame: LivingFrame) {
        if (frame.showCaption) {
            views.setTextViewText(R.id.living_frame_caption, frame.name)
            views.setViewVisibility(R.id.living_frame_caption, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.living_frame_caption, View.GONE)
        }
        views.setOnClickPendingIntent(R.id.living_frame_root, tapIntent(context, appWidgetId, frame))
        views.setContentDescription(R.id.living_frame_root, frame.name)
    }

    // -- pushing ----------------------------------------------------------------------------

    fun pushPlaceholder(context: Context, appWidgetId: Int, text: String): Boolean = try {
        val v = RemoteViews(context.packageName, R.layout.living_frames_widget_static)
        v.setViewVisibility(R.id.living_frame_static, View.GONE)
        v.setViewVisibility(R.id.living_frame_caption, View.GONE)
        v.setViewVisibility(R.id.living_frame_placeholder, View.VISIBLE)
        v.setTextViewText(R.id.living_frame_placeholder, text)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, v)
        true
    } catch (_: Exception) { false }

    /**
     * Renders [frame] at this widget's exact size and pushes it. If the system
     * rejects the update (bitmap-memory ceiling), the budget is halved and the
     * plan recomputed — up to four attempts.
     */
    suspend fun push(
        context: Context,
        repo: LivingFramesRepository,
        appWidgetId: Int,
        frame: LivingFrame
    ): Boolean = withContext(Dispatchers.Default) {
        val mgr = AppWidgetManager.getInstance(context)
        val (w, h) = widgetSizePx(context, mgr, appWidgetId)
        val density = context.resources.displayMetrics.density
        val animated = frame.isAnimated
        val sourceIndex = if (frame.isSlideshow) LivingFramesSlideshow.currentIndex(context, appWidgetId, frame) else 0
        var budget = bitmapBudget(context)
        var error = "The launcher rejected the update."

        for (attempt in 0 until 4) {
            val plan = LivingFrameEngine.plan(
                if (animated) frame.frameCount else 1, w, h,
                LivingFrameRenderer.isOpaque(frame), frame.fps, budget
            )
            val indices = if (animated) (0 until frame.frameCount step plan.step).toList() else listOf(sourceIndex)
            val need = maxOf(plan.outW, plan.outH)
            val bitmaps = ArrayList<Bitmap>(indices.size)
            try {
                for (i in indices) {
                    val src = repo.decodeAtLeast(frame.id, i, need) ?: continue
                    try {
                        bitmaps.add(
                            LivingFrameRenderer.render(src, frame, plan.outW, plan.outH, density * plan.scale, plan.rgb565)
                        )
                    } finally { runCatching { src.recycle() } }
                }
                if (bitmaps.isEmpty()) {
                    error = "Frames are missing. Re-import this widget."
                    break
                }
                val views = if (animated && bitmaps.size > 1) {
                    val v = RemoteViews(context.packageName, flipperLayout(plan.effectiveFps))
                    v.removeAllViews(R.id.living_frame_flip)
                    LivingFrameEngine.playbackOrder(bitmaps.size, frame.loopMode).forEach { idx ->
                        val child = RemoteViews(context.packageName, R.layout.living_frames_frame)
                        child.setImageViewBitmap(R.id.living_frame_item, bitmaps[idx])
                        v.addView(R.id.living_frame_flip, child)
                    }
                    v
                } else {
                    val v = RemoteViews(context.packageName, R.layout.living_frames_widget_static)
                    v.setImageViewBitmap(R.id.living_frame_static, bitmaps[0])
                    v.setViewVisibility(R.id.living_frame_static, View.VISIBLE)
                    v.setViewVisibility(R.id.living_frame_placeholder, View.GONE)
                    v
                }
                applyChrome(context, views, appWidgetId, frame)
                mgr.updateAppWidget(appWidgetId, views)
                writeInfo(
                    context, appWidgetId,
                    WidgetInfo(w, h, bitmaps.size, bitmaps.sumOf { it.allocationByteCount.toLong() },
                        (plan.scale * 100).roundToInt(), plan.rgb565, plan.step, plan.effectiveFps,
                        System.currentTimeMillis(), null)
                )
                return@withContext true
            } catch (e: Exception) {
                error = (e.message ?: error).take(90)
                budget /= 2
            } finally {
                bitmaps.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            }
        }
        writeInfo(context, appWidgetId, WidgetInfo(w, h, 0, 0, 0, false, 1, frame.fps, System.currentTimeMillis(), error))
        false
    }

    /** Binds (if needed) and pushes one widget. The single entry point for every refresh path. */
    suspend fun refresh(context: Context, repo: LivingFramesRepository, appWidgetId: Int): Boolean {
        repo.ensureLoaded()
        var frameId = repo.boundFrame(appWidgetId)
        if (frameId == null) {
            val pending = takePendingPinFrame(context)?.takeIf { repo.get(it) != null }
            val pick = pending?.let { repo.get(it) } ?: repo.snapshot().firstOrNull()
            if (pick == null) {
                pushPlaceholder(context, appWidgetId, "Create a widget in Toolz first")
                return false
            }
            repo.bindWidget(appWidgetId, pick.id)
            frameId = pick.id
        }
        val frame = repo.get(frameId) ?: run {
            pushPlaceholder(context, appWidgetId, "This widget was deleted. Long-press to remove it.")
            return false
        }
        val ok = push(context, repo, appWidgetId, frame)
        if (frame.isSlideshow) LivingFramesSlideshow.ensureScheduled(context, appWidgetId, frame.slideshowIntervalMin)
        return ok
    }

    /** Slideshow tick or tap-next: move the cursor, push the photo, arm the next alarm. */
    suspend fun advanceSlideshow(context: Context, repo: LivingFramesRepository, appWidgetId: Int): Boolean {
        repo.ensureLoaded()
        val frame = repo.boundFrame(appWidgetId)?.let { repo.get(it) } ?: return false
        if (!frame.isSlideshow) return false
        LivingFramesSlideshow.advance(context, appWidgetId, frame)
        val ok = push(context, repo, appWidgetId, frame)
        LivingFramesSlideshow.schedule(context, appWidgetId, frame.slideshowIntervalMin)
        return ok
    }

    suspend fun refreshFrame(context: Context, repo: LivingFramesRepository, frameId: String) {
        repo.boundWidgetIds(frameId).forEach { refresh(context, repo, it) }
    }

    // -- per-widget render report (shown in the tool) ----------------------------------------------

    data class WidgetInfo(
        val widthPx: Int,
        val heightPx: Int,
        val frames: Int,
        val bytes: Long,
        val scalePct: Int,
        val rgb565: Boolean,
        val step: Int,
        val fps: Int,
        val atMs: Long,
        val error: String?
    ) {
        val reduced: Boolean get() = scalePct in 1..99 || step > 1 || rgb565
    }

    private fun infoPrefs(context: Context) =
        context.getSharedPreferences("living_frames_widget_info", Context.MODE_PRIVATE)

    private fun writeInfo(context: Context, id: Int, i: WidgetInfo) {
        try {
            infoPrefs(context).edit().putString(
                "i_$id",
                listOf(i.widthPx, i.heightPx, i.frames, i.bytes, i.scalePct, if (i.rgb565) 1 else 0, i.step, i.fps, i.atMs)
                    .joinToString("|") + "|" + (i.error ?: "")
            ).apply()
        } catch (_: Exception) {}
    }

    fun readInfo(context: Context, id: Int): WidgetInfo? = try {
        val p = infoPrefs(context).getString("i_$id", null)?.split("|", limit = 10)
        if (p == null || p.size < 10) null else WidgetInfo(
            p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toLong(), p[4].toInt(), p[5] == "1",
            p[6].toInt(), p[7].toInt(), p[8].toLong(), p[9].ifBlank { null }
        )
    } catch (_: Exception) { null }

    fun clearInfo(context: Context, id: Int) {
        runCatching { infoPrefs(context).edit().remove("i_$id").apply() }
    }

    // -- pin flow -----------------------------------------------------------------------------------------

    private const val PIN_PREFS = "living_frames_pin"

    fun setPendingPinFrame(context: Context, frameId: String) {
        runCatching {
            context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE).edit()
                .putString("id", frameId).putLong("at", System.currentTimeMillis()).apply()
        }
    }

    /** requestPinAppWidget carries no extras, so the tool records which set was pinned (2-minute window). */
    fun takePendingPinFrame(context: Context): String? = try {
        val p = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE)
        val id = p.getString("id", null)
        val at = p.getLong("at", 0L)
        if (id == null || System.currentTimeMillis() - at > 120_000L) null
        else { p.edit().clear().apply(); id }
    } catch (_: Exception) { null }

    fun requestPin(context: Context, frame: LivingFrame): Boolean = try {
        val mgr = context.getSystemService(AppWidgetManager::class.java)
        if (mgr == null || !mgr.isRequestPinAppWidgetSupported) {
            false
        } else {
            val callback = PendingIntent.getBroadcast(
                context, frame.id.hashCode(),
                Intent(context, LivingFramesWidgetReceiver::class.java).apply {
                    action = LivingFramesWidgetReceiver.ACTION_PINNED
                    putExtra(LivingFramesWidgetReceiver.EXTRA_FRAME_ID, frame.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mgr.requestPinAppWidget(ComponentName(context, LivingFramesWidgetReceiver::class.java), null, callback)
        }
    } catch (_: Exception) { false }

    /**
     * Master toggle for the 4 Glance app widgets (Music / Pomodoro / Toolbar /
     * Stopwatch). Living widgets are never affected. Kept here so existing
     * callers keep compiling.
     */
    fun setAppWidgetsComponentEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        listOf(
            "com.frerox.toolz.widget.glance.MusicWidgetReceiver",
            "com.frerox.toolz.widget.glance.PomodoroWidgetReceiver",
            "com.frerox.toolz.widget.glance.SearchBarWidgetReceiver",
            "com.frerox.toolz.widget.glance.TimerWidgetReceiver"
        ).forEach { cls ->
            try {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, cls), state, PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}
        }
    }
}
