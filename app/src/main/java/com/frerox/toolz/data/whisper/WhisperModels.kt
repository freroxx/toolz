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
import kotlinx.serialization.json.Json

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
    @SerialName("hide_from_discover") val isHiddenFromDiscover: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
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

    // M-14 FIX (reviewwhisper.md): parse once per instance — onlineStatus is read on
    // every recomposition of every list row; the old getter re-parsed the timestamp each time.
    private val parsedLastSeen: java.time.OffsetDateTime? by lazy {
        lastSeenAt?.let { runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull() }
    }

    /** Formatted online status / last seen */
    val onlineStatus: String get() {
        val dt = parsedLastSeen ?: return "Offline"
        val diffMinutes = java.time.Duration.between(dt, java.time.OffsetDateTime.now()).toMinutes()

        return when {
            diffMinutes < 2 -> "Online"
            diffMinutes < 60 -> "Online ${diffMinutes}m ago"
            diffMinutes < 120 -> "Online 1h ago"
            else -> "Offline"
        }
    }

    /** First letter of effective name for avatar initials */
    val avatarInitial: String get() = effectiveName.firstOrNull()?.uppercase() ?: "?"

    /**
     * V3-FIX (item 8a): typed presence derived from [lastSeenAt]. Reuses the lazily
     * parsed timestamp so recomposition-heavy list rows don't re-parse per frame.
     */
    val presence: WhisperPresence get() = WhisperPresence.fromParsed(parsedLastSeen)
}

/**
 * V3-FIX (item 8a): replaces the scattered hardcoded `== "Online"` string compares in
 * UI code with a single typed derivation from the last-seen ISO timestamp.
 */
enum class WhisperPresence {
    ONLINE,
    RECENT,
    OFFLINE,
    UNKNOWN;

    companion object {
        /** A contact counts as ONLINE for this long after their last presence ping. */
        const val DEFAULT_ONLINE_WINDOW_MS: Long = 120_000L

        /** Within one hour of lastSeen still reads as RECENT rather than plain OFFLINE. */
        private const val RECENT_WINDOW_MS: Long = 60L * 60_000L

        fun from(lastSeenAtIso: String?, onlineWindowMs: Long = DEFAULT_ONLINE_WINDOW_MS): WhisperPresence =
            fromParsed(
                lastSeenAtIso?.let { iso -> runCatching { java.time.OffsetDateTime.parse(iso) }.getOrNull() },
                onlineWindowMs,
            )

        internal fun fromParsed(
            lastSeen: java.time.OffsetDateTime?,
            onlineWindowMs: Long = DEFAULT_ONLINE_WINDOW_MS,
        ): WhisperPresence {
            if (lastSeen == null) return UNKNOWN
            val elapsedMs = java.time.Duration.between(lastSeen, java.time.OffsetDateTime.now()).toMillis()
            // Slightly-future stamps (client/server clock skew) degrade gracefully:
            // anything inside the online window is ONLINE, never UNKNOWN/OFFLINE.
            return when {
                elapsedMs <= onlineWindowMs -> ONLINE
                elapsedMs <= RECENT_WINDOW_MS -> RECENT
                else -> OFFLINE
            }
        }
    }
}

/** V3-FIX (item 8a): free-function form required by the spec; see [WhisperPresence.from]. */
fun presenceFrom(lastSeenAtIso: String?, onlineWindowMs: Long = 120_000L): WhisperPresence =
    WhisperPresence.from(lastSeenAtIso, onlineWindowMs)

@Serializable
data class WhisperProfileUpdate(
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
    @SerialName("hide_from_discover") val isHiddenFromDiscover: Boolean? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
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
    @SerialName("hide_from_discover") val isHiddenFromDiscover: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("public_key") val publicKey: String? = null,
)

// ─────────────────────────────────────────────────────────────
// Messages & Reactions
// ─────────────────────────────────────────────────────────────

/**
 * Delivery/read receipt status.
 *
 * NOTE: DELIVERED was removed (M-5). The backend has no `is_delivered` ACK column;
 * message status is only PENDING (local optimistic), SENT (remote confirmed), or READ.
 */
enum class WhisperMessageStatus {
    PENDING,
    SENT,
    READ
}

/**
 * Centralized tombstone constants (L-1).
 * All checks on deleted/encrypted message content MUST use this object.
 */
object WhisperTombstone {
    /** Written to `content` by delete-for-everyone. Legacy variant without sender name. */
    const val CONTENT_LEGACY = "[deleted_by_sender]"

    /** Human-readable display string shown instead of the deleted message. */
    const val DISPLAY_TEXT = "This message has been deleted"

