package com.frerox.toolz.util

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.backup.LocalBackupManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupManager: LocalBackupManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            NotificationHelper.createAllChannels(appContext)
            val result = backupManager.exportReusableBackup(reason = "scheduled")
            NotificationHelper.showBackupSuccess(appContext, result.fileName, isScheduled = true)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            NotificationHelper.showBackupFailure(appContext, e.localizedMessage, isScheduled = true)
            Result.retry()
        }
    }
}
