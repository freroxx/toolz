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

package com.frerox.toolz.widget.livingframes

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameFit
import com.frerox.toolz.data.livingframes.LivingFrameTapAction
import com.frerox.toolz.ui.navigation.Screen

/**
 * RemoteViews helpers for Living Widgets.
 *
 * Push-budget table (Binder ~1MB shared limit per transaction):
 * - Animated (GIF/video, ARGB_8888 for transparency): ≤448px → ≤800KB worst.
 * - Static (photo/slideshow, RGB_565, opaque): ≤720px → ≤1MB worst, ~600KB typical.
 * - Frame flips use FULL updateAppWidget: some launchers (MIUI/ColorOS)
 *   silently drop partiallyUpdateAppWidget, which wedges animation on frame 0.
 */
object LivingFramesWidgetHelper {

    const val ACTION_TAP = "com.frerox.toolz.LIVING_FRAME_TAP"
    const val ACTION_NEXT = "com.frerox.toolz.LIVING_FRAME_NEXT"
    const val ACTION_SLIDE_ADVANCE = "com.frerox.toolz.LIVING_FRAME_SLIDE"
    const val EXTRA_WIDGET_ID = "living_frame_widget_id"

    /** Slideshow intervals offered in the UI (minutes). */
    val SLIDESHOW_INTERVALS_MIN = listOf(5, 15, 60, 360, 1440)

    fun slideshowIntervalLabel(mins: Int): String = when (mins) {
        5 -> "5 min"
        15 -> "15 min"
        60 -> "1 hour"
        360 -> "6 hours"
        1440 -> "Daily"
        else -> "$mins min"
    }

