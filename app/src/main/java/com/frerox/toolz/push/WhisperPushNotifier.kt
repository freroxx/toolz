/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.push

import android.content.Context
import com.frerox.toolz.data.whisper.WhisperNotificationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders Whisper wake-pings into notifications. Extracted 1:1 from the old
 * FirebaseMessagingService.onMessageReceived so the UnifiedPush push service —
 * and any future transport — shares a single implementation (F-Droid swap).
 *
 * Privacy contract: unchanged. Payloads carry NO message content — only ids and
 * names (senderId/messageId, group id/name, senderName/actorName). On webpush
 * targets the ping itself now travels as an RFC 8291-encrypted blob; plain
 * targets (ntfy/NextPush) see exactly the same id/name keys the old FCM pings
 * already exposed. Documented in WHISPER.md §11.
 */
@Singleton
class WhisperPushNotifier @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val notificationManager: WhisperNotificationManager,
    private val supabase: SupabaseClient,
    private val groupNotifPrefs: com.frerox.toolz.data.whisper.WhisperGroupNotifPrefs,
    private val groupDao: com.frerox.toolz.data.whisper.WhisperGroupDao,
) {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Handles one wake-ping. The Edge Function sends a flat JSON object of string
     * values — same keys as the old FCM data map:
     * `whisper_new_message`, `whisper_friend_request`, `whisper_group_invite`,
     * `whisper_group_message`, `whisper_group_event` (+ ids/names).
     */
    fun handlePayload(payloadJson: String) {
        val data = parseFlatPayload(payloadJson)
        val senderId = data["senderId"].orEmpty()
        val messageId = data["messageId"].orEmpty()

        if (data["whisper_friend_request"].toBoolean() || data["type"] == "friend_request") {
            if (senderId.isNotEmpty()) {
                val senderName = data["senderName"].takeIf { !it.isNullOrBlank() }
                    ?: appContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
                notificationManager.showFriendRequestNotification(
                    fromId = senderId,
                    fromName = senderName,
                )
            }
            return
        }

        // Phase-2 groups: invite / message / event wake pings (data-only, no content).
        if (data["whisper_group_invite"].toBoolean()) {
            val groupId = data["groupId"].orEmpty()
            val groupName = data["groupName"].takeIf { !it.isNullOrBlank() } ?: "Group"
            val inviterName = data["inviterName"].takeIf { !it.isNullOrBlank() }
                ?: appContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
            if (groupId.isNotEmpty()) {
                notificationManager.showGroupInviteNotification(groupId, groupName, inviterName)
            }
            return
        }
        if (data["whisper_group_message"].toBoolean()) {
            val groupId = data["groupId"].orEmpty()
            val groupName = data["groupName"].takeIf { !it.isNullOrBlank() } ?: "Group"
            val senderName = data["senderName"].takeIf { !it.isNullOrBlank() }
            // Messages are NOT governed by the event toggle (members get message
            // pings by default); group mute still applies inside the manager.
            if (groupId.isNotEmpty()) {
                val text = if (senderName != null) {
                    appContext.getString(
                        com.frerox.toolz.R.string.st_Whisper_Groups_SenderNewMessage,
                        senderName,
                    )
                } else {
                    appContext.getString(com.frerox.toolz.R.string.st_Whisper_Notif_NewMessage)
                }
                notificationManager.showGroupEventNotification(
                    groupId = groupId,
                    title = groupName,
                    text = text,
                    dedupeKey = "msg:${data["clientId"].orEmpty().ifBlank { data["messageId"].orEmpty() }}",
                )
            }
            return
        }
        if (data["whisper_group_event"].toBoolean()) {
            val groupId = data["groupId"].orEmpty()
            val kind = data["kind"].orEmpty()
            val actorId = data["actorId"].orEmpty()
            val eventId = data["eventId"].orEmpty()
            if (groupId.isNotEmpty() && kind.isNotEmpty()) {
                // Role + toggle are resolved against the local cache (the server
                // cannot know our role or our toggle) before anything is shown.
                // join/leave/decline honor the event toggle; admin-action kinds
                // (remove/promote/demote/rename/picture/settings) behave like
                // message pings — mute + open-chat + self only, no toggle.
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
                            ?: appContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
                        val subjectId = data["subjectId"].orEmpty()
                        val subjectName = data["subjectName"]?.takeIf { it.isNotBlank() }
                            ?: appContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
                        val adminText: String? = when (kind) {
                            "remove" -> if (subjectId.isNotBlank() && subjectId == me) {
                                appContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_RemovedYou, groupName,
                                )
                            } else {
                                appContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_RemovedOther, subjectName, groupName,
                                )
                            }
                            "promote" -> if (subjectId.isNotBlank() && subjectId == me) {
                                appContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_PromotedYou, groupName,
                                )
                            } else {
                                appContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_PromotedOther, subjectName, groupName,
                                )
                            }
                            "demote" -> if (subjectId.isNotBlank() && subjectId == me) {
                                appContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_DemotedYou, groupName,
                                )
                            } else {
                                appContext.getString(
                                    com.frerox.toolz.R.string.st_Whisper_Groups_DemotedOther, subjectName, groupName,
                                )
                            }
                            "rename" -> appContext.getString(
                                com.frerox.toolz.R.string.st_Whisper_Groups_Renamed, actorName,
                            )
                            "picture" -> appContext.getString(
                                com.frerox.toolz.R.string.st_Whisper_Groups_PictureUpdated, actorName,
                            )
                            "settings" -> appContext.getString(
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

        if (!data["whisper_new_message"].toBoolean() || senderId.isEmpty()) return

        // Generic, content-free notification — identical body to the in-app realtime
        // path so lock-screen behavior never leaks text (VISIBILITY_PRIVATE).
        // messageId allows dedupe (push + realtime); isRead=false
        // (edge function only sends on INSERT). Manager handles hidden/muted/dedupe.
        notificationManager.showMessageNotification(
            senderId = senderId,
            senderName = senderId,
            messageId = messageId.ifBlank { null },
            isRead = false,
        )
    }

    /**
     * The pings are flat `{String: String}` objects (same shape as the old FCM
     * data map). Deep values are not part of the contract today; any non-primitive
     * entry is skipped rather than stringified to keep parse failures silent.
     */
    private fun parseFlatPayload(payloadJson: String): Map<String, String> {
        val root = runCatching { Json.parseToJsonElement(payloadJson) }.getOrNull() as? JsonObject
            ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        for ((key, value) in root) {
            if (value is JsonNull) continue
            val primitive = value as? JsonPrimitive ?: continue
            out[key] = primitive.content
        }
        return out
    }
}
