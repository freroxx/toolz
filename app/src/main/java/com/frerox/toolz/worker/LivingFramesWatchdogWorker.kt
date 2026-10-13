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

package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.widget.livingframes.LivingFramesSlideshow
import com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Hourly housekeeping: re-arms slideshow alarms that an OEM or a reboot
 * killed, drops bindings for widgets the launcher no longer has, and renders
 * any widget that has never been rendered. It never touches widgets that are
 * already fine, so it costs next to nothing.
 */
@HiltWorker
class LivingFramesWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: LivingFramesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            repo.ensureLoaded()
            val ctx = applicationContext
            val ids = LivingFramesWidgetHelper.allWidgetIds(ctx)
            repo.pruneBindings(ids.toSet()).forEach {
                LivingFramesSlideshow.cancel(ctx, it)
                LivingFramesWidgetHelper.clearInfo(ctx, it)
            }
            ids.forEach { id ->
                try {
                    val frame = repo.boundFrame(id)?.let { repo.get(it) }
                    if (frame == null || LivingFramesWidgetHelper.readInfo(ctx, id) == null) {
                        LivingFramesWidgetHelper.refresh(ctx, repo, id)
                    } else if (frame.isSlideshow) {
                        LivingFramesSlideshow.ensureScheduled(ctx, id, frame.slideshowIntervalMin)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "living_frames_watchdog"

        fun schedule(context: Context) {
            try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<LivingFramesWatchdogWorker>(1, TimeUnit.HOURS).build()
                )
            } catch (_: Exception) {}
        }
    }
}
