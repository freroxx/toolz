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
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.frerox.toolz.data.livingframes.LivingFrameType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Classic RemoteViews provider for Living Widgets (photo / GIF-loop /
 * video-loop / slideshow). GIF/video animation is driven by
 * [LivingFramesPlaybackService] (updatePeriodMillis floors at 30 min and can
 * never animate); slideshows are driven by AlarmManager one-shot alarms.
 * All receiver work uses goAsync() — never blocks the broadcast.
 */
class LivingFramesWidgetReceiver : AppWidgetProvider() {

    companion object {
        const val ACTION_PINNED = "com.frerox.toolz.LIVING_FRAME_PINNED"
        const val EXTRA_FRAME_ID = "living_frame_id"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repo(context: Context) = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LivingFramesEntryPoint::class.java
        ).livingFramesRepository()
    } catch (_: Exception) { null }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        scope.launch {
            try {
                val r = repo(context) ?: return@launch
                try { r.load() } catch (_: Exception) {}
                ids.forEach { id -> bindOrCover(context, r, id) }
                LivingFramesPlaybackService.ensureRunning(context)
            } finally {
                try { result.finish() } catch (_: Exception) {}
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val r = repo(context) ?: return
        ids.forEach {
            r.unbindWidget(it)
            LivingFramesWidgetHelper.cancelSlideshow(context, it)
            LivingFramesWidgetHelper.clearDiag(context, it)
        }
        LivingFramesPlaybackService.ensureRunning(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PINNED -> {
                val result = goAsync()
                scope.launch {
                    try {
                        val frameId = intent.getStringExtra(EXTRA_FRAME_ID) ?: return@launch
                        val r = repo(context) ?: return@launch
                        try { r.load() } catch (_: Exception) {}
                        // Pin flow: bind every currently-unbound widget to the new frame.
                        val ids = LivingFramesWidgetHelper.allWidgetIds(context)
                        ids.forEach { id ->
                            if (r.boundFrame(id) == null) {
                                r.bindWidget(id, frameId)
                                bindOrCover(context, r, id)
                            }
                        }
                        LivingFramesPlaybackService.ensureRunning(context)
                    } finally {
                        try { result.finish() } catch (_: Exception) {}
                    }
                }
            }
            LivingFramesWidgetHelper.ACTION_NEXT -> {
                val id = intent.getIntExtra(LivingFramesWidgetHelper.EXTRA_WIDGET_ID, -1)
                if (id == -1) return
                val result = goAsync()
                scope.launch {
                    try {
                        advanceOnce(context, id)
                    } finally {
                        try { result.finish() } catch (_: Exception) {}
                    }
                }
            }
            LivingFramesWidgetHelper.ACTION_SLIDE_ADVANCE -> {
                val id = intent.getIntExtra(LivingFramesWidgetHelper.EXTRA_WIDGET_ID, -1)
                if (id == -1) return
                val result = goAsync()
                scope.launch {
                    try {
                        val r = repo(context) ?: return@launch
                        try { r.load() } catch (_: Exception) {}
                        val frameId = r.boundFrame(id)
                        val frame = frameId?.let { r.get(it) }
                        if (frame != null && frame.type == LivingFrameType.SLIDESHOW) {
                            val idx = LivingFramesWidgetHelper.advanceSlideIndex(
                                context, id, frame.frameCount, frame.shuffle
                            )
                            try {
                                r.decodeWidgetFrame(frame, idx)?.let { bmp ->
                                    LivingFramesWidgetHelper.pushFrame(context, id, frame, bmp)
                                    try { bmp.recycle() } catch (_: Exception) {}
                                }
                            } catch (_: Exception) {}
                            // Reschedule the next tick.
                            val mins = LivingFramesWidgetHelper.slideIntervalMin(context, id)
                            if (mins > 0) {
                                LivingFramesWidgetHelper.scheduleSlideshow(context, id, mins)
                            }
                        }
                    } finally {
                        try { result.finish() } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    /** Tap-next: slideshow advances now; animation nudges the service loop. */
    private suspend fun advanceOnce(context: Context, appWidgetId: Int) {
        try {
            val r = repo(context) ?: return
            try { r.load() } catch (_: Exception) {}
            val frame = r.boundFrame(appWidgetId)?.let { r.get(it) } ?: return
            if (frame.type == LivingFrameType.SLIDESHOW) {
                val idx = LivingFramesWidgetHelper.advanceSlideIndex(
                    context, appWidgetId, frame.frameCount, frame.shuffle
                )
                try {
                    r.decodeWidgetFrame(frame, idx)?.let { bmp ->
                        LivingFramesWidgetHelper.pushFrame(context, appWidgetId, frame, bmp)
                        try { bmp.recycle() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
                val mins = LivingFramesWidgetHelper.slideIntervalMin(context, appWidgetId)
                if (mins > 0) LivingFramesWidgetHelper.scheduleSlideshow(context, appWidgetId, mins)
            } else {
                LivingFramesPlaybackService.nudge(context, appWidgetId)
            }
        } catch (_: Exception) {}
    }

    private suspend fun bindOrCover(
        context: Context,
        r: com.frerox.toolz.data.livingframes.LivingFramesRepository,
        appWidgetId: Int
    ) {
        try {
            var frameId = r.boundFrame(appWidgetId)
            if (frameId == null) {
                // Prefer the set the user actually pinned (requestPinAppWidget
                // carries no extras); fall back to the most recent set.
                val pending = LivingFramesWidgetHelper.takePendingPinFrame(context)
                    ?.takeIf { r.get(it) != null }
                val latest = (pending?.let { r.get(it) } ?: r.snapshot().firstOrNull()) ?: run {
                    LivingFramesWidgetHelper.pushFull(
                        context, appWidgetId,
                        LivingFramesWidgetHelper.placeholderViews(context, "Add a widget in Toolz")
                    )
                    return
                }
                frameId = latest.id
                r.bindWidget(appWidgetId, frameId)
            }
            val frame = r.get(frameId) ?: run {
                LivingFramesWidgetHelper.pushFull(
                    context, appWidgetId,
                    LivingFramesWidgetHelper.placeholderViews(context, "Widget deleted")
                )
                return
            }
            if (frame.type == LivingFrameType.SLIDESHOW) {
                // Slideshows run on alarms, not the fps loop.
                val idx = LivingFramesWidgetHelper.slideIndex(context, appWidgetId)
                    .coerceIn(0, (frame.frameCount - 1).coerceAtLeast(0))
                val bmp = r.decodeWidgetFrame(frame, idx)
                LivingFramesWidgetHelper.pushFull(
                    context, appWidgetId,
                    LivingFramesWidgetHelper.fullViews(context, appWidgetId, frame, bmp)
                )
                try { bmp?.recycle() } catch (_: Exception) {}
                if (LivingFramesWidgetHelper.slideIntervalMin(context, appWidgetId) <= 0) {
                    LivingFramesWidgetHelper.scheduleSlideshow(
                        context, appWidgetId, frame.slideshowIntervalMin
                    )
                }
            } else {
                val cover = r.decodeWidgetFrame(frame, 0)
                LivingFramesWidgetHelper.pushFull(
                    context, appWidgetId,
                    LivingFramesWidgetHelper.fullViews(context, appWidgetId, frame, cover)
                )
                try { cover?.recycle() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
