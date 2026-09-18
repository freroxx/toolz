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
import com.frerox.toolz.util.TaskAlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TaskBootReceiver"

/**
 * T-P0-03: re-schedule all future task alarms after reboot / update /
 * timezone change. Without this, every exact alarm is wiped on reboot and
 * future tasks never ring. Mirrors `CalendarRescheduleReceiver`.
 *
 * - Uses [goAsync] so the process survives the DB + schedule work.
 * - DB on IO via [TaskAlarmScheduler.rescheduleAllFuture] (one-shot sync read
 *   — the dead `getTasksWithDueDate` Flow is wired via `getTasksWithDueDateSync`).
 * - Never schedules the past; honors the reminders toggle.
 */
@AndroidEntryPoint
class TaskBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: TaskAlarmScheduler

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
                val count = scheduler.rescheduleAllFuture()
                Log.i(TAG, "Rescheduled $count future task alarms on $action")
            } catch (e: Exception) {
                Log.e(TAG, "Task reschedule failed on $action", e)
            } finally {
                pending.finish()
            }
        }
    }
}
