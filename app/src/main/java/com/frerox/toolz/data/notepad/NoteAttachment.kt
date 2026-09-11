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

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Multi-attachment model (remake V2). Replaces the single-slot
 * attachedPdfUri/attachedImageUri/attachedAudioUri columns on [Note].
 *
 * kind: PDF | IMAGE | AUDIO
 * pageHint: for PDFs — page to open at (0-based).
 */
@Entity(
    tableName = "note_attachments",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId"), Index("kind")]
)
data class NoteAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Int,
    val kind: String,
    val uri: String,
    val displayName: String? = null,
    val sizeBytes: Long = 0L,
    val pageHint: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_PDF = "PDF"
        const val KIND_IMAGE = "IMAGE"
        const val KIND_AUDIO = "AUDIO"
    }
}
