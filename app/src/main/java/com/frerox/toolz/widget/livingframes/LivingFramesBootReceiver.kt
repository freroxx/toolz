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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frerox.toolz.data.livingframes.LivingFrameType
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Rebind covers + restart animation + reschedule slideshows after reboot / update. */
class LivingFramesBootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val a = intent?.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED ||
            a == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            a == Intent.ACTION_MY_PACKAGE_REPLACED ||
            a == Intent.ACTION_USER_PRESENT
        ) {
            val result = goAsync()
            scope.launch {
                try {
                    val ids = LivingFramesWidgetHelper.allWidgetIds(context)
                    if (ids.isEmpty()) return@launch
                    try {
                        val repo = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            LivingFramesEntryPoint::class.java
                        ).livingFramesRepository()
                        try { repo.load() } catch (_: Exception) {}
                        // Slideshow alarms don't survive reboot — reschedule them.
                        ids.forEach { id ->
                            try {
                                val frame = repo.boundFrame(id)?.let { repo.get(it) }
                                if (frame != null && frame.type == LivingFrameType.SLIDESHOW) {
                                    LivingFramesWidgetHelper.scheduleSlideshow(
                                        context, id, frame.slideshowIntervalMin
                                    )
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                    try {
                        context.sendBroadcast(
                            Intent(AppWidgetRefresh.ACTION).setClass(context, AppWidgetRefresh::class.java)
                        )
                    } catch (_: Exception) {}
                    LivingFramesPlaybackService.ensureRunning(context)
                } finally {
                    try { result.finish() } catch (_: Exception) {}
                }
            }
        }
    }

    /** Trampoline to refresh covers without a direct repo dependency. */
    class AppWidgetRefresh : BroadcastReceiver() {
        companion object {
            const val ACTION = "com.frerox.toolz.LIVING_FRAMES_REBIND"
        }

        override fun onReceive(context: Context, intent: Intent?) {
            try {
                val mgr = android.appwidget.AppWidgetManager.getInstance(context)
                val ids = LivingFramesWidgetHelper.allWidgetIds(context)
                if (ids.isNotEmpty()) {
                    mgr.notifyAppWidgetViewDataChanged(ids, android.R.id.content)
                }
                LivingFramesPlaybackService.ensureRunning(context)
            } catch (_: Exception) {}
        }
    }
}
