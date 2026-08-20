/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.whisper.WhisperDeletedMessagesStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Keeps device-only delete-for-me tombstones bounded without requiring a network connection. */
@HiltWorker
class WhisperLocalCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val deletedMessagesStore: WhisperDeletedMessagesStore,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): ListenableWorker.Result = try {
        deletedMessagesStore.purgeExpired()
        ListenableWorker.Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        // WorkManager cancels this coroutine on stop; rethrow so cancellation is honored.
        throw e
    } catch (_: Exception) {
        // The operation is local and idempotent; a future periodic pass can safely retry it,
        // so a persistent failure just ends this run instead of churning the retry queue.
        ListenableWorker.Result.failure()
    }
}
