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
import android.util.Log

/**
 * T-P0-01 watchdog: AlarmManager backup to the coroutine countdown.
 * Single source is ToolService.timerEndTimestamp (elapsedRealtime).
 * Fires even in Doze via ELAPSED_REALTIME_WAKEUP.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimerAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ToolService.ACTION_TIMER_FINISH &&
            intent.action != "com.frerox.toolz.TIMER_FINISH"
        ) {
            return
        }
        Log.d(TAG, "Watchdog fired — delegating to ToolService")
        try {
            val serviceIntent = Intent(context, ToolService::class.java).apply {
                action = ToolService.ACTION_TIMER_FINISH
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to forward watchdog to service", e)
        }
    }
}
