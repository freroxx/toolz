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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine

/**
 * Slideshow timing. Photos change on minute/hour scales, so each widget gets
 * a one-shot inexact alarm that reschedules itself when it fires. State is
 * (interval, next-fire wall time, position) per widget in plain prefs.
 *
 * RTC_WAKEUP is deliberate: ELAPSED_REALTIME is relative to boot and made a
 * saved "next" timestamp meaningless after a reboot. Wall time also lets the
 * watchdog re-arm an alarm that a force-stop silently removed: re-arming the
 * same future timestamp is idempotent.
 */
object LivingFramesSlideshow {

    val INTERVALS_MIN = listOf(1, 5, 15, 30, 60, 180, 360, 720, 1440)

    fun intervalLabel(mins: Int): String = when (mins) {
        1 -> "1 min"
        5 -> "5 min"
        15 -> "15 min"
        30 -> "30 min"
        60 -> "1 hour"
        180 -> "3 hours"
        360 -> "6 hours"
        720 -> "12 hours"
        1440 -> "Daily"
        else -> if (mins % 60 == 0) "${mins / 60} h" else "$mins min"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("living_frames_slides", Context.MODE_PRIVATE)

    private fun pending(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, LivingFramesActionReceiver::class.java).apply {
            action = LivingFramesWidgetHelper.ACTION_SLIDE_ADVANCE
            putExtra(LivingFramesWidgetHelper.EXTRA_WIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context, 0x5A1D + appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, appWidgetId: Int, intervalMin: Int) {
        val mins = intervalMin.coerceIn(1, 1440)
        val next = System.currentTimeMillis() + mins * 60_000L
        try {
            prefs(context).edit()
                .putInt("mins_$appWidgetId", mins)
                .putLong("next_$appWidgetId", next)
                .apply()
            armAt(context, appWidgetId, next)
        } catch (_: Exception) {
        }
    }

    /**
     * Repairs lost alarms (force-stop, OEM cleanup, reboot) without moving a
     * still-valid schedule. Called by every refresh and by the watchdog.
     *
     * Returns true when the saved slot was already overdue and the slideshow
     * advanced immediately. The caller must render that new photo; merely
     * re-arming would leave the old photo up for another whole interval.
     */
    @Synchronized
    fun ensureScheduled(context: Context, appWidgetId: Int, frame: LivingFrame): Boolean {
        val p = prefs(context)
        val mins = frame.slideshowIntervalMin.coerceIn(1, 1440)
        val storedMins = p.getInt("mins_$appWidgetId", 0)
        val next = p.getLong("next_$appWidgetId", 0L)
        val now = System.currentTimeMillis()
        if (storedMins != mins || next <= 0L) {
            schedule(context, appWidgetId, mins)
            return false
        }
        if (next <= now) {
            advance(context, appWidgetId, frame)
            schedule(context, appWidgetId, mins)
            return true
        }
        // AlarmManager may have dropped it during force-stop; re-arming
        // the same timestamp cannot create an early/late tick.
        armAt(context, appWidgetId, next)
        return false
    }

    private fun armAt(context: Context, appWidgetId: Int, triggerAtMs: Long) {
        try {
            context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMs,
                pending(context, appWidgetId)
            )
        } catch (_: Exception) {
        }
    }

    fun cancel(context: Context, appWidgetId: Int) {
        try {
            context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, appWidgetId))
        } catch (_: Exception) {
        }
        try {
            prefs(context).edit()
                .remove("mins_$appWidgetId")
                .remove("next_$appWidgetId")
                .remove("pos_$appWidgetId")
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun seed(appWidgetId: Int, frame: LivingFrame): Long =
        frame.createdAt xor (appWidgetId.toLong() * 0x9E3779B97F4A7C15uL.toLong())

    fun currentIndex(context: Context, appWidgetId: Int, frame: LivingFrame): Int =
        LivingFrameEngine.slideIndex(
            prefs(context).getInt("pos_$appWidgetId", 0),
            frame.frameCount,
            frame.shuffle,
            seed(appWidgetId, frame)
        )

    /** Moves to the next photo and returns its index. */
    fun advance(context: Context, appWidgetId: Int, frame: LivingFrame): Int {
        val pos = prefs(context).getInt("pos_$appWidgetId", 0) + 1
        prefs(context).edit().putInt("pos_$appWidgetId", pos).apply()
        return LivingFrameEngine.slideIndex(
            pos, frame.frameCount, frame.shuffle, seed(appWidgetId, frame)
        )
    }

    /**
     * Manual "next photo" tap: move the cursor and start a fresh interval.
     * Synchronized with [ensureScheduled] so a tick that lands at the same
     * moment cannot also advance and skip a photo.
     */
    @Synchronized
    fun advanceNow(context: Context, appWidgetId: Int, frame: LivingFrame) {
        advance(context, appWidgetId, frame)
        schedule(context, appWidgetId, frame.slideshowIntervalMin)
    }

    fun nextInMs(context: Context, appWidgetId: Int): Long {
        val next = prefs(context).getLong("next_$appWidgetId", 0L)
        return if (next <= 0L) {
            -1L
        } else {
            (next - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }

    fun nextLabel(context: Context, appWidgetId: Int): String? {
        val ms = nextInMs(context, appWidgetId)
        if (ms < 0) return null
        val mins = ms / 60_000L
        return when {
            ms < 60_000L -> "under a minute"
            mins < 60L -> "$mins min"
            else -> "${mins / 60} h ${mins % 60} min"
        }
    }
}
