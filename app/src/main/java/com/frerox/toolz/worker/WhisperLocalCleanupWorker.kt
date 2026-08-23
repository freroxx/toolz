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
    companion object {
        private const val TAG = "WhisperCleanup"
    }

    override suspend fun doWork(): ListenableWorker.Result =
        // V2-FIX L-?: the body is wrapped so a failure is always LOGGED (observability) —
        // the old bare failure() gave zero signal about recurring local-cleanup breakage.
        runCatching { deletedMessagesStore.evictOldest() }.fold(
            onSuccess = { ListenableWorker.Result.success() },
            onFailure = { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (com.frerox.toolz.BuildConfig.DEBUG) {
                    android.util.Log.w(TAG, "whisper local cleanup failed", e)
                } else {
                    android.util.Log.w(TAG, "whisper local cleanup failed: ${e.javaClass.name}")
                }
                ListenableWorker.Result.failure()
            },
        )
}
