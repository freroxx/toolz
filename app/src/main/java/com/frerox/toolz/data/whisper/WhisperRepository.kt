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
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all Whisper backend operations.
 * Wraps Supabase Postgrest, Realtime, and Storage operations.
 */
@Singleton
class WhisperRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    private val db get() = supabase.postgrest
    private val store get() = supabase.storage
    private val myId get() = supabase.auth.currentUserOrNull()?.id
        ?: error("Not authenticated")

    // ─────────────────────────────────────────────────────────
    // Profiles
    // ─────────────────────────────────────────────────────────

    /** Fetch the current user's own profile. */
    suspend fun getMyProfile(): Result<WhisperProfile> = runCatching {
        db.from("profiles")
            .select { filter { WhisperProfile::id eq myId } }
            .decodeSingle()
    }

    /** Fetch another user's profile by their UUID. */
    suspend fun getProfile(userId: String): Result<WhisperProfile> = runCatching {
        db.from("profiles")
            .select { filter { WhisperProfile::id eq userId } }
            .decodeSingle()
    }

    /**
     * Search public profiles by username (case-insensitive prefix match).
     * Private profiles appear in results but without bio/details (enforced by RLS).
     */
    suspend fun searchProfiles(query: String): Result<List<WhisperProfile>> = runCatching {
        db.from("profiles")
            .select {
                filter {
                    ilike("username", "%${query.trim()}%")
                    neq("id", myId)
                }
                limit(30)
            }
            .decodeList()
    }

    /** Update the current user's profile. */
    suspend fun updateProfile(update: WhisperProfileUpdate): Result<Unit> = runCatching {
        db.from("profiles")
            .update(update) { filter { WhisperProfile::id eq myId } }
    }

    /**
     * Upload a profile picture to Supabase Storage under `<myId>/avatar.<ext>`.
     * Returns the public URL.
     */
    suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String): Result<String> = runCatching {
        val ext = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png"  -> "png"
            "image/webp" -> "webp"
            else         -> "jpg"
        }
        val path = "$myId/avatar.$ext"
        store["whisper-avatars"].upload(path, imageBytes) { upsert = true }
        store["whisper-avatars"].publicUrl(path)
    }

    // ─────────────────────────────────────────────────────────
    // Messages
    // ─────────────────────────────────────────────────────────

    /** Fetch paginated messages for a conversation with [otherUserId]. */
    suspend fun getMessages(
        otherUserId: String,
        limit: Int = 50,
        beforeCreatedAt: String? = null,
    ): Result<List<WhisperMessage>> = runCatching {
        db.from("messages")
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
            .reversed()
    }

    /** Send a message to [receiverId]. */
    suspend fun sendMessage(receiverId: String, content: String): Result<WhisperMessage> =
        runCatching {
            db.from("messages")
                .insert(WhisperMessageInsert(senderId = myId, receiverId = receiverId, content = content))
                .decodeSingle()
        }

    /** Mark all unread messages from [senderId] as read. */
    suspend fun markMessagesAsRead(senderId: String): Result<Unit> = runCatching {
        db.from("messages")
            .update({ set("is_read", true) }) {
                filter {
                    eq("sender_id", senderId)
                    eq("receiver_id", myId)
                    eq("is_read", false)
                }
            }
    }

    /**
     * Subscribe to new incoming messages via Supabase Realtime.
     * Emits new [WhisperMessage] objects as they arrive from the server.
     */
    fun subscribeToIncomingMessages(): Flow<WhisperMessage> = flow {
        val channel = supabase.channel("whisper-messages-$myId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("receiver_id", FilterOperator.EQ, myId)
        }
        channel.subscribe()
        changes.collect { action ->
            try {
                emit(action.decodeRecord<WhisperMessage>())
            } catch (_: Exception) { /* skip malformed records */ }
        }
    }

    /**
     * Get unique conversations from the messages table — the latest message per partner.
     * Returns conversations sorted by most recent message descending.
     */
    suspend fun getConversations(): Result<List<WhisperConversation>> = runCatching {
        // Fetch all messages involving current user (RLS handles filtering)
        val allMessages = db.from("messages")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList<WhisperMessage>()

        // Group by conversation partner
        val grouped = allMessages.groupBy { msg ->
            if (msg.senderId == myId) msg.receiverId else msg.senderId
        }

        val conversations = mutableListOf<WhisperConversation>()
        for ((partnerId, msgs) in grouped) {
            val profileResult = getProfile(partnerId)
            val profile = profileResult.getOrNull() ?: continue
            val lastMsg = msgs.first() // already sorted desc
            val unread = msgs.count { it.receiverId == myId && !it.isRead }
            conversations.add(WhisperConversation(profile, lastMsg, unread))
        }
        conversations.sortedByDescending { it.lastMessage.createdAt }
    }

    // ─────────────────────────────────────────────────────────
    // Friends
    // ─────────────────────────────────────────────────────────

    /** Fetch all friend records for the current user. */
    suspend fun getFriendships(): Result<List<WhisperFriendship>> = runCatching {
        db.from("friends")
            .select {
                filter {
                    or {
                        eq("user_a", myId)
                        eq("user_b", myId)
                    }
                }
            }
            .decodeList()
    }

    /** Get accepted friends as profiles. */
    suspend fun getFriends(): Result<List<WhisperProfile>> = runCatching {
        val friendships = getFriendships().getOrThrow()
            .filter { it.status == "accepted" }

        friendships.mapNotNull { friendship ->
            getProfile(friendship.otherUserId(myId)).getOrNull()
        }
    }

    /** Get pending incoming friend requests (user_b = me, status = pending). */
    suspend fun getPendingIncoming(): Result<List<WhisperFriendship>> = runCatching {
        db.from("friends")
            .select {
                filter {
                    eq("user_b", myId)
                    eq("status", "pending")
                }
            }
            .decodeList()
    }

    /** Get pending outgoing friend requests (user_a = me, status = pending). */
    suspend fun getPendingOutgoing(): Result<List<WhisperFriendship>> = runCatching {
        db.from("friends")
            .select {
                filter {
                    eq("user_a", myId)
                    eq("status", "pending")
                }
            }
            .decodeList()
    }

    /** Send a friend request to [targetUserId]. */
    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> = runCatching {
        db.from("friends")
            .insert(WhisperFriendshipInsert(userA = myId, userB = targetUserId))
    }

    /** Accept a friend request. [friendshipId] must have user_b = me. */
    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> = runCatching {
        db.from("friends")
            .update({ set("status", "accepted") }) {
                filter { eq("id", friendshipId) }
            }
    }

    /** Decline or cancel a friend request / unfriend. */
    suspend fun deleteFriendship(friendshipId: String): Result<Unit> = runCatching {
        db.from("friends")
            .delete { filter { eq("id", friendshipId) } }
    }

    /** Get the current friendship status between me and [otherUserId]. */
    suspend fun getFriendshipStatus(otherUserId: String): Result<Pair<FriendStatus, WhisperFriendship?>> =
        runCatching {
            val records = db.from("friends")
                .select {
                    filter {
                        or {
                            and {
                                eq("user_a", myId)
                                eq("user_b", otherUserId)
                            }
                            and {
                                eq("user_a", otherUserId)
                                eq("user_b", myId)
                            }
                        }
                    }
                }
                .decodeList<WhisperFriendship>()

            val record = records.firstOrNull()
            val status = record?.friendStatus() ?: FriendStatus.NONE
            Pair(status, record)
        }

    /**
     * Subscribe to friend request changes via Realtime.
     */
    fun subscribeToFriendRequests(): Flow<WhisperFriendship> = flow {
        val channel = supabase.channel("whisper-friends-$myId")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "friends"
            filter("user_b", FilterOperator.EQ, myId)
        }
        channel.subscribe()
        changes.collect { action ->
            try {
                emit(action.decodeRecord<WhisperFriendship>())
            } catch (_: Exception) { /* skip */ }
        }
    }
}
