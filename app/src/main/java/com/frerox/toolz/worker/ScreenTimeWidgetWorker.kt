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
import com.frerox.toolz.widget.WidgetUpdateManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic refresh for the Screen Time Glance widget.
 *
 * The widget otherwise only re-renders on host requests and settings
 * changes, so without this its ring/top-apps freeze for the whole day.
 * 15 minutes is the WorkManager minimum and matches usage-stats
 * granularity — fresh enough to look live, cheap enough to always run.
 */
@HiltWorker
class ScreenTimeWidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val widgetUpdateManager: WidgetUpdateManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ScreenTimeWidgetWorker"
        const val UNIQUE_WORK = "ScreenTimeWidgetRefresh"

        fun schedule(context: Context) {
            try {
                val request = PeriodicWorkRequestBuilder<ScreenTimeWidgetWorker>(15, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (e: Exception) {
                Log.w(TAG, "schedule failed (non-fatal)", e)
            }
        }

        fun cancel(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
            } catch (e: Exception) {
                Log.w(TAG, "cancel failed (non-fatal)", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // Self-healing: the Application hook schedules unconditionally,
            // but with zero placed instances there is nothing to refresh —
            // cancel so we don't wake the device every 15 min for a no-op.
            // Re-placement re-schedules via onEnabled.
            val ids = try {
                androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
                    .getGlanceIds(com.frerox.toolz.widget.glance.ScreenTimeGlanceWidget::class.java)
            } catch (_: Exception) {
                null
            }
            if (ids != null && ids.isEmpty()) {
                cancel(applicationContext)
                return Result.success()
            }
            widgetUpdateManager.updateScreenTimeWidget()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed", e)
            Result.retry()
        }
    }
}
