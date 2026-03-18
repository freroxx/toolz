package com.frerox.toolz.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.frerox.toolz.data.calendar.EventEntry
import com.frerox.toolz.service.EventReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleEventReminders(event: EventEntry) {
        if (event.isCompleted || !event.remindersEnabled) {
            cancelEventReminders(event)
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
        if (triggerTime <= System.currentTimeMillis()) return

        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("reminder_type", type)
        }

        // Unique requestCode for each reminder type of each event
        val requestCode = event.id * 10 + when(type) {
            "24H" -> 1
            "12H" -> 2
            "1H" -> 3
            else -> 0
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelEventReminders(event: EventEntry) {
        listOf("24H", "12H", "1H").forEach { type ->
            val requestCode = event.id * 10 + when(type) {
                "24H" -> 1
                "12H" -> 2
                "1H" -> 3
                else -> 0
            }
            val intent = Intent(context, EventReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
