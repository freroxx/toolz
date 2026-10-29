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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One clipboard capture.
 *
 * Backwards compatibility rules:
 *  - Every new column has a default and the 66 → 67 migration is additive.
 *  - [contentHash] (Int) is the legacy dedup key. New captures use
 *    [contentHashSha256] and still write the legacy hash so old rows dedupe.
 *  - [sourceApp] is the real package name for new captures. Legacy rows used
 *    this column for the capture source; [source] is now the capture source.
 *  - [deletedAt] turns deletes into a restorable trash action.
 *  - Sensitive/locked/pinned rows are exempt from every automatic cleanup.
 */
@Entity(
    tableName = "clipboard_entries",
    indices = [
        Index("contentHashSha256"),
        Index("timestamp"),
        Index("deletedAt"),
        Index("isPinned"),
        Index("isSensitive"),
    ],
)
data class ClipboardEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = ClipboardType.TEXT,
    val isPinned: Boolean = false,
    val previewUrl: String? = null,
    val sourceApp: String? = null,
    val summary: String? = null,
    val isAiProcessed: Boolean = false,
    /** Legacy Java-style hash kept for old dedup rows. */
    val contentHash: Int = 0,
    /** Capture path: SHIZUKU, FOREGROUND, MANUAL, SHARE, PROCESS_TEXT, POPUP, TILE. */
    val source: String? = null,
    /** SHA-256 over text + HTML + attachment hashes. */
    val contentHashSha256: String? = null,
    val htmlContent: String? = null,
    val mimeType: String? = null,
    @ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0L,
    @ColumnInfo(defaultValue = "0") val attachmentCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val isSensitive: Boolean = false,
    val sensitivityReason: String? = null,
    @ColumnInfo(defaultValue = "0") val isLocked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val passwordVaultMatch: Boolean = false,
    val metadataJson: String? = null,
    val deletedAt: Long? = null,
    val expiresAt: Long? = null,
    /** AI state: null/IDLE, QUEUED, RUNNING, DONE, FAILED, SKIPPED_SENSITIVE, DISABLED. */
    val aiState: String? = null,
    val aiError: String? = null,
) {
    /** Sensitive, locked and pinned clips are never auto-deleted. */
    val isCleanupExempt: Boolean
        get() = isPinned || isSensitive || isLocked

    val isTextLike: Boolean
        get() = type !in setOf(ClipboardType.IMAGE, ClipboardType.FILE, ClipboardType.MULTI)

    val isTrashed: Boolean
        get() = deletedAt != null
}
