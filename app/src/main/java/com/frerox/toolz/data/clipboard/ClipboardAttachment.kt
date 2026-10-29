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

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Attachment metadata for a clip. The bytes live in [ClipboardBlobStore],
 * content-addressed by [blobId] (SHA-256 of the plaintext), so the same image
 * pasted twice is stored once and survives clip-level trash/undo.
 */
@Entity(
    tableName = "clipboard_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ClipboardEntry::class,
            parentColumns = ["id"],
            childColumns = ["clipId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("clipId"),
        Index("blobId"),
    ],
)
data class ClipboardAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clipId: Int,
    val blobId: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val position: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
)
