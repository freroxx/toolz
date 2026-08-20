package com.frerox.toolz.data.whisper

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.frerox.toolz.worker.WhisperDeliveryWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules prompt, network-aware encrypted outbox delivery without waking the app unnecessarily. */
@Singleton
class WhisperDeliveryScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleNow() {
        val request = OneTimeWorkRequestBuilder<WhisperDeliveryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        // APPEND_OR_REPLACE (not KEEP): if a replay is already enqueued, the new request is
        // appended behind it instead of being silently dropped, so a send that arrives while
        // an earlier replay is pending is never lost. The repository flush is idempotent, so
        // a queued second run is harmless. This lane deliberately stays distinct from the
        // periodic "WhisperEncryptedDelivery" lane scheduled in ToolzApplication; sharing a
        // unique name between the two would need a ToolzApplication-side change.
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "WhisperEncryptedDeliveryNow"
        const val TAG = "whisper_encrypted_delivery"
    }
}
