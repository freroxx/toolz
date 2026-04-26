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
    const val CHANNEL_APP_UPDATES = "app_updates"
    const val CHANNEL_TASK_REMINDERS = "task_reminders"
    const val CHANNEL_EVENT_REMINDERS = "event_reminders"
    const val CHANNEL_MUSIC_DOWNLOADS = "music_downloads"

    // Notification IDs
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
                description = "Progress of file format conversions"
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
}