    fun allWidgetIds(context: Context): IntArray = try {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, LivingFramesWidgetReceiver::class.java))
    } catch (_: Exception) { intArrayOf() }

    fun buildTapIntent(context: Context, appWidgetId: Int, frame: LivingFrame): android.app.PendingIntent {
        val intent = when (frame.tapAction) {
            LivingFrameTapAction.NOTHING -> Intent(context, LivingFramesWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            LivingFrameTapAction.NEXT_FRAME -> Intent(context, LivingFramesWidgetReceiver::class.java).apply {
                action = ACTION_NEXT
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
            }
            LivingFrameTapAction.OPEN_PREVIEW -> Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.LivingWidgets.route)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        return if (frame.tapAction == LivingFrameTapAction.OPEN_PREVIEW) {
            android.app.PendingIntent.getActivity(context, appWidgetId, intent, flags)
        } else {
            android.app.PendingIntent.getBroadcast(context, appWidgetId, intent, flags)
        }
    }

    fun fullViews(context: Context, appWidgetId: Int, frame: LivingFrame, bitmap: Bitmap?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.living_frames_widget)
        val showFit = frame.fit == LivingFrameFit.FIT
        val visibleId = if (showFit) R.id.living_frame_image_fit else R.id.living_frame_image_crop
        val hiddenId = if (showFit) R.id.living_frame_image_crop else R.id.living_frame_image_fit
        if (bitmap != null) {
            views.setImageViewBitmap(visibleId, bitmap)
        } else {
            views.setImageViewResource(visibleId, R.drawable.ic_widget_play)
        }
        try {
            views.setViewVisibility(visibleId, android.view.View.VISIBLE)
            views.setViewVisibility(hiddenId, android.view.View.GONE)
        } catch (_: Exception) {}
        if (frame.showCaption) {
            try {
                views.setTextViewText(R.id.living_frame_caption, frame.name)
                views.setViewVisibility(R.id.living_frame_caption, android.view.View.VISIBLE)
            } catch (_: Exception) {}
        } else {
            try {
                views.setViewVisibility(R.id.living_frame_caption, android.view.View.GONE)
            } catch (_: Exception) {}
        }
        try {
            views.setOnClickPendingIntent(R.id.living_frame_root, buildTapIntent(context, appWidgetId, frame))
        } catch (_: Exception) {}
        views.setContentDescription(visibleId, frame.name)
        return views
    }

    fun placeholderViews(context: Context, text: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.living_frames_widget)
        views.setImageViewResource(R.id.living_frame_image_crop, R.drawable.ic_music_note)
        try {
            views.setViewVisibility(R.id.living_frame_image_crop, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.living_frame_image_fit, android.view.View.GONE)
        } catch (_: Exception) {}
        views.setContentDescription(R.id.living_frame_image_crop, text)
        return views
    }

    fun pushFull(context: Context, appWidgetId: Int, views: RemoteViews): Boolean {
        return try {
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
            true
        } catch (_: Exception) { false }
    }

    /**
     * Push one animation frame. Always a FULL updateAppWidget: partial
     * updates are silently ignored by several OEM launchers.
     * Returns false when the IPC itself failed.
     */
    fun pushFrame(context: Context, appWidgetId: Int, frame: LivingFrame, bitmap: Bitmap): Boolean {
        return pushFull(context, appWidgetId, fullViews(context, appWidgetId, frame, bitmap))
    }

    // -- pending pin binding ---------------------------------------------------
    // requestPinAppWidget carries no extras to onUpdate, so the tool records
    // which set the user pinned; the receiver consumes it for unbound widgets.

    private const val PIN_PREFS = "living_frames_pin"
    private const val KEY_PENDING_ID = "pending_frame_id"
    private const val KEY_PENDING_AT = "pending_at_ms"

    fun setPendingPinFrame(context: Context, frameId: String) {
        try {
            context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_ID, frameId)
                .putLong(KEY_PENDING_AT, android.os.SystemClock.elapsedRealtime())
                .apply()
        } catch (_: Exception) {}
    }

    /** Consume the pending pin target (expires after 2 minutes). */
    fun takePendingPinFrame(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE)
            val id = prefs.getString(KEY_PENDING_ID, null)
            val at = prefs.getLong(KEY_PENDING_AT, 0L)
            if (id == null || android.os.SystemClock.elapsedRealtime() - at > 120_000L) {
                null
            } else {
                try { prefs.edit().remove(KEY_PENDING_ID).remove(KEY_PENDING_AT).apply() } catch (_: Exception) {}
                id
            }
        } catch (_: Exception) { null }
    }

    fun requestPin(context: Context, frame: LivingFrame): Boolean {
        return try {
            val mgr = context.getSystemService(AppWidgetManager::class.java) ?: return false
            if (!mgr.isRequestPinAppWidgetSupported) return false
            val provider = ComponentName(context, LivingFramesWidgetReceiver::class.java)
            // Bind after pin via EXTRA bind callback — receiver onUpdate picks the
            // most-recent frame when no binding exists yet (see receiver).
            val success = android.app.PendingIntent.getBroadcast(
                context, frame.id.hashCode(),
                Intent(context, LivingFramesWidgetReceiver::class.java).apply {
                    action = LivingFramesWidgetReceiver.ACTION_PINNED
                    putExtra(LivingFramesWidgetReceiver.EXTRA_FRAME_ID, frame.id)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )
            mgr.requestPinAppWidget(provider, null, success)
            true
        } catch (_: Exception) { false }
    }

    /**
     * Master toggle for the 4 Glance app widgets (Music / Pomodoro / Toolbar /
     * Stopwatch). Living Frames widgets are NEVER affected.
     */
    fun setAppWidgetsComponentEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val classes = listOf(
            "com.frerox.toolz.widget.glance.MusicWidgetReceiver",
            "com.frerox.toolz.widget.glance.PomodoroWidgetReceiver",
            "com.frerox.toolz.widget.glance.SearchBarWidgetReceiver",
            "com.frerox.toolz.widget.glance.TimerWidgetReceiver"
        )
        classes.forEach { cls ->
            try {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, cls),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}
        }
    }

    // -- playback status heartbeat -----------------------------------------
    // The service writes a timestamp every ~2s while its loop runs. The tool
    // reads it to show Animating/Paused — no IPC, no ActivityManager hacks.

    private const val STATUS_PREFS = "living_frames_status"
    private const val KEY_BEAT_MS = "last_beat_elapsed_ms"
    private const val KEY_SCREEN_ON = "beat_screen_on"
    private const val KEY_SAVER = "beat_saver_on"

    fun writeHeartbeat(context: Context, screenOn: Boolean = true, saverOn: Boolean = false) {
        try {
            context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_BEAT_MS, android.os.SystemClock.elapsedRealtime())
                .putBoolean(KEY_SCREEN_ON, screenOn)
                .putBoolean(KEY_SAVER, saverOn)
                .apply()
        } catch (_: Exception) {}
    }

    data class PlaybackStatus(
        val animating: Boolean,
        val widgetCount: Int,
        /** Machine reason when not animating: SCREEN_OFF, SAVER, IDLE, DEAD. */
        val pausedReason: String? = null
    )

    fun readStatus(context: Context): PlaybackStatus {
        val ids = try { allWidgetIds(context) } catch (_: Exception) { intArrayOf() }
        if (ids.isEmpty()) return PlaybackStatus(false, 0, "IDLE")
        return try {
            val prefs = context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE)
            val last = prefs.getLong(KEY_BEAT_MS, 0L)
            val fresh = last != 0L && android.os.SystemClock.elapsedRealtime() - last < 8000L
            if (fresh) {
                // A fresh beat during saver/screen-off means "alive but idle".
                return when {
                    prefs.getBoolean(KEY_SAVER, false) -> PlaybackStatus(false, ids.size, "SAVER")
                    !prefs.getBoolean(KEY_SCREEN_ON, true) -> PlaybackStatus(false, ids.size, "SCREEN_OFF")
                    else -> PlaybackStatus(true, ids.size, null)
                }
            }
            // Stale beat: surface the last known halt reason instead of a green lie.
            val reason = when {
                prefs.getBoolean(KEY_SAVER, false) -> "SAVER"
                !prefs.getBoolean(KEY_SCREEN_ON, true) -> "SCREEN_OFF"
                last == 0L -> "DEAD"
                else -> "DEAD"
            }
            PlaybackStatus(false, ids.size, reason)
        } catch (_: Exception) {
            PlaybackStatus(false, ids.size, "DEAD")
        }
    }

    /**
     * Start playback from a foreground context (tool UI, pin flow). Unlike
     * [LivingFramesPlaybackService.ensureRunning], callers must only invoke
     * this while the app is in the foreground — FGS starts from the
     * background are denied on Android 12+.
     */
    fun startPlaybackFromForeground(context: Context) {
        try {
            val i = Intent(context, LivingFramesPlaybackService::class.java)
                .setAction(LivingFramesPlaybackService.ACTION_REFRESH)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        } catch (_: Exception) {
            LivingFramesPlaybackService.ensureRunning(context)
        }
    }

    // -- slideshow scheduler (AlarmManager — no service needed) ---------------
    // Slideshows change photos on minute/hour scales, so each widget gets a
    // one-shot ELAPSED_REALTIME alarm that reschedules itself on fire.

    private const val SLIDE_PREFS = "living_frames_slides"

    private fun slidePrefs(context: Context) =
        context.getSharedPreferences(SLIDE_PREFS, Context.MODE_PRIVATE)

    private fun slidePending(context: Context, appWidgetId: Int): android.app.PendingIntent {
        val intent = Intent(context, LivingFramesWidgetReceiver::class.java).apply {
            action = ACTION_SLIDE_ADVANCE
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
        }
        return android.app.PendingIntent.getBroadcast(
            context, 0x5A1D + appWidgetId, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        )
    }

    fun scheduleSlideshow(context: Context, appWidgetId: Int, intervalMin: Int) {
        try {
            cancelSlideshow(context, appWidgetId)
            val mins = intervalMin.coerceIn(1, 1440)
            val next = android.os.SystemClock.elapsedRealtime() + mins * 60_000L
            slidePrefs(context).edit()
                .putInt("mins_$appWidgetId", mins)
                .putLong("next_$appWidgetId", next)
                .apply()
            val alarm = context.getSystemService(android.app.AlarmManager::class.java)
                ?: return
            alarm.setAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                next,
                slidePending(context, appWidgetId)
            )
        } catch (_: Exception) {}
    }

    fun cancelSlideshow(context: Context, appWidgetId: Int) {
        try {
            val alarm = context.getSystemService(android.app.AlarmManager::class.java)
            alarm?.cancel(slidePending(context, appWidgetId))
        } catch (_: Exception) {}
        try {
            slidePrefs(context).edit()
                .remove("mins_$appWidgetId")
                .remove("next_$appWidgetId")
                .remove("idx_$appWidgetId")
                .apply()
        } catch (_: Exception) {}
    }

    fun slideIntervalMin(context: Context, appWidgetId: Int): Int =
        try { slidePrefs(context).getInt("mins_$appWidgetId", 0) } catch (_: Exception) { 0 }

    fun slideNextInMs(context: Context, appWidgetId: Int): Long {
        return try {
            val next = slidePrefs(context).getLong("next_$appWidgetId", 0L)
            if (next == 0L) -1L
            else (next - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } catch (_: Exception) { -1L }
    }

    fun slideIndex(context: Context, appWidgetId: Int): Int =
        try { slidePrefs(context).getInt("idx_$appWidgetId", 0).coerceAtLeast(0) } catch (_: Exception) { 0 }

    /** Advance slideshow cursor (sequential or shuffled) and persist it. */
    fun advanceSlideIndex(context: Context, appWidgetId: Int, count: Int, shuffle: Boolean): Int {
        val next = if (count <= 1) 0
        else if (shuffle && count > 2) {
            val cur = slideIndex(context, appWidgetId)
            var n = cur
            var guard = 0
            while (n == cur && guard++ < 8) n = (Math.random() * count).toInt()
            n
        } else (slideIndex(context, appWidgetId) + 1) % count
        try { slidePrefs(context).edit().putInt("idx_$appWidgetId", next).apply() } catch (_: Exception) {}
        return next
    }

    fun slideNextInLabel(context: Context, appWidgetId: Int): String? {
        val ms = slideNextInMs(context, appWidgetId)
        if (ms < 0) return null
        val mins = ms / 60_000L
        return when {
            ms < 60_000L -> "in under a minute"
            mins < 60L -> "in ${mins}m"
            else -> "in ${mins / 60}h ${mins % 60}m"
        }
    }

    // -- per-widget diagnostics ------------------------------------------------
    // Service records last pushed frame per widget (throttled); the tool reads
    // it back so "doesn't work" is always answerable: loop dead vs pushes lost.

    private const val DIAG_PREFS = "living_frames_diag"

    fun writeDiag(context: Context, appWidgetId: Int, index: Int, total: Int) {
        try {
            context.getSharedPreferences(DIAG_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("d_$appWidgetId", "$index/$total@${android.os.SystemClock.elapsedRealtime()}")
                .apply()
        } catch (_: Exception) {}
    }

    /** widgetId -> "7/24 · 3s ago". */
    fun readDiag(context: Context): Map<Int, String> {
        return try {
            val prefs = context.getSharedPreferences(DIAG_PREFS, Context.MODE_PRIVATE)
            val now = android.os.SystemClock.elapsedRealtime()
            prefs.all.mapNotNull { (k, v) ->
                val id = k.removePrefix("d_").toIntOrNull() ?: return@mapNotNull null
                val raw = v as? String ?: return@mapNotNull null
                val parts = raw.split("@")
                if (parts.size != 2) return@mapNotNull null
                val ageMs = (now - (parts[1].toLongOrNull() ?: 0L)).coerceAtLeast(0L)
                val ago = when {
                    ageMs < 5_000L -> "just now"
                    ageMs < 60_000L -> "${ageMs / 1000}s ago"
                    else -> "${ageMs / 60_000}m ago"
                }
                id to "${parts[0]} · $ago"
            }.toMap()
        } catch (_: Exception) { emptyMap() }
    }

    fun clearDiag(context: Context, appWidgetId: Int) {
        try {
            context.getSharedPreferences(DIAG_PREFS, Context.MODE_PRIVATE)
                .edit().remove("d_$appWidgetId").apply()
        } catch (_: Exception) {}
    }
}
