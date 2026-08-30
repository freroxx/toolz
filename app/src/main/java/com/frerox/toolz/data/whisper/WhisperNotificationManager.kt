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
import java.util.concurrent.ConcurrentHashMap
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
    private val hiddenChatsStore: WhisperHiddenChatsStore,
) {
    companion object {
        const val CHANNEL_ID = "whisper_messages"
        // V2-FIX M-M?: dedicated low-importance channel for group summaries — silencing the
        // ambient summary must not mute per-message notifications (and vice versa).
        // Channel names/descriptions resolve from string resources at runtime so they
        // follow the device locale (see createChannel()).
        const val SUMMARY_CHANNEL_ID = "whisper_message_summary"
        private const val TAG = "WhisperNotifMgr"
        private const val GROUP_KEY = "com.frerox.toolz.WHISPER_MESSAGES"
        private const val REQUEST_CODE_BASE = 9000
        private const val SUMMARY_NOTIF_ID = 8999
        private const val FIRST_MESSAGE_NOTIF_ID = 1000
        // Keep this band small enough that REQUEST_CODE_BASE + id never approaches
        // Int.MAX_VALUE. V2-FIX M-H?: friend-request ids moved OUT of the old 2_100_000_000+
        // range into a dedicated band directly ABOVE this one — see FRIEND_REQUEST_* below.
        private const val LAST_MESSAGE_NOTIF_ID = 1_000_000

        /**
         * V2-FIX M-H?: dedicated ID band for friend-request notifications so they can never
         * collide with per-sender conversation ids ([FIRST_MESSAGE_NOTIF_ID],
         * [LAST_MESSAGE_NOTIF_ID)). Range: 2_000_000 .. 2_099_999 — disjoint from the
         * conversation band and from SUMMARY_NOTIF_ID.
         */
        private const val FRIEND_REQUEST_NOTIF_ID_BASE = 2_000_000
        private const val FRIEND_REQUEST_NOTIF_ID_SPAN = 100_000
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
    @Volatile var isViewingFriendRequests: Boolean = false
    // Dedupe: same messageId from FCM + realtime must not double-notify within TTL.
    private val recentlyNotifiedMessageIds = ConcurrentHashMap<String, Long>()
    private val NOTIF_DEDUPE_TTL_MS = 30_000L
    private val MAX_RECENT_NOTIF_IDS = 1_024

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
            context.getString(R.string.st_Whisper_Notification_ChannelName),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.st_Whisper_Notification_ChannelDesc)
            enableVibration(true)
        }
        notifManager.createNotificationChannel(channel)
        // V2-FIX M-M?: low-importance channel hosting ONLY the group summary notification.
        val summaryChannel = NotificationChannel(
            SUMMARY_CHANNEL_ID,
            context.getString(R.string.st_Whisper_Notification_ChannelSummaryName),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.st_Whisper_Notification_ChannelSummaryDesc)
        }
        notifManager.createNotificationChannel(summaryChannel)
    }

    private fun observeAppLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { isInForeground = true }
            override fun onStop(owner: LifecycleOwner) { isInForeground = false }
        })
    }

    /**
     * Shows a message notification for [senderId].
     * Suppressed if user is in that active chat, muted, hidden, read, or duplicate.
     */
    fun showMessageNotification(
        senderId: String,
        senderName: String,
        messageId: String? = null,
        isRead: Boolean = false,
    ) {
        // Never notify for read messages (ghost after mark-as-read).
        if (isRead) return
        // Dedupe same messageId from FCM + realtime within TTL.
        if (!messageId.isNullOrBlank() && !shouldNotifyForMessage(messageId)) return
        // Skip if muted or hidden (hidden chats should not ping).
        if (mutePrefs.isMuted(senderId)) {
            // V2-FIX M-M?: sender names/ids never appear in release logs (privacy).
            if (com.frerox.toolz.BuildConfig.DEBUG) {
                Log.d(TAG, "User $senderName ($senderId) is muted — skipping notification")
            }
            return
        }
        // Fix: hidden (delete-chat / archive) must NOT suppress notifications —
        // it caused asymmetric delivery: userA who had archived the chat never got
        // pinged when userB replied, while userB (not archived) did. Archived chats
        // still ping and the hub unhides on next loadConversationsInternal (toUnhide).
        // Keep isHidden check out of the notification path.

        // Skip notification if the app is in foreground AND the user is already in that specific chat
        if (isInForeground && currentChatId == senderId) {
            // V2-FIX M-M?: same release-log hygiene as above — no ids in release.
            if (com.frerox.toolz.BuildConfig.DEBUG) {
                Log.d(TAG, "User in chat with $senderName ($senderId) — skipping notification")
            }
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
            .setContentTitle(context.getString(R.string.st_Whisper_Notif_NewMessage))
            // Never copy decrypted content to the lock screen or notification history.
            .setContentText(context.getString(R.string.st_Whisper_Notif_OpenToRead))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // V2-FIX M-M?: the group summary posts on its own low-importance channel so it can
        // be silenced independently of the per-message notifications.
        val summaryNotification = NotificationCompat.Builder(context, SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_whisper_notif)
            .setContentTitle("Whisper")
            .setContentText(context.getString(R.string.st_Whisper_Notif_SummaryText))
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

    /** Friend request notification — stable sender id avoids display-name collisions. V2-FIX M-H?: id lives in the dedicated FRIEND_REQUEST_* band, disjoint from conversation ids. */
    fun showFriendRequestNotification(fromId: String, fromName: String) {
        if (isInForeground && isViewingFriendRequests) return
        val notifId = friendRequestNotifId(fromId)
        // Tapping the notification opens MainActivity and surfaces the request list
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.frerox.toolz.OPEN_WHISPER_FRIEND_REQUESTS"
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
            .setContentTitle(context.getString(R.string.st_Whisper_Notif_FriendRequest))
            .setContentText(fromName)
            // Sender names and requests stay off the lock screen like message notifications.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
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
        // V2-FIX M-M?: drop the group summary once its last child is gone.
        maybeCancelGroupSummary()
    }

    /**
     * V2-FIX M-M?: the group summary used to outlive every child — a lone "Whisper / New
     * encrypted messages" notification stayed in the shade until tapped. After any
     * message-band child is cancelled, query the ACTIVE notification list and cancel the
     * summary when no child remains (friend requests share the group key but post in their
     * own band and do not count as message children).
     */
    private fun maybeCancelGroupSummary() {
        try {
            val hasActiveChild = notifManager.activeNotifications.any { sbn ->
                sbn.id != SUMMARY_NOTIF_ID &&
                    sbn.id in FIRST_MESSAGE_NOTIF_ID until LAST_MESSAGE_NOTIF_ID &&
                    sbn.notification.group == GROUP_KEY
            }
            if (!hasActiveChild) notifManager.cancel(SUMMARY_NOTIF_ID)
        } catch (_: Exception) {
            // activeNotifications is best-effort (some OEM builds misbehave); worst case
            // the summary lingers until the next cancel pass.
        }
    }

    /** Dedupe same messageId from FCM + realtime within TTL. */
    private fun shouldNotifyForMessage(messageId: String): Boolean {
        if (messageId.isBlank()) return true
        val now = System.currentTimeMillis()
        val prev = recentlyNotifiedMessageIds.putIfAbsent(messageId, now)
        if (recentlyNotifiedMessageIds.size > MAX_RECENT_NOTIF_IDS) {
            recentlyNotifiedMessageIds.entries.removeIf { (_, ts) -> now - ts > NOTIF_DEDUPE_TTL_MS }
            if (recentlyNotifiedMessageIds.size > MAX_RECENT_NOTIF_IDS) {
                val byAge = recentlyNotifiedMessageIds.entries.sortedBy { it.value }
                byAge.take(byAge.size / 3).forEach { recentlyNotifiedMessageIds.remove(it.key) }
            }
        }
        if (prev == null) return true
        if (now - prev <= NOTIF_DEDUPE_TTL_MS) return false
        recentlyNotifiedMessageIds[messageId] = now
        return true
    }

    /** Stable id for a friend-request notification inside [FRIEND_REQUEST_NOTIF_ID_BASE, +SPAN). */
    private fun friendRequestNotifId(fromId: String): Int =
        FRIEND_REQUEST_NOTIF_ID_BASE + ((fromId.hashCode() and 0x7FFFFFFF) % FRIEND_REQUEST_NOTIF_ID_SPAN)

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
