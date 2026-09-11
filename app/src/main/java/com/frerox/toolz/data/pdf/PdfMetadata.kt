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

package com.frerox.toolz.data.pdf

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_metadata")
data class PdfMetadata(
    @PrimaryKey val uri: String,
    val isPinned: Boolean = false,
    val lastAccessed: Long = System.currentTimeMillis(),
    val ocrContent: String? = null, // Plain text for search
    val structuredOcrData: String? = null, // JSON of blocks with coordinates
    // ── Remake V2: reader state + doc identity (defaults keep old rows valid) ──
    val lastPage: Int = 0,
    val lastZoom: Float = 1f,
    val readingMode: String = "CONTINUOUS", // CONTINUOUS | PAGED
    val paperMode: String = "PAPER", // PAPER | SEPIA | NIGHT
    val title: String? = null, // PDDocumentInformation.title
    val author: String? = null,
    val pageCount: Int = 0
)
