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

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Pocket-resume trigger: the [MusicPlayerService] itself is a MediaSessionService
 * (woken by the system MediaButtonReceiver for headset/BT play/pause keys even when
 * the app is dead), but that only works if there is a last-song queue to resume.
 *
 * This receiver warms the service on every event that implies "the user may press
 * play next without opening Toolz": boot, update, BT connect/disconnect and the
 * audio-becoming-noisy broadcast. The service's onCreate() restores the last song
 * at its exact saved second (autoPlay=false), so a later headset resume press just
 * hits play — no need to open Toolz. It never holds a 24/7 foreground loop; it is
 * purely event-triggered.
 */
class MusicBackgroundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("MusicBackground", "Received action: $action")

        val warmActions = setOf(
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            AudioManager.ACTION_AUDIO_BECOMING_NOISY,
            Intent.ACTION_HEADSET_PLUG,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT
        )

        if (action in warmActions) {
            val serviceIntent = Intent(context, MusicPlayerService::class.java)
            try {
                // Foreground-capable start: on O+ a background startService() from a
                // receiver throws; startForegroundService lets the MediaSessionService
                // warm up (restore queue) and then go idle until a media key arrives.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("MusicBackground", "Failed to start MusicPlayerService: ${e.message}")
                // Last-resort fallback for very old paths.
                runCatching { context.startService(serviceIntent) }
            }
        }
    }
}
