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
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.todo.TaskDao
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.service.TaskReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Objects
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TaskAlarmScheduler"

/** Lead time of the early reminder before the due date. */
const val TASK_REMINDER_LEAD_MILLIS = 15 * 60 * 1000L

/** Result of [TaskAlarmScheduler.scheduleReminder] — surfaced as UX, never silently dropped. */
enum class ScheduleOutcome {
    /** Early (-15min) + at-due alarms both armed. */
    SCHEDULED,

    /** Reminder time already passed but due is future: armed at due time instead. */
    AT_DUE_FALLBACK,

    /** Due already passed: nothing armed (caller cancels). */
    PAST,

    /** Task has no due date / is completed: nothing to arm. */
    SKIPPED,

    /** User disabled task reminders: cancelled instead of armed. */
    DISABLED
}

/**
 * T-P0-03 — Task reminder scheduling (single truth for alarm assembly).
 *
 * Contract (atomic with [TaskReminderReceiver], `TaskBootReceiver`, restore path):
 * - Gated on `taskReminderNotifications`: when off, schedule calls CANCEL.
 * - `reminder<=now<due` (due in 5min, midnight-past, etc.) NEVER silently drops:
 *   arms the at-due alarm directly ([ScheduleOutcome.AT_DUE_FALLBACK]).
 * - Never schedules the past.
 * - Two alarms (early + at-due) use DISTINCT stable requestCodes
 *   ([reminderRequestCode]/[dueRequestCode]); never reuse one code for both.
 * - [cancelReminder] also cancels the legacy `task.id` code so pre-update
 *   alarms don't leak after this release.
 */
@Singleton
class TaskAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val taskDao: TaskDao
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_REMINDER_KIND = "reminder_kind"
        const val KIND_EARLY = "early"
        const val KIND_AT_DUE = "at_due"

        /** Stable, namespaced requestCodes — never `taskId` / `taskId+1000` (collide). */
        fun reminderRequestCode(taskId: Int): Int = Objects.hash(taskId, "task_reminder_early")

        fun dueRequestCode(taskId: Int): Int = Objects.hash(taskId, "task_reminder_due")

        fun doneRequestCode(taskId: Int): Int = Objects.hash(taskId, "task_done")
    }

    suspend fun scheduleReminder(task: TaskEntry): ScheduleOutcome {
        val dueDate = task.dueDate ?: return ScheduleOutcome.SKIPPED
        if (task.isCompleted) {
            cancelReminder(task)
            return ScheduleOutcome.SKIPPED
        }
        val enabled = try {
            settingsRepository.taskReminderNotifications.first()
        } catch (e: Exception) {
            Log.w(TAG, "toggle read failed, defaulting ON", e)
            true
        }
        if (!enabled) {
            cancelReminder(task)
            return ScheduleOutcome.DISABLED
        }
        val now = System.currentTimeMillis()
        if (dueDate <= now) {
            cancelReminder(task)
            return ScheduleOutcome.PAST
        }
        val reminderTime = dueDate - TASK_REMINDER_LEAD_MILLIS
        return if (reminderTime > now) {
            arm(task.id, reminderTime, KIND_EARLY, reminderRequestCode(task.id))
            arm(task.id, dueDate, KIND_AT_DUE, dueRequestCode(task.id))
            ScheduleOutcome.SCHEDULED
        } else {
            // Due too soon (or midnight-normalization left <15min): remind AT due.
            cancelEarlyOnly(task.id)
            arm(task.id, dueDate, KIND_AT_DUE, dueRequestCode(task.id))
            ScheduleOutcome.AT_DUE_FALLBACK
        }
    }

    fun cancelReminder(task: TaskEntry) {
        cancelReminder(task.id)
    }

    fun cancelReminder(taskId: Int) {
        // New codes + legacy pre-update code (early alarm used bare task.id).
        cancel(reminderRequestCode(taskId), KIND_EARLY, taskId)
        cancel(dueRequestCode(taskId), KIND_AT_DUE, taskId)
        cancel(taskId, KIND_EARLY, taskId) // legacy
    }

    /**
     * Re-arms every future, enabled reminder. Used by `TaskBootReceiver`
     * (reboot/timezone/update), the restore path, and toggle-on.
     * @return count of tasks re-armed.
     */
    suspend fun rescheduleAllFuture(): Int {
        val enabled = try {
            settingsRepository.taskReminderNotifications.first()
        } catch (e: Exception) {
            Log.w(TAG, "toggle read failed during reschedule, defaulting ON", e)
            true
        }
        if (!enabled) return 0
        val tasks = try {
            taskDao.getTasksWithDueDateSync()
        } catch (e: Exception) {
            Log.e(TAG, "getTasksWithDueDateSync failed", e)
            return 0
        }
        var count = 0
        val now = System.currentTimeMillis()
        tasks.filter { !it.isCompleted && (it.dueDate ?: 0L) > now }.forEach {
            try {
                if (scheduleReminder(it) != ScheduleOutcome.PAST) count++
            } catch (e: Exception) {
                Log.w(TAG, "Reschedule failed for task ${it.id}", e)
            }
        }
        Log.i(TAG, "Rescheduled $count future task alarms")
        return count
    }

    /** Cancels every armed task alarm (toggle-off path). @return count cancelled. */
    suspend fun cancelAll(): Int {
        val tasks = try {
            taskDao.getTasksWithDueDateSync()
        } catch (e: Exception) {
            Log.e(TAG, "cancelAll read failed", e)
            return 0
        }
        tasks.forEach { cancelReminder(it.id) }
        return tasks.size
    }

    private fun arm(taskId: Int, triggerAt: Long, kind: String, requestCode: Int) {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = TaskReminderReceiver.ACTION_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_REMINDER_KIND, kind)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Inexact fallback (hours-late possible): still arm; the Todo UI
                // shows an exact-permission banner so the user can fix it.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancelEarlyOnly(taskId: Int) {
        cancel(reminderRequestCode(taskId), KIND_EARLY, taskId)
        cancel(taskId, KIND_EARLY, taskId) // legacy
    }

    private fun cancel(requestCode: Int, kind: String, taskId: Int) {
        try {
            val intent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = TaskReminderReceiver.ACTION_REMINDER
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_REMINDER_KIND, kind)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
            pi.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "cancel failed for $taskId/$kind", e)
        }
    }
}
