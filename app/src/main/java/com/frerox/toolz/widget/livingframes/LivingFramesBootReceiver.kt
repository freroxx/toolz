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
import com.frerox.toolz.worker.LivingFramesRenderWorker
import com.frerox.toolz.worker.LivingFramesWatchdogWorker
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarms don't survive a reboot, so slideshow timers are re-armed here.
 * Everything else needs nothing: the launcher re-requests widget updates
 * itself and the receiver renders them.
 */
class LivingFramesBootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> Unit
            else -> return
        }
        // An update replaces the code behind existing PendingIntents. Re-push
        // every widget so taps, slideshow timers and the pin flow switch to
        // the non-exported action receiver instead of waiting for a resize.
        val republish = action == Intent.ACTION_MY_PACKAGE_REPLACED
        val result = goAsync()
        scope.launch {
            try {
                // Schedule first so an Hilt/property failure below cannot leave
                // the watchdog unemployed for the rest of the boot cycle.
                LivingFramesWatchdogWorker.schedule(context)
                val ids = LivingFramesWidgetHelper.allWidgetIdsOrNull(context)
                if (ids == null) return@launch
                val repo = EntryPointAccessors.fromApplication(
                    context.applicationContext, LivingFramesEntryPoint::class.java
                ).livingFramesRepository()
                repo.ensureLoaded()
                if (ids.isNotEmpty() && republish && !repo.isLoadFailed()) {
                    LivingFramesRenderWorker.enqueue(context, ids)
                }
                if (ids.isNotEmpty()) {
                    // A reboot can happen long after the saved slot, so an
                    // overdue slideshow must advance now and have its new
                    // photo rendered. Enqueue those ids even during a
                    // republish: unique work replaces the earlier request, so
                    // this cannot duplicate work.
                    val advanced = mutableListOf<Int>()
                    ids.forEach { id ->
                        val frame = repo.boundFrame(id)?.let { repo.get(it) }
                        if (frame != null && frame.isSlideshow &&
                            LivingFramesSlideshow.ensureScheduled(context, id, frame)
                        ) {
                            advanced += id
                        }
                    }
                    if (advanced.isNotEmpty()) {
                        LivingFramesRenderWorker.enqueue(context, advanced.toIntArray())
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { result.finish() }
            }
        }
    }
}
