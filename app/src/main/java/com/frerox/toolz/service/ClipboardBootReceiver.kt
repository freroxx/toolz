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
        // PAUSED — clipboard tool is under development. Do not start the service.
        // To re-enable: call ClipboardService.startIfNeeded(context.applicationContext).
    }
}
