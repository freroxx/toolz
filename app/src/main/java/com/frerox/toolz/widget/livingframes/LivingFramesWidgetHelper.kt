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
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameLoopTransition
import com.frerox.toolz.data.livingframes.LivingFrameQuality
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
 * change, resize, slideshow tick, pause/resume.
 *
 * Static ViewFlipper playback is preferred whenever a clip fits the
 * RemoteViews bitmap budget. All children arrive in the same push, so the
 * launcher can never miss a frame and show frame zero; the adapter path is
 * only used for long clips or budgets too small to render a sharp loop.
 */
object LivingFramesWidgetHelper {

    const val ACTION_NEXT = "com.frerox.toolz.LIVING_FRAME_NEXT"
    const val ACTION_SLIDE_ADVANCE = "com.frerox.toolz.LIVING_FRAME_SLIDE"
    const val ACTION_PAUSE_PLAY = "com.frerox.toolz.LIVING_FRAME_PAUSE_PLAY"
    const val EXTRA_WIDGET_ID = "living_frame_widget_id"
    const val EXTRA_STEP_FROM = "living_frame_step_from"
    const val EXTRA_STEP_TO = "living_frame_step_to"
    const val EXTRA_STEP_MIX = "living_frame_step_mix"
    const val EXTRA_START_INDEX = "living_frame_start_index"

    /**
     * Bump when rendering changes in a way that invalidates already-pushed
     * RemoteViews. The hourly watchdog re-renders older widgets once.
     */
    const val RENDER_VERSION = 3

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
     * This is now the preferred path for every loop that fits the bitmap
     * budget; the adapter path is the fallback for long clips.
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

    /**
     * PAUSE_PLAY is only exact on the static path: collection children cannot
     * carry their own click PendingIntents, so a streaming widget falls back
     * to opening the editor instead of pausing on an unknown frame.
     */
    private fun effectiveTap(frame: LivingFrame, allowPause: Boolean): LivingFrameTapAction {
        val action = frame.tapAction
        return when {
            action == LivingFrameTapAction.NEXT_FRAME && !frame.isSlideshow ->
                LivingFrameTapAction.OPEN_PREVIEW
            action == LivingFrameTapAction.PAUSE_PLAY && !allowPause ->
                LivingFrameTapAction.OPEN_PREVIEW
            else -> action
        }
    }

