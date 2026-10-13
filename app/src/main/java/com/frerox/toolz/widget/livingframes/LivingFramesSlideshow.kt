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
import android.os.SystemClock
import com.frerox.toolz.data.livingframes.LivingFrame
import com.frerox.toolz.data.livingframes.LivingFrameEngine

/**
 * Slideshow timing. Photos change on minute/hour scales, so each widget gets
 * a one-shot inexact alarm that reschedules itself when it fires. State is
 * (interval, next-fire time, position) per widget in plain prefs.
 */
object LivingFramesSlideshow {

    val INTERVALS_MIN = listOf(5, 15, 60, 360, 1440)

    fun intervalLabel(mins: Int): String = when (mins) {
        5 -> "5 min"
        15 -> "15 min"
        60 -> "1 hour"
        360 -> "6 hours"
        1440 -> "Daily"
        else -> if (mins % 60 == 0) "${mins / 60} h" else "$mins min"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences("living_frames_slides", Context.MODE_PRIVATE)

    private fun pending(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, LivingFramesWidgetReceiver::class.java).apply {
            action = LivingFramesWidgetHelper.ACTION_SLIDE_ADVANCE
            putExtra(LivingFramesWidgetHelper.EXTRA_WIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context, 0x5A1D + appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, appWidgetId: Int, intervalMin: Int) {
        try {
            val mins = intervalMin.coerceIn(1, 1440)
            val next = SystemClock.elapsedRealtime() + mins * 60_000L
            prefs(context).edit()
                .putInt("mins_$appWidgetId", mins)
                .putLong("next_$appWidgetId", next)
                .apply()
            context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, next, pending(context, appWidgetId)
            )
        } catch (_: Exception) {}
    }

    /** Schedules only when nothing is pending or the pending time already passed (lost alarm). */
    fun ensureScheduled(context: Context, appWidgetId: Int, intervalMin: Int) {
        val p = prefs(context)
        val mins = p.getInt("mins_$appWidgetId", 0)
        val next = p.getLong("next_$appWidgetId", 0L)
        val lost = next == 0L || next < SystemClock.elapsedRealtime() - 60_000L
        if (mins != intervalMin.coerceIn(1, 1440) || lost) schedule(context, appWidgetId, intervalMin)
    }

    fun cancel(context: Context, appWidgetId: Int) {
        try {
            context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, appWidgetId))
        } catch (_: Exception) {}
        try {
            prefs(context).edit()
                .remove("mins_$appWidgetId").remove("next_$appWidgetId").remove("pos_$appWidgetId")
                .apply()
        } catch (_: Exception) {}
    }

    private fun seed(appWidgetId: Int, frame: LivingFrame): Long =
        frame.createdAt xor (appWidgetId.toLong() * 0x9E3779B97F4A7C15uL.toLong())

    fun currentIndex(context: Context, appWidgetId: Int, frame: LivingFrame): Int =
        LivingFrameEngine.slideIndex(
            prefs(context).getInt("pos_$appWidgetId", 0), frame.frameCount, frame.shuffle, seed(appWidgetId, frame)
        )

    /** Moves to the next photo and returns its index. */
    fun advance(context: Context, appWidgetId: Int, frame: LivingFrame): Int {
        val pos = prefs(context).getInt("pos_$appWidgetId", 0) + 1
        prefs(context).edit().putInt("pos_$appWidgetId", pos).apply()
        return LivingFrameEngine.slideIndex(pos, frame.frameCount, frame.shuffle, seed(appWidgetId, frame))
    }

    fun nextInMs(context: Context, appWidgetId: Int): Long {
        val next = prefs(context).getLong("next_$appWidgetId", 0L)
        return if (next == 0L) -1L else (next - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    fun nextLabel(context: Context, appWidgetId: Int): String? {
        val ms = nextInMs(context, appWidgetId)
        if (ms < 0) return null
        val mins = ms / 60_000L
        return when {
            ms < 60_000L -> "under a minute"
            mins < 60L -> "${mins} min"
            else -> "${mins / 60} h ${mins % 60} min"
        }
    }
}
