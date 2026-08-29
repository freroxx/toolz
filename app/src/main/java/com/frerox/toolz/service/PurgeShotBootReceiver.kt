/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PurgeShotBootReceiver : BroadcastReceiver() {
    companion object { private const val TAG = "PurgeShotBoot" }

    @Inject lateinit var repository: PurgeShotRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val receivedAction = intent.action
        Log.i(TAG, "Received $receivedAction")
        if (receivedAction in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON"
            )
        ) {
            // Restore queue in background (WorkManager + AlarmManager)
            scope.launch {
                try {
                    repository.ensureRestoredAndRescheduled()
                    repository.processDue()
                } catch (e: Exception) {
                    Log.w(TAG, "restore failed", e)
                }
            }
            // Restart observer service if enabled
            scope.launch {
                try {
                    val enabled = settingsRepository.purgeShotEnabled.first()
                    if (enabled) {
                        val svc = Intent(context, PurgeShotService::class.java).apply { setAction(PurgeShotService.ACTION_START) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc) else context.startService(svc)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "start service failed", e)
                }
            }
        }
    }
}
