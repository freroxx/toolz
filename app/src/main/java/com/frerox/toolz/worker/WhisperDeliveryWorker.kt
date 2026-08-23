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

    companion object {
        // V2-FIX M-M?: cap on WorkManager runs (~5) before the worker gives up and reports
        // failure; per-message drop accounting (attempts → noteDropped) stays upstream.
        private const val MAX_DELIVERY_ATTEMPTS = 5
    }

    override suspend fun doWork(): ListenableWorker.Result = try {
        // V2-FIX M-H?: transient failures must propagate so WorkManager backoff engages.
        // flushOutgoingMessages() throws when the flush itself cannot proceed (offline,
        // storage/transport errors); per-message permanent rejections are handled inside
        // the repository (attempts++, then drop + notify), so they never reach here.
        repository.flushOutgoingMessages()
        ListenableWorker.Result.success()
    } catch (e: CancellationException) {
        // WorkManager cancels this coroutine on stop; rethrow so cancellation is honored
        // instead of being swallowed and turned into a pointless retry.
        throw e
    } catch (e: Exception) {
        // Permanent failures (bad request, forbidden, gone) can never succeed on retry;
        // their drop handling lives upstream, so end as success instead of churning the
        // queue. Transient failures retry with backoff, capped at MAX_DELIVERY_ATTEMPTS.
        when {
            WhisperErrorMapper.isPermanentError(e) -> ListenableWorker.Result.success()
            runAttemptCount >= MAX_DELIVERY_ATTEMPTS - 1 -> ListenableWorker.Result.failure()
            else -> ListenableWorker.Result.retry()
        }
    }
}
