package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.whisper.WhisperErrorMapper
import com.frerox.toolz.data.whisper.WhisperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

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
    } catch (e: CancellationException) {
        // WorkManager cancels this coroutine on stop; rethrow so cancellation is honored
        // instead of being swallowed and turned into a pointless retry.
        throw e
    } catch (e: Exception) {
        // Permanent failures (bad request, forbidden, gone) can never succeed on retry;
        // only transient errors belong in the backoff queue (L-3).
        if (WhisperErrorMapper.isPermanentError(e)) ListenableWorker.Result.failure() else ListenableWorker.Result.retry()
    }
}
