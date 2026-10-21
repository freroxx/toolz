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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import com.frerox.toolz.widget.livingframes.LivingFramesWidgetHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Off-main-thread widget rendering. AppWidgetProvider's goAsync window is
 * only ~10 seconds; decoding 40 frames can exceed it on a cold process, so
 * receivers hand work to this worker and finish immediately.
 */
@HiltWorker
class LivingFramesRenderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: LivingFramesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getInt(KEY_WIDGET_ID, -1)
        if (id <= 0) return Result.success()
        return try {
            // Render one widget at a time. WorkManager may run several unique
            // workers in parallel, and each render already uses a large slice
            // of the process bitmap budget.
            val ok = RENDER_LOCK.withLock {
                LivingFramesWidgetHelper.refresh(applicationContext, repo, id)
            }
            if (!ok) Log.w(TAG, "Widget $id rendered with a placeholder")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Widget $id render failed", e)
            // A transient codec/storage failure may succeed on a retry; the
            // existing push remains in place until then.
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LivingFramesRender"
        private const val KEY_WIDGET_ID = "living_frame_widget_id"
        private const val UNIQUE_PREFIX = "living_frames_render_"

        private val RENDER_LOCK = Mutex()

        fun enqueue(context: Context, appWidgetIds: IntArray, delayMs: Long = 0L) {
            if (appWidgetIds.isEmpty()) return
            appWidgetIds.distinct().forEach { id ->
                try {
                    val data = Data.Builder()
                        .putInt(KEY_WIDGET_ID, id)
                        .build()
                    val request = OneTimeWorkRequestBuilder<LivingFramesRenderWorker>()
                        .setInputData(data)
                        .setInitialDelay(delayMs.coerceAtLeast(0L), java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "$UNIQUE_PREFIX$id",
                        ExistingWorkPolicy.REPLACE,
                        request
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not enqueue render for widget $id", e)
                }
            }
        }

        fun enqueue(context: Context, appWidgetId: Int, delayMs: Long = 0L) {
            enqueue(context, intArrayOf(appWidgetId), delayMs)
        }

        fun cancel(context: Context, appWidgetId: Int) {
            runCatching {
                WorkManager.getInstance(context)
                    .cancelUniqueWork("$UNIQUE_PREFIX$appWidgetId")
            }
        }
    }
}
