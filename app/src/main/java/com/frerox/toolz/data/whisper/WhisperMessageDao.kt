/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WhisperMessageDao {

    @Query("""
        SELECT * FROM whisper_messages 
        WHERE (senderId = :myId AND receiverId = :otherId) 
           OR (senderId = :otherId AND receiverId = :myId)
        ORDER BY createdAt ASC
    """)
    fun getMessages(myId: String, otherId: String): Flow<List<WhisperMessageEntity>>

    // P1-6 FIX: Paginated DAO for power users (10k+). Avoid loading entire thread.
    @Query("""
        SELECT * FROM whisper_messages 
        WHERE (senderId = :myId AND receiverId = :otherId) 
           OR (senderId = :otherId AND receiverId = :myId)
        ORDER BY createdAt DESC LIMIT :limit OFFSET :offset
    """)
    suspend fun getMessagesPaged(myId: String, otherId: String, limit: Int, offset: Int): List<WhisperMessageEntity>

    @Query("""
        SELECT COUNT(*) FROM whisper_messages 
        WHERE (senderId = :myId AND receiverId = :otherId) 
           OR (senderId = :otherId AND receiverId = :myId)
    """)
    suspend fun countMessages(myId: String, otherId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<WhisperMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: WhisperMessageEntity)

    @Query("DELETE FROM whisper_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("SELECT * FROM whisper_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): WhisperMessageEntity?
    
    @Update
    suspend fun updateMessage(message: WhisperMessageEntity)

    @Query("UPDATE whisper_messages SET isRead = 1 WHERE senderId = :senderId AND receiverId = :myId")
    suspend fun markAsRead(senderId: String, myId: String)

    @Query("DELETE FROM whisper_messages")
    suspend fun clearAll()
}
