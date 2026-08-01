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

enum class DeepDiveState { NONE, PENDING, IN_PROGRESS, COMPLETED, FADED }

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
    val searchSources: String? = null,
    val canDeepDive: Boolean = false,
    val deepDiveState: DeepDiveState = DeepDiveState.NONE,
)

@Dao
interface AiDao {

    @Query("SELECT * FROM ai_chats ORDER BY timestamp DESC")
    fun getAllChats(): Flow<List<AiChat>>

    @Query("SELECT * FROM ai_chats ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentChats(limit: Int): Flow<List<AiChat>>

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

    @Update
    suspend fun updateMessage(message: AiMessage)

    @Query("SELECT * FROM ai_chats")
    suspend fun getAllChatsSync(): List<AiChat>

    @Query("SELECT * FROM ai_messages")
    suspend fun getAllMessagesSync(): List<AiMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<AiChat>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<AiMessage>)
}
