package com.frerox.toolz.worker

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.UpdateCheckResult
import com.frerox.toolz.data.update.UpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Background worker always allows notification if update is found
            val result = updateRepository.checkForUpdates(showNotification = true)
            settingsRepository.setLastUpdateCheck(System.currentTimeMillis())

            if (result is UpdateCheckResult.NewUpdate) {
                val autoUpdate = settingsRepository.autoUpdateEnabled.first()
                if (autoUpdate) {
                    downloadApkInBackground(result.version, result.downloadUrl)
                }
                return Result.success()
            }
            
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("UpdateCheck", "Update check failed", e)
            Result.retry()
        }
    }

    private fun downloadApkInBackground(version: String, url: String) {
        val downloadManager = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Toolz System Update")
            .setDescription("Downloading version $version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(applicationContext, Environment.DIRECTORY_DOWNLOADS, "toolz_update_$version.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadManager.enqueue(request)
    }
}
