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
import android.os.Bundle
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.worker.LivingFramesWatchdogWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * RemoteViews provider for Living Widgets. Nothing here keeps running: each
 * broadcast renders what changed, pushes it once, and finishes. Animation is
 * the launcher's job (see [LivingFramesWidgetHelper]).
 */
class LivingFramesWidgetReceiver : AppWidgetProvider() {

    companion object {
        const val ACTION_PINNED = "com.frerox.toolz.LIVING_FRAME_PINNED"
        const val EXTRA_FRAME_ID = "living_frame_id"

        // AppWidgetProvider instances are per-broadcast, so shared state lives here.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val resizeJobs = ConcurrentHashMap<Int, Job>()
    }

    private fun repo(context: Context): LivingFramesRepository? = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext, LivingFramesEntryPoint::class.java
        ).livingFramesRepository()
    } catch (_: Exception) { null }

    override fun onEnabled(context: Context) {
        LivingFramesWatchdogWorker.schedule(context)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val result = goAsync()
        scope.launch {
            try {
                val r = repo(context) ?: return@launch
                ids.forEach { LivingFramesWidgetHelper.refresh(context, r, it) }
                LivingFramesWatchdogWorker.schedule(context)
            } finally {
                runCatching { result.finish() }
            }
        }
    }

    /** Launchers fire this repeatedly while dragging a resize handle; render once it settles. */
    override fun onAppWidgetOptionsChanged(
        context: Context, mgr: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        val result = goAsync()
        resizeJobs.remove(appWidgetId)?.cancel()
        val job = scope.launch {
            try {
                delay(350L)
                val r = repo(context) ?: return@launch
                LivingFramesWidgetHelper.refresh(context, r, appWidgetId)
            } finally {
                resizeJobs.remove(appWidgetId)
                runCatching { result.finish() }
            }
        }
        resizeJobs[appWidgetId] = job
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val r = repo(context)
        ids.forEach {
            r?.unbindWidget(it)
            LivingFramesSlideshow.cancel(context, it)
            LivingFramesWidgetHelper.clearInfo(context, it)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PINNED -> {
                val frameId = intent.getStringExtra(EXTRA_FRAME_ID) ?: return
                val result = goAsync()
                scope.launch {
                    try {
                        val r = repo(context) ?: return@launch
                        r.ensureLoaded()
                        // Anything the launcher added without binding yet belongs to this pin.
                        LivingFramesWidgetHelper.allWidgetIds(context).forEach { id ->
                            if (r.boundFrame(id) == null) {
                                r.bindWidget(id, frameId)
                                LivingFramesWidgetHelper.refresh(context, r, id)
                            }
                        }
                    } finally {
                        runCatching { result.finish() }
                    }
                }
            }
            LivingFramesWidgetHelper.ACTION_NEXT,
            LivingFramesWidgetHelper.ACTION_SLIDE_ADVANCE -> {
                val id = intent.getIntExtra(LivingFramesWidgetHelper.EXTRA_WIDGET_ID, -1)
                if (id == -1) return
                val result = goAsync()
                scope.launch {
                    try {
                        val r = repo(context) ?: return@launch
                        LivingFramesWidgetHelper.advanceSlideshow(context, r, id)
                    } finally {
                        runCatching { result.finish() }
                    }
                }
            }
        }
    }
}
