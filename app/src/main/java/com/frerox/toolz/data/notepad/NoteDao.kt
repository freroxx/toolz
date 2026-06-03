package com.frerox.toolz.data.notepad

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedNotes(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

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

    @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :noteId")
    suspend fun updatePinned(noteId: Int, isPinned: Boolean)

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getAllNotesSync(): List<Note>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<Note>)
}
