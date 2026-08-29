/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.worker.PurgeShotDetectWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class PurgeShotBootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "PurgeShotBoot"
        private val TRIGGER_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_USER_PRESENT,
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.LOCKED_BOOT_COMPLETED"
        )
    }

    @Inject lateinit var repository: PurgeShotRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val receivedAction = intent.action
        Log.i(TAG, "Received $receivedAction")
        if (receivedAction !in TRIGGER_ACTIONS) return

        // goAsync() extends the receiver's process-priority window past onReceive() returning.
        // Without it, the system can (and on aggressive OEM skins, will) deprioritize or kill
        // this process within a couple of seconds — well before the DataStore read + Room
        // restore + AlarmManager rescheduling below reliably complete, silently dropping queued
        // deletions across a reboot.
        val pending = goAsync()
        scope.launch {
            try {
                // Restore queue (WorkManager + AlarmManager)
                try {
                    repository.ensureRestoredAndRescheduled()
                    repository.processDue()
                } catch (e: Exception) {
                    Log.w(TAG, "restore failed", e)
                }

                // Restart the observer + JobScheduler content trigger if enabled — this is what
                // gives outside-Toolz detection back after a reboot or app update.
                try {
                    val enabled = settingsRepository.purgeShotEnabled.first()
                    if (enabled) {
                        val svc = Intent(context, PurgeShotService::class.java).apply {
                            action = PurgeShotService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(svc)
                        } else {
                            context.startService(svc)
                        }
                        // Content-trigger jobs are consumed on dispatch and can't be persisted
                        // across reboot (see PurgeShotObserverJobService.schedule's note), so
                        // this boot-time call is what actually re-arms outside-app detection —
                        // it isn't optional redundancy.
                        PurgeShotObserverJobService.schedule(context)
                        // Periodic WorkManager sweep as a last-resort net for any screenshot
                        // that both the live observer and the content-trigger job miss.
                        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "PurgeShotDetect",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            PeriodicWorkRequestBuilder<PurgeShotDetectWorker>(15, TimeUnit.MINUTES).build()
                        )
                        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "PurgeShotReschedule",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            PeriodicWorkRequestBuilder<com.frerox.toolz.worker.PurgeShotRescheduleWorker>(6, TimeUnit.HOURS).build()
                        )
                    } else {
                        PurgeShotObserverJobService.cancel(context)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "start service failed", e)
                }
            } finally {
                pending.finish()
            }
        }
    }
}