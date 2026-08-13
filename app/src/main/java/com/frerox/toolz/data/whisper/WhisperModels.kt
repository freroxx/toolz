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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────
// Profile
// ─────────────────────────────────────────────────────────────

@Serializable
data class WhisperProfile(
    val id: String = "",
    val username: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("public_key") val publicKey: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
) {
    /** Clean normalized handle if username is a raw hash */
    val effectiveUsername: String get() {
        return if (username.length > 20 && username.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "anon_${username.take(6)}"
        } else {
            username
        }
    }

    /** Display name with username fallback */
    val effectiveName: String get() = displayName?.takeIf { it.isNotBlank() } ?: effectiveUsername

    /** First letter of effective name for avatar initials */
    val avatarInitial: String get() = effectiveName.firstOrNull()?.uppercase() ?: "?"
}

@Serializable
data class WhisperProfileUpdate(
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("public_key") val publicKey: String? = null,
)

/** Minimal insert model — only contains fields that the app sets; no server-managed timestamps. */
@Serializable
data class WhisperProfileInsert(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_private") val isPrivate: Boolean = false,
    @SerialName("public_key") val publicKey: String? = null,
)

// ─────────────────────────────────────────────────────────────
// Messages
// ─────────────────────────────────────────────────────────────

@Serializable
data class WhisperMessage(
    val id: String = "",
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("receiver_id") val receiverId: String = "",
    val content: String = "",
    @SerialName("content_iv") val contentIv: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
) {
    fun isSentByMe(myUserId: String) = senderId == myUserId

    /** Whether this message was deleted for everyone */
    val isDeletedForEveryone: Boolean get() =
        content == "[deleted_by_sender]" || content.startsWith("[deleted_by_sender:")

    /** Extracted sender name attached to deletion tombstone */
    val deletedSenderName: String? get() {
        if (!isDeletedForEveryone) return null
        return if (content.startsWith("[deleted_by_sender:") && content.endsWith("]")) {
            content.removePrefix("[deleted_by_sender:").removeSuffix("]").trim()
        } else {
            null
        }
    }
}

@Serializable
data class WhisperMessageInsert(
    val id: String? = null,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val content: String,
    @SerialName("content_iv") val contentIv: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * A conversation summary — the latest message thread with another user.
 */
data class WhisperConversation(
    val otherUser: WhisperProfile,
    val lastMessage: WhisperMessage,
    val unreadCount: Int,
    val isMuted: Boolean = false,
)

// ─────────────────────────────────────────────────────────────
// Friends
// ─────────────────────────────────────────────────────────────

enum class FriendStatus { PENDING, ACCEPTED, BLOCKED, NONE }

@Serializable
data class WhisperFriendship(
    val id: String = "",
    @SerialName("user_a") val userA: String = "",
    @SerialName("user_b") val userB: String = "",
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
) {
    fun friendStatus(): FriendStatus = when (status) {
        "accepted" -> FriendStatus.ACCEPTED
        "pending"  -> FriendStatus.PENDING
        "blocked"  -> FriendStatus.BLOCKED
        else       -> FriendStatus.NONE
    }

    fun otherUserId(myId: String): String = if (userA == myId) userB else userA
    fun iRequested(myId: String): Boolean = userA == myId
}

@Serializable
data class WhisperFriendshipInsert(
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
)

data class WhisperFriendRequestItem(
    val friendship: WhisperFriendship,
    val senderProfile: WhisperProfile?,
)

// ─────────────────────────────────────────────────────────────
// Blocked Users
// ─────────────────────────────────────────────────────────────

@Serializable
data class WhisperBlock(
    val id: String = "",
    @SerialName("blocker_id") val blockerId: String = "",
    @SerialName("blocked_id") val blockedId: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class WhisperBlockInsert(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
)

// ─────────────────────────────────────────────────────────────
// Clear Chat Options
// ─────────────────────────────────────────────────────────────

enum class ClearChatTimeRange {
    PAST_24_HOURS,
    PAST_7_DAYS,
    PAST_30_DAYS,
    ALL_TIME,
    CUSTOM
}

// ─────────────────────────────────────────────────────────────
// Auth Token (anonymous)
// ─────────────────────────────────────────────────────────────

data class WhisperAnonToken(
    val token: String,          // 64-char hex string — user must save this!
    val virtualEmail: String,   // SHA-256(token) + "@anon.toolz"
)

// ─────────────────────────────────────────────────────────────
// UI State sealed classes
// ─────────────────────────────────────────────────────────────

sealed class WhisperAuthState {
    object Idle : WhisperAuthState()
    object Loading : WhisperAuthState()
    data class Error(val message: String) : WhisperAuthState()
    object Authenticated : WhisperAuthState()
}

data class WhisperUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentProfile: WhisperProfile? = null,
    val conversations: List<WhisperConversation> = emptyList(),
    val friends: List<WhisperProfile> = emptyList(),
    val pendingIncoming: List<WhisperFriendship> = emptyList(),
    val pendingIncomingRequests: List<WhisperFriendRequestItem> = emptyList(),
    val pendingOutgoing: List<WhisperFriendship> = emptyList(),
    val searchResults: List<WhisperProfile> = emptyList(),
    val recommendedProfiles: List<WhisperProfile> = emptyList(),
    val mutedUserIds: Set<String> = emptySet(),
    val error: String? = null,
)

data class WhisperChatUiState(
    val isLoading: Boolean = false,
    val isFriendStatusLoaded: Boolean = false,
    val otherUser: WhisperProfile? = null,
    val messages: List<WhisperMessage> = emptyList(),
    val friendStatus: FriendStatus = FriendStatus.NONE,
    val iAmRequester: Boolean = false,
    val isPartnerTyping: Boolean = false,
    val isMuted: Boolean = false,
    val isBlockedByMe: Boolean = false,
    val clearedUndoMessagesCount: Int = 0,
    val error: String? = null,
)
