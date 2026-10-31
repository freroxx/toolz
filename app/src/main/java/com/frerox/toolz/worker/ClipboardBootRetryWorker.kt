/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ClipboardService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Deferred boot-time recovery for the clipboard watcher.
 *
 * At boot Shizuku is often not running yet, so the immediate check in
 * [com.frerox.toolz.service.ClipboardBootReceiver] cannot start the service.
 * This worker retries with exponential backoff until a privileged read path is
 * available, then starts the watcher once and stops.
 */
@HiltWorker
class ClipboardBootRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val monitoring = runCatching {
            settingsRepository.clipboardMonitoringEnabled.first()
        }.getOrDefault(false)
        val seen = runCatching {
            settingsRepository.clipboardOnboardingSeen.first()
        }.getOrDefault(false)
        if (!monitoring || !seen) return Result.success()

        val capabilities = ClipboardGate.capabilities(applicationContext, seen)
        if (capabilities.backgroundCapable) {
            ClipboardService.startIfNeeded(applicationContext)
            return Result.success()
        }
        // Give up after roughly two hours of retries. The setup banner and the
        // next app launch still recover the service if Shizuku appears later.
        return if (runAttemptCount < 8) Result.retry() else Result.failure()
    }

    companion object {
        private const val UNIQUE = "ClipboardBootRetry"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ClipboardBootRetryWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
        }
    }
}
