package com.frerox.toolz.data.whisper

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Device-only lifecycle state for Daily Ups. [sealedDescriptor] is the encrypted
 * descriptor received over a 1:1 session; no decrypted URL, image key, or photo bytes
 * are retained here. SQLCipher protects this cache at rest as an additional layer.
 */
@Entity(
    tableName = "whisper_story_local",
    indices = [Index(value = ["expiresAtMs"]), Index(value = ["ownerId", "expiresAtMs"])],
)
data class WhisperStoryLocalEntity(
    @PrimaryKey val storyId: String,
    val ownerId: String,
    val sealedDescriptor: String,
    val expiresAtMs: Long,
    val receiptsEnabled: Boolean,
    val state: String,
    val updatedAtMs: Long,
)

@Dao
interface WhisperStoryLocalDao {
    @Query("SELECT * FROM whisper_story_local WHERE expiresAtMs > :nowMs AND state = 'active' ORDER BY expiresAtMs ASC LIMIT :limit")
    fun observeActive(nowMs: Long, limit: Int = 100): Flow<List<WhisperStoryLocalEntity>>

    /** My own published rows (state='own'); feed the My Ups strip for a day. */
    @Query("SELECT * FROM whisper_story_local WHERE expiresAtMs > :nowMs AND state = 'own' ORDER BY expiresAtMs ASC LIMIT :limit")
    fun observeOwn(nowMs: Long, limit: Int = 10): Flow<List<WhisperStoryLocalEntity>>

    /** Publish-day guard: skip the server round-trip when a local row already exists. */
    @Query("SELECT storyId FROM whisper_story_local WHERE storyId IN (:storyIds)")
    suspend fun existingIds(storyIds: List<String>): List<String>

    @Query("SELECT * FROM whisper_story_local WHERE storyId = :storyId")
    suspend fun byId(storyId: String): WhisperStoryLocalEntity?

    /** Sync reconcile inputs (bounded reads; prune decisions run client-side). */
    @Query("SELECT * FROM whisper_story_local WHERE state = 'active' AND expiresAtMs > :nowMs")
    suspend fun listActive(nowMs: Long): List<WhisperStoryLocalEntity>

    @Query("SELECT * FROM whisper_story_local WHERE state = 'own' AND expiresAtMs > :nowMs")
    suspend fun listOwn(nowMs: Long): List<WhisperStoryLocalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(story: WhisperStoryLocalEntity)

    @Query("DELETE FROM whisper_story_local WHERE storyId = :storyId")
    suspend fun delete(storyId: String)

    /** Bounded transaction-safe sweep; only rows actually due are eligible. */
    @Query("DELETE FROM whisper_story_local WHERE storyId IN (SELECT storyId FROM whisper_story_local WHERE expiresAtMs <= :nowMs OR state != 'active' ORDER BY expiresAtMs ASC LIMIT :limit)")
    suspend fun deleteDue(nowMs: Long, limit: Int): Int

    @Query("DELETE FROM whisper_story_local")
    suspend fun clearAll()
}
