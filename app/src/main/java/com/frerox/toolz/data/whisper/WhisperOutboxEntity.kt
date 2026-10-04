/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * P3: ciphertext-only outbox row (replaces the JSON SharedPreferences blob).
 *
 * Invariants carried over from [WhisperQueuedMessage]:
 *  - ONLY ciphertext is persisted — never plaintext for retry;
 *  - [clientId] is the client-generated message UUID, so a server insert that lost
 *    its response is recognized as delivered via the duplicate-key path;
 *  - ordering is insertion order ([enqueuedAtMs], then clientId as tiebreak), which
 *    preserves the old list-append semantics the flush loop relies on.
 */
@Entity(
    tableName = "whisper_outbox",
    indices = [
        Index(value = ["enqueuedAtMs"]),
        Index(value = ["receiverId"]),
        Index(value = ["groupId"]),
    ],
)
data class WhisperOutboxEntity(
    @PrimaryKey val clientId: String,
    val senderId: String,
    val receiverId: String,
    val encryptedContent: String,
    val contentIv: String,
    val replyToId: String?,
    val createdAt: String,
    /** Wall-clock insert time — drives stable FIFO ordering and oldest-first eviction. */
    val enqueuedAtMs: Long,
    val attempts: Int = 0,
    /**
     * Phase-2 groups: non-null when this row is one fan-out leg of a group
     * send (one row per member; receiverId is that leg's member). Null for
     * 1:1 rows. Nullable + DEFAULT NULL in MIGRATION_61_62 so every existing
     * 1:1 row migrates untouched (Room validates the default on both sides —
     * no @ColumnInfo needed for a null default).
     */
    val groupId: String? = null,
) {
    fun toQueued(): WhisperQueuedMessage = WhisperQueuedMessage(
        clientId = clientId,
        senderId = senderId,
        receiverId = receiverId,
        encryptedContent = encryptedContent,
        contentIv = contentIv,
        replyToId = replyToId,
        createdAt = createdAt,
        attempts = attempts,
        groupId = groupId,
    )

    companion object {
        fun fromQueued(q: WhisperQueuedMessage, enqueuedAtMs: Long): WhisperOutboxEntity =
            WhisperOutboxEntity(
                clientId = q.clientId,
                senderId = q.senderId,
                receiverId = q.receiverId,
                encryptedContent = q.encryptedContent,
                contentIv = q.contentIv,
                replyToId = q.replyToId,
                createdAt = q.createdAt,
                enqueuedAtMs = enqueuedAtMs,
                attempts = q.attempts,
                groupId = q.groupId,
            )
    }
}

@Dao
interface WhisperOutboxDao {

    /** FIFO order matching the previous in-memory list semantics. */
    @Query("SELECT * FROM whisper_outbox ORDER BY enqueuedAtMs ASC, clientId ASC")
    suspend fun entries(): List<WhisperOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WhisperOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<WhisperOutboxEntity>)

    @Query("DELETE FROM whisper_outbox WHERE clientId = :clientId")
    suspend fun delete(clientId: String)

    @Query("DELETE FROM whisper_outbox")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM whisper_outbox")
    suspend fun count(): Int
}
