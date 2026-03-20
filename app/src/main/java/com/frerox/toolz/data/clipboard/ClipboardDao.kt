package com.frerox.toolz.data.clipboard

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Query("SELECT * FROM clipboard_entries ORDER BY isPinned DESC, timestamp DESC")
    fun getAllEntries(): Flow<List<ClipboardEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntry): Long

    @Update
    suspend fun update(entry: ClipboardEntry)

    @Delete
    suspend fun delete(entry: ClipboardEntry)

    @Query("UPDATE clipboard_entries SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePin(id: Int)

    @Query("DELETE FROM clipboard_entries WHERE timestamp < :timestamp AND isPinned = 0")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM clipboard_entries")
    suspend fun getEntryCount(): Int

    @Query("DELETE FROM clipboard_entries WHERE id IN (SELECT id FROM clipboard_entries WHERE isPinned = 0 ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestUnpinned(count: Int)

    @Query("DELETE FROM clipboard_entries WHERE isPinned = 0")
    suspend fun clearAllUnpinned()

    @Query("SELECT * FROM clipboard_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntry(): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE id = :id")
    suspend fun getEntryById(id: Int): ClipboardEntry?

    @Query("UPDATE clipboard_entries SET summary = :summary, type = :type, isAiProcessed = 1 WHERE id = :id")
    suspend fun updateAiDetails(id: Int, summary: String?, type: String)

    @Query("SELECT * FROM clipboard_entries WHERE isAiProcessed = 0 ORDER BY timestamp DESC")
    suspend fun getUnprocessedEntries(): List<ClipboardEntry>
}