    /** Prefix used when sender name is embedded: "[deleted_by_sender:<name>]". */
    const val CONTENT_PREFIX = "[deleted_by_sender:"

    /** Placeholder shown for rows with null content_iv (legacy/plaintext guard, M-3). */
    const val LEGACY_ENCRYPTED = "[Legacy encrypted message]"

    fun isTombstone(content: String): Boolean =
        content == CONTENT_LEGACY ||
        content == DISPLAY_TEXT ||
        content.startsWith(CONTENT_PREFIX)

    fun extractSenderName(content: String): String? =
        if (content.startsWith(CONTENT_PREFIX) && content.endsWith("]"))
            content.removePrefix(CONTENT_PREFIX).removeSuffix("]").trim()
        else null
}

@Serializable
data class WhisperMessageReactionRow(
    val id: String = "",
    @SerialName("message_id") val messageId: String = "",
    @SerialName("user_id") val userId: String = "",
    val emoji: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class WhisperMessageReactionInsert(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String,
)

@Serializable
data class WhisperReactionSummary(
    val emoji: String = "",
    val count: Int = 0,
    val userIds: List<String> = emptyList(),
    val reactedByMe: Boolean = false,
)

sealed interface WhisperChatEvent {
    data class MessageEvent(val message: WhisperMessage) : WhisperChatEvent
    data class ReactionEvent(val messageId: String, val userId: String, val emoji: String) : WhisperChatEvent
    data class DeleteEvent(val messageId: String) : WhisperChatEvent
}

@Serializable
data class WhisperMessage(
    val id: String = "",
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("receiver_id") val receiverId: String = "",
    val content: String = "",
    @SerialName("content_iv") val contentIv: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    // In-memory / enriched fields
    @kotlinx.serialization.Transient val replyToContent: String? = null,
    @kotlinx.serialization.Transient val replyToSenderName: String? = null,
    @kotlinx.serialization.Transient val reactions: List<WhisperReactionSummary> = emptyList(),
    @kotlinx.serialization.Transient val isPending: Boolean = false,
) {
    fun isSentByMe(myUserId: String) = senderId == myUserId

    /** Delivery & read receipt status */
    fun status(myUserId: String): WhisperMessageStatus = when {
        isPending -> WhisperMessageStatus.PENDING
        isRead -> WhisperMessageStatus.READ
        else -> WhisperMessageStatus.SENT
    }

    /** Whether this message was deleted for everyone */
    val isDeletedForEveryone: Boolean get() = WhisperTombstone.isTombstone(content)

    /** Extracted sender name attached to deletion tombstone */
    val deletedSenderName: String? get() = WhisperTombstone.extractSenderName(content)
}

@Serializable
data class WhisperMessageInsert(
    val id: String? = null,
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val content: String,
    @SerialName("content_iv") val contentIv: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Ciphertext-only outbox entry. Plaintext is deliberately never persisted for retry. */
@Serializable
data class WhisperQueuedMessage(
    val clientId: String,
    val senderId: String,
    val receiverId: String,
    val encryptedContent: String,
    val contentIv: String,
    val replyToId: String? = null,
    val createdAt: String,
    val attempts: Int = 0,
)

/** Encrypted-image metadata. This envelope itself is sent as an encrypted Whisper message. */
@Serializable
data class WhisperImageAttachment(
    val version: Int = 1,
    val url: String,
    val iv: String,
    val mimeType: String,
    val attachmentId: String? = null,
    val expiresAtEpochSeconds: Long? = null,
    val sizeBytes: Int = 0,
) {
    fun toMessageContent(): String = MESSAGE_PREFIX + Json.encodeToString(serializer(), this)

    companion object {
        const val MESSAGE_PREFIX = "whisper:image:"

        // Tolerant parser: a partner on a newer app version may attach extra envelope
        // fields — unknown keys must be skipped, never fail the whole message (H-11).
        private val envelopeJson = Json { ignoreUnknownKeys = true }

        fun fromMessageContent(content: String): WhisperImageAttachment? = runCatching {
            if (!content.startsWith(MESSAGE_PREFIX)) return null
            envelopeJson.decodeFromString(serializer(), content.removePrefix(MESSAGE_PREFIX))
        }.getOrNull()
    }
}

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
    val virtualEmail: String,   // SHA-256(token) + "@whisper.toolz.app"
)

// ─────────────────────────────────────────────────────────────
// UI State sealed classes
// ─────────────────────────────────────────────────────────────

sealed class UiText {
    data class DynamicString(val value: String) : UiText()
    class StringResource(val resId: Int, vararg val args: Any) : UiText()

    fun asString(context: android.content.Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
    }
}

@androidx.compose.runtime.Composable
fun UiText.asString(): String {
    return when (this) {
        is UiText.DynamicString -> value
        is UiText.StringResource -> androidx.compose.ui.res.stringResource(resId, *args)
    }
}

sealed class WhisperAuthState {
    object Idle : WhisperAuthState()
    object Loading : WhisperAuthState()
    data class Error(val message: UiText) : WhisperAuthState()
    data class Notice(val message: UiText) : WhisperAuthState()
    /** Account exists but is deliberately not allowed into Whisper until Supabase confirms ownership. */
    data class EmailVerificationRequired(val email: String) : WhisperAuthState()
    object Authenticated : WhisperAuthState()
}

data class WhisperUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentProfile: WhisperProfile? = null,
    val conversations: List<WhisperConversation> = emptyList(),
    val friends: List<WhisperProfile> = emptyList(),
    // L-2 FIX (reviewwhisper.md): the duplicate `pendingIncoming` list was removed —
    // pendingIncomingRequests is the single source of truth (nav badges derive from it).
    val pendingIncomingRequests: List<WhisperFriendRequestItem> = emptyList(),
    val pendingOutgoing: List<WhisperFriendship> = emptyList(),
    val searchResults: List<WhisperProfile> = emptyList(),
    val recommendedProfiles: List<WhisperProfile> = emptyList(),
    val discoverProfiles: List<WhisperProfile> = emptyList(),
    val discoverPage: Int = 0,
    val isDiscoverLoadingNext: Boolean = false,
    val hasReachedEndOfDiscover: Boolean = false,
    val mutedUserIds: Set<String> = emptySet(),
    val error: UiText? = null,
    // V2-FIX L-?: dedicated success/info channel so non-error notices (e.g. key-rotation
    // success) never masquerade as errors. Displayed/cleared via WhisperViewModel.clearInfo().
    val infoMessage: UiText? = null,
) {
    val totalUnreadCount: Int get() = conversations.sumOf { it.unreadCount }
}

