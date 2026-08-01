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

package com.frerox.toolz.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.frerox.toolz.R

object NotificationHelper {

    // Channel IDs
    const val CHANNEL_TOOL_ACTIVE = "tool_service_channel"
    const val CHANNEL_TOOL_ALARM = "tool_alarm_channel"
    const val CHANNEL_CLIPBOARD = "clipboard_channel"
    const val CHANNEL_STEP_COUNTER = "step_counter_channel"
    const val CHANNEL_VOICE_RECORDER = "voice_recorder_channel"
    const val CHANNEL_FILE_CONVERSION = "file_conversion_channel"
    const val CHANNEL_CAFFEINATE = "caffeinate_channel"
    const val CHANNEL_APP_UPDATES = "app_updates"
    const val CHANNEL_TASK_REMINDERS = "task_reminders"
    const val CHANNEL_EVENT_REMINDERS = "event_reminders"
    const val CHANNEL_MUSIC_DOWNLOADS = "music_downloads"
    const val CHANNEL_BACKUPS = "backups_channel"

    // Notification IDs
    const val ID_FOREGROUND_SERVICE = 1000
    const val ID_STOPWATCH = 2001
    const val ID_TIMER = 2002
    const val ID_POMODORO = 2003
    const val ID_TODO = 2004
    const val ID_TIMER_ALARM = 3001
    const val ID_POMODORO_ALARM = 3002
    const val ID_CLIPBOARD = 4001
    const val ID_STEP_COUNTER = 5001
    const val ID_VOICE_RECORDER = 6001
    const val ID_FILE_CONVERSION = 7001
    const val ID_APP_UPDATE = 8001
    const val ID_UPDATE_READY = 8002
    const val ID_MUSIC_DOWNLOAD_BASE = 9000
    const val ID_BACKUP_OPERATION = 10001

    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_TOOL_ACTIVE,
                "Active Tools",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of active stopwatch, timers, and sessions"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_TOOL_ALARM,
                "Alarms & Timers",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when timers or focus sessions finish"
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_CLIPBOARD,
                "Clipboard Intelligence",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background AI processing for clipboard content"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_STEP_COUNTER,
                "Step Counter",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily step progress updates"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_VOICE_RECORDER,
                "Voice Recorder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active voice recording status"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_FILE_CONVERSION,
                "File Conversion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress and status of file format conversions"
            },
            NotificationChannel(
                CHANNEL_CAFFEINATE,
                "Caffeinate",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background status for keeping the screen awake"
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_APP_UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about new versions and installation readiness"
            },
            NotificationChannel(
                CHANNEL_TASK_REMINDERS,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Deadlines and reminders for your tasks"
            },
            NotificationChannel(
                CHANNEL_EVENT_REMINDERS,
                "Event Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Upcoming calendar events"
            },
            NotificationChannel(
                CHANNEL_MUSIC_DOWNLOADS,
                "Music Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status of track downloads"
            },
            NotificationChannel(
                CHANNEL_BACKUPS,
                "Backup & Restore",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Status of backup and restore operations"
            }
        )

        manager.createNotificationChannels(channels)
    }

    fun baseBuilder(context: Context, channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Default icon
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
    }

    fun showBackupSuccess(context: Context, fileName: String, isScheduled: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = if (isScheduled) "Scheduled Backup Success" else "Backup Created Successfully"
        val notification = baseBuilder(context, CHANNEL_BACKUPS)
            .setContentTitle(title)
            .setContentText("File saved: $fileName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(ID_BACKUP_OPERATION, notification)
    }

    fun showBackupFailure(context: Context, error: String?, isScheduled: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = if (isScheduled) "Scheduled Backup Failed" else "Backup Failed"
        val notification = baseBuilder(context, CHANNEL_BACKUPS)
            .setContentTitle(title)
            .setContentText(error ?: "An unknown error occurred")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(ID_BACKUP_OPERATION, notification)
    }

    fun showRestoreSuccess(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = baseBuilder(context, CHANNEL_BACKUPS)
            .setContentTitle("Restore Complete")
            .setContentText("Your data has been restored successfully.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(ID_BACKUP_OPERATION, notification)
    }

    fun showRestoreFailure(context: Context, error: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = baseBuilder(context, CHANNEL_BACKUPS)
            .setContentTitle("Restore Failed")
            .setContentText(error ?: "An unknown error occurred")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(ID_BACKUP_OPERATION, notification)
    }
}
