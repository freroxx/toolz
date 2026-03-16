package com.frerox.toolz.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.todo.TaskDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TaskReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var taskDao: TaskDao

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("task_id", -1)
        val action = intent.action

        if (action == "ACTION_DONE" && taskId != -1) {
            handleDoneAction(taskId)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(taskId)
            return
        }

        if (taskId != -1) {
            showNotification(context, taskId)
        }
    }

    private fun handleDoneAction(taskId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val task = taskDao.getTaskById(taskId)
            task?.let {
                taskDao.updateTask(it.copy(isCompleted = true, completedAt = System.currentTimeMillis()))
            }
        }
    }

    private fun showNotification(context: Context, taskId: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val task = taskDao.getTaskById(taskId) ?: return@launch
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "task_reminders"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for upcoming task deadlines"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                taskId, 
                intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val doneIntent = Intent(context, TaskReminderReceiver::class.java).apply {
                action = "ACTION_DONE"
                putExtra("task_id", taskId)
            }
            val donePendingIntent = PendingIntent.getBroadcast(
                context, 
                taskId + 1000, 
                doneIntent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Task Due Soon")
                .setContentText(task.title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.checkbox_on_background, "DONE", donePendingIntent)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()

            notificationManager.notify(taskId, notification)
        }
    }
}
