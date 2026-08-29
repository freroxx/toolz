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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * System JobScheduler content-trigger — wakes the app on *any* MediaStore image change
 * even when PurgeShotService was killed or Toolz is not in the foreground.
 * This is the key to "works for all screenshots, even outside Toolz".
 *
 * Triggered via JobInfo.addTriggerContentUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI).
 * Content-trigger jobs are one-shot by OS design — the trigger is consumed as soon as the
 * job is dispatched, so we must call [schedule] again for every future change. We do that
 * eagerly, before starting the (possibly slow/killable) detection work, so a job that gets
 * killed mid-flight doesn't leave us silently blind until the next content change.
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
                // NOTE: setPersisted(true) is illegal together with addTriggerContentUri — the
                // platform throws IllegalArgumentException for content-trigger jobs marked
                // persisted, since content triggers are re-registered per dispatch rather than
                // restored from a persisted job store across reboot. That combination here was
                // silently throwing on every call (caught below), so schedule() was a no-op and
                // the JobScheduler path never actually armed. Reboot survival is instead handled
                // by PurgeShotBootReceiver re-registering the job on ACTION_BOOT_COMPLETED.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setRequiresBatteryNotLow(false)
                    builder.setRequiresCharging(false)
                }
                val result = scheduler.schedule(builder.build())
                Log.d(TAG, "schedule result=$result")
            } catch (e: Exception) {
                Log.w(TAG, "schedule failed", e)
            }
        }

        fun cancel(context: Context) {
            try {
                context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
            } catch (_: Exception) {
            }
        }
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var repository: PurgeShotRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob triggered=${params?.triggeredContentUris?.joinToString()}")

        // Re-arm the trigger immediately. The OS consumes the content trigger the moment this
        // job is dispatched, so if we wait until the coroutine's finally block to reschedule,
        // a job killed mid-flight (low memory, Doze edge cases, OEM task killers) leaves us with
        // zero registered triggers and no more wakeups until the next content change happens to
        // be caught by something else.
        schedule(applicationContext)

        scope.launch {
            var handled = false
            try {
                handled = PurgeShotDetector.detectAndHandle(
                    context = applicationContext,
                    repository = repository,
                    settingsRepository = settingsRepository,
                    awaitSettle = true
                )
            } catch (e: Exception) {
                Log.w(TAG, "job detection failed", e)
            } finally {
                Log.d(TAG, "handled=$handled")
                jobFinished(params, false)
            }
        }
        return true // async
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        // System is reclaiming this dispatch before we finished. The trigger was already
        // re-armed at the top of onStartJob, so we don't need Android to redeliver this exact
        // instance — the next content change re-dispatches us anyway.
        return false
    }
}