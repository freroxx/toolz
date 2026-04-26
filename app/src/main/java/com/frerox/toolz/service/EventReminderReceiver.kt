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
import com.frerox.toolz.R
import com.frerox.toolz.data.calendar.EventRepository
import com.frerox.toolz.ui.screens.calendar.EventAlarmActivity
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EventReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: EventRepository

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getIntExtra("event_id", -1)
        val type = intent.getStringExtra("reminder_type") ?: return // "24H", "12H", "1H"

        if (eventId == -1) return

        CoroutineScope(Dispatchers.IO).launch {
            val events = repository.getAllEvents().first()
            val event = events.find { it.id == eventId } ?: return@launch
            
            if (event.isCompleted) return@launch

            if (type == "1H") {
                val alarmIntent = Intent(context, EventAlarmActivity::class.java).apply {
                    putExtra("event_title", event.title)
                    putExtra("event_id", event.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(alarmIntent)
            } else {
                showNotification(context, event.title, "Starting in $type", event.id)
            }
        }
    }

    private fun showNotification(context: Context, title: String, content: String, id: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationHelper.createAllChannels(context)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_EVENT_REMINDERS)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(id + title.hashCode(), notification)
    }
}
