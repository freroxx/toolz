/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * T-P0-01: restores timer after reboot / package replace.
 * ToolService.onCreate does the actual resume (elapsedRealtime math);
 * this receiver just ensures the service is started when a timer was running.
 */
@AndroidEntryPoint
class TimerBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimerBootRcvr"
        private val TRIGGER_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in TRIGGER_ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try {
                val wasRunning = try { settingsRepository.timerRunning.first() } catch (_: Exception) { false }
                val endElapsed = try { settingsRepository.timerEndElapsed.first() } catch (_: Exception) { 0L }
                if (wasRunning && endElapsed > 0L) {
                    // ElapsedRealtime survives reboot? No — elapsedRealtime resets on reboot.
                    // If endElapsed is in the past (always after reboot since counter reset),
                    // onCreate restore will fire finish once. Still start service to surface it.
                    // If device has RTC-based fallback in future, this also re-arms watchdog.
                    Log.d(TAG, "Timer was running at shutdown (end=$endElapsed now=${SystemClock.elapsedRealtime()}), restarting service")
                    val serviceIntent = Intent(context, ToolService::class.java).apply {
                        this.action = ToolService.ACTION_TIMER_FINISH
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "start service failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Timer boot restore failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
