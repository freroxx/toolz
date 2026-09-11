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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteAttachmentDao {
    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun observeForNote(noteId: Int): Flow<List<NoteAttachment>>

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun listForNote(noteId: Int): List<NoteAttachment>

    @Query("SELECT * FROM note_attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteAttachment?

    @Query("SELECT * FROM note_attachments WHERE uri = :uri LIMIT 20")
    suspend fun findNotesWithUri(uri: String): List<NoteAttachment>

    @Query("SELECT COUNT(*) FROM note_attachments WHERE noteId = :noteId AND kind = :kind")
    suspend fun countByKind(noteId: Int, kind: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: NoteAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NoteAttachment>)

    @Delete
    suspend fun delete(attachment: NoteAttachment)

    @Query("DELETE FROM note_attachments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM note_attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Int)

    @Query("UPDATE note_attachments SET pageHint = :page WHERE id = :id")
    suspend fun updatePageHint(id: Long, page: Int)
}
