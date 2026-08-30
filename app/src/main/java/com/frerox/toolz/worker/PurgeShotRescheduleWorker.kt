/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic safety-net: every 6 hours, sweep due entries and re-mirror.
 * Also catches edge case where exact alarm was cleared by battery optimization.
 */
@HiltWorker
class PurgeShotRescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PurgeShotRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        repository.ensureRestoredAndRescheduled()
        repository.processDue()
        // Keep JobScheduler alive even when app is killed — re-arm content trigger every 6h if enabled
        if (settingsRepository.purgeShotEnabled.first()) {
            try { com.frerox.toolz.service.PurgeShotObserverJobService.schedule(applicationContext) } catch (_: Exception) {}
        }
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
