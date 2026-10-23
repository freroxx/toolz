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
import android.os.Bundle
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.worker.LivingFramesRenderWorker
import com.frerox.toolz.worker.LivingFramesWatchdogWorker
import dagger.hilt.android.EntryPointAccessors

/**
 * RemoteViews provider for Living Widgets. This receiver is exported because
 * the system must be able to deliver APPWIDGET_UPDATE. It intentionally does
 * not handle custom slide/pin actions; those live in the non-exported
 * [LivingFramesActionReceiver] and are reached only through PendingIntents.
 *
 * Rendering is handed to WorkManager because a cold multi-frame decode can
 * exceed the broadcast's ~10-second goAsync window.
 */
class LivingFramesWidgetReceiver : AppWidgetProvider() {

    companion object {
        // Kept here for compatibility with existing PendingIntents and tests.
        const val ACTION_PINNED = "com.frerox.toolz.LIVING_FRAME_PINNED"
        const val EXTRA_FRAME_ID = "living_frame_id"
    }

    private fun repo(context: Context): LivingFramesRepository? = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext, LivingFramesEntryPoint::class.java
        ).livingFramesRepository()
    } catch (_: Exception) {
        null
    }

    override fun onEnabled(context: Context) {
        LivingFramesWatchdogWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        // The last widget is gone; stop the hourly housekeeping until one
        // is added again.
        LivingFramesWatchdogWorker.cancel(context)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        LivingFramesRenderWorker.enqueue(context, ids)
        LivingFramesWatchdogWorker.schedule(context)
    }

    /** Launchers fire this repeatedly while dragging a resize handle; render once it settles. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        LivingFramesRenderWorker.enqueue(context, appWidgetId, delayMs = 400L)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val r = repo(context)
        ids.forEach {
            r?.unbindWidget(it)
            LivingFramesSlideshow.cancel(context, it)
            LivingFramesWidgetHelper.clearInfo(context, it)
            LivingFramesRenderWorker.cancel(context, it)
        }
    }
}
