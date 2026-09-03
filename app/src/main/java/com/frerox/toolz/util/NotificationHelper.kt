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
    const val CHANNEL_IMAGE_DOWNLOADS = "toolz_image_downloads"
    const val CHANNEL_VIDEO_DOWNLOADS = "toolz_video_downloads"
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
                context.getString(R.string.st_Channel_ActiveTools),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.st_Channel_ActiveTools_Desc)
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_TOOL_ALARM,
                context.getString(R.string.st_Channel_Alarms),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.st_Channel_Alarms_Desc)
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_CLIPBOARD,
                context.getString(R.string.st_Channel_Clipboard),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.st_Channel_Clipboard_Desc)
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_STEP_COUNTER,
                context.getString(R.string.st_Channel_Steps),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.st_Channel_Steps_Desc)
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_VOICE_RECORDER,
                context.getString(R.string.st_Channel_Recorder),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.st_Channel_Recorder_Desc)
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_FILE_CONVERSION,
                context.getString(R.string.st_Channel_Conversion),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.st_Channel_Conversion_Desc)
            },
            NotificationChannel(
                CHANNEL_CAFFEINATE,
                context.getString(R.string.st_Channel_Caffeinate),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.st_Channel_Caffeinate_Desc)
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_APP_UPDATES,
                context.getString(R.string.st_Channel_Updates),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.st_Channel_Updates_Desc)
            },
            NotificationChannel(
                CHANNEL_TASK_REMINDERS,
                context.getString(R.string.st_Channel_Tasks),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.st_Channel_Tasks_Desc)
            },
            NotificationChannel(
                CHANNEL_EVENT_REMINDERS,
                context.getString(R.string.st_Channel_Events),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.st_Channel_Events_Desc)
            },
            NotificationChannel(
                CHANNEL_MUSIC_DOWNLOADS,
                context.getString(R.string.st_Channel_MusicDownloads),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.st_Channel_MusicDownloads_Desc)
                setShowBadge(false)
                enableVibration(false)
            },
            NotificationChannel(
                CHANNEL_IMAGE_DOWNLOADS,
                "Image Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Image downloads from search — polished Toolz notifications"
                setShowBadge(false)
                enableVibration(false)
            },
            NotificationChannel(
                CHANNEL_VIDEO_DOWNLOADS,
                "Video Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "YouTube video downloads"
                setShowBadge(false)
                enableVibration(false)
            },
            NotificationChannel(
                CHANNEL_BACKUPS,
                context.getString(R.string.st_Channel_Backups),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.st_Channel_Backups_Desc)
            }
        )

        manager.createNotificationChannels(channels)
    }

    fun baseBuilder(context: Context, channelId: String): NotificationCompat.Builder {
        val large = try { android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground) } catch (_: Exception) { null }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(large)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
    }

    fun showBackupSuccess(context: Context, fileName: String, isScheduled: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = if (isScheduled) context.getString(R.string.st_Notification_Backup_Success_Scheduled) 
                    else context.getString(R.string.st_Notification_Backup_Success_Manual)
        val notification = baseBuilder(context, CHANNEL_BACKUPS)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.st_Notification_Backup_Success_File, fileName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(ID_BACKUP_OPERATION, notification)
    }

    fun showBackupFailure(context: Context, error: String?, isScheduled: Boolean = false) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = if (isScheduled) context.getString(R.string.st_Notification_Backup_Failure_Scheduled) 
                    else context.getString(R.string.st_Notification_Backup_Failure_Manual)
        val notification = baseBuilder(context, CHANNEL_BACKUPS)
            .setContentTitle(title)
            .setContentText(error ?: context.getString(R.string.st_OnboardingScreen_u1v2)) // Reusing error string if null
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(ID_BACKUP_OPERATION, notification)
    }

    fun showRestoreSuccess(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = baseBuilder(context, CHANNEL_BACKUPS)
            .setContentTitle(context.getString(R.string.st_Notification_Restore_Success_Title))
            .setContentText(context.getString(R.string.st_Notification_Restore_Success_Desc))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(ID_BACKUP_OPERATION, notification)
    }

    fun showRestoreFailure(context: Context, error: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = baseBuilder(context, CHANNEL_BACKUPS)
            .setContentTitle(context.getString(R.string.st_Notification_Restore_Failure_Title))
            .setContentText(error ?: context.getString(R.string.st_OnboardingScreen_u1v2))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(ID_BACKUP_OPERATION, notification)
    }

    fun toolzLargeIcon(context: Context): android.graphics.Bitmap? = try {
        val raw = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_logo)
            ?: android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
        if (raw == null) null
        else {
            val size = minOf(raw.width, raw.height)
            val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(output)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                shader = android.graphics.BitmapShader(raw, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                val scale = size.toFloat() / minOf(raw.width, raw.height).toFloat()
                val dx = (size - raw.width * scale) / 2f
                val dy = (size - raw.height * scale) / 2f
                shader?.let {
                    val m = android.graphics.Matrix()
                    m.setScale(scale, scale)
                    m.postTranslate(dx, dy)
                    it.setLocalMatrix(m)
                }
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            val borderPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                color = android.graphics.Color.WHITE
                strokeWidth = size * 0.03f
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - borderPaint.strokeWidth / 2, borderPaint)
            output
        }
    } catch (_: Exception) { null }
}
