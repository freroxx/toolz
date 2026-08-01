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
    @Query("SELECT * FROM clipboard_entries")
    suspend fun getAllEntriesSync(): List<ClipboardEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<ClipboardEntry>)
}
