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

package com.frerox.toolz.ui.screens.livingframes

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.widget.livingframes.LivingFramesImportCancelReceiver

/**
 * Process-local cancel flag for the notification action. The import runs in
 * the same process as the UI, and the importer reports progress per frame, so
 * the callback observes this quickly without exposing the ViewModel to a
 * broadcast receiver.
 */
object LivingFramesImportCancellation {
    @Volatile private var requested = false

    fun request() {
        requested = true
    }

    fun reset() {
        requested = false
    }

    /** Returns true once, then clears the flag. */
    fun consume(): Boolean {
        if (!requested) return false
        requested = false
        return true
    }
}

/**
 * Ongoing progress notification for Living Widget imports. The dialog can be
 * hidden so the user can keep customizing; this keeps progress visible and
 * gives them a cancel action from outside the app.
 */
object LivingFramesImportNotifications {

    private fun canNotify(context: Context): Boolean = try {
        val permission = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (_: Exception) {
        false
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0x1F1,
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.LivingWidgets.route)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0x1F2,
            Intent(context, LivingFramesImportCancelReceiver::class.java).apply {
                action = LivingFramesImportCancelReceiver.ACTION_CANCEL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun start(context: Context, label: String) = update(context, label, 0f)

    fun update(context: Context, label: String, progress: Float) {
        if (!canNotify(context)) return
        try {
            NotificationHelper.createAllChannels(context)
            val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
            val notification = NotificationHelper.progressBuilder(
                context,
                NotificationHelper.CHANNEL_LIVING_FRAMES_IMPORT,
                context.getString(R.string.lw_notification_title),
                label,
                pct
            )
                .setOngoing(true)
                .setContentIntent(contentIntent(context))
                .addAction(
                    0,
                    context.getString(R.string.lw_notification_cancel),
                    cancelIntent(context)
                )
                .build()
            NotificationManagerCompat.from(context)
                .notify(NotificationHelper.ID_LIVING_FRAMES_IMPORT, notification)
        } catch (_: Exception) {
        }
    }

    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context)
                .cancel(NotificationHelper.ID_LIVING_FRAMES_IMPORT)
        } catch (_: Exception) {
        }
    }
}
