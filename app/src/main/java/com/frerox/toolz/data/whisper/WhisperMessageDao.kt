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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<WhisperMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: WhisperMessageEntity)

    @Query("DELETE FROM whisper_messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM whisper_messages WHERE (senderId = :myId AND receiverId = :otherId) OR (senderId = :otherId AND receiverId = :myId)")
    suspend fun clearChat(myId: String, otherId: String)

    @Query("SELECT * FROM whisper_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): WhisperMessageEntity?
    
    @Update
    suspend fun updateMessage(message: WhisperMessageEntity)

    @Query("UPDATE whisper_messages SET isRead = 1 WHERE senderId = :senderId AND receiverId = :myId")
    suspend fun markAsRead(senderId: String, myId: String)

    @Query("DELETE FROM whisper_messages")
    suspend fun clearAll()
}
