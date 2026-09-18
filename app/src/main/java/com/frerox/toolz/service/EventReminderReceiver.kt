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

package com.frerox.toolz.service

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.calendar.EventDao
import com.frerox.toolz.ui.screens.calendar.AlarmIntentKeys
import com.frerox.toolz.ui.screens.calendar.EventAlarmActivity
import com.frerox.toolz.util.CalendarUtils
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "EventReminderReceiver"

@AndroidEntryPoint
class EventReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var eventDao: EventDao

    override fun onReceive(context: Context, intent: Intent) {
        // FIX: shared AlarmIntentKeys (was raw literals — same values, one contract).
        val eventId = intent.getIntExtra(AlarmIntentKeys.EVENT_ID, -1)
        val type = intent.getStringExtra(AlarmIntentKeys.REMINDER_TYPE) ?: return // "24H", "12H", "1H"

        if (eventId == -1) {
            Log.w(TAG, "Missing event_id, ignoring alarm")
            return
        }

        // CAL-P0-07: goAsync keeps the process alive until DB fetch + notify/FSI complete.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // CAL-P0-07: single-row fetch (no full-table O(n) + SQLCipher open per firing).
                val event = try {
                    eventDao.getEventByIdSync(eventId)
                } catch (e: Exception) {
                    Log.e(TAG, "getEventByIdSync failed for $eventId", e)
                    null
                } ?: return@launch

                // Defense if cancel missed: never fire for disabled/completed/past.
                if (!event.remindersEnabled || event.isCompleted) return@launch
                // FIX: stale-past recheck — a Doze-delayed or orphaned PendingIntent
                // (e.g. pre-fix requestCode scheme) must not notify for a long-past
                // event. Finished long ago = drop silently with a log.
                if (event.timestamp <= System.currentTimeMillis() - 60_000L) {
                    Log.i(TAG, "Dropping stale $type alarm for past event $eventId")
                    return@launch
                }

                val eventTime = event.timestamp

                if (type == "1H") {
                    // CAL-P0-05: background startActivity is blocked on API 29+ when
                    // backgrounded/locked. Post HIGH notification with full-screen intent;
                    // keep direct start only as foreground fallback.
                    if (isAppForeground(context)) {
                        try {
                            val alarmIntent = Intent(context, EventAlarmActivity::class.java).apply {
                                putExtra(AlarmIntentKeys.EVENT_ID, event.id)
                                putExtra(AlarmIntentKeys.EVENT_TITLE, event.title)
                                putExtra(AlarmIntentKeys.EVENT_TIME, eventTime)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            context.startActivity(alarmIntent)
                        } catch (e: Exception) {
                            Log.w(TAG, "Foreground startActivity failed, falling back to FSI", e)
                            showAlarmNotification(context, event.id, event.title, eventTime, type)
                        }
                    } else {
                        showAlarmNotification(context, event.id, event.title, eventTime, type)
                    }
                } else {
                    showReminderNotification(context, event.id, event.title, eventTime, type)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Event reminder failed for $eventId/$type", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun isAppForeground(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pkg = context.packageName
            @Suppress("DEPRECATION")
            am.runningAppProcesses?.any {
                it.processName == pkg &&
                    it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun alarmFullScreenPendingIntent(
        context: Context, eventId: Int, title: String, eventTime: Long
    ): PendingIntent {
        val fullScreenIntent = Intent(context, EventAlarmActivity::class.java).apply {
            putExtra(AlarmIntentKeys.EVENT_ID, eventId)
            putExtra(AlarmIntentKeys.EVENT_TITLE, title)
            putExtra(AlarmIntentKeys.EVENT_TIME, eventTime)
        }
        // Distinct requestCode per event (snooze uses its own namespace in CalendarUtils).
        val rc = CalendarUtils.alarmRequestCode(eventId, "1H")
        return PendingIntent.getActivity(
            context, rc, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun contentPendingIntent(
        context: Context, eventId: Int, type: String
    ): PendingIntent {
        // CAL-P0-07: deep-link MainActivity(navigate=calendar, event_id) via TaskStackBuilder.
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "calendar")
            putExtra("event_id", eventId)
        }
        val rc = CalendarUtils.alarmRequestCode(eventId, type) + 100_000
        return TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(rc, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } ?: PendingIntent.getActivity(
            context, rc, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun leadText(type: String, eventTime: Long): String {
        val whenFmt = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        val whenStr = try { whenFmt.format(Date(eventTime)) } catch (_: Exception) { "" }
        return when (type) {
            "24H" -> "Starts in 24 hours — $whenStr"
            "12H" -> "Starts in 12 hours — $whenStr"
            "1H" -> "Starts in 1 hour — $whenStr"
            else -> "Starting soon — $whenStr"
        }
    }

    private fun showAlarmNotification(
        context: Context, eventId: Int, title: String, eventTime: Long, type: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !nm.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled — 1H alarm for $eventId has no visual path")
            return
        }
        NotificationHelper.createAllChannels(context)
        // CAL-P0-05: FSI + CATEGORY_ALARM so it shows over lock screen (USE_FULL_SCREEN_INTENT declared).
        val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_EVENT_REMINDERS)
            .setContentTitle(title)
            .setContentText(leadText(type, eventTime))
            .setSmallIcon(R.drawable.ic_stat_event)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentPendingIntent(context, eventId, type))
            .setFullScreenIntent(alarmFullScreenPendingIntent(context, eventId, title, eventTime), true)
            .setWhen(eventTime)
            .build()
        // CAL-P0-07: distinct ID per lead so 24H/12H don't overwrite each other.
        nm.notify(CalendarUtils.notificationId(eventId, type), notification)
    }

    private fun showReminderNotification(
        context: Context, eventId: Int, title: String, eventTime: Long, type: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !nm.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled — $type reminder for $eventId suppressed")
            return
        }
        NotificationHelper.createAllChannels(context)
        val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_EVENT_REMINDERS)
            .setContentTitle(title)
            .setContentText(leadText(type, eventTime))
            .setSmallIcon(R.drawable.ic_stat_event)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(contentPendingIntent(context, eventId, type))
            .setWhen(eventTime)
            .build()
        nm.notify(CalendarUtils.notificationId(eventId, type), notification)
    }
}
