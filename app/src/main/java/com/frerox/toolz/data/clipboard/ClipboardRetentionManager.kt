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

import android.util.Log
import com.frerox.toolz.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ClipboardRetention"
private const val TRASH_RETENTION_DAYS = 30L

/**
 * Retention, trash and quota enforcement.
 *
 * Invariants:
 *  - pinned, sensitive and manually locked clips are never auto-deleted;
 *  - sensitive clips never expire;
 *  - trash is recoverable for 30 days, then purged with its blobs;
 *  - the attachment store never exceeds the configured total quota unless
 *    every remaining clip is exempt (in which case capture is rejected).
 */
@Singleton
class ClipboardRetentionManager @Inject constructor(
    private val clipboardDao: ClipboardDao,
    private val blobStore: ClipboardBlobStore,
    private val settingsRepository: SettingsRepository,
) {

    data class Stats(
        val activeCount: Int = 0,
        val trashedCount: Int = 0,
        val sensitiveCount: Int = 0,
        val attachmentBytes: Long = 0L,
        val blobBytes: Long = 0L,
        val itemQuotaMb: Int = 25,
        val totalQuotaMb: Int = 250,
        val maxEntries: Int = 500,
    )

    suspend fun stats(): Stats = withContext(Dispatchers.IO) {
        Stats(
            activeCount = clipboardDao.getActiveEntryCount(),
            trashedCount = clipboardDao.getTrashedEntryCount(),
            sensitiveCount = clipboardDao.getSensitiveEntryCount(),
            attachmentBytes = clipboardDao.getAttachmentBytes() ?: 0L,
            blobBytes = blobStore.totalBytes(),
            itemQuotaMb = settingsRepository.clipboardItemQuotaMb.first(),
            totalQuotaMb = settingsRepository.clipboardTotalQuotaMb.first(),
            maxEntries = settingsRepository.clipboardMaxEntries.first(),
        )
    }

    /** Runs after every capture; cheap enough to keep the history tidy. */
    suspend fun enforceAfterCapture() = withContext(Dispatchers.IO) {
        try {
            purgeExpiredTrash()
            enforceExpiry()
            enforceRetention()
            enforceCountCap()
            // Deleted rows cascade to attachments, but their content-addressed
            // blobs only disappear once GC confirms nothing references them.
            blobStore.gc()
            enforceBlobQuota()
        } catch (e: Exception) {
            Log.w(TAG, "Post-capture cleanup failed", e)
        }
    }

    /** Full sweep used by the periodic worker. */
    suspend fun enforceAll() = withContext(Dispatchers.IO) {
        try {
            purgeExpiredTrash()
            enforceExpiry()
            enforceRetention()
            enforceCountCap()
            enforceBlobQuota()
            blobStore.gc()
            blobStore.pruneShareCache()
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed", e)
        }
    }

    private suspend fun enforceExpiry() {
        clipboardDao.deleteExpired(System.currentTimeMillis())
    }

    private suspend fun enforceRetention() {
        val days = settingsRepository.clipboardRetentionDays.first()
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        clipboardDao.deleteOlderThan(cutoff)
    }

    private suspend fun enforceCountCap() {
        val cap = settingsRepository.clipboardMaxEntries.first()
        val count = clipboardDao.getEligibleEntryCount()
        if (count > cap) {
            clipboardDao.deleteOldestEligible(count - cap)
        }
    }

    private suspend fun purgeExpiredTrash() {
        val cutoff = System.currentTimeMillis() - TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L
        clipboardDao.deleteTrashedBefore(cutoff)
    }

    private suspend fun enforceBlobQuota() {
        val totalQuota = settingsRepository.clipboardTotalQuotaMb.first() * 1024L * 1024L
        if (blobStore.totalBytes() <= totalQuota) return
        trimToQuota(totalQuota)
    }

    /**
     * Permanently deletes the oldest eligible clips (with attachments) until
     * the blob store fits [targetBytes]. Never touches exempt clips or blobs
     * that an in-flight capture is still staging.
     */
    suspend fun trimToQuota(
        targetBytes: Long,
        protectedIds: Set<String> = emptySet(),
    ) = withContext(Dispatchers.IO) {
        var guard = 0
        while (blobStore.totalBytes() > targetBytes && guard < 10_000) {
            val candidate = clipboardDao.getOldestEligibleWithAttachments() ?: break
            clipboardDao.deleteById(candidate.id)
            blobStore.gc(protectedIds)
            guard++
        }
        blobStore.gc(protectedIds)
    }

    /** Moves unpinned clips to trash (recoverable for 30 days). */
    suspend fun clearAllUnpinned() = withContext(Dispatchers.IO) {
        clipboardDao.clearAllUnpinned(System.currentTimeMillis())
    }

    /** Permanently deletes every trashed clip and its blobs. */
    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        clipboardDao.emptyTrash()
        blobStore.gc()
    }

    suspend fun deleteNow(id: Int) = withContext(Dispatchers.IO) {
        clipboardDao.deleteById(id)
        blobStore.gc()
    }
}
