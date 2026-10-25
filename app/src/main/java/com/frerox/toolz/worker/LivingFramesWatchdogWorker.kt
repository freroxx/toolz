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
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Hourly housekeeping: re-arms slideshow alarms that an OEM, force-stop or
 * reboot killed, drops bindings for widgets the launcher no longer has, and
 * renders any widget that has never been rendered.
 *
 * Enumeration failure is treated as "unknown", never as "no widgets"; the
 * previous code turned a binder hiccup into deletion of every binding.
 */
@HiltWorker
class LivingFramesWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: LivingFramesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            repo.ensureLoaded()
            val ctx = applicationContext
            val ids = LivingFramesWidgetHelper.allWidgetIdsOrNull(ctx)
                ?: return Result.retry()

            if (repo.isLoadFailed()) {
                // A failed metadata read is not a reason to overwrite every
                // home-screen widget with an error card. Keep the last pushed
                // RemoteViews and let the user retry the load from Toolz.
                Log.w(TAG, "Skipping widget work while metadata is unreadable")
                return Result.success()
            }

            repo.pruneBindings(ids.toSet()).forEach {
                LivingFramesSlideshow.cancel(ctx, it)
                LivingFramesWidgetHelper.clearInfo(ctx, it)
            }

            // Expired staging folders and orphaned imports should not count
            // against the storage quota forever. Never clean while metadata
            // is unreadable: every real set would look orphaned.
            if (!repo.isLoadFailed()) {
                runCatching { repo.cleanupOrphans() }
            }

            ids.forEach { id ->
                try {
                    val frame = repo.boundFrame(id)?.let { repo.get(it) }
                    val info = LivingFramesWidgetHelper.readInfo(ctx, id)
                    if (frame == null) {
                        LivingFramesRenderWorker.enqueue(ctx, id)
                    } else {
                        val moved = if (frame.isSlideshow) {
                            LivingFramesSlideshow.ensureScheduled(ctx, id, frame)
                        } else {
                            false
                        }
                        if (moved ||
                            info == null ||
                            info.error != null ||
                            info.renderVersion < LivingFramesWidgetHelper.RENDER_VERSION
                        ) {
                            // Renders from an older build may still have the
                            // frame-zero flash or over-scaled corners; refresh
                            // them once after an upgrade. A slideshow that
                            // advanced after a missed alarm also needs its new
                            // photo pushed immediately.
                            LivingFramesRenderWorker.enqueue(ctx, id)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Watchdog skipped widget $id", e)
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LivingFramesWatchdog"
        private const val UNIQUE = "living_frames_watchdog"

        fun schedule(context: Context) {
            try {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<LivingFramesWatchdogWorker>(
                        1, TimeUnit.HOURS
                    ).build()
                )
            } catch (_: Exception) {
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE) }
        }
    }
}
