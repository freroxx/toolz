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

package com.frerox.toolz.data.notepad

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedNotes(): Flow<List<Note>>

    /** DB-side search — replaces in-memory contains() filtering in the UI. */
    @Query(
        "SELECT * FROM notes WHERE isDeleted = 0 AND " +
            "(title LIKE '%' || :query || '%' ESCAPE '\\' OR content LIKE '%' || :query || '%' ESCAPE '\\') " +
            "ORDER BY isPinned DESC, timestamp DESC LIMIT :limit"
    )
    fun searchNotes(query: String, limit: Int = 200): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0")
    fun countActiveNotes(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 1")
    fun countTrashedNotes(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun permanentlyDeleteNote(note: Note)

    @Delete
    suspend fun permanentlyDeleteNotes(notes: List<Note>)

    @Query("UPDATE notes SET isDeleted = 1, deletedTimestamp = :timestamp WHERE id = :noteId")
    suspend fun moveToTrash(noteId: Int, timestamp: Long)

    @Query("UPDATE notes SET isDeleted = 1, deletedTimestamp = :timestamp WHERE id IN (:noteIds)")
    suspend fun moveMultipleToTrash(noteIds: List<Int>, timestamp: Long)

    @Query("UPDATE notes SET isDeleted = 0, deletedTimestamp = 0 WHERE id = :noteId")
    suspend fun restoreFromTrash(noteId: Int)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyTrash()

    /** Trash auto-expiry: permanently drops rows trashed before [before]. */
    @Query("DELETE FROM notes WHERE isDeleted = 1 AND deletedTimestamp < :before AND deletedTimestamp != 0")
    suspend fun deleteExpiredTrash(before: Long): Int

    @Query("UPDATE notes SET isPinned = :isPinned, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun updatePinned(noteId: Int, isPinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllNotesSync(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: Int): Note?

    @Query("UPDATE notes SET attachedPdfUri = :uri WHERE id = :noteId")
    suspend fun updateAttachedPdfUri(noteId: Int, uri: String?)

    @Query("UPDATE notes SET attachedImageUri = :uri WHERE id = :noteId")
    suspend fun updateAttachedImageUri(noteId: Int, uri: String?)

    @Query("UPDATE notes SET attachedAudioUri = :uri, attachedAudioName = :name WHERE id = :noteId")
    suspend fun updateAttachedAudio(noteId: Int, uri: String?, name: String?)

    @Query("UPDATE notes SET summary = :summary WHERE id = :noteId")
    suspend fun updateSummary(noteId: Int, summary: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<Note>)
}
