/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.time.Instant
import java.time.OffsetDateTime

@Entity(
    tableName = "whisper_messages",
    indices = [
        Index(value = ["senderId"]),
        Index(value = ["receiverId"]),
        Index(value = ["createdAt"]),
        // P1 FIX: Hot query is (senderId,receiverId,createdAt) — composite covers it.
        Index(value = ["senderId", "receiverId", "createdAt"])
    ]
)
data class WhisperMessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String,
    val contentIv: String?,
    val replyToId: String?,
    val isRead: Boolean,
    val createdAt: String,
    val replyToContent: String? = null,
    val replyToSenderName: String? = null,
    val isDeletedForEveryone: Boolean = false,
    // V2-FIX (reviewwhisper.md) H-10: numeric sort key derived from createdAt so Room can
    // order chronologically even when ISO strings compare inconsistently. defaultValue
    // matches MIGRATION_47_48's "ADD COLUMN ... DEFAULT 0" (Room validates both sides).
    @ColumnInfo(defaultValue = "0") val sortEpoch: Long = 0,

    // PHASE 1 (roadmap §1.2): wire protocol version (0 = legacy pair, 2 = envelope).
    @ColumnInfo(defaultValue = "0")
    val protocolVersion: Int = 0,

    // V6-R7 (#cache): last-known reaction summaries (JSON) so reactions render
    // INSTANTLY on chat re-entry instead of waiting for the REST enrichment pass.
    @ColumnInfo(defaultValue = "")
    val reactionsJson: String = "",
) {
    fun toModel(): WhisperMessage = WhisperMessage(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        content = content,
        contentIv = contentIv,
        replyToId = replyToId,
        isRead = isRead,
        createdAt = createdAt,
        replyToContent = replyToContent,
        replyToSenderName = replyToSenderName,
        // Cached pending rows render as a neutral placeholder instead of a real bubble.
        isPending = content == "[message pending sync]",
        protocolVersion = protocolVersion,
        reactions = decodeReactions(reactionsJson),
    )

    companion object {
        /**
         * V2-FIX (reviewwhisper.md) H-10: parses an ISO-8601 timestamp ("...Z" or with a
         * UTC offset) into epoch millis for [sortEpoch]; falls back to 0L on blank input
         * or unparseable text instead of crashing the insert path.
         */
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun encodeReactions(reactions: List<WhisperReactionSummary>): String =
            if (reactions.isEmpty()) "" else runCatching {
                val ser = kotlinx.serialization.builtins.ListSerializer(WhisperReactionSummary.serializer())
                json.encodeToString(ser, reactions)
            }.getOrDefault("")

        fun decodeReactions(jsonStr: String): List<WhisperReactionSummary> =
            if (jsonStr.isBlank()) emptyList() else runCatching {
                val ser = kotlinx.serialization.builtins.ListSerializer(WhisperReactionSummary.serializer())
                json.decodeFromString(ser, jsonStr)
            }.getOrDefault(emptyList())

        fun parseSortEpoch(iso: String?): Long {
            if (iso.isNullOrBlank()) return 0L
            return runCatching { Instant.parse(iso).toEpochMilli() }
                .recoverCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
                .getOrDefault(0L)
        }
    }
}

fun WhisperMessage.toEntity(): WhisperMessageEntity = WhisperMessageEntity(
    id = id,
    senderId = senderId,
    receiverId = receiverId,
    // Room is a ciphertext-only transport cache, never a plaintext message archive.
    // Encrypted payloads keep their ciphertext (decrypted at read time by the repository),
    // pending/unsent messages store a neutral marker, and tombstones stay intact.
    // Any legacy non-encrypted row is scrubbed to WhisperTombstone.LEGACY_ENCRYPTED.
    content = when {
        contentIv != null -> content
        isPending -> "[message pending sync]"
        WhisperTombstone.isTombstone(content) -> content
        else -> WhisperTombstone.LEGACY_ENCRYPTED
    },
    contentIv = contentIv,
    replyToId = replyToId,
    isRead = isRead,
    createdAt = createdAt,
    replyToContent = null,
    replyToSenderName = replyToSenderName,
    isDeletedForEveryone = isDeletedForEveryone,
    // V2-FIX (reviewwhisper.md) H-10: every entity construction site goes through this
    // mapper (server rows, pending ghosts, delivered outbox rows), so deriving the
    // monotonic sort key here covers all of them.
    sortEpoch = WhisperMessageEntity.parseSortEpoch(createdAt),
    // PHASE 1 (roadmap §1.2): wire protocol version of the stored ciphertext,
    // inferred from its shape — 2 = v5 multi-key envelope, 0 = legacy v1 pair.
    // Single inference point covers every construction site, same as sortEpoch.
    protocolVersion = if (WhisperEnvelope.isEnvelope(content)) 2 else 0,
    reactionsJson = WhisperMessageEntity.encodeReactions(reactions),
)
