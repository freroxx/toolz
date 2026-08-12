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
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
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
    private val myId get() = supabase.auth.currentUserOrNull()?.id
        ?: error("Not authenticated")

    // PROFILES
    suspend fun getMyProfile(): Result<WhisperProfile> = runCatching {
        val existing = db.from("profiles")
            .select { filter { WhisperProfile::id eq myId } }
            .decodeList<WhisperProfile>()
            .firstOrNull()

        val pubKey = crypto.getPublicKeyBase64()
        val authUser = supabase.auth.currentUserOrNull()
        val email = authUser?.email

        if (existing == null) {
            val defaultUsername = "user_${myId.take(8)}"
            val insertData = WhisperProfileInsert(id = myId, username = defaultUsername, email = email, publicKey = pubKey)
            db.from("profiles").insert(insertData) { defaultToNull = false }
            WhisperProfile(id = myId, username = defaultUsername, email = email, publicKey = pubKey)
        } else {
            val needsKey = existing.publicKey == null && pubKey != null
            val needsEmail = existing.email == null && email != null
            if (needsKey || needsEmail) {
                val update = WhisperProfileUpdate(publicKey = pubKey.takeIf { needsKey }, email = email.takeIf { needsEmail })
                db.from("profiles").update(update) { filter { WhisperProfile::id eq myId } }
                existing.copy(publicKey = pubKey ?: existing.publicKey, email = email ?: existing.email)
            } else existing
        }
    }

    suspend fun getProfile(userId: String): Result<WhisperProfile> = runCatching {
        db.from("profiles")
            .select { filter { WhisperProfile::id eq userId } }
            .decodeSingle()
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
        db.from("profiles").update(updateWithKey) { filter { WhisperProfile::id eq myId } }
    }

    suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String): Result<String> = runCatching {
        val ext = when (mimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "$myId/avatar.$ext"
        store.from("whisper-avatars").upload(path, imageBytes) { upsert = true }
        store.from("whisper-avatars").publicUrl(path)
    }

    // MESSAGES
    suspend fun getMessages(otherUserId: String, limit: Int = 50, beforeCreatedAt: String? = null): Result<List<WhisperMessage>> = runCatching {
        val partnerProfile = getProfile(otherUserId).getOrNull()
        val partnerPubKey = partnerProfile?.publicKey

        val rawMessages = db.from("messages")
            .select {
                filter {
                    or("and(sender_id.eq.$myId,receiver_id.eq.$otherUserId),and(sender_id.eq.$otherUserId,receiver_id.eq.$myId)")
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
        val receiverProfile = getProfile(receiverId).getOrNull()
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

    fun subscribeToIncomingMessages(): Flow<WhisperMessage> = flow {
        val channel = supabase.channel("whisper-messages-$myId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter = "receiver_id=eq.$myId"  // server-side filter!
        }
        channel.subscribe()
        changes.collect { action ->
            try {
                val msg = action.decodeRecord<WhisperMessage>()
                if (msg.receiverId == myId) {  // double-check
                    // Attempt decryption
                    val decrypted = if (msg.contentIv != null) {
                        val senderProfile = runCatching { getProfile(msg.senderId).getOrNull() }.getOrNull()
                        val senderKey = senderProfile?.publicKey
                        if (senderKey != null) {
                            crypto.decryptMessage(msg.content, msg.contentIv, senderKey)
                        } else msg.content
                    } else msg.content
                    emit(msg.copy(content = decrypted))
                }
            } catch (_: Exception) { /* skip malformed */ }
        }
    }

    suspend fun getConversations(): Result<List<WhisperConversation>> = runCatching {
        // Try RPC first for O(n partners) efficiency
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
            // Fallback: limited query when RPC not available
            val allMessages = db.from("messages")
                .select {
                    filter {
                        or("sender_id.eq.$myId,receiver_id.eq.$myId")
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

    fun subscribeToFriendRequests(): Flow<WhisperFriendship> = flow {
        val channel = supabase.channel("whisper-friends-$myId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "friends" }
        channel.subscribe()
        changes.collect { action ->
            try {
                val friendship = action.decodeRecord<WhisperFriendship>()
                if (friendship.userB == myId) emit(friendship)
            } catch (_: Exception) { }
        }
    }
}
