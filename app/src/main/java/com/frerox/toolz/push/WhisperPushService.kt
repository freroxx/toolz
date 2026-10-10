/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.push

import android.content.Context
import com.frerox.toolz.data.whisper.WhisperNotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * V3-FIX (reviewwhisper.md item 6): receives FCM data-only wake pings sent by the
 * `whisper-push-send` Edge Function when a message lands for a user whose realtime
 * socket is dead (app backgrounded/killed).
 *
 * Privacy contract: the payload carries NO message content — only
 * `{whisper_new_message: true, senderId}`. Nothing readable ever transits Google's
 * push transport; the encrypted body stays in Supabase until the app syncs.
 */
@AndroidEntryPoint
class WhisperPushService : FirebaseMessagingService() {

    @Inject lateinit var notificationManager: WhisperNotificationManager
    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var tokenStore: WhisperPushTokenStore
    @Inject lateinit var groupNotifPrefs: com.frerox.toolz.data.whisper.WhisperGroupNotifPrefs
    @Inject lateinit var groupDao: com.frerox.toolz.data.whisper.WhisperGroupDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Fired on install/rotate. If the user isn't signed in yet, the token is parked
        // and uploaded on the next launch once a session exists (see TokenStore).
        serviceScope.launch { tokenStore.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val senderId = message.data["senderId"].orEmpty()
        val messageId = message.data["messageId"].orEmpty()

        if (message.data["whisper_friend_request"].toBoolean() || message.data["type"] == "friend_request") {
            if (senderId.isNotEmpty()) {
                val senderName = message.data["senderName"].takeIf { !it.isNullOrBlank() }
                    ?: applicationContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
                notificationManager.showFriendRequestNotification(
                    fromId = senderId,
                    fromName = senderName
                )
            }
            return
        }

        // Phase-2 groups: invite / message / event wake pings (data-only, no content).
        if (message.data["whisper_group_invite"].toBoolean()) {
            val groupId = message.data["groupId"].orEmpty()
            val groupName = message.data["groupName"].takeIf { !it.isNullOrBlank() } ?: "Group"
            val inviterName = message.data["inviterName"].takeIf { !it.isNullOrBlank() }
                ?: applicationContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
            if (groupId.isNotEmpty()) {
                notificationManager.showGroupInviteNotification(groupId, groupName, inviterName)
            }
            return
        }
        if (message.data["whisper_group_message"].toBoolean()) {
            val groupId = message.data["groupId"].orEmpty()
            val groupName = message.data["groupName"].takeIf { !it.isNullOrBlank() } ?: "Group"
            val senderName = message.data["senderName"].takeIf { !it.isNullOrBlank() }
            // Messages are NOT governed by the event toggle (members get message
            // pings by default); group mute still applies inside the manager.
            if (groupId.isNotEmpty()) {
                val text = if (senderName != null) {
                    applicationContext.getString(
                        com.frerox.toolz.R.string.st_Whisper_Groups_SenderNewMessage,
                        senderName,
                    )
                } else {
                    applicationContext.getString(com.frerox.toolz.R.string.st_Whisper_Notif_NewMessage)
                }
                notificationManager.showGroupEventNotification(
                    groupId = groupId,
                    title = groupName,
                    text = text,
                    dedupeKey = "msg:${message.data["clientId"].orEmpty().ifBlank { message.data["messageId"].orEmpty() }}",
                )
            }
            return
        }
        if (message.data["whisper_group_event"].toBoolean()) {
            val groupId = message.data["groupId"].orEmpty()
            val kind = message.data["kind"].orEmpty()
            val actorId = message.data["actorId"].orEmpty()
            val eventId = message.data["eventId"].orEmpty()
            if (groupId.isNotEmpty() && kind.isNotEmpty()) {
                // Role + toggle are resolved against the local cache (the server
                // cannot know our role or our toggle) before anything is shown.
                // join/leave/decline honor the event toggle; admin-action kinds
                // (remove/promote/demote/rename/picture/settings) behave like
                // message pings — mute + open-chat + self only, no toggle.
                val data = message.data.toMap()
                serviceScope.launch {
                    runCatching {
                        val me = supabase.auth.currentUserOrNull()?.id.orEmpty()
                        if (actorId.isNotBlank() && actorId == me) return@launch
                        val group = groupDao.group(groupId)
                        val members = groupDao.members(groupId)
                        val myRole = members.firstOrNull { it.memberId == me }?.role
                        val amOwner = group?.createdBy == me && me.isNotBlank()
                        val amOwnerOrAdmin = amOwner || myRole == "admin"
                        val groupName = data["groupName"]?.takeIf { it.isNotBlank() } ?: "Group"
                        val actorName = data["actorName"]?.takeIf { it.isNotBlank() }
                            ?: applicationContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
                        val subjectId = data["subjectId"].orEmpty()
                        val subjectName = data["subjectName"]?.takeIf { it.isNotBlank() }
                            ?: applicationContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
                        val adminText: String? = when (kind) {
                            "remove" -> if (subjectId.isNotBlank() && subjectId == me) {
                                applicationContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_RemovedYou, groupName,
                                )
                            } else {
                                applicationContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_RemovedOther, subjectName, groupName,
                                )
                            }
                            "promote" -> if (subjectId.isNotBlank() && subjectId == me) {
                                applicationContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_PromotedYou, groupName,
                                )
                            } else {
                                applicationContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_PromotedOther, subjectName, groupName,
                                )
                            }
                            "demote" -> if (subjectId.isNotBlank() && subjectId == me) {
                                applicationContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_DemotedYou, groupName,
                                )
                            } else {
                                applicationContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_DemotedOther, subjectName, groupName,
                                )
                            }
                            "rename" -> applicationContext.getString(
                                com.frerox.toolz.R.string.st_Whisper_Groups_Renamed, actorName,
                            )
                            "picture" -> applicationContext.getString(
                                com.frerox.toolz.R.string.st_Whisper_Groups_PictureUpdated, actorName,
                            )
                            "settings" -> applicationContext.getString(
                                com.frerox.toolz.R.string.st_Whisper_Groups_SettingsUpdated, actorName,
                            )
                            else -> null
                        }
                        if (adminText != null) {
                            notificationManager.showGroupEventNotification(
                                groupId = groupId,
                                title = groupName,
                                text = adminText,
                                dedupeKey = "evt:$eventId",
                            )
                            return@launch
                        }
                        val scope = when (kind) {
                            "join" -> com.frerox.toolz.data.whisper.GroupNotifScope.JOIN
                            "leave" -> com.frerox.toolz.data.whisper.GroupNotifScope.LEAVE
                            "decline" -> com.frerox.toolz.data.whisper.GroupNotifScope.DECLINE
                            else -> return@launch
                        }
                        val toggleOn = groupNotifPrefs.isEnabled(groupId, amOwnerOrAdmin)
                        if (!com.frerox.toolz.data.whisper.shouldNotifyGroupEvent(scope, actorId, me, amOwner, toggleOn)) return@launch
                        val text = when (kind) {
                            "join" -> com.frerox.toolz.data.whisper.WhisperGroupNotifTemplates.joinText(actorName, groupName)
                            "leave" -> com.frerox.toolz.data.whisper.WhisperGroupNotifTemplates.leaveText(actorName, groupName)
                            else -> com.frerox.toolz.data.whisper.WhisperGroupNotifTemplates.declineText(actorName, groupName)
                        }
                        notificationManager.showGroupEventNotification(
                            groupId = groupId,
                            title = groupName,
                            text = text,
                            dedupeKey = "evt:$eventId",
                        )
                    }
                }
            }
            return
        }

        if (!message.data["whisper_new_message"].toBoolean() || senderId.isEmpty()) return

        // Generic, content-free notification — identical body to the in-app realtime
        // path so lock-screen behavior never leaks text (VISIBILITY_PRIVATE).
        // FIX: include messageId for dedupe (FCM + realtime), and isRead=false
        // (edge function only sends on INSERT). Manager handles hidden/muted/dedupe.
        notificationManager.showMessageNotification(
            senderId = senderId,
            senderName = senderId,
            messageId = messageId.ifBlank { null },
            isRead = false,
        )
    }

    override fun onDestroy() {
        serviceScope.coroutineContext.cancelChildren()
        super.onDestroy()
    }
}

