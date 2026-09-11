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

/**
 * Outcome of attaching a PDF to a note. Shared by the Notes tool and the
 * PDF reader tool so both sides report the same honest result instead of
 * swallowing failures into a generic Boolean.
 */
sealed interface PdfAttachResult {
    /** Row written; the attachment is readable and will open. [uri] is the stored URI. */
    data class Attached(val uri: String = "") : PdfAttachResult

    /** Note already holds 10 PDFs. */
    data object Capped : PdfAttachResult

    /** The source bytes can't be opened (lost grant, deleted file, …). */
    data object Unreadable : PdfAttachResult

    /** Anything else (DB error, …). [reason] is logged, never shown raw. */
    data class Failed(val reason: String) : PdfAttachResult
}
