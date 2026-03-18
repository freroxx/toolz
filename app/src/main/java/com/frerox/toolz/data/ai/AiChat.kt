package com.frerox.toolz.data.ai

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ai_chats")
data class AiChat(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    /**
     * Alias used by [com.frerox.toolz.ui.screens.ai.chatGroup] so the UI
     * layer can reference a semantic name without a DB migration.
     */
    val createdAt: Long get() = timestamp
}

@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity         = AiChat::class,
            parentColumns  = ["id"],
            childColumns   = ["chatId"],
            onDelete       = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("chatId")]
)
data class AiMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val chatId: Int,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface AiDao {

    @Query("SELECT * FROM ai_chats ORDER BY timestamp DESC")
    fun getAllChats(): Flow<List<AiChat>>

    @Insert
    suspend fun insertChat(chat: AiChat): Long

    @Query("SELECT * FROM ai_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: Int): Flow<List<AiMessage>>

    @Insert
    suspend fun insertMessage(message: AiMessage)

    /** Room cascades to [ai_messages] via the [ForeignKey.CASCADE] constraint. */
    @Delete
    suspend fun deleteChat(chat: AiChat)

    @Query("DELETE FROM ai_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Int)

    @Update
    suspend fun updateChat(chat: AiChat)
}