/**
 * Persists the current FCM registration token to `whisper_fcm_tokens` (RLS-scoped to
 * the signed-in row owner). Handles the "token arrives before login" race by parking
 * it locally and retrying on subsequent launches.
 */
@Singleton
class WhisperPushTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) {
    @Serializable
    private data class TokenRow(
        @SerialName("user_id") val userId: String,
        @SerialName("token") val token: String,
    )

    suspend fun register(token: String) {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            parkPending(token)
            return
        }
        val uploaded = runCatching {
            // user_id is the table PK, so a plain upsert merges duplicates.
            supabase.postgrest.from(TOKENS_TABLE).upsert(TokenRow(userId = userId, token = token))
        }.isSuccess
        if (uploaded) clearPending()
    }

    /** Called once per app start: uploads any token parked before sign-in. */
    suspend fun retryPendingIfAny() {
        val pending = pendingToken() ?: return
        register(pending)
    }

    private fun prefs() =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parkPending(token: String) {
        prefs().edit().putString(KEY_PENDING, token).commit()
    }

    private fun pendingToken(): String? =
        prefs().getString(KEY_PENDING, null)

    private fun clearPending() {
        prefs().edit().remove(KEY_PENDING).commit()
    }

    private companion object {
        const val PREFS = "whisper_push_pending"
        const val KEY_PENDING = "pending_token"
        const val TOKENS_TABLE = "whisper_fcm_tokens"
    }
}
