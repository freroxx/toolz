/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.purgeshot

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PurgeShotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PurgeShotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<PurgeShotEntity>): List<Long>

    @Update
    suspend fun update(entity: PurgeShotEntity)

    @Query("SELECT * FROM purge_shot_queue ORDER BY scheduledDeleteAtMs ASC")
    fun observeAll(): Flow<List<PurgeShotEntity>>

    @Query("SELECT * FROM purge_shot_queue WHERE status = 'PENDING' ORDER BY scheduledDeleteAtMs ASC")
    fun observePending(): Flow<List<PurgeShotEntity>>

    @Query("SELECT * FROM purge_shot_queue WHERE status = 'PENDING' ORDER BY scheduledDeleteAtMs ASC")
    suspend fun getPendingSync(): List<PurgeShotEntity>

    @Query("SELECT * FROM purge_shot_queue ORDER BY createdAtMs DESC")
    suspend fun getAllSync(): List<PurgeShotEntity>

    @Query("SELECT * FROM purge_shot_queue WHERE id = :id")
    suspend fun getById(id: Long): PurgeShotEntity?

    @Query("SELECT * FROM purge_shot_queue WHERE fileUriString = :uri")
    suspend fun getByUri(uri: String): PurgeShotEntity?

    @Query("DELETE FROM purge_shot_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE purge_shot_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE purge_shot_queue SET status = 'CANCELLED' WHERE id = :id")
    suspend fun cancelById(id: Long)

    @Query("DELETE FROM purge_shot_queue WHERE status IN ('DELETED','CANCELLED','EXPIRED') AND scheduledDeleteAtMs < :cutoffMs")
    suspend fun purgeTerminalOlderThan(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM purge_shot_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM purge_shot_queue WHERE status = 'PENDING'")
    suspend fun pendingCountSync(): Int

    @Query("DELETE FROM purge_shot_queue WHERE status = 'PENDING'")
    suspend fun clearPending()

    @Query("SELECT * FROM purge_shot_queue WHERE scheduledDeleteAtMs <= :nowMs AND status = 'PENDING'")
    suspend fun getDue(nowMs: Long = System.currentTimeMillis()): List<PurgeShotEntity>

    @Query("UPDATE purge_shot_queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun incrementAttempts(id: Long, error: String?)

    @Query("SELECT * FROM purge_shot_queue WHERE fileUriString = :uri AND status = 'PENDING' LIMIT 1")
    suspend fun findPendingByUri(uri: String): PurgeShotEntity?

    @Query("SELECT * FROM purge_shot_queue WHERE filePath = :path AND status = 'PENDING' LIMIT 1")
    suspend fun findPendingByPath(path: String): PurgeShotEntity?

    @Query("SELECT * FROM purge_shot_queue WHERE filePath = :path LIMIT 1")
    suspend fun findAnyByPath(path: String): PurgeShotEntity?
}
