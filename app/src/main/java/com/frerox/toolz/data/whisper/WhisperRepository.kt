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

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.broadcast.BroadcastPayload
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val crypto: WhisperCrypto,
) {
    private val db get() = supabase.postgrest
    private val store get() = supabase.storage
    private val realtime get() = supabase.realtime
    val myId get() = supabase.auth.currentUserOrNull()?.id ?: ""

    private val profileCache = mutableMapOf<String, WhisperProfile>()

    // PROFILES
    suspend fun getMyProfile(forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        if (!forceRefresh && profileCache.containsKey(myId)) {
            val cached = profileCache[myId]
            if (cached?.publicKey != null) return Result.success(cached)
        }

        val existing = db.from("profiles")
            .select { filter { eq("id", myId) } }
            .decodeList<WhisperProfile>()
            .firstOrNull()

        val pubKey = crypto.getPublicKeyBase64()

        val profile = if (existing == null) {
            val defaultUsername = "user_${myId.take(8)}"
            val insertData = WhisperProfileInsert(
                id = myId,
                username = defaultUsername,
                isPrivate = false,
                publicKey = pubKey
            )
            db.from("profiles").insert(insertData) { defaultToNull = false }
            WhisperProfile(id = myId, username = defaultUsername, isPrivate = false, publicKey = pubKey)
        } else {
            // Check if username is an ugly 64-char token string and normalize it
            val isUglyHexUsername = existing.username.length >= 32 &&
                existing.username.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

            val cleanUsername = if (isUglyHexUsername) "anon_${existing.username.take(6)}" else null
            val needsKey = existing.publicKey.isNullOrBlank() && pubKey != null

            if (needsKey || cleanUsername != null) {
                val update = WhisperProfileUpdate(
                    publicKey = pubKey.takeIf { needsKey },
                    username = cleanUsername
                )
                db.from("profiles").update(update) { filter { eq("id", myId) } }
                existing.copy(
                    publicKey = pubKey ?: existing.publicKey,
                    username = cleanUsername ?: existing.username
                )
            } else existing
        }
        profileCache[myId] = profile
        profile
    }

    suspend fun getProfile(userId: String, forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        if (!forceRefresh && profileCache.containsKey(userId)) {
            val cached = profileCache[userId]
            if (cached != null && (!cached.publicKey.isNullOrBlank() || userId == myId)) {
                return Result.success(cached)
            }
        }
        val p = db.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingle<WhisperProfile>()
        profileCache[userId] = p
        p
    }

    suspend fun searchProfiles(query: String): Result<List<WhisperProfile>> = runCatching {
        val q = query.trim()
        db.from("profiles")
            .select {
                filter {
                    or {
                        ilike("username", "%$q%")
                        ilike("display_name", "%$q%")
                    }
                    neq("id", myId)
                }
                limit(30)
            }
            .decodeList()
    }

    suspend fun checkUsernameAvailable(username: String): Result<Boolean> = runCatching {
        val results = db.from("profiles")
            .select { filter { eq("username", username.trim().lowercase()) } }
            .decodeList<WhisperProfile>()
        results.isEmpty()
    }

    suspend fun updateProfile(update: WhisperProfileUpdate): Result<Unit> = runCatching {
        val pubKey = crypto.getPublicKeyBase64()
        val updateWithKey = if (update.publicKey == null && pubKey != null) update.copy(publicKey = pubKey) else update
        db.from("profiles").update(updateWithKey) { filter { eq("id", myId) } }
        profileCache.remove(myId)
    }

    suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String): Result<String> = runCatching {
        val ext = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val path = "$myId/avatar.$ext"
        store.from("whisper-avatars").upload(path, imageBytes) { upsert = true }
        val publicUrl = store.from("whisper-avatars").publicUrl(path)
        val urlWithCacheBuster = "$publicUrl?t=${System.currentTimeMillis()}"
        updateProfile(WhisperProfileUpdate(avatarUrl = urlWithCacheBuster))
        profileCache.remove(myId)
        urlWithCacheBuster
    }

    suspend fun deleteAvatar(): Result<Unit> = runCatching {
        db.from("profiles").update(mapOf("avatar_url" to null as String?)) {
            filter { eq("id", myId) }
        }
        profileCache.remove(myId)
    }

    // ─────────────────────────────────────────────────────────────
    // MESSAGES & REALTIME CHAT
    // ─────────────────────────────────────────────────────────────

    private fun conversationKey(userA: String, userB: String): String =
        if (userA < userB) "${userA}_${userB}" else "${userB}_${userA}"

    suspend fun getMessages(otherUserId: String, limit: Int = 100, beforeCreatedAt: String? = null): Result<List<WhisperMessage>> = runCatching {
        val partnerProfile = getProfile(otherUserId, forceRefresh = true).getOrNull()
        val partnerPubKey = partnerProfile?.publicKey

        // If I blocked this user, do not load their incoming messages
        val isBlocked = isUserBlockedByMe(otherUserId)

        val rawMessages = db.from("messages")
            .select {
                filter {
                    or {
                        and {
                            eq("sender_id", myId)
                            eq("receiver_id", otherUserId)
                        }
                        and {
                            eq("sender_id", otherUserId)
                            eq("receiver_id", myId)
                        }
                    }
                    if (beforeCreatedAt != null) lt("created_at", beforeCreatedAt)
                }
                order("created_at", Order.DESCENDING)
                limit(limit.toLong())
            }
            .decodeList<WhisperMessage>()

        rawMessages
            .filter { msg -> !isBlocked || msg.senderId == myId }
            .map { msg ->
                if (msg.isDeletedForEveryone) {
                    msg
                } else if (msg.contentIv != null && partnerPubKey != null) {
                    val decrypted = crypto.decryptMessage(msg.content, msg.contentIv, partnerPubKey)
                    msg.copy(content = decrypted)
                } else msg
            }.reversed()
    }

    suspend fun sendMessage(receiverId: String, content: String): Result<WhisperMessage> = runCatching {
        val receiverProfile = getProfile(receiverId, forceRefresh = true).getOrNull()
        val receiverPubKey = receiverProfile?.publicKey
        val encryptedPair = receiverPubKey?.let { key -> crypto.encryptMessage(content, key) }
        val insert = if (encryptedPair != null) {
            WhisperMessageInsert(senderId = myId, receiverId = receiverId, content = encryptedPair.first, contentIv = encryptedPair.second)
        } else {
            WhisperMessageInsert(senderId = myId, receiverId = receiverId, content = content)
        }
        val insertedMsg = db.from("messages").insert(insert) { select() }.decodeSingle<WhisperMessage>()
        val clearMsg = insertedMsg.copy(content = content)

        // Instant Realtime Peer-to-Peer Broadcast
        runCatching {
            val convoKey = conversationKey(myId, receiverId)
            val chatChannel = supabase.channel("chat_$convoKey")
            chatChannel.subscribe()
            chatChannel.broadcast(
                event = "new_message",
                payload = BroadcastPayload.Json(
                    buildJsonObject {
                        put("id", insertedMsg.id)
                        put("sender_id", insertedMsg.senderId)
                        put("receiver_id", insertedMsg.receiverId)
                        put("content", insertedMsg.content)
                        put("content_iv", insertedMsg.contentIv)
                        put("created_at", insertedMsg.createdAt)
                    }
                )
            )
        }

        clearMsg
    }

    suspend fun deleteMessageForEveryone(messageId: String, senderDisplayName: String): Result<Unit> = runCatching {
        val tombstone = "[deleted_by_sender:$senderDisplayName]"
        db.from("messages").update(
            buildJsonObject {
                put("content", tombstone)
                put("content_iv", null as String?)
            }
        ) {
            filter {
                eq("id", messageId)
                eq("sender_id", myId)
            }
        }
    }

    suspend fun deleteMessageForMe(messageId: String): Result<Unit> = runCatching {
        // If it's my sent message, delete from server; otherwise delete locally
        db.from("messages").delete {
            filter {
                eq("id", messageId)
                eq("sender_id", myId)
            }
        }
    }

    suspend fun markMessagesAsRead(senderId: String): Result<Unit> = runCatching {
        db.from("messages").update({ set("is_read", true) }) {
            filter { eq("sender_id", senderId); eq("receiver_id", myId); eq("is_read", false) }
        }
    }

    /**
     * Dual-Channel Realtime Chat Flow:
     * Combines Supabase Realtime Broadcast (<50ms) + Postgres Changes.
     */
    fun subscribeToChat(otherUserId: String): Flow<WhisperMessage> = callbackFlow {
        if (myId.isEmpty() || otherUserId.isEmpty()) {
            close()
            return@callbackFlow
        }

        val convoKey = conversationKey(myId, otherUserId)
        val channel = supabase.channel("chat_$convoKey")

        // 1. Listen for Instant Realtime Broadcasts
        val broadcastFlow = channel.broadcastFlow<JsonObject>("new_message")
        val bJob = launch {
            broadcastFlow.collect { json ->
                try {
                    val id = json["id"]?.jsonPrimitive?.content ?: return@collect
                    val senderId = json["sender_id"]?.jsonPrimitive?.content ?: return@collect
                    val receiverId = json["receiver_id"]?.jsonPrimitive?.content ?: return@collect
                    val rawContent = json["content"]?.jsonPrimitive?.content ?: ""
                    val contentIv = json["content_iv"]?.jsonPrimitive?.content
                    val createdAt = json["created_at"]?.jsonPrimitive?.content ?: ""

                    if (isUserBlockedByMe(senderId)) return@collect

                    val otherProfile = runCatching { getProfile(otherUserId).getOrNull() }.getOrNull()
                    val otherKey = otherProfile?.publicKey

                    val decrypted = if (rawContent.startsWith("[deleted_by_sender")) {
                        rawContent
                    } else if (contentIv != null && otherKey != null) {
                        crypto.decryptMessage(rawContent, contentIv, otherKey)
                    } else rawContent

                    val msg = WhisperMessage(
                        id = id,
                        senderId = senderId,
                        receiverId = receiverId,
                        content = decrypted,
                        contentIv = contentIv,
                        createdAt = createdAt
                    )
                    trySend(msg)
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Broadcast parse error", e)
                }
            }
        }

        // 2. Listen for Postgres Changes (Inserts and Updates for deletions/reads)
        val postgresChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }
        val pJob = launch {
            postgresChanges.collect { action ->
                try {
                    val msg = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperMessage>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperMessage>()
                        else -> null
                    }
                    if (msg != null && (
                        (msg.senderId == otherUserId && msg.receiverId == myId) ||
                        (msg.senderId == myId && msg.receiverId == otherUserId)
                    )) {
                        if (isUserBlockedByMe(msg.senderId)) return@collect

                        val otherProfile = runCatching { getProfile(otherUserId).getOrNull() }.getOrNull()
                        val otherKey = otherProfile?.publicKey

                        val decrypted = if (msg.isDeletedForEveryone) {
                            msg.content
                        } else if (msg.contentIv != null && otherKey != null) {
                            crypto.decryptMessage(msg.content, msg.contentIv, otherKey)
                        } else msg.content

                        trySend(msg.copy(content = decrypted))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Postgres realtime collect error", e)
                }
            }
        }

        channel.subscribe()

        awaitClose {
            bJob.cancel()
            pJob.cancel()
            launch {
                try { realtime.removeChannel(channel) } catch (_: Exception) {}
            }
        }
    }

    fun subscribeToIncomingMessages(userId: String): Flow<WhisperMessage> = callbackFlow {
        if (userId.isEmpty()) {
            close()
            return@callbackFlow
        }

        val channelName = "whisper-messages-$userId"
        val channel = supabase.channel(channelName)

        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
        }

        val job = launch {
            changes.collect { action ->
                try {
                    val msg = when (action) {
                        is PostgresAction.Insert -> action.decodeRecord<WhisperMessage>()
                        is PostgresAction.Update -> action.decodeRecord<WhisperMessage>()
                        else -> null
                    }
                    if (msg != null && (msg.receiverId == userId || msg.senderId == userId)) {
                        if (isUserBlockedByMe(msg.senderId)) return@collect

                        val otherId = if (msg.senderId == userId) msg.receiverId else msg.senderId
                        val otherProfile = runCatching { getProfile(otherId).getOrNull() }.getOrNull()
                        val otherKey = otherProfile?.publicKey

                        val decrypted = if (msg.isDeletedForEveryone) {
                            msg.content
                        } else if (msg.contentIv != null && otherKey != null) {
                            crypto.decryptMessage(msg.content, msg.contentIv, otherKey)
                        } else msg.content

                        trySend(msg.copy(content = decrypted))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhisperRepo", "Realtime collect error", e)
                }
            }
        }

        channel.subscribe()

        awaitClose {
            job.cancel()
            launch {
                try { realtime.removeChannel(channel) } catch (_: Exception) {}
            }
        }
    }

    // CLEAR CHAT
    suspend fun clearMessagesForRange(
        otherUserId: String,
        fromIso: String? = null,
        toIso: String? = null
    ): Result<List<WhisperMessage>> = runCatching {
        val toDelete = db.from("messages").select {
            filter {
                eq("sender_id", myId)
                eq("receiver_id", otherUserId)
                if (fromIso != null) gte("created_at", fromIso)
                if (toIso != null) lte("created_at", toIso)
            }
        }.decodeList<WhisperMessage>()

        if (toDelete.isNotEmpty()) {
            db.from("messages").delete {
                filter {
                    eq("sender_id", myId)
                    eq("receiver_id", otherUserId)
                    if (fromIso != null) gte("created_at", fromIso)
                    if (toIso != null) lte("created_at", toIso)
                }
            }
        }
        toDelete
    }

    suspend fun restoreMessages(messages: List<WhisperMessage>): Result<Unit> = runCatching {
        if (messages.isEmpty()) return@runCatching
        val inserts = messages.map { msg ->
            WhisperMessageInsert(
                senderId = msg.senderId,
                receiverId = msg.receiverId,
                content = msg.content,
                contentIv = msg.contentIv,
                isRead = msg.isRead,
                createdAt = msg.createdAt
            )
        }
        db.from("messages").insert(inserts)
    }

    suspend fun getConversations(): Result<List<WhisperConversation>> = runCatching {
        @Serializable
        data class ConvRow(
            @SerialName("partner_id") val partnerId: String = "",
            @SerialName("last_content") val lastContent: String = "",
            @SerialName("last_content_iv") val lastContentIv: String? = null,
            @SerialName("last_created_at") val lastCreatedAt: String = "",
            @SerialName("unread_count") val unreadCount: Long = 0,
        )

        val rows = runCatching {
            db.rpc("get_conversations", buildJsonObject { put("p_user_id", myId) })
                .decodeList<ConvRow>()
        }.getOrNull()

        if (rows != null) {
            val conversations = mutableListOf<WhisperConversation>()
            for (row in rows) {
                if (isUserBlockedByMe(row.partnerId)) continue
                val profile = getProfile(row.partnerId).getOrNull() ?: continue
                val decryptedContent = if (row.lastContent.startsWith("[deleted_by_sender")) {
                    "Message deleted"
                } else if (row.lastContentIv != null && profile.publicKey != null) {
                    crypto.decryptMessage(row.lastContent, row.lastContentIv, profile.publicKey)
                } else row.lastContent

                val fakeMsg = WhisperMessage(
                    id = "",
                    senderId = row.partnerId,
                    receiverId = myId,
                    content = decryptedContent,
                    isRead = row.unreadCount == 0L,
                    createdAt = row.lastCreatedAt,
                )
                conversations.add(WhisperConversation(profile, fakeMsg, row.unreadCount.toInt()))
            }
            conversations
        } else {
            val allMessages = db.from("messages")
                .select {
                    filter {
                        or {
                            eq("sender_id", myId)
                            eq("receiver_id", myId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<WhisperMessage>()

            val grouped = allMessages.groupBy { msg ->
                if (msg.senderId == myId) msg.receiverId else msg.senderId
            }
            val conversations = mutableListOf<WhisperConversation>()
            for ((partnerId, msgs) in grouped) {
                if (isUserBlockedByMe(partnerId)) continue
                val profile = getProfile(partnerId).getOrNull() ?: continue
                val lastMsg = msgs.first()
                val decryptedContent = if (lastMsg.isDeletedForEveryone) {
                    "Message deleted"
                } else if (lastMsg.contentIv != null && profile.publicKey != null) {
                    crypto.decryptMessage(lastMsg.content, lastMsg.contentIv, profile.publicKey)
                } else lastMsg.content

                val unread = msgs.count { it.receiverId == myId && !it.isRead }
                conversations.add(WhisperConversation(profile, lastMsg.copy(content = decryptedContent), unread))
            }
            conversations.sortedByDescending { it.lastMessage.createdAt }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // FRIENDS & ROBUST BIDIRECTIONAL RELATIONSHIPS
    // ─────────────────────────────────────────────────────────────

    suspend fun getFriendships(): Result<List<WhisperFriendship>> = runCatching {
        db.from("friends").select {
            filter { or { eq("user_a", myId); eq("user_b", myId) } }
        }.decodeList()
    }

    suspend fun getFriends(): Result<List<WhisperProfile>> = runCatching {
        val friendships = getFriendships().getOrThrow().filter { it.status == "accepted" }
        friendships.mapNotNull { friendship -> getProfile(friendship.otherUserId(myId)).getOrNull() }
    }

    suspend fun getPendingIncoming(): Result<List<WhisperFriendship>> = runCatching {
        db.from("friends").select { filter { eq("user_b", myId); eq("status", "pending") } }.decodeList()
    }

    suspend fun getPendingIncomingWithProfiles(): Result<List<WhisperFriendRequestItem>> = runCatching {
        val pending = getPendingIncoming().getOrThrow()
        pending.map { f ->
            val senderProfile = getProfile(f.userA).getOrNull()
            WhisperFriendRequestItem(friendship = f, senderProfile = senderProfile)
        }
    }

    suspend fun getPendingOutgoing(): Result<List<WhisperFriendship>> = runCatching {
        db.from("friends").select { filter { eq("user_a", myId); eq("status", "pending") } }.decodeList()
    }

    /**
     * Send friend request with duplicate protection:
     * If the other user already sent a request to me, automatically accept it!
     */
    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> = runCatching {
        val existingPair = getFriendshipStatus(targetUserId).getOrNull()
        val (status, record) = existingPair ?: Pair(FriendStatus.NONE, null)

        if (status == FriendStatus.ACCEPTED) {
            return@runCatching // Already friends!
        }

        if (record != null) {
            if (record.userA == targetUserId && record.userB == myId) {
                // The other user already requested to be friends -> accept directly!
                acceptFriendRequest(record.id).getOrThrow()
                return@runCatching
            }
            if (record.userA == myId && record.userB == targetUserId) {
                return@runCatching // Already sent
            }
        }

        db.from("friends").insert(WhisperFriendshipInsert(userA = myId, userB = targetUserId))
    }

    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> = runCatching {
        // Find friendship
        val existing = db.from("friends").select { filter { eq("id", friendshipId) } }
            .decodeSingleOrNull<WhisperFriendship>()

        db.from("friends").update({ set("status", "accepted") }) { filter { eq("id", friendshipId) } }

        // Clean up any duplicate records between the same two users
        if (existing != null) {
            val uA = existing.userA
            val uB = existing.userB
            runCatching {
                db.from("friends").delete {
                    filter {
                        neq("id", friendshipId)
                        or {
                            and { eq("user_a", uA); eq("user_b", uB) }
                            and { eq("user_a", uB); eq("user_b", uA) }
                        }
                    }
                }
            }
        }
    }

    suspend fun deleteFriendship(friendshipId: String): Result<Unit> = runCatching {
        db.from("friends").delete { filter { eq("id", friendshipId) } }
    }

    suspend fun getFriendshipStatus(otherUserId: String): Result<Pair<FriendStatus, WhisperFriendship?>> = runCatching {
        val records = db.from("friends").select {
            filter {
                or {
                    and { eq("user_a", myId); eq("user_b", otherUserId) }
                    and { eq("user_a", otherUserId); eq("user_b", myId) }
                }
            }
        }.decodeList<WhisperFriendship>()

        val accepted = records.firstOrNull { it.status == "accepted" }
        if (accepted != null) return@runCatching Pair(FriendStatus.ACCEPTED, accepted)

        val pending = records.firstOrNull { it.status == "pending" }
        if (pending != null) return@runCatching Pair(FriendStatus.PENDING, pending)

        Pair(records.firstOrNull()?.friendStatus() ?: FriendStatus.NONE, records.firstOrNull())
    }

    fun subscribeToFriendUpdates(): Flow<WhisperFriendship> = flow {
        val channel = supabase.channel("whisper-friends-all-$myId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "friends" }
        channel.subscribe()
        changes.collect { action ->
            try {
                val record = when (action) {
                    is PostgresAction.Insert -> action.decodeRecord<WhisperFriendship>()
                    is PostgresAction.Update -> action.decodeRecord<WhisperFriendship>()
                    else -> null
                }
                if (record != null && (record.userA == myId || record.userB == myId)) {
                    emit(record)
                }
            } catch (_: Exception) { }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // BLOCKING (ROBUST: USER2 NEVER SEES THEY ARE BLOCKED)
    // ─────────────────────────────────────────────────────────────

    private val blockedByMeCache = mutableSetOf<String>()

    private suspend fun isUserBlockedByMe(userId: String): Boolean {
        if (blockedByMeCache.contains(userId)) return true
        val isBlocked = runCatching {
            val blocks = db.from("whisper_blocks").select {
                filter {
                    eq("blocker_id", myId)
                    eq("blocked_id", userId)
                }
            }.decodeList<WhisperBlock>()
            blocks.isNotEmpty()
        }.getOrDefault(false)

        if (isBlocked) blockedByMeCache.add(userId)
        return isBlocked
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> = runCatching {
        blockedByMeCache.add(targetUserId)
        runCatching {
            db.from("whisper_blocks").insert(WhisperBlockInsert(blockerId = myId, blockedId = targetUserId))
        }
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> = runCatching {
        blockedByMeCache.remove(targetUserId)
        runCatching {
            db.from("whisper_blocks").delete { filter { eq("blocker_id", myId); eq("blocked_id", targetUserId) } }
        }
    }

    suspend fun isBlockedByMe(otherUserId: String): Boolean = isUserBlockedByMe(otherUserId)

    // ─────────────────────────────────────────────────────────────
    // FRIENDS OF FRIENDS (RECOMMENDED PROFILES)
    // ─────────────────────────────────────────────────────────────

    suspend fun getFriendsOfFriends(): Result<List<WhisperProfile>> = runCatching {
        val myFriends = getFriends().getOrNull() ?: emptyList()
        val myFriendIds = myFriends.map { it.id }.toSet() + myId

        if (myFriends.isEmpty()) {
            return@runCatching db.from("profiles")
                .select {
                    filter {
                        eq("is_private", false)
                        neq("id", myId)
                    }
                    limit(15)
                }
                .decodeList<WhisperProfile>()
        }

        val candidateFriendships = db.from("friends").select {
            filter { eq("status", "accepted") }
            limit(100)
        }.decodeList<WhisperFriendship>()

        val candidateUserIds = mutableSetOf<String>()
        for (f in candidateFriendships) {
            if (f.userA in myFriendIds && f.userB !in myFriendIds) {
                candidateUserIds.add(f.userB)
            } else if (f.userB in myFriendIds && f.userA !in myFriendIds) {
                candidateUserIds.add(f.userA)
            }
        }

        if (candidateUserIds.isEmpty()) {
            return@runCatching db.from("profiles")
                .select {
                    filter {
                        eq("is_private", false)
                        neq("id", myId)
                    }
                    limit(15)
                }
                .decodeList<WhisperProfile>()
                .filter { it.id !in myFriendIds }
        }

        val recommended = mutableListOf<WhisperProfile>()
        for (cId in candidateUserIds.take(15)) {
            val p = getProfile(cId).getOrNull()
            if (p != null && !p.isPrivate && p.id != myId && !isUserBlockedByMe(p.id)) {
                recommended.add(p)
            }
        }
        recommended
    }

    // ─────────────────────────────────────────────────────────────
    // TYPING INDICATORS
    // ─────────────────────────────────────────────────────────────

    suspend fun sendTypingStatus(targetUserId: String, isTyping: Boolean) {
        runCatching {
            val channelKey = conversationKey(myId, targetUserId)
            val channel = supabase.channel("typing_$channelKey")
            channel.subscribe()
            channel.broadcast(
                event = "typing",
                payload = BroadcastPayload.Json(
                    buildJsonObject {
                        put("sender_id", myId)
                        put("is_typing", isTyping)
                    }
                )
            )
        }
    }

    fun subscribeToTypingStatus(otherUserId: String): Flow<Boolean> = flow {
        val channelKey = conversationKey(myId, otherUserId)
        val channel = supabase.channel("typing_$channelKey")
        val broadcasts = channel.broadcastFlow<JsonObject>("typing")
        channel.subscribe()
        broadcasts.collect { json ->
            try {
                val senderId = json["sender_id"]?.jsonPrimitive?.content
                val isTyping = json["is_typing"]?.jsonPrimitive?.booleanOrNull ?: false
                if (senderId == otherUserId && !isUserBlockedByMe(otherUserId)) {
                    emit(isTyping)
                }
            } catch (_: Exception) { }
        }
    }
}
