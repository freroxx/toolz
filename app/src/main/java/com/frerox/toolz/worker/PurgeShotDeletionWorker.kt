/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.purgeshot.PurgeShotEntity
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Log

/**
 * Durable deletion worker — one per queued screenshot.
 * Also handles periodic retry for failed deletions (counts as "queue service").
 *
 * Guarantees:
 * - WorkManager persists across reboot/update (rescheduled via PurgeShotBootReceiver)
 * - Also scheduled via AlarmManager for exact timing (secondary trigger)
 * - If file already gone, marks as EXPIRED/DELETED instead of failing infinitely
 */
@HiltWorker
class PurgeShotDeletionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PurgeShotRepository,
    private val purgeDao: com.frerox.toolz.data.purgeshot.PurgeShotDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PurgeShotWorker"
    }

    override suspend fun doWork(): Result {
        val id = inputData.getLong("purge_id", -1L)
        return try {
            if (id == -1L) {
                // No ID — process all due (fallback for alarm-triggered generic work)
                repository.processDue()
                Result.success()
            } else {
                val entity = purgeDao.getById(id)
                if (entity == null) {
                    Log.w(TAG, "Entity $id not found, success")
                    return Result.success()
                }
                if (entity.status != PurgeShotEntity.STATUS_PENDING) {
                    Log.d(TAG, "Entity $id status=${entity.status}, skipping")
                    return Result.success()
                }
                // Check if due yet — WorkManager may fire early after reschedule
                val now = System.currentTimeMillis()
                if (now < entity.scheduledDeleteAtMs) {
                    val remaining = entity.scheduledDeleteAtMs - now
                    Log.d(TAG, "Entity $id not due yet, remaining $remaining ms -> will re-enqueue")
                    // Not due — this should not happen if scheduled correctly, but handle
                    return Result.retry()
                }
                val ok = repository.deleteFile(entity)
                if (ok) {
                    purgeDao.updateStatus(id, PurgeShotEntity.STATUS_DELETED)
                    Log.i(TAG, "Deleted screenshot id=$id uri=${entity.fileUriString}")
                    Result.success()
                } else {
                    // Check if file gone via external query
                    val stillExists = try {
                        val resolver = applicationContext.contentResolver
                        val uri = android.net.Uri.parse(entity.fileUriString)
                        resolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } ?: false
                    } catch (_: Exception) { false }
                    if (!stillExists) {
                        purgeDao.updateStatus(id, PurgeShotEntity.STATUS_EXPIRED)
                        Log.i(TAG, "File already gone, marking expired id=$id")
                        Result.success()
                    } else {
                        purgeDao.incrementAttempts(id, "delete failed")
                        val attempts = entity.attempts + 1
                        if (attempts >= 5) {
                            purgeDao.updateStatus(id, PurgeShotEntity.STATUS_FAILED)
                            Log.w(TAG, "Failed 5 times, marking FAILED id=$id")
                            Result.failure()
                        } else {
                            Log.w(TAG, "Delete failed attempt $attempts id=$id, retry")
                            Result.retry()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "doWork exception", e)
            Result.retry()
        }
    }
}
