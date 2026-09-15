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
import android.util.Log

class ClipboardBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_PRESENT
        ) {
            return
        }
        // The service self-stops if monitoring is off or neither Shizuku nor
        // accessibility is available — so it is safe to request a start here.
        try {
            ClipboardService.startIfNeeded(context.applicationContext)
        } catch (e: Exception) {
            Log.w("ClipboardBoot", "start failed: ${e.message}")
        }
    }
}
