/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

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
    val isDeletedForEveryone: Boolean = false
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
        isPending = content == "[message pending sync]"
    )
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
    isDeletedForEveryone = isDeletedForEveryone
)
