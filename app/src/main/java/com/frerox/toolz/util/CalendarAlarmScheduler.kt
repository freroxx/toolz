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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.frerox.toolz.data.calendar.EventEntry
import com.frerox.toolz.service.EventReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CalendarAlarmScheduler"

@Singleton
class CalendarAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleEventReminders(event: EventEntry) {
        // CAL-P0-04: cancel-before-schedule FIRST — stale PendingIntents from a
        // previous time must never survive an edit (incl. future->past moves).
        // Keep this line even when early-returning below.
        cancelEventReminders(event)

        if (event.isCompleted || !event.remindersEnabled) {
            return
        }

        // 24 Hours before
        schedule(event, "24H", event.timestamp - (24 * 60 * 60 * 1000))
        // 12 Hours before
        schedule(event, "12H", event.timestamp - (12 * 60 * 60 * 1000))
        // 1 Hour before (Alarm)
        schedule(event, "1H", event.timestamp - (60 * 60 * 1000))
    }

    private fun schedule(event: EventEntry, type: String, triggerTime: Long) {
        // Past triggers are skipped — but the cancel above already ran, so no
        // stale alarm survives a future->past edit (CAL-P0-04).
        if (triggerTime <= System.currentTimeMillis()) return

        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("reminder_type", type)
            putExtra("event_time", event.timestamp)
        }

        // CAL-P0-08: centralized requestCode scheme (see CalendarUtils).
        val requestCode = CalendarUtils.alarmRequestCode(event.id, type)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            // CAL-P0-06: revoked exact-alarm permission must not crash — fall back.
            Log.w(TAG, "Exact alarm denied for event ${event.id}/$type, using inexact fallback", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback alarm also failed for event ${event.id}/$type", e2)
            }
        }
    }

    fun cancelEventReminders(event: EventEntry) {
        listOf("24H", "12H", "1H").forEach { type ->
            // CAL-P0-08: FLAG_NO_CREATE — never create a PendingIntent just to cancel it.
            val requestCode = CalendarUtils.alarmRequestCode(event.id, type)
            val intent = Intent(context, EventReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
