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
            val needsKey = existing.publicKey.isNullOrBlank() && pubKey != null
            if (needsKey) {
                val update = WhisperProfileUpdate(publicKey = pubKey)
                db.from("profiles").update(update) { filter { eq("id", myId) } }
                existing.copy(publicKey = pubKey)
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

    // MESSAGES
    suspend fun getMessages(otherUserId: String, limit: Int = 100, beforeCreatedAt: String? = null): Result<List<WhisperMessage>> = runCatching {
        val partnerProfile = getProfile(otherUserId, forceRefresh = true).getOrNull()
        val partnerPubKey = partnerProfile?.publicKey

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

        rawMessages.map { msg ->
            if (msg.contentIv != null && partnerPubKey != null) {
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
        insertedMsg.copy(content = content)
    }

    suspend fun markMessagesAsRead(senderId: String): Result<Unit> = runCatching {
        db.from("messages").update({ set("is_read", true) }) {
            filter { eq("sender_id", senderId); eq("receiver_id", myId); eq("is_read", false) }
        }
    }

    fun subscribeToIncomingMessages(userId: String): Flow<WhisperMessage> = callbackFlow {
        if (userId.isEmpty()) {
            android.util.Log.w("WhisperRepo", "Cannot subscribe: userId is empty")
            close()
            return@callbackFlow
        }

        val channelName = "whisper-messages-$userId"
        val channel = supabase.channel(channelName)

        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }

        val job = launch {
            changes.collect { action ->
                try {
                    val msg = action.decodeRecord<WhisperMessage>()
                    if (msg.receiverId == userId || msg.senderId == userId) {
                        val otherId = if (msg.senderId == userId) msg.receiverId else msg.senderId
                        val otherProfile = runCatching { getProfile(otherId).getOrNull() }.getOrNull()
                        val otherKey = otherProfile?.publicKey

                        val decrypted = if (msg.contentIv != null && otherKey != null) {
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
        // First, fetch messages sent by me in this range so we can support undo
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
                val profile = getProfile(row.partnerId).getOrNull() ?: continue
                val decryptedContent = if (row.lastContentIv != null && profile.publicKey != null) {
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
                val profile = getProfile(partnerId).getOrNull() ?: continue
                val lastMsg = msgs.first()
                val decryptedContent = if (lastMsg.contentIv != null && profile.publicKey != null) {
                    crypto.decryptMessage(lastMsg.content, lastMsg.contentIv, profile.publicKey)
                } else lastMsg.content
                val unread = msgs.count { it.receiverId == myId && !it.isRead }
                conversations.add(WhisperConversation(profile, lastMsg.copy(content = decryptedContent), unread))
            }
            conversations.sortedByDescending { it.lastMessage.createdAt }
        }
    }

    // FRIENDS
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

    suspend fun getPendingOutgoing(): Result<List<WhisperFriendship>> = runCatching {
        db.from("friends").select { filter { eq("user_a", myId); eq("status", "pending") } }.decodeList()
    }

    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> = runCatching {
        db.from("friends").insert(WhisperFriendshipInsert(userA = myId, userB = targetUserId))
    }

    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> = runCatching {
        db.from("friends").update({ set("status", "accepted") }) { filter { eq("id", friendshipId) } }
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
        val record = records.firstOrNull()
        Pair(record?.friendStatus() ?: FriendStatus.NONE, record)
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

    // BLOCKING
    suspend fun blockUser(targetUserId: String): Result<Unit> = runCatching {
        // First try dedicated blocks table, fallback to friends table blocked status
        val insertResult = runCatching {
            db.from("whisper_blocks").insert(WhisperBlockInsert(blockerId = myId, blockedId = targetUserId))
        }
        if (insertResult.isFailure) {
            // Fallback: update friendship status to blocked
            val statusResult = getFriendshipStatus(targetUserId).getOrNull()
            val existing = statusResult?.second
            if (existing != null) {
                db.from("friends").update({ set("status", "blocked") }) { filter { eq("id", existing.id) } }
            } else {
                db.from("friends").insert(WhisperFriendship(userA = myId, userB = targetUserId, status = "blocked"))
            }
        }
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> = runCatching {
        runCatching {
            db.from("whisper_blocks").delete { filter { eq("blocker_id", myId); eq("blocked_id", targetUserId) } }
        }
        // Also cleanup friends blocked status
        val statusResult = getFriendshipStatus(targetUserId).getOrNull()
        val existing = statusResult?.second
        if (existing != null && existing.status == "blocked") {
            deleteFriendship(existing.id)
        }
    }

    suspend fun getBlockStatus(otherUserId: String): Result<Pair<Boolean, Boolean>> = runCatching {
        var blockedByMe = false
        var blockedByOther = false

        // Check blocks table
        val blocks = runCatching {
            db.from("whisper_blocks").select {
                filter {
                    or {
                        and { eq("blocker_id", myId); eq("blocked_id", otherUserId) }
                        and { eq("blocker_id", otherUserId); eq("blocked_id", myId) }
                    }
                }
            }.decodeList<WhisperBlock>()
        }.getOrNull() ?: emptyList()

        for (b in blocks) {
            if (b.blockerId == myId) blockedByMe = true
            if (b.blockerId == otherUserId) blockedByOther = true
        }

        // Check friends table as fallback
        val friendship = getFriendshipStatus(otherUserId).getOrNull()?.second
        if (friendship?.status == "blocked") {
            if (friendship.userA == myId) blockedByMe = true
            else blockedByOther = true
        }

        Pair(blockedByMe, blockedByOther)
    }

    // FRIENDS OF FRIENDS (RECOMMENDED PROFILES)
    suspend fun getFriendsOfFriends(): Result<List<WhisperProfile>> = runCatching {
        // 1. Get my current friends
        val myFriends = getFriends().getOrNull() ?: emptyList()
        val myFriendIds = myFriends.map { it.id }.toSet() + myId

        if (myFriends.isEmpty()) {
            // If no friends, return some public profiles as recommendations
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

        // 2. Query friendships involving my friends
        val candidateFriendships = db.from("friends").select {
            filter {
                eq("status", "accepted")
            }
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
            // Fallback to general public profiles
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
            if (p != null && !p.isPrivate && p.id != myId) {
                recommended.add(p)
            }
        }
        recommended
    }

    // TYPING INDICATORS
    suspend fun sendTypingStatus(targetUserId: String, isTyping: Boolean) {
        runCatching {
            val channelKey = if (myId < targetUserId) "${myId}_${targetUserId}" else "${targetUserId}_${myId}"
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
        val channelKey = if (myId < otherUserId) "${myId}_${otherUserId}" else "${otherUserId}_${myId}"
        val channel = supabase.channel("typing_$channelKey")
        val broadcasts = channel.broadcastFlow<JsonObject>("typing")
        channel.subscribe()
        broadcasts.collect { json ->
            try {
                val senderId = json["sender_id"]?.jsonPrimitive?.content
                val isTyping = json["is_typing"]?.jsonPrimitive?.booleanOrNull ?: false
                if (senderId == otherUserId) {
                    emit(isTyping)
                }
            } catch (_: Exception) { }
        }
    }
}
