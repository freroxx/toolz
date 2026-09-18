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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.frerox.toolz.data.calendar.EventDao
import com.frerox.toolz.util.CalendarAlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CalendarReschedule"

/**
 * CAL-P0-03: re-schedule all future calendar alarms after reboot / update /
 * timezone change. Without this, every exact alarm is wiped on reboot and
 * future events never ring.
 *
 * - Uses [goAsync] so the process survives the DB + schedule work.
 * - DB on IO, never on main. Never schedules past events.
 */
@AndroidEntryPoint
class CalendarRescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var eventDao: EventDao

    @Inject
    lateinit var scheduler: CalendarAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val watched = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        if (action !in watched) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                // Avoid full-table load — only future events can still fire.
                val upcoming = try {
                    eventDao.getUpcomingSync(now)
                } catch (e: Exception) {
                    Log.e(TAG, "getUpcomingSync failed, falling back to filtered full load", e)
                    eventDao.getAllEventsSync().filter { it.timestamp > now }
                }
                var count = 0
                upcoming
                    .filter { it.timestamp > now && it.remindersEnabled && !it.isCompleted }
                    .forEach {
                        try {
                            scheduler.scheduleEventReminders(it)
                            count++
                        } catch (e: Exception) {
                            Log.w(TAG, "Reschedule failed for event ${it.id}", e)
                        }
                    }
                Log.i(TAG, "Rescheduled $count future event alarms on $action")
            } catch (e: Exception) {
                Log.e(TAG, "Calendar reschedule failed on $action", e)
            } finally {
                pending.finish()
            }
        }
    }
}
