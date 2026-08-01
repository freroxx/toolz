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
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val color: Int, // ARGB format
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val fontStyle: String = "SANS_SERIF", // "SANS_SERIF", "SERIF", "MONOSPACE", "ROBOTO", "CASUAL", "CURSIVE"
    val fontSize: Float = 16f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val attachedPdfUri: String? = null,
    val attachedAudioUri: String? = null,
    val attachedAudioName: String? = null,
    val attachedImageUri: String? = null,
    val cardSize: String = "AUTO",
    val summary: String? = null,
    val isDeleted: Boolean = false,
    val deletedTimestamp: Long = 0L
)
