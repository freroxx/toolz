package com.frerox.toolz.worker

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.UpdateConstants
import com.frerox.toolz.data.update.UpdateService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val updateService: UpdateService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val response = updateService.getLatestRelease(UpdateConstants.GITHUB_OWNER, UpdateConstants.GITHUB_REPO)
            if (response.isSuccessful) {
                val release = response.body() ?: return Result.failure()
                
                val currentVersionName = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "0.0.0"
                
                // Compare versions (simple string comparison or semantic versioning)
                // GitHub tags are often "v1.7.6", while versionName is "1.7.6"
                val latestVersion = release.tagName.removePrefix("v")
                
                if (isNewerVersion(currentVersionName, latestVersion)) {
                    val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                    val apkUrl = apkAsset?.downloadUrl ?: ""
                    
                    if (apkUrl.isNotEmpty()) {
                        settingsRepository.setAvailableUpdate(latestVersion, release.body, apkUrl)
                        
                        val autoUpdate = settingsRepository.autoUpdateEnabled.first()
                        if (autoUpdate) {
                            downloadApkInBackground(latestVersion, apkUrl)
                        } else {
                            showUpdateNotification(latestVersion)
                        }
                    }
                }
                
                settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until minOf(currentParts.size, latestParts.size)) {
            if (latestParts[i] > currentParts[i]) return true
            if (latestParts[i] < currentParts[i]) return false
        }
        return latestParts.size > currentParts.size
    }

    private fun downloadApkInBackground(version: String, url: String) {
        val downloadManager = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Toolz System Update")
            .setDescription("Preparing latest version $version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setDestinationInExternalFilesDir(applicationContext, Environment.DIRECTORY_DOWNLOADS, "toolz_update_$version.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadManager.enqueue(request)
    }

    private fun showUpdateNotification(version: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "app_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_update_dialog", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
            .setContentTitle("New Engine Patch: $version")
            .setContentText("A new performance update is available for deployment.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .build()

        notificationManager.notify(1001, notification)
    }
}
