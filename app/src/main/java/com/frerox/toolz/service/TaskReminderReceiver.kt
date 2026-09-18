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
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.todo.TaskDao
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.util.TaskAlarmScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TaskReminderReceiver"

/**
 * T-P0-03 — Task reminder firing (atomic with [TaskAlarmScheduler]).
 *
 * - `goAsync()` + `finish()`: never killed mid-DB-fetch.
 * - Validates `task_id != -1`; awaits the DB row BEFORE touching notifications.
 * - Gated on `taskReminderNotifications` (dead toggle fix): when off, cancels
 *   instead of showing.
 * - DONE uses the namespaced [ACTION_DONE] (legacy bare `ACTION_DONE` still
 *   accepted for in-flight notifications, never sent).
 * - Tap deep-links Todo via `TaskStackBuilder` (was: home).
 */
@AndroidEntryPoint
class TaskReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var settingsRepository: SettingsRepository

    companion object {
        const val ACTION_REMINDER = "com.frerox.toolz.action.TASK_REMINDER"
        const val ACTION_DONE = "com.frerox.toolz.action.TASK_DONE"

        /** Legacy pre-namespace action — accept, never send. */
        const val ACTION_DONE_LEGACY = "ACTION_DONE"

        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_NAVIGATE_TO = MainActivity.EXTRA_NAVIGATE_TO
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        val action = intent.action

        if ((action == ACTION_DONE || action == ACTION_DONE_LEGACY) && taskId != -1) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    handleDoneAction(taskId)
                } catch (e: Exception) {
                    Log.e(TAG, "DONE failed for $taskId", e)
                } finally {
                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(taskNotificationId(taskId))
                    } catch (_: Exception) { }
                    pending.finish()
                }
            }
            return
        }

        if (taskId == -1) {
            Log.w(TAG, "Missing task_id, ignoring alarm (action=$action)")
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                showNotification(context, taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Reminder failed for $taskId", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleDoneAction(taskId: Int) {
        val task = try {
            taskDao.getTaskById(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "getTaskById failed for $taskId", e)
            null
        } ?: return
        try {
            taskDao.updateTask(task.copy(isCompleted = true, completedAt = System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.e(TAG, "mark-done failed for $taskId", e)
        }
    }

    private suspend fun showNotification(context: Context, taskId: Int) {
        val enabled = try {
            settingsRepository.taskReminderNotifications.first()
        } catch (e: Exception) {
            Log.w(TAG, "toggle read failed, defaulting ON", e)
            true
        }
        if (!enabled) {
            Log.i(TAG, "Reminders disabled — suppressing $taskId")
            return
        }
        // Await the DB row BEFORE touching notifications (no fire-then-fetch race).
        val task = try {
            taskDao.getTaskById(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "getTaskById failed for $taskId", e)
            null
        } ?: return
        if (task.isCompleted) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !nm.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled — reminder for $taskId has no visual path")
            return
        }
        NotificationHelper.createAllChannels(context)

        val contentIntent = TaskStackBuilder.create(context).run {
            val target = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TO, "todo")
                putExtra(EXTRA_TASK_ID, taskId)
            }
            addNextIntentWithParentStack(target)
            getPendingIntent(
                taskId,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } ?: PendingIntent.getActivity(
            context,
            taskId,
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TO, "todo")
                putExtra(EXTRA_TASK_ID, taskId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_DONE
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val donePi = PendingIntent.getBroadcast(
            context,
            TaskAlarmScheduler.doneRequestCode(taskId),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val atDue = task.dueDate?.let { it <= System.currentTimeMillis() + 60_000L } == true
        val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_TASK_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (atDue) "Task due now" else "Task due soon")
            .setContentText(task.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.checkbox_on_background, "DONE", donePi)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        nm.notify(taskNotificationId(taskId), notification)
    }

    private fun taskNotificationId(taskId: Int): Int = taskId
}
