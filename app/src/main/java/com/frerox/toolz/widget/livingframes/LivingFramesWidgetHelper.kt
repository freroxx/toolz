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
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFrameRenderer
import com.frerox.toolz.data.livingframes.LivingFrameResolution
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.screens.livingframes.LivingFrameTools
import kotlinx.coroutines.CancellationException
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

    /**
     * Kept for callers that only need a best-effort list. Use
     * [allWidgetIdsOrNull] when an empty list must not be confused with a
     * failed binder call (the watchdog used to prune every binding on error).
     */
    fun allWidgetIds(context: Context): IntArray = allWidgetIdsOrNull(context) ?: intArrayOf()

    fun allWidgetIdsOrNull(context: Context): IntArray? = try {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, LivingFramesWidgetReceiver::class.java))
    } catch (_: Exception) {
        null
    }

    /**
     * Widget size in dp as the launcher reports it. Android's contract gives
     * portrait dimensions through MIN_WIDTH/MAX_HEIGHT and landscape through
     * MAX_WIDTH/MIN_HEIGHT; using only the portrait pair squashes landscape
     * widgets.
     */
    fun widgetSizeDp(context: Context, mgr: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
        val o = try {
            mgr.getAppWidgetOptions(appWidgetId)
        } catch (_: Exception) {
            null
        }
        val minW = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val maxW = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0) ?: 0
        val minH = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        val maxH = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        val portrait =
            context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = if (portrait) {
            minW.takeIf { it > 0 } ?: maxW
        } else {
            maxW.takeIf { it > 0 } ?: minW
        }
        val h = if (portrait) {
            maxH.takeIf { it > 0 } ?: minH
        } else {
            minH.takeIf { it > 0 } ?: maxH
        }
        return (if (w > 0) w else 150) to (if (h > 0) h else 150)
    }

    fun widgetSizePx(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int
    ): Pair<Int, Int> {
        val d = context.resources.displayMetrics.density
        val (w, h) = widgetSizeDp(context, mgr, appWidgetId)
        return (w * d).roundToInt().coerceIn(96, 1600) to
            (h * d).roundToInt().coerceIn(96, 1600)
    }

    /** Launcher grid cells, e.g. "4×2" (standard 70dp-cell rule). */
    fun cellLabel(context: Context, appWidgetId: Int): String {
        val (w, h) = widgetSizeDp(
            context,
            AppWidgetManager.getInstance(context),
            appWidgetId
        )
        return "${((w + 30) / 70).coerceAtLeast(1)}×${((h + 30) / 70).coerceAtLeast(1)}"
    }

    /**
     * RemoteViews' bitmap-memory ceiling is expressed in screen-sized
     * allocations: 6 × width × height × 4 bytes per pixel. The old code
     * omitted the 4-byte pixel size, so it thought the budget was four times
     * smaller than it is and downscaled every large widget for no reason.
     * Keep 55% for binder serialization and ViewFlipper child overhead.
     */
    fun bitmapBudget(context: Context): Long {
        val dm = context.resources.displayMetrics
        val cap = (6.0 * dm.widthPixels.toDouble() * dm.heightPixels.toDouble() * 4.0)
            .toLong()
        return (cap * 0.55).toLong()
            .coerceAtLeast(minOf(256L * 1024, cap))
            .coerceAtMost(cap)
    }

    // -- layouts --------------------------------------------------------------

    /**
     * Single-RemoteViews fallback. Every frame is embedded in one ViewFlipper.
     * Animations stream by default; this path is only used when a launcher
     * rejects collection widgets, so it must still show a correct still or
     * short loop instead of an empty widget.
     */
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
        40 -> R.layout.living_frames_widget_f40
        48 -> R.layout.living_frames_widget_f48
        60 -> R.layout.living_frames_widget_f60
        else -> R.layout.living_frames_widget_f12
    }

    /**
     * Streaming layout. The AdapterViewFlipper asks the RemoteViewsService for
     * one frame at a time, so long/high-fps clips are never squashed into the
     * launcher's binder budget. flipInterval is XML-only, hence one layout per
     * supported rate.
     */
    private fun streamingLayout(fps: Int): Int = when (fps) {
        1 -> R.layout.living_frames_widget_stream_f1
        2 -> R.layout.living_frames_widget_stream_f2
        4 -> R.layout.living_frames_widget_stream_f4
        5 -> R.layout.living_frames_widget_stream_f5
        6 -> R.layout.living_frames_widget_stream_f6
        8 -> R.layout.living_frames_widget_stream_f8
        10 -> R.layout.living_frames_widget_stream_f10
        15 -> R.layout.living_frames_widget_stream_f15
        20 -> R.layout.living_frames_widget_stream_f20
        24 -> R.layout.living_frames_widget_stream_f24
        30 -> R.layout.living_frames_widget_stream_f30
        40 -> R.layout.living_frames_widget_stream_f40
        48 -> R.layout.living_frames_widget_stream_f48
        60 -> R.layout.living_frames_widget_stream_f60
        else -> R.layout.living_frames_widget_stream_f12
    }

    // -- tap handling ---------------------------------------------------------

    private fun tapIntent(
        context: Context,
        appWidgetId: Int,
        frame: LivingFrame
    ): PendingIntent? {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val action = if (
            frame.tapAction == LivingFrameTapAction.NEXT_FRAME && !frame.isSlideshow
        ) {
            LivingFrameTapAction.OPEN_PREVIEW
        } else {
            frame.tapAction
        }
        return when (action) {
            LivingFrameTapAction.NOTHING -> null
            LivingFrameTapAction.NEXT_FRAME -> PendingIntent.getBroadcast(
                context, appWidgetId,
                Intent(context, LivingFramesActionReceiver::class.java).apply {
                    this.action = ACTION_NEXT
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                }, flags
            )
            LivingFrameTapAction.OPEN_PREVIEW,
            LivingFrameTapAction.OPEN_TOOL -> PendingIntent.getActivity(
                context, appWidgetId,
                Intent(context, MainActivity::class.java).apply {
                    val route = if (action == LivingFrameTapAction.OPEN_TOOL) {
                        LivingFrameTools.routeOrNull(frame.tapTargetRoute)
                    } else {
                        null
                    }
                    putExtra(
                        MainActivity.EXTRA_NAVIGATE_TO,
                        route ?: Screen.LivingWidgets.route
                    )
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }, flags
            )
        }
    }

    private fun applyChrome(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        frame: LivingFrame
    ) {
        if (frame.showCaption) {
            views.setTextViewText(R.id.living_frame_caption, frame.name)
            views.setViewVisibility(R.id.living_frame_caption, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.living_frame_caption, View.GONE)
        }
        views.setOnClickPendingIntent(
            R.id.living_frame_root,
            tapIntent(context, appWidgetId, frame)
        )
        views.setContentDescription(R.id.living_frame_root, frame.name)
    }

    // -- pushing --------------------------------------------------------------

    fun pushPlaceholder(context: Context, appWidgetId: Int, text: String): Boolean = try {
        val v = RemoteViews(context.packageName, R.layout.living_frames_widget_static)
        v.setViewVisibility(R.id.living_frame_static, View.GONE)
        v.setViewVisibility(R.id.living_frame_caption, View.GONE)
        v.setViewVisibility(R.id.living_frame_placeholder, View.VISIBLE)
        v.setTextViewText(R.id.living_frame_placeholder, text)
        v.setContentDescription(R.id.living_frame_root, text)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, v)
        true
    } catch (_: Exception) {
        false
    }

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
        val displayFps = LivingFrameFps.snap(frame.fps)
        val resolution = LivingFrameResolution.snap(frame.resolutionPercent)
        val outW = (w * resolution / 100f).roundToInt().coerceAtLeast(48)
        val outH = (h * resolution / 100f).roundToInt().coerceAtLeast(48)

        // Speed and display rate are resolved into an explicit display
        // sequence. The launcher flipper only understands a fixed interval,
        // so this is how ×0.5 slow motion and ×2 fast loops stay honest.
        val sequence = if (animated) {
            LivingFrameEngine.displaySequence(
                frame.frameCount,
                frame.sourceFps,
                displayFps,
                frame.speed,
                frame.loopMode
            )
        } else {
            listOf(
                if (frame.isSlideshow) {
                    LivingFramesSlideshow.currentIndex(context, appWidgetId, frame)
                } else {
                    0
                }
            )
        }

        var budget = bitmapBudget(context)
        var error = "The launcher rejected the update."

        // Streaming is the default for every animation, not a quality tier.
        // The adapter fetches one frame at a time, so the launcher's binder
        // budget cannot force a long clip down to a handful of pixels.
        if (animated) {
            try {
                val views = RemoteViews(
                    context.packageName,
                    streamingLayout(displayFps)
                )
                val adapter = Intent(context, LivingFramesRemoteViewsService::class.java).apply {
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                    data = livingFramesAdapterUri(appWidgetId, frame.id)
                }
                views.setRemoteAdapter(R.id.living_frame_flip, adapter)
                applyChrome(context, views, appWidgetId, frame)
                mgr.updateAppWidget(appWidgetId, views)
                mgr.notifyAppWidgetViewDataChanged(appWidgetId, R.id.living_frame_flip)
                writeInfo(
                    context, appWidgetId,
                    WidgetInfo(
                        outW, outH, sequence.size, 0L, resolution, false, 1,
                        displayFps, System.currentTimeMillis(), null,
                        streamed = true, speed = frame.speed
                    )
                )
                return@withContext true
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // Some launchers reject collection widgets. Fall through to
                // the single-RemoteViews path instead of showing nothing.
                val message = t.message
                if (!message.isNullOrBlank()) error = message.take(90)
            }
        }

        for (attempt in 0 until 4) {
            // The sequence already contains speed resampling and boomerang
            // order, so the planner treats it as a plain loop. Dropping every
            // Nth display frame keeps the real-time loop length.
            val plan = LivingFrameEngine.plan(
                if (animated) sequence.size else 1, outW, outH,
                LivingFrameRenderer.isOpaque(frame), displayFps, budget,
                LivingFrameLoopMode.LOOP
            )
            val indices = if (animated) {
                sequence.filterIndexed { index, _ -> index % plan.step == 0 }
            } else {
                sequence
            }
            val need = maxOf(plan.outW, plan.outH)
            val bitmaps = ArrayList<Bitmap>(indices.size)
            try {
                for (i in indices) {
                    val src = repo.decodeAtLeast(frame.id, i, need) ?: continue
                    try {
                        bitmaps.add(
                            LivingFrameRenderer.render(
                                src, frame, plan.outW, plan.outH,
                                density * plan.scale, plan.rgb565
                            )
                        )
                    } finally {
                        runCatching { src.recycle() }
                    }
                }
                if (bitmaps.size < indices.size) {
                    // A decode can fail because this attempt's bitmaps were
                    // too large; retry smaller. A genuinely corrupt frame also
                    // ends in a clear placeholder after the retries.
                    error = "Some frames are missing. Re-import this widget."
                    budget /= 2
                    continue
                }
                val views = if (animated && bitmaps.size > 1) {
                    val v = RemoteViews(
                        context.packageName,
                        flipperLayout(plan.effectiveFps)
                    )
                    v.removeAllViews(R.id.living_frame_flip)
                    bitmaps.forEach { bitmap ->
                        val child = RemoteViews(
                            context.packageName,
                            R.layout.living_frames_frame
                        )
                        child.setImageViewBitmap(R.id.living_frame_item, bitmap)
                        v.addView(R.id.living_frame_flip, child)
                    }
                    v
                } else {
                    val v = RemoteViews(
                        context.packageName,
                        R.layout.living_frames_widget_static
                    )
                    v.setImageViewBitmap(R.id.living_frame_static, bitmaps[0])
                    v.setViewVisibility(R.id.living_frame_static, View.VISIBLE)
                    v.setViewVisibility(R.id.living_frame_placeholder, View.GONE)
                    v
                }
                applyChrome(context, views, appWidgetId, frame)
                mgr.updateAppWidget(appWidgetId, views)
                writeInfo(
                    context, appWidgetId,
                    WidgetInfo(
                        plan.outW, plan.outH, bitmaps.size,
                        plan.bytes,
                        (plan.outW * 100f / w).roundToInt().coerceIn(1, 100),
                        plan.rgb565, plan.step, plan.effectiveFps,
                        System.currentTimeMillis(), null,
                        streamed = false, speed = frame.speed
                    )
                )
                return@withContext true
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val message = t.message
                if (!message.isNullOrBlank()) error = message.take(90)
                budget /= 2
            } finally {
                bitmaps.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            }
        }
        writeInfo(
            context, appWidgetId,
            WidgetInfo(
                outW, outH, 0, 0, resolution, false, 1, displayFps,
                System.currentTimeMillis(), error, streamed = false, speed = frame.speed
            )
        )
        pushPlaceholder(
            context, appWidgetId,
            "Couldn't render this Living Widget. Try resizing it or re-adding it."
        )
        false
    }

    /** Binds (if needed) and pushes one widget. The single entry point for every refresh path. */
    suspend fun refresh(
        context: Context,
        repo: LivingFramesRepository,
        appWidgetId: Int
    ): Boolean {
        repo.ensureLoaded()
        var frameId = repo.boundFrame(appWidgetId)
        if (frameId == LivingFramesRepository.DELETED_WIDGET_BINDING) {
            LivingFramesSlideshow.cancel(context, appWidgetId)
            pushPlaceholder(
                context, appWidgetId,
                "This widget was deleted. Long-press to remove it."
            )
            return false
        }
        if (frameId == null) {
            val pendingPin = peekPendingPinFrame(context)
            val pending = pendingPin?.let { repo.get(it) }
            // Never attach "the newest set" to an arbitrary widget: that made
            // pinning two sets non-deterministic. A single-set library is a
            // safe migration fallback; otherwise the config activity binds it.
            // When a pin is pending, never substitute a different set.
            val pick = when {
                pending != null -> pending
                pendingPin != null -> null
                else -> repo.snapshot().singleOrNull()
            }
            if (pick == null) {
                val text = when {
                    repo.isLoadFailed() ->
                        "Couldn't read the Living Widget library. Your files are safe."
                    pendingPin != null -> {
                        // The set was removed while the pin request was in
                        // flight. Drop the stale request and ask for a new one.
                        clearPendingPin(context)
                        "That set was deleted. Choose another one in Toolz."
                    }
                    else -> "Choose a Living Widget set in Toolz"
                }
                pushPlaceholder(context, appWidgetId, text)
                return false
            }
            if (!repo.bindWidget(appWidgetId, pick.id)) {
                pushPlaceholder(context, appWidgetId, "Couldn't attach this widget. Try re-adding it.")
                return false
            }
            if (pendingPin != null) clearPendingPin(context)
            frameId = pick.id
        }
        val frame = repo.get(frameId)
        if (frame == null) {
            if (repo.isLoadFailed()) {
                // A corrupt/unreadable JSON file must not destroy the
                // association between this widget and its set. Keep the
                // binding; the next successful load restores it.
                pushPlaceholder(
                    context, appWidgetId,
                    "Couldn't read the Living Widget library. Your files are safe."
                )
                return false
            }
            // Keep a tombstone so the single-set migration fallback below
            // cannot later attach this widget to a different set.
            repo.markWidgetDeleted(appWidgetId)
            LivingFramesSlideshow.cancel(context, appWidgetId)
            pushPlaceholder(
                context, appWidgetId,
                "This widget was deleted. Long-press to remove it."
            )
            return false
        }
        val ok = push(context, repo, appWidgetId, frame)
        if (frame.isSlideshow) {
            LivingFramesSlideshow.ensureScheduled(
                context, appWidgetId, frame.slideshowIntervalMin
            )
        } else {
            // Reconfiguration from a slideshow to an animation/photo must not
            // leave a stale one-shot alarm behind.
            LivingFramesSlideshow.cancel(context, appWidgetId)
        }
        return ok
    }

    /** Slideshow tick or tap-next: move the cursor, push the photo, arm the next alarm. */
    suspend fun advanceSlideshow(
        context: Context,
        repo: LivingFramesRepository,
        appWidgetId: Int
    ): Boolean {
        repo.ensureLoaded()
        val frame = repo.boundFrame(appWidgetId)?.let { repo.get(it) } ?: return false
        if (!frame.isSlideshow) return false
        LivingFramesSlideshow.advance(context, appWidgetId, frame)
        val ok = push(context, repo, appWidgetId, frame)
        LivingFramesSlideshow.schedule(context, appWidgetId, frame.slideshowIntervalMin)
        return ok
    }

    suspend fun refreshFrame(
        context: Context,
        repo: LivingFramesRepository,
        frameId: String
    ) {
        repo.boundWidgetIds(frameId).forEach { refresh(context, repo, it) }
    }

    // -- per-widget render report (shown in the tool) -------------------------

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
        val error: String?,
        /** True when the launcher renders frames through the adapter service. */
        val streamed: Boolean = false,
        val speed: Float = 1f
    ) {
        val reduced: Boolean
            get() = !streamed && (scalePct in 1..99 || step > 1 || rgb565)
    }

    private fun infoPrefs(context: Context) =
        context.getSharedPreferences("living_frames_widget_info", Context.MODE_PRIVATE)

    private fun writeInfo(context: Context, id: Int, i: WidgetInfo) {
        try {
            infoPrefs(context).edit().putString(
                "i_$id",
                listOf(
                    i.widthPx, i.heightPx, i.frames, i.bytes, i.scalePct,
                    if (i.rgb565) 1 else 0, i.step, i.fps, i.atMs,
                    (i.error ?: "").replace('|', '/'),
                    if (i.streamed) 1 else 0, i.speed
                ).joinToString("|")
            ).apply()
        } catch (_: Exception) {
        }
    }

    fun readInfo(context: Context, id: Int): WidgetInfo? = try {
        val p = infoPrefs(context).getString("i_$id", null)?.split("|")
        if (p == null || p.size < 10) {
            null
        } else {
            WidgetInfo(
                p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toLong(),
                p[4].toInt(), p[5] == "1", p[6].toInt(), p[7].toInt(),
                p[8].toLong(), p[9].ifBlank { null },
                streamed = p.getOrNull(10) == "1",
                speed = p.getOrNull(11)?.toFloatOrNull() ?: 1f
            )
        }
    } catch (_: Exception) {
        null
    }

    fun clearInfo(context: Context, id: Int) {
        runCatching { infoPrefs(context).edit().remove("i_$id").apply() }
    }

    // -- pin flow -------------------------------------------------------------

    private const val PIN_PREFS = "living_frames_pin"
    private const val PIN_TTL_MS = 120_000L

    fun setPendingPinFrame(context: Context, frameId: String) {
        runCatching {
            context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE).edit()
                .putString("id", frameId)
                .putLong("at", System.currentTimeMillis())
                .apply()
        }
    }

    /** Non-destructive read; the callback may arrive after the launcher UI settles. */
    fun peekPendingPinFrame(context: Context): String? = try {
        val p = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE)
        val id = p.getString("id", null)
        val at = p.getLong("at", 0L)
        if (id == null || System.currentTimeMillis() - at > PIN_TTL_MS) null else id
    } catch (_: Exception) {
        null
    }

    fun clearPendingPin(context: Context) {
        runCatching {
            context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply()
        }
    }

    @Deprecated("Use peekPendingPinFrame + clearPendingPin")
    fun takePendingPinFrame(context: Context): String? {
        val id = peekPendingPinFrame(context)
        if (id != null) clearPendingPin(context)
        return id
    }

    fun requestPin(context: Context, frame: LivingFrame): Boolean {
        val ok = try {
            val mgr = context.getSystemService(AppWidgetManager::class.java)
            if (mgr == null || !mgr.isRequestPinAppWidgetSupported) {
                false
            } else {
                val callback = PendingIntent.getBroadcast(
                    context, frame.id.hashCode(),
                    Intent(context, LivingFramesActionReceiver::class.java).apply {
                        action = LivingFramesWidgetReceiver.ACTION_PINNED
                        putExtra(LivingFramesWidgetReceiver.EXTRA_FRAME_ID, frame.id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                mgr.requestPinAppWidget(
                    ComponentName(context, LivingFramesWidgetReceiver::class.java),
                    null,
                    callback
                )
            }
        } catch (_: Exception) {
            false
        }
        if (!ok) clearPendingPin(context)
        return ok
    }

    /**
     * Master toggle for the 4 Glance app widgets (Music / Pomodoro / Toolbar /
     * Stopwatch). Living widgets are never affected. Kept here so existing
     * callers keep compiling.
     */
    fun setAppWidgetsComponentEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        listOf(
            "com.frerox.toolz.widget.glance.MusicWidgetReceiver",
            "com.frerox.toolz.widget.glance.PomodoroWidgetReceiver",
            "com.frerox.toolz.widget.glance.SearchBarWidgetReceiver",
            "com.frerox.toolz.widget.glance.TimerWidgetReceiver"
        ).forEach { cls ->
            try {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, cls),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {
            }
        }
    }
}
