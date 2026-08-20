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
 */
@Singleton
class WhisperNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mutePrefs: WhisperMutePreferences,
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
    var currentChatId: String? = null

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
     * Suppressed if user is in that active chat or if user is muted.
     */
    fun showMessageNotification(
        senderId: String,
        senderName: String,
    ) {
        // Skip if muted
        if (mutePrefs.isMuted(senderId)) {
            Log.d(TAG, "User $senderName ($senderId) is muted — skipping notification")
            return
        }

        // Skip notification if the app is in foreground AND the user is already in that specific chat
        if (isInForeground && currentChatId == senderId) {
            Log.d(TAG, "User in chat with $senderName — skipping notification")
            return
        }

        val notifId = senderNotifId(senderId)

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
            .setContentTitle("New Whisper message")
            // Never copy decrypted content to the lock screen or notification history.
            .setContentText("Open Whisper to read it")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            notifManager.notify(notifId, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification", e)
        }
    }

    /** Friend request notification */
    fun showFriendRequestNotification(fromName: String) {
        if (isInForeground) return
        val notifId = "friend_req_$fromName".hashCode()
        // Tapping the notification opens MainActivity and surfaces the request list;
        // there is no chat to deep-link into, so only the request flag is passed.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_friend_requests", true)
        }
        // Request code stays below REQUEST_CODE_BASE + 1000, the floor used by the
        // per-sender message PendingIntents, so the two can never collide.
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_whisper_notif)
            .setContentTitle("New Friend Request")
            .setContentText("$fromName wants to be your friend on Whisper")
            // Sender names and requests stay off the lock screen like message notifications.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            notifManager.notify(notifId, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification", e)
        }
    }

    /** Dismiss notification when user opens chat */
    fun cancelMessageNotification(senderId: String) {
        notifManager.cancel(senderNotifId(senderId))
    }

    private fun senderNotifId(senderId: String): Int =
        // (hashCode() and 0x7FFFFFFF) + 1000 can overflow past Int.MAX_VALUE for the
        // largest hash codes and wrap to a negative notification id (which crashes
        // notify()). Modulo 2_000_000_000 keeps the base small enough that the +1000
        // offset stays within Int.MAX_VALUE (max 2,000,000,999).
        ((senderId.hashCode() and 0x7FFFFFFF) % 2_000_000_000) + 1000
}