// ─────────────────────────────────────────────────────────────
// Key Trust & Verification
// ─────────────────────────────────────────────────────────────

enum class KeyTrustStatus { NO_KEY, MATCH, CHANGED, ROTATED_AUTO, ROTATED_MANUAL }

/**
 * Snapshot of how much we trust the current encryption key of a conversation
 * partner, together with the fingerprints needed to verify it in person.
 * 7-day polished: ROTATED_AUTO (weekly, not scary), ROTATED_MANUAL (user tapped rotate),
 * CHANGED remains warning for truly unexpected/malicious.
 */
data class KeyTrustInfo(
    val status: KeyTrustStatus = KeyTrustStatus.NO_KEY,
    /** Fingerprint of the partner's current public key. */
    val partnerFingerprint: String? = null,
    /** Fingerprint of my own public key, so the partner can compare theirs. */
    val myFingerprint: String? = null,
    /** True only if this exact key was verified by the user (compared in person). */
    val isVerified: Boolean = false,
    /** Human-readable rotate reason for polished banner (P0-1: resource-backed, localizable). */
    val rotateMessage: UiText? = null,
    /** True if this change looks like expected scheduled rotation (not MITM). */
    val isExpectedRotation: Boolean = false,
)

data class WhisperChatUiState(
    val isLoading: Boolean = false,
    val isFriendStatusLoaded: Boolean = false,
    val otherUser: WhisperProfile? = null,
    val messages: List<WhisperMessage> = emptyList(),
    val friendStatus: FriendStatus = FriendStatus.NONE,
    val iAmRequester: Boolean = false,
    val isPartnerTyping: Boolean = false,
    val isPartnerOnline: Boolean = false,
    val partnerLastSeen: String? = null,
    val isMuted: Boolean = false,
    val isBlockedByMe: Boolean = false,
    val isBlockedByOther: Boolean = false,
    val replyingToMessage: WhisperMessage? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val matchingMessageIds: Set<String> = emptySet(),
    val activeSearchMatchIndex: Int = -1,
    val unreadMessagesScrolledUp: Int = 0,
    val keyTrust: KeyTrustInfo? = null,
    val isUploadingAttachment: Boolean = false,
    val decryptedImageBytes: Map<String, ByteArray> = emptyMap(),
    val isRealtimeDisconnected: Boolean = false,
    val error: UiText? = null,
)

/** 30s clear-chat undo banner state, kept separate so 1 Hz countdown ticks don't recompose the message list. */
data class WhisperUndoUiState(
    val clearedCount: Int = 0,
    val secondsRemaining: Int = 0,
)