    private fun tapIntent(
        context: Context,
        appWidgetId: Int,
        frame: LivingFrame,
        allowPause: Boolean
    ): PendingIntent? {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return when (val action = effectiveTap(frame, allowPause)) {
            LivingFrameTapAction.NOTHING,
            LivingFrameTapAction.PAUSE_PLAY -> null
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

    /**
     * Per-child pause toggle. Every child gets a unique data URI, because
     * PendingIntent identity ignores extras: reusing one PendingIntent would
     * make every frame report the last-created step.
     */
    private fun pauseIntent(
        context: Context,
        appWidgetId: Int,
        step: LivingFrameEngine.DisplayStep,
        index: Int
    ): PendingIntent {
        val milli = (step.mix.coerceIn(0f, 1f) * 1000f).roundToInt()
        val intent = Intent(context, LivingFramesActionReceiver::class.java).apply {
            action = ACTION_PAUSE_PLAY
            data = Uri.parse(
                "toolz://living-frames/pause/$appWidgetId/$index/${step.from}/${step.to}/$milli"
            )
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
            putExtra(EXTRA_STEP_FROM, step.from)
            putExtra(EXTRA_STEP_TO, step.to)
            putExtra(EXTRA_STEP_MIX, milli)
        }
        return PendingIntent.getBroadcast(
            context,
            appWidgetId * 4096 + (index.coerceAtLeast(0) % 4096),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun applyChrome(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        frame: LivingFrame,
        rootTap: PendingIntent?
    ) {
        if (frame.showCaption) {
            views.setTextViewText(R.id.living_frame_caption, frame.name)
            views.setViewVisibility(R.id.living_frame_caption, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.living_frame_caption, View.GONE)
        }
        if (rootTap != null) {
            views.setOnClickPendingIntent(R.id.living_frame_root, rootTap)
        }
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
        // Must match LivingFramesRemoteViewsService: the sequence is resampled
        // for this exact flipper rate, and the layout below is chosen from it.
        val displayFps = if (animated) {
            LivingFrameEngine.playbackFps(frame)
        } else {
            LivingFrameFps.snap(frame.fps)
        }
        val resolution = LivingFrameResolution.snap(frame.resolutionPercent)
        val outW = (w * resolution / 100f).roundToInt().coerceAtLeast(48)
        val outH = (h * resolution / 100f).roundToInt().coerceAtLeast(48)
        val opaque = LivingFrameRenderer.isOpaque(frame)

        // Pause only exists while it is the configured action on an animation.
        // Changing the action (or deleting the set) must not leave a widget
        // frozen with no visible way to resume.
        if (!animated || frame.tapAction != LivingFrameTapAction.PAUSE_PLAY) {
            LivingFramesPause.clear(context, appWidgetId)
        }

        val pausePoint = if (animated) {
            LivingFramesPause.point(context, appWidgetId)
        } else {
            null
        }
        if (pausePoint != null) {
            return@withContext pushPaused(
                context, repo, appWidgetId, frame, pausePoint,
                w, h, outW, outH, density, opaque, displayFps
            )
        }

        if (!animated) {
            val index = if (frame.isSlideshow) {
                LivingFramesSlideshow.currentIndex(context, appWidgetId, frame)
            } else {
                0
            }
            val ok = pushStill(
                context, repo, appWidgetId, frame, index,
                w, h, outW, outH, density, opaque
            )
            if (ok) LivingFramesPause.clearResume(context, appWidgetId)
            return@withContext ok
        }

        val baseSteps = LivingFrameEngine.displaySteps(frame, displayFps)
        val resume = LivingFramesPause.resumePoint(context, appWidgetId)
        // Streaming always runs at the configured rate, so its resume position
        // is independent of whatever rate the static planner chooses below.
        val streamStart = if (resume != null) {
            LivingFrameEngine.nearestStepIndex(baseSteps, resume.from, resume.to, resume.mix)
        } else {
            0
        }

        var budget = bitmapBudget(context)
        var error = "The launcher rejected the update."

        // Static-first: one push carries the whole loop, so the launcher has
        // every frame in memory and cannot miss one and fall back to frame 0.
        // planStatic returns null only when the clip is too long or would need
        // to be blurred below 40% to fit; those stream instead.
        for (attempt in 0 until 4) {
            val plan = LivingFrameEngine.planStatic(
                frameCount = frame.frameCount,
                sourceFps = frame.sourceFps,
                speed = frame.speed,
                requestedFps = displayFps,
                widthPx = outW,
                heightPx = outH,
                opaque = opaque,
                budgetBytes = budget,
                loopMode = frame.loopMode,
                transition = frame.loopTransition
            ) ?: break
            // The planner may trade fps for sharpness. The children and the
            // flipper layout must use the rate it actually chose, or the loop
            // plays at the wrong speed and the byte budget counts the wrong
            // number of frames.
            val planSteps = if (plan.effectiveFps == displayFps) {
                baseSteps
            } else {
                LivingFrameEngine.displaySteps(frame, plan.effectiveFps)
            }
            val planStart = if (resume != null) {
                LivingFrameEngine.nearestStepIndex(
                    planSteps, resume.from, resume.to, resume.mix
                )
            } else {
                0
            }
            val steps = LivingFrameEngine.rotateSteps(planSteps, planStart)
            val bitmaps = try {
                renderSteps(repo, frame, steps, plan, w, h, density)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val message = t.message
                if (!message.isNullOrBlank()) error = message.take(90)
                null
            }
            if (bitmaps == null) {
                // A decode failure is usually an out-of-memory attempt; retry
                // with half the bitmap budget. A corrupt frame ends in the
                // clear placeholder after the retries.
                error = "Some frames are missing. Re-import this widget."
                budget /= 2
                continue
            }
            try {
                val pauseEnabled = frame.tapAction == LivingFrameTapAction.PAUSE_PLAY
                val views = buildFlipper(
                    context, appWidgetId, frame, steps, bitmaps, plan.effectiveFps,
                    pauseEnabled
                )
                applyChrome(
                    context, views, appWidgetId, frame,
                    rootTap = if (pauseEnabled) null else tapIntent(
                        context, appWidgetId, frame, allowPause = false
                    )
                )
                mgr.updateAppWidget(appWidgetId, views)
                writeInfo(
                    context, appWidgetId,
                    WidgetInfo(
                        plan.outW, plan.outH, steps.size,
                        plan.bytes,
                        (plan.outW * 100f / w).roundToInt().coerceIn(1, 100),
                        plan.rgb565, plan.step, plan.effectiveFps,
                        System.currentTimeMillis(), null,
                        streamed = false, speed = frame.speed,
                        paused = false, renderVersion = RENDER_VERSION
                    )
                )
                LivingFramesPause.clearResume(context, appWidgetId)
                return@withContext true
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val message = t.message
                if (!message.isNullOrBlank()) error = message.take(90)
                budget /= 2
            } finally {
                bitmaps.values.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            }
        }

        // Static was impossible or rejected. Streaming still beats a blank
        // widget, and its smaller frames plus last-frame loading view remove
        // the worst of the old frame-zero flash.
        val streamError = tryStreaming(
            context, repo, appWidgetId, frame, w, h, outW, outH, density, opaque,
            displayFps, baseSteps.size, streamStart,
            baseSteps[streamStart.coerceIn(0, baseSteps.lastIndex)]
        )
        if (streamError == null) {
            LivingFramesPause.clearResume(context, appWidgetId)
            return@withContext true
        }
        if (streamError.isNotBlank()) error = streamError

        writeInfo(
            context, appWidgetId,
            WidgetInfo(
                outW, outH, 0, 0, resolution, false, 1, displayFps,
                System.currentTimeMillis(), error, streamed = false,
                speed = frame.speed, paused = false, renderVersion = RENDER_VERSION
            )
        )
        pushPlaceholder(
            context, appWidgetId,
            "Couldn't render this Living Widget. Try resizing it or re-adding it."
        )
        false
    }

    /** A still photo, the current slideshow photo, or a paused animation. */
    private suspend fun pushStill(
        context: Context,
        repo: LivingFramesRepository,
        appWidgetId: Int,
        frame: LivingFrame,
        index: Int,
        w: Int,
        h: Int,
        outW: Int,
        outH: Int,
        density: Float,
        opaque: Boolean
    ): Boolean {
        var budget = bitmapBudget(context)
        for (attempt in 0 until 4) {
            val plan = LivingFrameEngine.planStill(outW, outH, opaque, budget)
            val densityX = LivingFrameEngine.densityScale(density, plan.outW, w)
            val densityY = LivingFrameEngine.densityScale(density, plan.outH, h)
            val bitmap = try {
                renderSingle(repo, frame, index, plan, densityX, densityY)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                null
            } ?: return false
            try {
                val views = RemoteViews(
                    context.packageName, R.layout.living_frames_widget_static
                )
                views.setImageViewBitmap(R.id.living_frame_static, bitmap)
                views.setViewVisibility(R.id.living_frame_static, View.VISIBLE)
                views.setViewVisibility(R.id.living_frame_placeholder, View.GONE)
                applyChrome(
                    context, views, appWidgetId, frame,
                    tapIntent(context, appWidgetId, frame, allowPause = false)
                )
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
                writeInfo(
                    context, appWidgetId,
                    WidgetInfo(
                        plan.outW, plan.outH, 1, plan.bytes,
                        (plan.outW * 100f / w).roundToInt().coerceIn(1, 100),
                        plan.rgb565, 1, LivingFrameFps.snap(frame.fps),
                        System.currentTimeMillis(), null,
                        streamed = false, speed = frame.speed,
                        paused = false, renderVersion = RENDER_VERSION
                    )
                )
                return true
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                budget /= 2
            } finally {
                runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
            }
        }
        return false
    }

    /**
     * Exact pause: one pre-rendered image of the tapped display step, with no
     * flipper running. Resuming rotates the loop so that same step becomes
     * child 0, which means no jump back to the first frame.
     */
    private suspend fun pushPaused(
        context: Context,
        repo: LivingFramesRepository,
        appWidgetId: Int,
        frame: LivingFrame,
        point: LivingFramesPause.Point,
        w: Int,
        h: Int,
        outW: Int,
        outH: Int,
        density: Float,
        opaque: Boolean,
        displayFps: Int
    ): Boolean {
        val steps = LivingFrameEngine.displaySteps(frame, displayFps)
        // Reproduce the exact step the tap captured, including the seam
        // blend weight. Snapping to a nearby child would nudge the frozen
        // picture even when nothing changed.
        val exact = LivingFrameEngine.DisplayStep(
            point.from, point.to, point.mix.coerceIn(0f, 1f)
        )
        val step = if (
            exact.from in 0 until frame.frameCount &&
            exact.to in 0 until frame.frameCount
        ) {
            exact
        } else {
            steps.getOrNull(
                LivingFrameEngine.nearestStepIndex(
                    steps, point.from, point.to, point.mix
                )
            ) ?: LivingFrameEngine.DisplayStep(0, 0, 0f)
        }
        val index = LivingFrameEngine.nearestStepIndex(
            steps, step.from, step.to, step.mix
        )

        var budget = bitmapBudget(context)
        // Prefer the same size the animation uses so resuming cannot change
        // sharpness; fall back to a still plan when the loop itself streams.
        for (attempt in 0 until 4) {
            val plan = LivingFrameEngine.planStatic(
                frameCount = frame.frameCount,
                sourceFps = frame.sourceFps,
                speed = frame.speed,
                requestedFps = displayFps,
                widthPx = outW,
                heightPx = outH,
                opaque = opaque,
                budgetBytes = budget,
                loopMode = frame.loopMode,
                transition = frame.loopTransition
            ) ?: LivingFrameEngine.planStill(outW, outH, opaque, budget)
            val densityX = LivingFrameEngine.densityScale(density, plan.outW, w)
            val densityY = LivingFrameEngine.densityScale(density, plan.outH, h)
            val bitmap = try {
                renderStep(repo, frame, step, plan, densityX, densityY)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                null
            } ?: return false
            try {
                val views = RemoteViews(
                    context.packageName, R.layout.living_frames_widget_static
                )
                views.setImageViewBitmap(R.id.living_frame_static, bitmap)
                views.setViewVisibility(R.id.living_frame_static, View.VISIBLE)
                views.setViewVisibility(R.id.living_frame_placeholder, View.GONE)
                applyChrome(
                    context, views, appWidgetId, frame,
                    pauseIntent(context, appWidgetId, step, index)
                )
                AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
                writeInfo(
                    context, appWidgetId,
                    WidgetInfo(
                        plan.outW, plan.outH, 1, plan.bytes,
                        (plan.outW * 100f / w).roundToInt().coerceIn(1, 100),
                        plan.rgb565, plan.step, displayFps,
                        System.currentTimeMillis(), null,
                        streamed = false, speed = frame.speed,
                        paused = true, renderVersion = RENDER_VERSION
                    )
                )
                return true
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                budget /= 2
            } finally {
                runCatching { if (!bitmap.isRecycled) bitmap.recycle() }
            }
        }
        return false
    }

    /**
     * Render every display child (including seam blends) once, keyed by step.
     * Repeated display steps share one bitmap, which keeps large loops from
     * decoding the same source frame multiple times.
     */
    private suspend fun renderSteps(
        repo: LivingFramesRepository,
        frame: LivingFrame,
        steps: List<LivingFrameEngine.DisplayStep>,
        plan: LivingFrameEngine.RenderPlan,
        widgetW: Int,
        widgetH: Int,
        density: Float
    ): Map<LivingFrameEngine.DisplayStep, Bitmap>? {
        val densityX = LivingFrameEngine.densityScale(density, plan.outW, widgetW)
        val densityY = LivingFrameEngine.densityScale(density, plan.outH, widgetH)
        val out = LinkedHashMap<LivingFrameEngine.DisplayStep, Bitmap>(steps.size)
        try {
            for (step in steps) {
                if (out.containsKey(step)) continue
                val bitmap = renderStep(repo, frame, step, plan, densityX, densityY)
                if (bitmap == null) {
                    out.values.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
                    return null
                }
                out[step] = bitmap
            }
        } catch (t: Throwable) {
            // An OOM partway through a long loop must not strand the hundreds
            // of bitmaps already rendered; the caller retries with a smaller
            // plan immediately.
            out.values.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            throw t
        }
        return out
    }

    private suspend fun renderStep(
        repo: LivingFramesRepository,
        frame: LivingFrame,
        step: LivingFrameEngine.DisplayStep,
        plan: LivingFrameEngine.RenderPlan,
        densityX: Float,
        densityY: Float
    ): Bitmap? {
        val need = LivingFrameEngine.requiredSourceSide(
            plan.outW, plan.outH, frame.width, frame.height, frame.fit
        )
        if (!step.blended) {
            val src = repo.decodeAtLeast(frame.id, step.from, need) ?: return null
            return try {
                LivingFrameRenderer.render(
                    src, frame, plan.outW, plan.outH,
                    densityX, densityY, plan.rgb565
                )
            } finally {
                runCatching { if (!src.isRecycled) src.recycle() }
            }
        }
        val from = repo.decodeAtLeast(frame.id, step.from, need) ?: return null
        val to = try {
            repo.decodeAtLeast(frame.id, step.to, need)
        } catch (t: Throwable) {
            runCatching { if (!from.isRecycled) from.recycle() }
            throw t
        } ?: run {
            runCatching { if (!from.isRecycled) from.recycle() }
            return null
        }
        return try {
            LivingFrameRenderer.renderBlend(
                from = from,
                to = to,
                frame = frame,
                outW = plan.outW,
                outH = plan.outH,
                densityX = densityX,
                densityY = densityY,
                mix = step.mix,
                rgb565 = plan.rgb565,
                soft = frame.loopTransition == LivingFrameLoopTransition.BLUR
            )
        } finally {
            runCatching { if (!from.isRecycled) from.recycle() }
            runCatching { if (!to.isRecycled) to.recycle() }
        }
    }

    private suspend fun renderSingle(
        repo: LivingFramesRepository,
        frame: LivingFrame,
        index: Int,
        plan: LivingFrameEngine.RenderPlan,
        densityX: Float,
        densityY: Float
    ): Bitmap? = renderStep(
        repo, frame, LivingFrameEngine.DisplayStep(index, index, 0f),
        plan, densityX, densityY
    )

    private fun buildFlipper(
        context: Context,
        appWidgetId: Int,
        frame: LivingFrame,
        steps: List<LivingFrameEngine.DisplayStep>,
        bitmaps: Map<LivingFrameEngine.DisplayStep, Bitmap>,
        fps: Int,
        pauseEnabled: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, flipperLayout(fps))
        views.removeAllViews(R.id.living_frame_flip)
        steps.forEachIndexed { index, step ->
            val child = RemoteViews(context.packageName, R.layout.living_frames_frame)
            val bitmap = bitmaps[step] ?: return@forEachIndexed
            child.setImageViewBitmap(R.id.living_frame_item, bitmap)
            if (pauseEnabled) {
                child.setOnClickPendingIntent(
                    R.id.living_frame_item,
                    pauseIntent(context, appWidgetId, step, index)
                )
            }
            views.addView(R.id.living_frame_flip, child)
        }
        return views
    }

    /**
     * Returns null on success, or the launcher's error text (possibly blank)
     * when the collection widget was rejected.
     */
    private suspend fun tryStreaming(
        context: Context,
        repo: LivingFramesRepository,
        appWidgetId: Int,
        frame: LivingFrame,
        widgetW: Int,
        widgetH: Int,
        outW: Int,
        outH: Int,
        density: Float,
        opaque: Boolean,
        displayFps: Int,
        frames: Int,
        startIndex: Int,
        baseStep: LivingFrameEngine.DisplayStep
    ): String? {
        var base: Bitmap? = null
        var error = ""
        try {
            val views = RemoteViews(
                context.packageName,
                streamingLayout(displayFps)
            )
            val adapter = Intent(context, LivingFramesRemoteViewsService::class.java).apply {
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
                putExtra(EXTRA_START_INDEX, startIndex)
                // The URI also carries the start index: the host can reuse a
                // service/factory when only the extras differ, and a resume
                // must not be served from the pre-pause factory.
                data = livingFramesAdapterUri(appWidgetId, frame.id, startIndex)
            }
            views.setRemoteAdapter(R.id.living_frame_flip, adapter)
            // Some launchers draw a collection widget before the adapter's
            // first child arrives. Put the first rendered frame behind the
            // flipper so that gap is the animation's own first frame, not a
            // blank/loading flash. Translucent sets are skipped: stacking the
            // same frame behind itself would silently raise opacity.
            if (frame.opacity >= 0.999f) {
                val plan = LivingFrameEngine.planStill(
                    outW, outH, opaque, bitmapBudget(context)
                )
                val densityX = LivingFrameEngine.densityScale(density, plan.outW, widgetW)
                val densityY = LivingFrameEngine.densityScale(density, plan.outH, widgetH)
                base = try {
                    renderStep(repo, frame, baseStep, plan, densityX, densityY)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    // Streaming can still work without the static base.
                    null
                }
                if (base != null) {
                    views.setImageViewBitmap(R.id.living_frame_static, base)
                    views.setViewVisibility(R.id.living_frame_static, View.VISIBLE)
                }
            }
            applyChrome(
                context, views, appWidgetId, frame,
                tapIntent(context, appWidgetId, frame, allowPause = false)
            )
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
            AppWidgetManager.getInstance(context)
                .notifyAppWidgetViewDataChanged(appWidgetId, R.id.living_frame_flip)
            writeInfo(
                context, appWidgetId,
                WidgetInfo(
                    outW, outH, frames, 0L,
                    LivingFrameResolution.snap(frame.resolutionPercent),
                    false, 1, displayFps,
                    System.currentTimeMillis(), null,
                    streamed = true, speed = frame.speed,
                    paused = false, renderVersion = RENDER_VERSION
                )
            )
            return null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Some launchers reject collection widgets. Fall through to the
            // single-RemoteViews path instead of showing nothing.
            error = t.message?.take(90).orEmpty()
        } finally {
            base?.let { runCatching { if (!it.isRecycled) it.recycle() } }
        }
        return error
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
            LivingFramesPause.clear(context, appWidgetId)
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
        val speed: Float = 1f,
        val paused: Boolean = false,
        /** Renderer generation; older widgets are refreshed by the watchdog. */
        val renderVersion: Int = RENDER_VERSION
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
                    if (i.streamed) 1 else 0, i.speed,
                    if (i.paused) 1 else 0, i.renderVersion
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
                speed = p.getOrNull(11)?.toFloatOrNull() ?: 1f,
                paused = p.getOrNull(12) == "1",
                // Old records have no version: treat them as stale so the
                // watchdog re-renders the fixed corners once.
                renderVersion = p.getOrNull(13)?.toIntOrNull() ?: 0
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

/**
 * Per-widget pause point. Kept in SharedPreferences because the tap arrives
 * in a broadcast receiver, while the actual render runs in WorkManager.
 */
internal object LivingFramesPause {

    data class Point(val from: Int, val to: Int, val mix: Float)

    private const val PREFS = "living_frames_pause"
    private const val PAUSED = "p_"
    private const val RESUME = "r_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun point(context: Context, id: Int): Point? = read(context, PAUSED + id)

    fun resumePoint(context: Context, id: Int): Point? = read(context, RESUME + id)

    fun pause(context: Context, id: Int, point: Point) {
        runCatching {
            prefs(context).edit().putString(PAUSED + id, encode(point)).apply()
        }
    }

    fun resume(context: Context, id: Int): Point? {
        val point = point(context, id) ?: return null
        runCatching {
            prefs(context).edit()
                .remove(PAUSED + id)
                .putString(RESUME + id, encode(point))
                .apply()
        }
        return point
    }

    fun clearResume(context: Context, id: Int) {
        runCatching { prefs(context).edit().remove(RESUME + id).apply() }
    }

    fun clear(context: Context, id: Int) {
        runCatching {
            prefs(context).edit().remove(PAUSED + id).remove(RESUME + id).apply()
        }
    }

    private fun read(context: Context, key: String): Point? = try {
        val parts = prefs(context).getString(key, null)?.split("|") ?: return null
        if (parts.size < 3) {
            null
        } else {
            Point(
                parts[0].toInt().coerceAtLeast(0),
                parts[1].toInt().coerceAtLeast(0),
                (parts[2].toInt().coerceIn(0, 1000)) / 1000f
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun encode(point: Point): String =
        "${point.from}|${point.to}|${(point.mix.coerceIn(0f, 1f) * 1000f).roundToInt()}"
}
