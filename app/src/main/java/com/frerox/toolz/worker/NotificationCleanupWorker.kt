package com.frerox.toolz.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.frerox.toolz.data.notifications.NotificationRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class NotificationCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NotificationRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val days = settingsRepository.notificationRetentionDays.first()
            repository.clearOldNotifications(days)
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            ListenableWorker.Result.failure()
        }
    }
}
