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
        Index(value = ["createdAt"])
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
        replyToSenderName = replyToSenderName
    )
}

fun WhisperMessage.toEntity(): WhisperMessageEntity = WhisperMessageEntity(
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
    isDeletedForEveryone = isDeletedForEveryone
)
