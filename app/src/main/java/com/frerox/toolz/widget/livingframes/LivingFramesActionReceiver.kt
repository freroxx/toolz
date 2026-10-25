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

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameFps
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.worker.LivingFramesRenderWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Non-exported target for the PendingIntents that handle taps, slideshow
 * ticks and the pin callback. Keeping it separate stops other apps from
 * broadcasting ACTION_NEXT / ACTION_PINNED into the exported widget provider.
 */
class LivingFramesActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun repo(context: Context): LivingFramesRepository? = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext, LivingFramesEntryPoint::class.java
        ).livingFramesRepository()
    } catch (_: Exception) {
        null
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LivingFramesWidgetReceiver.ACTION_PINNED -> handlePin(context, intent)
            LivingFramesWidgetHelper.ACTION_NEXT,
            LivingFramesWidgetHelper.ACTION_SLIDE_ADVANCE -> handleAdvance(context, intent)
            LivingFramesWidgetHelper.ACTION_PAUSE_PLAY -> handlePausePlay(context, intent)
        }
    }

    /**
     * Exact pause/resume. The tapped display step arrives in the intent, so
     * the frozen frame is the one the user saw, not the animation's first
     * frame. Resuming keeps that step as the new loop start.
     */
    private fun handlePausePlay(context: Context, intent: Intent) {
        val id = intent.getIntExtra(LivingFramesWidgetHelper.EXTRA_WIDGET_ID, -1)
        if (id <= 0) return
        val result = goAsync()
        scope.launch {
            try {
                val r = repo(context) ?: return@launch
                r.ensureLoaded()
                val frame = r.boundFrame(id)?.let { r.get(it) } ?: return@launch
                // A stale tap from an old RemoteViews must not freeze a widget
                // that no longer offers a way to resume.
                if (!frame.isAnimated ||
                    frame.tapAction != LivingFrameTapAction.PAUSE_PLAY
                ) {
                    LivingFramesPause.clear(context, id)
                    return@launch
                }
                // Two quick taps must toggle twice. Without this lock both
                // coroutines could read the same pre-toggle state and end up
                // paused (or both resume) after a double tap.
                synchronized(PAUSE_TOGGLE_LOCK) {
                    if (LivingFramesPause.point(context, id) != null) {
                        LivingFramesPause.resume(context, id)
                    } else {
                        val point = exactTapPoint(intent, frame)
                            ?: approximateCurrentPoint(context, id, frame)
                            ?: return@launch
                        LivingFramesPause.pause(context, id, point)
                    }
                }
                LivingFramesRenderWorker.enqueue(context, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pause toggle failed", e)
            } finally {
                runCatching { result.finish() }
            }
        }
    }

    /** The exact step carried by a child tap. Root taps omit the extras. */
    private fun exactTapPoint(
        intent: Intent,
        frame: LivingFrame
    ): LivingFramesPause.Point? {
        val from = intent.getIntExtra(LivingFramesWidgetHelper.EXTRA_STEP_FROM, -1)
        val to = intent.getIntExtra(LivingFramesWidgetHelper.EXTRA_STEP_TO, -1)
        if (from !in 0 until frame.frameCount ||
            to !in 0 until frame.frameCount
        ) {
            return null
        }
        val milli = intent.getIntExtra(LivingFramesWidgetHelper.EXTRA_STEP_MIX, 0)
        return LivingFramesPause.Point(
            from = from,
            to = to,
            mix = milli.coerceIn(0, 1000) / 1000f
        )
    }

    /**
     * Fallback for a root tap on a widget whose launcher does not deliver
     * child clicks. The last render report says when the loop started and
     * which child was first; the flipper advances on the launcher's clock, so
     * this reconstructs the child that should be on screen now. Exact child
     * taps always win, and a pause never needs this path.
     */
    private fun approximateCurrentPoint(
        context: Context,
        appWidgetId: Int,
        frame: LivingFrame
    ): LivingFramesPause.Point? {
        val info = LivingFramesWidgetHelper.readInfo(context, appWidgetId) ?: return null
        val fps = info.fps.takeIf { it > 0 } ?: LivingFrameEngine.playbackFps(frame)
        val steps = LivingFrameEngine.displaySteps(frame, fps)
        if (steps.isEmpty()) return null
        val start = info.startIndex.coerceIn(0, steps.lastIndex)
        val interval = LivingFrameFps.intervalMs(fps).coerceAtLeast(1)
        val elapsed = (System.currentTimeMillis() - info.atMs).coerceAtLeast(0L)
        val index = ((start + elapsed / interval) % steps.size).toInt()
        val step = steps[index.coerceIn(0, steps.lastIndex)]
        return LivingFramesPause.Point(step.from, step.to, step.mix)
    }

    private fun handlePin(context: Context, intent: Intent) {
        val result = goAsync()
        val requestedFrame = intent.getStringExtra(LivingFramesWidgetReceiver.EXTRA_FRAME_ID)
        val requestedWidget = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        scope.launch {
            try {
                val r = repo(context) ?: return@launch
                r.ensureLoaded()
                val loadFailed = r.isLoadFailed()
                // The frame carried by the callback is authoritative. Falling
                // back to the pending pin when that frame is missing could
                // attach a completely different set to the new widget.
                val frameId = if (requestedFrame != null) {
                    requestedFrame.takeIf { r.get(it) != null }
                } else {
                    LivingFramesWidgetHelper.peekPendingPinFrame(context)
                        ?.takeIf { r.get(it) != null }
                }
                if (frameId == null) {
                    // Keep a pending pin while the library is unreadable; the
                    // user may repair metadata and pin again.
                    if (!loadFailed) LivingFramesWidgetHelper.clearPendingPin(context)
                    return@launch
                }

                val target = if (
                    requestedWidget != AppWidgetManager.INVALID_APPWIDGET_ID &&
                    requestedWidget > 0
                ) {
                    requestedWidget
                } else {
                    // Ancient launchers omitted EXTRA_APPWIDGET_ID. Only bind
                    // when there is exactly one unbound widget, so a second
                    // pin can never steal the first widget.
                    val unbound = LivingFramesWidgetHelper.allWidgetIdsOrNull(context)
                        ?.filter { r.boundFrame(it) == null }
                        .orEmpty()
                    if (unbound.size == 1) unbound.single()
                    else AppWidgetManager.INVALID_APPWIDGET_ID
                }
                if (target != AppWidgetManager.INVALID_APPWIDGET_ID && r.bindWidget(target, frameId)) {
                    LivingFramesWidgetHelper.clearPendingPin(context)
                    LivingFramesRenderWorker.enqueue(context, target)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pin callback failed", e)
            } finally {
                runCatching { result.finish() }
            }
        }
    }

    private fun handleAdvance(context: Context, intent: Intent) {
        val id = intent.getIntExtra(LivingFramesWidgetHelper.EXTRA_WIDGET_ID, -1)
        if (id <= 0) return
        val result = goAsync()
        scope.launch {
            try {
                val r = repo(context) ?: return@launch
                r.ensureLoaded()
                val frame = r.boundFrame(id)?.let { r.get(it) } ?: return@launch
                if (!frame.isSlideshow) return@launch
                val moved = if (intent.action == LivingFramesWidgetHelper.ACTION_SLIDE_ADVANCE) {
                    // The alarm is one-shot, but a concurrent refresh may
                    // already have advanced an overdue slot. Only move once.
                    LivingFramesSlideshow.ensureScheduled(context, id, frame)
                } else {
                    // A tap on a slideshow means "next photo now", even when
                    // the scheduled slot has not elapsed yet.
                    LivingFramesSlideshow.advanceNow(context, id, frame)
                    true
                }
                if (moved) {
                    // Rendering runs in a worker; this receiver finishes quickly.
                    LivingFramesRenderWorker.enqueue(context, id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Slideshow tick failed", e)
            } finally {
                runCatching { result.finish() }
            }
        }
    }

    private companion object {
        const val TAG = "LivingFramesAction"
        val PAUSE_TOGGLE_LOCK = Any()
    }
}
