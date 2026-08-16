package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.whisper.WhisperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Network-constrained replay for encrypted Whisper messages that could not be inserted live. */
@HiltWorker
class WhisperDeliveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: WhisperRepository,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): ListenableWorker.Result = try {
        repository.flushOutgoingMessages()
        ListenableWorker.Result.success()
    } catch (_: Exception) {
        ListenableWorker.Result.retry()
    }
}
