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
import com.frerox.toolz.data.update.GitHubRelease
import com.frerox.toolz.data.update.UpdateConstants
import com.frerox.toolz.data.update.UpdateService
import com.frerox.toolz.data.update.UpdateHelper
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
            // Try GitHub API first (more dynamic)
            val response = updateService.getLatestRelease(UpdateConstants.GITHUB_OWNER, UpdateConstants.GITHUB_REPO)
            
            if (response.isSuccessful) {
                val release = response.body()
                if (release != null) {
                    val currentVersionName = getCurrentVersionName()
                    val latestVersion = release.tagName.removePrefix("v")
                    
                    if (isNewerVersion(currentVersionName, latestVersion)) {
                        val preferredAbi = settingsRepository.preferredAbi.first()
                        val bestAsset = UpdateHelper.getBestAsset(release.assets, preferredAbi)
                        val apkUrl = bestAsset?.downloadUrl ?: ""
                        
                        if (apkUrl.isNotEmpty()) {
                            settingsRepository.setAvailableUpdate(latestVersion, release.body, apkUrl)
                            handleUpdateAvailability(latestVersion, apkUrl)
                            return Result.success()
                        }
                    }
                }
            }
            
            // Fallback to manifest if API fails or no release found
            android.util.Log.d("UpdateCheck", "GitHub API failed or no release, trying manifest fallback")
            tryManifestFallback()
        } catch (e: Exception) {
            android.util.Log.e("UpdateCheck", "Update check failed", e)
            tryManifestFallback()
        }
    }

    private suspend fun tryManifestFallback(): Result {
        return try {
            val manifestResponse = updateService.getUpdateManifest(UpdateConstants.MANIFEST_URL)
            if (manifestResponse.isSuccessful) {
                val manifest = manifestResponse.body() ?: return Result.failure()
                val currentVersionName = getCurrentVersionName()
                
                if (isNewerVersion(currentVersionName, manifest.versionName)) {
                    val preferredAbi = settingsRepository.preferredAbi.first()
                    val bestRelease = manifest.releases?.let { UpdateHelper.getBestRelease(it, preferredAbi) }
                    val apkUrl = bestRelease?.downloadUrl ?: ""

                    if (apkUrl.isNotEmpty()) {
                        settingsRepository.setAvailableUpdate(manifest.versionName, manifest.changelog ?: "", apkUrl)
                        handleUpdateAvailability(manifest.versionName, apkUrl)
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

    private suspend fun handleUpdateAvailability(version: String, url: String) {
        val autoUpdate = settingsRepository.autoUpdateEnabled.first()
        if (autoUpdate) {
            downloadApkInBackground(version, url)
        } else {
            showUpdateNotification(version)
        }
        settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
    }

    private fun getCurrentVersionName(): String {
        return try {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (current == latest) return false
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLength) {
            val curr = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > curr) return true
            if (lat < curr) return false
        }
        return false
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
            .setContentTitle("Update Available: $version")
            .setContentText("A new performance patch is ready for deployment.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .build()

        notificationManager.notify(1001, notification)
    }
}
