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
 * kind: PDF | IMAGE | AUDIO | FILE. Use the KIND_* constants — raw strings
 * are rejected by [NoteAttachmentKind].
 * pageHint: for PDFs — page to open at (0-based).
 * mimeType/durationMs: optional metadata for honest UI (size, audio length).
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
    indices = [
        Index("noteId"),
        Index("kind"),
        Index(value = ["noteId", "kind"]),
        Index("uri"),
        // Not unique: legacy backfill + dual-write history may contain
        // duplicates; dedup is enforced in code (see NoteRepository).
        Index(value = ["noteId", "uri"]),
    ]
)
data class NoteAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Int,
    val kind: String,
    val uri: String,
    val displayName: String? = null,
    val sizeBytes: Long = 0L,
    val pageHint: Int = 0,
    val mimeType: String? = null,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val KIND_PDF = "PDF"
        const val KIND_IMAGE = "IMAGE"
        const val KIND_AUDIO = "AUDIO"
        /** Generic file (future-proof; UI treats like PDF row without preview). */
        const val KIND_FILE = "FILE"
        val ALL_KINDS = setOf(KIND_PDF, KIND_IMAGE, KIND_AUDIO, KIND_FILE)
    }
}

/** Validated attachment kind — rejects typo strings at the gate. */
object NoteAttachmentKind {
    fun requireValid(kind: String): String {
        require(kind in NoteAttachment.ALL_KINDS) { "Unknown attachment kind: $kind" }
        return kind
    }

    fun isValid(kind: String): Boolean = kind in NoteAttachment.ALL_KINDS
}
