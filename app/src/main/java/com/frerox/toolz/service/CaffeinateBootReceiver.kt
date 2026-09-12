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

package com.frerox.toolz.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * Restores Caffeinate after a device reboot (or package replace).
 *
 * Re-arms INFINITE mode automatically.
 * AUTO mode waits for the next app-foreground event from the accessibility service,
 * which is safer than blindly starting the wakelock on boot.
 */
@AndroidEntryPoint
class CaffeinateBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CaffeinateBootRcvr"
        private val TRIGGER_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT,
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received action: $action")
        if (action !in TRIGGER_ACTIONS) return

        val pending = goAsync()
        scope.launch {
            try {
                val mode = try {
                    settingsRepository.caffeinateMode.first()
                } catch (e: Exception) {
                    "OFF"
                }

                Log.d(TAG, "Persisted mode on boot: $mode")

                if (mode == "INFINITE") {
                    val serviceIntent = Intent(context, CaffeinateService::class.java).apply {
                        this.action = CaffeinateService.ACTION_START_INFINITE
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d(TAG, "Re-armed INFINITE mode after boot")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore Caffeinate after boot", e)
            } finally {
                pending.finish()
            }
        }
    }
}
