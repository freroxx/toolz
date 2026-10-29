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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.frerox.toolz.data.clipboard.ClipboardAiProcessor
import com.frerox.toolz.data.clipboard.ClipboardRetentionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net: expiry, retention, count cap, trash purge, blob quota
 * and automatic AI enrichment. Capture-time enforcement is the primary path;
 * this worker catches anything that happened while the service was off.
 */
@HiltWorker
class ClipboardCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val retentionManager: ClipboardRetentionManager,
    private val aiProcessor: ClipboardAiProcessor,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        retentionManager.enforceAll()
        aiProcessor.processQueued(2)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val UNIQUE = "ClipboardCleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ClipboardCleanupWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun runNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<ClipboardCleanupWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
