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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.frerox.toolz.R

object NotificationHelper {

    // Small icon: monochrome silhouette only. Never use ic_launcher_foreground
    // (opaque adaptive foreground renders as a white square on API 26+).
    const val SMALL_ICON = R.drawable.ic_stat_toolz

    // Accent applied to all Toolz notifications for a consistent brand tint.
    const val ACCENT_COLOR = 0xFFFF6D00.toInt()

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

    // Non-overlapping download ID namespaces. Each band is 1000 wide so
    // concurrent downloads never collide across tools.
    const val ID_VIDEO_BASE = 20000
    const val ID_SOCIAL_BASE = 21000
    const val ID_MUSIC_BASE = 22000
    const val ID_YTMP3_BASE = 23000
    const val ID_IMAGE_BASE = 24000
    private const val ID_BAND_SIZE = 1000

    /** Stable per-download ID inside a tool namespace. Same key re-downloads collapse. */
    fun downloadId(base: Int, key: String): Int =
        base + ((key.hashCode() and 0x7fffffff) % ID_BAND_SIZE)

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
                description = "Image downloads from search"
                setShowBadge(false)
                enableVibration(false)
            },
            NotificationChannel(
                CHANNEL_VIDEO_DOWNLOADS,
                "Video Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Video and social media downloads"
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
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(SMALL_ICON)
            .setLargeIcon(toolzLargeIcon(context))
            .setColor(ACCENT_COLOR)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
    }

    /**
     * Progress builder: silent, single-slot, collapses into the terminal
     * notification that reuses the same ID.
     */
    fun progressBuilder(
        context: Context,
        channelId: String,
        title: String,
        text: String?,
        progress: Int
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(SMALL_ICON)
            .setLargeIcon(toolzLargeIcon(context))
            .setColor(ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(progress in 1..99)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    /** Terminal builder: clears the progress bar, alerts once, swipe-dismissable. */
    fun terminalBuilder(
        context: Context,
        channelId: String,
        title: String,
        text: String?,
        highPriority: Boolean = false
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(SMALL_ICON)
            .setLargeIcon(toolzLargeIcon(context))
            .setColor(ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
    }

    /**
     * CAL-P3 (additive, never changes [baseBuilder] defaults shared by other tools):
     * alarm builder with monochrome status icon + re-alert on snooze re-fire.
     */
    fun alarmBuilder(context: Context, channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_event)
            .setColor(ACCENT_COLOR)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
    }

    /** Throttle gate for foreground progress: at most one post per interval or 5% delta. */
    fun shouldPublishProgress(
        lastAt: Long,
        lastPct: Int,
        now: Long,
        pct: Int,
        minIntervalMs: Long = 800L,
        minDeltaPct: Int = 2
    ): Boolean {
        if (pct >= 100 || pct <= 0) return true
        if (pct - lastPct >= minDeltaPct) return true
        return now - lastAt >= minIntervalMs
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

    @Volatile private var largeIconCache: android.graphics.Bitmap? = null
    @Volatile private var largeIconKey: String? = null

    fun toolzLargeIcon(context: Context): android.graphics.Bitmap? = try {
        val raw = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_logo)
            ?: android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
            ?: return null
        val cacheKey = "${raw.width}x${raw.height}"
        if (cacheKey == largeIconKey && largeIconCache != null) return largeIconCache
        val size = minOf(raw.width, raw.height).coerceAtLeast(1)
        // Rounded-rectangle app mark (~28% radius) so the tray/shade shows
        // rounded corners everywhere instead of a circle or raw square.
        val radius = size * 0.28f
        val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            shader = android.graphics.BitmapShader(raw, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            val scale = size.toFloat() / minOf(raw.width, raw.height).toFloat()
            val dx = (size - raw.width * scale) / 2f
            val dy = (size - raw.height * scale) / 2f
            val m = android.graphics.Matrix()
            m.setScale(scale, scale)
            m.postTranslate(dx, dy)
            shader.setLocalMatrix(m)
        }
        val rect = android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        val borderPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            color = android.graphics.Color.WHITE
            strokeWidth = (size * 0.02f).coerceAtLeast(1f)
        }
        val inset = borderPaint.strokeWidth / 2f
        canvas.drawRoundRect(
            android.graphics.RectF(inset, inset, size - inset, size - inset),
            radius, radius, borderPaint
        )
        largeIconCache = output
        largeIconKey = cacheKey
        output
    } catch (_: Exception) { null }
}
