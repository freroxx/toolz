/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WhisperMessageDao {

    /**
     * Latest [MAX_FLOW_MESSAGES] rows for the conversation, ascending.
     * The subselect bounds memory/CPU for power users with huge threads while
     * preserving chronological order for the UI; the paged DAO path was never
     * wired up, so this is the practical guard against unbounded loads.
     *
     * V2-FIX (reviewwhisper.md) H-10: ORDER BY now keys on the numeric [sortEpoch]
     * (derived from createdAt) with created_at as a stable tiebreaker, both
     * directions consistent — ISO string comparison alone misorders rows whose
     * timestamps carry differing offsets/precision.
     */
    @Query("""
        SELECT * FROM (
            SELECT * FROM whisper_messages 
            WHERE (senderId = :myId AND receiverId = :otherId) 
               OR (senderId = :otherId AND receiverId = :myId)
            ORDER BY sortEpoch DESC, createdAt DESC LIMIT 500
        ) ORDER BY sortEpoch ASC, createdAt ASC
    """)
    fun getMessages(myId: String, otherId: String): Flow<List<WhisperMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<WhisperMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: WhisperMessageEntity)

    @Query("DELETE FROM whisper_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("SELECT * FROM whisper_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): WhisperMessageEntity?

    @Query("UPDATE whisper_messages SET isRead = 1 WHERE senderId = :senderId AND receiverId = :myId")
    suspend fun markAsRead(senderId: String, myId: String)

    @Query("DELETE FROM whisper_messages")
    suspend fun clearAll()
}
