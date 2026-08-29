/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * System JobScheduler content-trigger — wakes the app on *any* MediaStore image change
 * even when PurgeShotService was killed or Toolz is not in foreground.
 * This is the key to "works for all screenshots, even outside Toolz".
 *
 * Triggered via JobInfo.addTriggerContentUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI).
 * On trigger, we delegate to PurgeShotService's logic (query + smart-auto vs popup)
 * and immediately reschedule.
 */
@AndroidEntryPoint
class PurgeShotObserverJobService : JobService() {

    companion object {
        private const val TAG = "PurgeShotJob"
        private const val JOB_ID = 0x50_05_11 // 5242897
        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                val component = ComponentName(context, PurgeShotObserverJobService::class.java)
                val builder = JobInfo.Builder(JOB_ID, component)
                    .addTriggerContentUri(
                        JobInfo.TriggerContentUri(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                        )
                    )
                    .setTriggerContentMaxDelay(900) // batch within 0.9s
                    .setTriggerContentUpdateDelay(400)
                    .setPersisted(true) // survives reboot
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setRequiresBatteryNotLow(false)
                    builder.setRequiresCharging(false)
                }
                // Don't require network/device idle — must fire offline
                val result = scheduler.schedule(builder.build())
                Log.d(TAG, "JobScheduler schedule result=$result")
            } catch (e: Exception) {
                Log.w(TAG, "schedule failed", e)
            }
        }

        fun cancel(context: Context) {
            try { context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID) } catch (_: Exception) {}
        }
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var repository: PurgeShotRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob triggered=${params?.triggeredContentUris?.joinToString()}")
        scope.launch {
            try {
                if (!settingsRepository.purgeShotEnabled.first()) {
                    Log.d(TAG, "disabled, skipping")
                    jobFinished(params, false)
                    return@launch
                }
                // Delegate to service's static handler OR replicate query here
                // Simplest: ensure service is running and let its observer handle, plus do a direct check here as fallback
                val handled = handleScreenshotViaFallback()
                Log.d(TAG, "fallback handled=$handled")
            } catch (e: Exception) {
                Log.w(TAG, "job failed", e)
            } finally {
                // Content-trigger jobs are one-shot; reschedule for next trigger
                schedule(applicationContext)
                jobFinished(params, false)
            }
        }
        return true // async
    }

    override fun onStopJob(params: JobParameters?): Boolean = true // reschedule

    private suspend fun handleScreenshotViaFallback(): Boolean {
        // Reuse same query logic as PurgeShotService but without requiring service alive
        // We do a lightweight query for latest image and delegate to service's smart-auto pipeline
        // To avoid duplicating code, just start PurgeShotService which will do the full check
        return try {
            val ctx = applicationContext
            val intent = android.content.Intent(ctx, PurgeShotService::class.java).apply {
                action = "com.frerox.toolz.PURGESHOT_HANDLE_FALLBACK"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent) else ctx.startService(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "fallback startService failed", e)
            false
        }
    }
}
