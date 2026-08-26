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
 * P3: device-only delete-for-me tombstone row (replaces the 5k-entry
 * SharedPreferences string-set that was rewritten wholesale on every change).
 *
 * The REMOTE `whisper_deleted_tombstones` table remains the durable source of
 * truth across reinstalls; this table is the local mirror whose cap/eviction
 * semantics match the old [WhisperDeletedMessagesStore] (keep the NEWEST
 * [WhisperDeletedMessagesStore.MAX_TOMBSTONES] entries by delete timestamp).
 */
@Entity(
    tableName = "whisper_local_tombstones",
    indices = [
        Index(value = ["deletedAtMs"]),
    ],
)
data class WhisperLocalTombstoneEntity(
    @PrimaryKey val messageId: String,
    val deletedAtMs: Long,
)

@Dao
interface WhisperLocalTombstoneDao {

    @Query("SELECT * FROM whisper_local_tombstones ORDER BY deletedAtMs ASC")
    suspend fun entriesOldestFirst(): List<WhisperLocalTombstoneEntity>

    @Query("SELECT * FROM whisper_local_tombstones")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<WhisperLocalTombstoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<WhisperLocalTombstoneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WhisperLocalTombstoneEntity)

    @Query("DELETE FROM whisper_local_tombstones WHERE messageId IN (:messageIds)")
    suspend fun deleteAllByIds(messageIds: List<String>)

    @Query("DELETE FROM whisper_local_tombstones")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM whisper_local_tombstones")
    suspend fun count(): Int
}
