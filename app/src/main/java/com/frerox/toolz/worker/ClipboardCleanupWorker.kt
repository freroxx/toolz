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
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ClipboardService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class ClipboardCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val clipboardDao: ClipboardDao,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = settingsRepository.clipboardRetentionDays.first()
            if (retentionDays > 0) {
                val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
                clipboardDao.deleteOlderThan(cutoff)
            }
            val count = clipboardDao.getEntryCount()
            if (count > ClipboardService.MAX_ENTRIES) {
                clipboardDao.deleteOldestUnpinned(count - ClipboardService.MAX_ENTRIES)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE = "ClipboardCleanup"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ClipboardCleanupWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}
