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
        private const val SUMMARY_NOTIF_ID = 8999
        private const val FIRST_MESSAGE_NOTIF_ID = 1000
        // Keep this band well below the friend-request range (2_100_000_000+) and small
        // enough that REQUEST_CODE_BASE + id never approaches Int.MAX_VALUE.
        private const val LAST_MESSAGE_NOTIF_ID = 1_000_000
    }

    private val notifManager = NotificationManagerCompat.from(context)
    // Stable per-sender notification IDs: hashCode() collisions silently overwrote one
    // conversation's notification with another's. Allocation is persisted so IDs survive
    // process death and remain unique until the (practically unreachable) band exhausts.
    private val idPrefs = context.getSharedPreferences("whisper_notif_ids", Context.MODE_PRIVATE)
    private val senderNotifIds = HashMap<String, Int>()
    private var nextMessageId = FIRST_MESSAGE_NOTIF_ID
    @Volatile private var isInForeground = false
    @Volatile var currentChatId: String? = null

    init {
        createChannel()
        observeAppLifecycle()
        synchronized(senderNotifIds) {
            for ((key, value) in idPrefs.all) {
                if (key.startsWith("id_") && value is Int) senderNotifIds[key.removePrefix("id_")] = value
            }
            nextMessageId = idPrefs.getInt("next", FIRST_MESSAGE_NOTIF_ID)
            if (nextMessageId >= LAST_MESSAGE_NOTIF_ID) {
                // Band exhausted after ~1M senders — recycle cleanly.
                // M-9 FIX (reviewwhisper.md): dismiss OUR active notifications first so
                // stale IDs can never be cancelled/mismatched after the reset.
                runCatching {
                    notifManager.activeNotifications
                        .filter { it.notification.group == GROUP_KEY || it.id == SUMMARY_NOTIF_ID }
                        .forEach { notifManager.cancel(it.id) }
                }
                senderNotifIds.clear()
                nextMessageId = FIRST_MESSAGE_NOTIF_ID
            }
        }
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

        val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_whisper_notif)
            .setContentTitle("Whisper")
            .setContentText("New encrypted messages")
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            notifManager.notify(notifId, notification)
            notifManager.notify(SUMMARY_NOTIF_ID, summaryNotification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification", e)
        }
    }

    /** Friend request notification — uses stable sender id to avoid display-name collisions. Distinct range from messages to avoid overwrite. */
    fun showFriendRequestNotification(fromId: String, fromName: String) {
        if (isInForeground) return
        val notifId = ((fromId.hashCode() and 0x7FFFFFFF) % 100_000) + 2_100_000_000
        // Tapping the notification opens MainActivity and surfaces the request list;
        // there is no chat to deep-link into, so only the request flag is passed.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_friend_requests", true)
        }
        // Keep friend-request PendingIntent requestCode below BASE+1000 so it never collides
        // with per-sender message PendingIntents (which use BASE + senderNotifId >= BASE+1000).
        // Using notifId % 900 keeps it in 0..899 range.
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + (notifId % 900),
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

    private fun senderNotifId(senderId: String): Int = synchronized(senderNotifIds) {
        senderNotifIds[senderId]?.let { return it }
        val id = nextMessageId++
        if (nextMessageId >= LAST_MESSAGE_NOTIF_ID) {
            // Practically unreachable; recycle the band rather than overflow into
            // the friend-request range or negative IDs (which crash notify()).
            idPrefs.edit().clear().apply()
            senderNotifIds.clear()
            nextMessageId = FIRST_MESSAGE_NOTIF_ID
        }
        idPrefs.edit().putInt("id_$senderId", id).putInt("next", nextMessageId).apply()
        senderNotifIds[senderId] = id
        id
    }
}
