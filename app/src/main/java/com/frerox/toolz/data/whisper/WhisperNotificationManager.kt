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

package com.frerox.toolz.data.whisper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Whisper in-process push notifications.
 *
 * Posts a system notification when a new message arrives and the app is in the background.
 * Notifications are grouped by sender conversation (one notification per chat thread).
 *
 * NOTE: This is an in-process notification system. Notifications only fire when the
 * app process is alive. True background delivery requires FCM + Edge Function setup.
 */
@Singleton
class WhisperNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "whisper_messages"
        const val CHANNEL_NAME = "Whisper Messages"
        private const val TAG = "WhisperNotifMgr"
        private const val GROUP_KEY = "com.frerox.toolz.WHISPER_MESSAGES"
        private const val REQUEST_CODE_BASE = 9000
    }

    private val notifManager = NotificationManagerCompat.from(context)
    private var isInForeground = true
    var currentChatId: String? = null // Track which chat the user is currently viewing

    init {
        createChannel()
        observeAppLifecycle()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "End-to-end encrypted Whisper messages"
            enableVibration(true)
        }
        notifManager.createNotificationChannel(channel)
    }

    private fun observeAppLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { isInForeground = true }
            override fun onStop(owner: LifecycleOwner) { isInForeground = false }
        })
    }

    /**
     * Shows a message notification for [senderId].
     * Uses the notification ID derived from the senderId so it is updated (not duplicated)
     * for each new message from the same sender.
     *
     * Only shows when the app is NOT in the foreground.
     */
    fun showMessageNotification(
        senderId: String,
        senderName: String,
        preview: String,
    ) {
        // Skip notification if the app is in foreground AND the user is already in that specific chat
        if (isInForeground && currentChatId == senderId) {
            Log.d(TAG, "User in chat with $senderName — skipping notification")
            return
        }

        val notifId = senderNotifId(senderId)

        // Deep link intent → open WhisperChat for this sender
        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            action = "com.frerox.toolz.OPEN_WHISPER_CHAT"
            putExtra("otherUserId", senderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + notifId,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_whisper_notif)
            .setContentTitle(senderName)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notifManager.notify(notifId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted: ${e.message}")
        }
    }

    /** Friend request notification */
    fun showFriendRequestNotification(fromName: String) {
        if (isInForeground) return
        val notifId = "friend_req_$fromName".hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_whisper_notif)
            .setContentTitle("New Friend Request")
            .setContentText("$fromName wants to be your friend on Whisper")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            notifManager.notify(notifId, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted: ${e.message}")
        }
    }

    /** Call when the user opens a chat to dismiss its notification. */
    fun cancelMessageNotification(senderId: String) {
        notifManager.cancel(senderNotifId(senderId))
    }

    private fun senderNotifId(senderId: String): Int =
        (senderId.hashCode() and 0x7FFFFFFF) + 1000
}
