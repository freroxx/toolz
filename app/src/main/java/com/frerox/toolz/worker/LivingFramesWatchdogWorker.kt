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
import com.frerox.toolz.data.livingframes.LivingFrameType
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Self-healing for Living Widgets. Repairs what dies without a foreground
 * context: slideshow alarms (OEMs + reboot clear them) and stale photo
 * covers. It deliberately does NOT start the fps loop — background FGS
 * starts are denied on Android 12+, so GIF/video resume when the tool opens.
 */
@HiltWorker
class LivingFramesWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: LivingFramesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            repo.load()
            val ctx = applicationContext
            LivingFramesWidgetHelper.allWidgetIds(ctx).forEach { id ->
                try {
                    val frame = repo.boundFrame(id)?.let { repo.get(it) } ?: return@forEach
                    when (frame.type) {
                        LivingFrameType.SLIDESHOW -> {
                            if (LivingFramesWidgetHelper.slideIntervalMin(ctx, id) <= 0) {
                                LivingFramesWidgetHelper.scheduleSlideshow(
                                    ctx, id, frame.slideshowIntervalMin
                                )
                            }
                        }
                        LivingFrameType.PHOTO -> {
                            repo.decodeWidgetFrame(frame, 0)?.let { bmp ->
                                try {
                                    LivingFramesWidgetHelper.pushFrame(ctx, id, frame, bmp)
                                } finally {
                                    try { bmp.recycle() } catch (_: Exception) {}
                                }
                            }
                        }
                        else -> { /* fps loop owns GIF/video; needs foreground */ }
                    }
                } catch (_: Exception) {}
            }
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }

    companion object {
        private const val UNIQUE = "living_frames_watchdog"

        fun schedule(context: Context) {
            try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<LivingFramesWatchdogWorker>(
                        15, TimeUnit.MINUTES
                    ).build()
                )
            } catch (_: Exception) {}
        }
    }
}
