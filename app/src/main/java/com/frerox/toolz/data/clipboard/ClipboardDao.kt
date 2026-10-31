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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    // ── Active history ────────────────────────────────────────────────────

    @Query("SELECT * FROM clipboard_entries WHERE deletedAt IS NULL ORDER BY isPinned DESC, timestamp DESC")
    fun getAllEntries(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getTrashedEntries(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries ORDER BY timestamp DESC")
    fun getAllEntriesIncludingTrash(): Flow<List<ClipboardEntry>>

    @Query("SELECT * FROM clipboard_entries ORDER BY timestamp DESC")
    suspend fun getAllEntriesSync(): List<ClipboardEntry>

    @Query("SELECT * FROM clipboard_entries WHERE deletedAt IS NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestEntry(): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE id = :id")
    suspend fun getEntryById(id: Int): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE deletedAt IS NULL AND contentHashSha256 = :hash ORDER BY timestamp DESC LIMIT 1")
    suspend fun getBySha256(hash: String): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE contentHashSha256 = :hash ORDER BY timestamp DESC LIMIT 1")
    suspend fun getBySha256Any(hash: String): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE deletedAt IS NULL AND contentHash = :hash ORDER BY timestamp DESC LIMIT 1")
    suspend fun getByContentHash(hash: Int): ClipboardEntry?

    @Query("SELECT * FROM clipboard_entries WHERE contentHash = :hash ORDER BY timestamp DESC LIMIT 1")
    suspend fun getByContentHashAny(hash: Int): ClipboardEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ClipboardEntry): Long

    @Update
    suspend fun update(entry: ClipboardEntry)

    @Delete
    suspend fun delete(entry: ClipboardEntry)

    @Query("DELETE FROM clipboard_entries WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE clipboard_entries SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePin(id: Int)

    @Query("UPDATE clipboard_entries SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Int, pinned: Boolean)

    @Query("UPDATE clipboard_entries SET timestamp = :timestamp, source = :source, sourceApp = COALESCE(:sourceApp, sourceApp) WHERE id = :id")
    suspend fun bumpEntry(id: Int, timestamp: Long, source: String?, sourceApp: String?)

    @Query("UPDATE clipboard_entries SET summary = :summary, type = :type, isAiProcessed = 1, aiState = 'DONE', aiError = NULL WHERE id = :id")
    suspend fun updateAiDetails(id: Int, summary: String?, type: String)

    @Query("UPDATE clipboard_entries SET aiState = :state, aiError = :error WHERE id = :id")
    suspend fun updateAiState(id: Int, state: String?, error: String?)

    /** Drops any AI summary and marks the clip as permanently ineligible. */
    @Query("UPDATE clipboard_entries SET summary = NULL, isAiProcessed = 0, aiState = :state, aiError = NULL WHERE id = :id")
    suspend fun clearAiDetails(id: Int, state: String?)

    @Query("UPDATE clipboard_entries SET isSensitive = :sensitive, sensitivityReason = :reason, passwordVaultMatch = :vaultMatch, isLocked = :locked WHERE id = :id")
    suspend fun updateSensitivity(id: Int, sensitive: Boolean, reason: String?, vaultMatch: Boolean, locked: Boolean)

    @Query("UPDATE clipboard_entries SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: Int, locked: Boolean)

    @Query("UPDATE clipboard_entries SET expiresAt = :expiresAt WHERE id = :id")
    suspend fun setExpiresAt(id: Int, expiresAt: Long?)

    /**
     * Recomputes expiry for every non-sensitive clip after the retention
     * preference changes. Without this an old clip keeps the expiry it was
     * created with, so increasing retention from 7 to 30 days would still
     * delete it on day 7.
     */
    @Query(
        "UPDATE clipboard_entries SET expiresAt = " +
            "CASE WHEN :retentionMs > 0 THEN timestamp + :retentionMs ELSE NULL END " +
            "WHERE deletedAt IS NULL AND isPinned = 0 AND isSensitive = 0 AND isLocked = 0",
    )
    suspend fun recomputeExpiry(retentionMs: Long)

    /**
     * Clips eligible for automatic AI enrichment. FAILED/RUNNING/DISABLED/
     * SKIPPED states are deliberately excluded: failures must not be retried
     * on every poll (that would hammer a broken provider). [requeueStalledAi]
     * puts them back when the user explicitly re-enables AI.
     */
    @Query(
        "SELECT * FROM clipboard_entries WHERE isAiProcessed = 0 " +
            "AND deletedAt IS NULL AND isSensitive = 0 AND isLocked = 0 " +
            "AND type NOT IN ('IMAGE', 'FILE', 'MULTI') " +
            "AND (aiState IS NULL OR aiState = 'IDLE') ORDER BY timestamp DESC",
    )
    suspend fun getUnprocessedEntries(): List<ClipboardEntry>

    /** Re-queues clips skipped only because AI was off/unconfigured or failed. */
    @Query(
        "UPDATE clipboard_entries SET aiState = 'IDLE', aiError = NULL " +
            "WHERE isAiProcessed = 0 AND deletedAt IS NULL " +
            "AND isSensitive = 0 AND isLocked = 0 " +
            "AND aiState IN ('DISABLED', 'FAILED')",
    )
    suspend fun requeueStalledAi()

    /**
     * Recovers clips left in RUNNING after a process death. Called once per
     * process when no enrichment is actually in flight, so a crashed AI run
     * cannot keep a clip stuck forever.
     */
    @Query(
        "UPDATE clipboard_entries SET aiState = 'IDLE', aiError = NULL " +
            "WHERE isAiProcessed = 0 AND aiState = 'RUNNING'",
    )
    suspend fun resetStalledRunningAi()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<ClipboardEntry>)

    // ── Trash / retention ─────────────────────────────────────────────────

    @Query("UPDATE clipboard_entries SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: Int, timestamp: Long)

    @Query("UPDATE clipboard_entries SET deletedAt = :timestamp WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun softDeleteMany(ids: List<Int>, timestamp: Long)

    @Query("UPDATE clipboard_entries SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Int)

    @Query("UPDATE clipboard_entries SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreMany(ids: List<Int>)

    @Query("DELETE FROM clipboard_entries WHERE deletedAt IS NOT NULL AND deletedAt < :timestamp")
    suspend fun deleteTrashedBefore(timestamp: Long): Int

    @Query("DELETE FROM clipboard_entries WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash(): Int

    /** Auto-cleanup never touches pinned, sensitive or locked clips. */
    @Query("DELETE FROM clipboard_entries WHERE deletedAt IS NULL AND isPinned = 0 AND isSensitive = 0 AND isLocked = 0 AND timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /** Expiry is only ever set on non-sensitive clips; enforce it defensively anyway. */
    @Query("DELETE FROM clipboard_entries WHERE deletedAt IS NULL AND isPinned = 0 AND isSensitive = 0 AND isLocked = 0 AND expiresAt IS NOT NULL AND expiresAt < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE deletedAt IS NULL AND isPinned = 0 AND isSensitive = 0 AND isLocked = 0 AND attachmentCount > 0")
    suspend fun getEligibleAttachmentEntryCount(): Int

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE deletedAt IS NULL AND isPinned = 0 AND isSensitive = 0 AND isLocked = 0")
    suspend fun getEligibleEntryCount(): Int

    @Query("DELETE FROM clipboard_entries WHERE id IN (SELECT id FROM clipboard_entries WHERE deletedAt IS NULL AND isPinned = 0 AND isSensitive = 0 AND isLocked = 0 ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestEligible(count: Int)

    @Query("SELECT * FROM clipboard_entries WHERE deletedAt IS NULL AND isPinned = 0 AND isSensitive = 0 AND isLocked = 0 AND attachmentCount > 0 ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestEligibleWithAttachments(): ClipboardEntry?

    @Query("UPDATE clipboard_entries SET deletedAt = :timestamp WHERE deletedAt IS NULL AND isPinned = 0")
    suspend fun clearAllUnpinned(timestamp: Long)

    @Query("SELECT COUNT(*) FROM clipboard_entries")
    suspend fun getEntryCount(): Int

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE deletedAt IS NULL")
    suspend fun getActiveEntryCount(): Int

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE deletedAt IS NOT NULL")
    suspend fun getTrashedEntryCount(): Int

    @Query("SELECT COUNT(*) FROM clipboard_entries WHERE deletedAt IS NULL AND isSensitive = 1")
    suspend fun getSensitiveEntryCount(): Int

    // ── Attachments ───────────────────────────────────────────────────────

    @Query("SELECT * FROM clipboard_attachments ORDER BY clipId ASC, position ASC")
    fun getAllAttachments(): Flow<List<ClipboardAttachment>>

    @Query("SELECT * FROM clipboard_attachments WHERE clipId = :clipId ORDER BY position ASC")
    fun getAttachmentsForClip(clipId: Int): Flow<List<ClipboardAttachment>>

    @Query("SELECT * FROM clipboard_attachments WHERE clipId = :clipId ORDER BY position ASC")
    suspend fun getAttachmentsForClipSync(clipId: Int): List<ClipboardAttachment>

    @Query("SELECT * FROM clipboard_attachments")
    suspend fun getAllAttachmentsSync(): List<ClipboardAttachment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: ClipboardAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<ClipboardAttachment>)

    @Query("DELETE FROM clipboard_attachments WHERE clipId = :clipId")
    suspend fun deleteAttachmentsForClip(clipId: Int)

    @Query("SELECT DISTINCT blobId FROM clipboard_attachments")
    suspend fun getReferencedBlobIds(): List<String>

    @Query("SELECT COUNT(*) FROM clipboard_attachments WHERE blobId = :blobId")
    suspend fun countBlobReferences(blobId: String): Int

    @Query("SELECT SUM(sizeBytes) FROM clipboard_attachments")
    suspend fun getAttachmentBytes(): Long?
}
