/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.push

import android.content.Context
import android.util.Log
import com.frerox.toolz.data.whisper.WhisperGroupDao
import com.frerox.toolz.data.whisper.WhisperGroupNotifPrefs
import com.frerox.toolz.data.whisper.WhisperNotificationManager
import com.frerox.toolz.data.whisper.WhisperRepository
import com.frerox.toolz.data.whisper.friendNameMap
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
 * Privacy contract (WHISPER.md §11): the payload carries NO message content.
 * On webpush targets the ping travels as an RFC 8291-encrypted blob and may
 * include human names (senderName, groupName, actorName, posterName). On PLAIN
 * targets (ntfy/NextPush) the edge function scrubs every name key — that
 * third party learns IDs only — and this class resolves each name on-device:
 * friends from the shared friend-names cache, groups from the local Room
 * table, with the documented generic fallbacks when a cache can't answer.
 */
@Singleton
class WhisperPushNotifier @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val notificationManager: WhisperNotificationManager,
    private val supabase: SupabaseClient,
    private val groupNotifPrefs: WhisperGroupNotifPrefs,
    private val groupDao: WhisperGroupDao,
    private val repository: WhisperRepository,
) {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Handles one wake-ping. The Edge Function sends a flat JSON object of string
     * values — same keys as the old FCM data map:
     * `whisper_new_message`, `whisper_friend_request`, `whisper_group_invite`,
     * `whisper_group_message`, `whisper_group_event`, `whisper_story_new` (+ ids/names).
     */
    fun handlePayload(payloadJson: String) {
        val data = parseFlatPayload(payloadJson)
        // Parse failure = unknown ping kind; nothing below can act on it anyway.
        if (data.isEmpty()) return
        serviceScope.launch {
            // A wake-ping must never take the push service down with it: any
            // resolution/DAO failure degrades copy, not delivery.
            runCatching { render(data.withLocalNames()) }
                .onFailure { e ->
                    if (com.frerox.toolz.BuildConfig.DEBUG) {
                        Log.d(TAG, "wake-ping render failed", e)
                    }
                }
        }
    }

    /**
     * Name keys the server only guarantees on ENCRYPTED webpush targets. On
     * plain targets they are absent, so each is rebuilt here from local truth:
     * friend ids → the shared friend-names cache (TTL-cached, RLS-scoped to the
     * signed-in user — it can only return names we already had access to), and
     * group ids → the local group table. Present values always win; the server
     * contract stays the single source for anything webpush-delivered.
     */
    private suspend fun Map<String, String>.withLocalNames(): Map<String, String> {
        val out = toMutableMap()
        suspend fun resolve(id: String): String? {
            if (id.isBlank()) return null
            return runCatching { repository.friendNameMap()[id] }
                .getOrNull()?.takeIf { it.isNotBlank() }
        }
        if (get("whisper_story_new").toBoolean() && get("posterName").isNullOrBlank()) {
            resolve(get("posterId").orEmpty())?.let { out["posterName"] = it }
        }
        if (get("whisper_friend_request").toBoolean() && get("senderName").isNullOrBlank()) {
            resolve(get("senderId").orEmpty())?.let { out["senderName"] = it }
        }
        if (get("whisper_group_invite").toBoolean()) {
            if (get("inviterName").isNullOrBlank()) {
                resolve(get("inviterId").orEmpty())?.let { out["inviterName"] = it }
            }
            if (get("groupName").isNullOrBlank()) {
                out["groupName"] = localGroupName(get("groupId").orEmpty())
            }
        }
        if (get("whisper_group_message").toBoolean()) {
            if (get("senderName").isNullOrBlank()) {
                resolve(get("senderId").orEmpty())?.let { out["senderName"] = it }
            }
            if (get("groupName").isNullOrBlank()) {
                out["groupName"] = localGroupName(get("groupId").orEmpty())
            }
        }
        if (get("whisper_group_event").toBoolean()) {
            if (get("actorName").isNullOrBlank()) {
                resolve(get("actorId").orEmpty())?.let { out["actorName"] = it }
            }
            if (get("subjectName").isNullOrBlank()) {
                resolve(get("subjectId").orEmpty())?.let { out["subjectName"] = it }
            }
            if (get("groupName").isNullOrBlank()) {
                out["groupName"] = localGroupName(get("groupId").orEmpty())
            }
        }
        return out
    }

    /** Friend name via the shared TTL cache (null when the cache can't answer). */
    private suspend fun resolveFriendOrNull(id: String): String? =
        if (id.isBlank()) null
        else runCatching { repository.friendNameMap()[id] }
            .getOrNull()?.takeIf { it.isNotBlank() }

    /** Friend name resolution through the shared TTL cache; never throws. */
    private suspend fun resolveFriendName(id: String): String =
        resolveFriendOrNull(id) ?: appContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)

    /** Local group-table name; the hard-coded "Group" fallback matches the legacy wire default. */
    private suspend fun localGroupName(groupId: String): String =
        if (groupId.isBlank()) "Group"
        else groupDao.group(groupId)?.name?.takeIf { it.isNotBlank() } ?: "Group"

    private suspend fun render(data: Map<String, String>) {
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

        // Daily Ups wake ping (Phase-2 stories, data-only). Story ID names the
        // story; the NOTIFICATION carries only the poster's name (payload keys
        // mirror the server contract; see WhisperNotificationManager).
        if (data["whisper_story_new"].toBoolean()) {
            // Daily Ups are hidden in this release; swallow the wake ping
            // instead of posting a notification for an unreachable surface.
            if (!com.frerox.toolz.data.whisper.WhisperDailyUpsConfig.ENABLED) return
            val storyId = data["storyId"].orEmpty()
            val posterId = data["posterId"].orEmpty()
            if (storyId.isNotEmpty() && posterId.isNotEmpty()) {
                val posterName = data["posterName"].takeIf { !it.isNullOrBlank() }
                    ?: appContext.getString(com.frerox.toolz.R.string.st_Whisper_SomeoneDefault)
                notificationManager.showDailyUpNotification(
                    posterId = posterId,
                    posterName = posterName,
                    storyId = storyId,
                )
            }
            return
        }

        // Phase-2 groups: invite / message / event wake pings (data-only, no content).
        // Groups are hidden in this release; any group ping is dropped rather
        // than opening a notification whose destination is unreachable.
        if (!com.frerox.toolz.data.whisper.groupsEnabled()) {
            if (data["whisper_group_invite"].toBoolean() ||
                data["whisper_group_message"].toBoolean() ||
                data["whisper_group_event"].toBoolean()) {
                return
            }
        }
        if (data["whisper_group_invite"].toBoolean()) {
            val groupId = data["groupId"].orEmpty()
            val groupName = data["groupName"].takeIf { !it.isNullOrBlank() } ?: localGroupName(groupId)
            val inviterName = data["inviterName"].takeIf { !it.isNullOrBlank() }
                ?: resolveFriendName(data["inviterId"].orEmpty())
            if (groupId.isNotEmpty()) {
                notificationManager.showGroupInviteNotification(groupId, groupName, inviterName)
            }
            return
        }
        if (data["whisper_group_message"].toBoolean()) {
            val groupId = data["groupId"].orEmpty()
            val groupName = data["groupName"].takeIf { !it.isNullOrBlank() } ?: localGroupName(groupId)
            val senderName = data["senderName"].takeIf { !it.isNullOrBlank() }
                ?: resolveFriendOrNull(data["senderId"].orEmpty())
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
                runCatching {
                    val me = supabase.auth.currentUserOrNull()?.id.orEmpty()
                    if (actorId.isNotBlank() && actorId == me) return
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
                        return
                    }
                    val scope = when (kind) {
                        "join" -> com.frerox.toolz.data.whisper.GroupNotifScope.JOIN
                        "leave" -> com.frerox.toolz.data.whisper.GroupNotifScope.LEAVE
                        "decline" -> com.frerox.toolz.data.whisper.GroupNotifScope.DECLINE
                        else -> return
                    }
                    val toggleOn = groupNotifPrefs.isEnabled(groupId, amOwnerOrAdmin)
                    if (!com.frerox.toolz.data.whisper.shouldNotifyGroupEvent(scope, actorId, me, amOwner, toggleOn)) return
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

    companion object {
        private const val TAG = "WhisperPushNotifier"
    }
}
