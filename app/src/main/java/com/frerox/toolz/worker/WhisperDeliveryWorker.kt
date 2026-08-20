package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.whisper.WhisperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.jan.supabase.exceptions.RestException
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
        // only transient errors belong in the backoff queue. Local fallback until the
        // shared WhisperErrorMapper.isPermanentError helper lands.
        if (e.isPermanentFailure()) ListenableWorker.Result.failure() else ListenableWorker.Result.retry()
    }

    private fun Exception.isPermanentFailure(): Boolean =
        (this is RestException && statusCode in setOf(400, 401, 403, 404, 410)) ||
            this is IllegalArgumentException ||
            this is IllegalStateException
}
