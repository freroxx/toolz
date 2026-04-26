package com.frerox.toolz.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val updateService: UpdateService,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {
    suspend fun checkForUpdates(showNotification: Boolean = false): UpdateCheckResult {
        return try {
            // 1. Try GitHub Release API (dynamic)
            val response = try {
                updateService.getLatestRelease(
                    UpdateConstants.GITHUB_OWNER,
                    UpdateConstants.GITHUB_REPO
                )
            } catch (e: Exception) {
                null
            }

            if (response?.isSuccessful == true) {
                val release = response.body()
                if (release != null) {
                    val currentVersion = getCurrentVersionName()
                    val latestVersion = release.tagName.removePrefix("v")

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        val preferredAbi = settingsRepository.preferredAbi.first()
                        val bestAsset = UpdateHelper.getBestAsset(release.assets, preferredAbi)
                        
                        if (bestAsset != null) {
                            val result = UpdateCheckResult.NewUpdate(
                                version = latestVersion,
                                changelog = release.body ?: "Bug fixes and performance improvements.",
                                downloadUrl = bestAsset.downloadUrl,
                                isCritical = false
                            )
                            saveUpdateInfo(result)
                            if (showNotification) {
                                showUpdateNotification(latestVersion)
                            }
                            return result
                        }
                    } else {
                        return UpdateCheckResult.UpToDate
                    }
                }
            }

            // 2. Fallback to Manifest (statically controlled)
            val manifestResponse = try {
                updateService.getUpdateManifest(UpdateConstants.MANIFEST_URL)
            } catch (e: Exception) {
                null
            }
            
            if (manifestResponse?.isSuccessful == true) {
                val manifest = manifestResponse.body()
                if (manifest != null) {
                    val currentVersion = getCurrentVersionName()
                    if (isNewerVersion(currentVersion, manifest.versionName)) {
                        val preferredAbi = settingsRepository.preferredAbi.first()
                        val bestRelease = manifest.releases?.let { UpdateHelper.getBestRelease(it, preferredAbi) }
                        
                        if (bestRelease != null) {
                            val result = UpdateCheckResult.NewUpdate(
                                version = manifest.versionName,
                                changelog = manifest.changelog ?: "New version available with improvements.",
                                downloadUrl = bestRelease.downloadUrl,
                                isCritical = manifest.isCritical ?: false
                            )
                            saveUpdateInfo(result)
                            if (showNotification) {
                                showUpdateNotification(manifest.versionName)
                            }
                            return result
                        }
                    } else {
                        return UpdateCheckResult.UpToDate
                    }
                }
            }
            
            UpdateCheckResult.Error("Could not fetch update information")
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error during update check")
        }
    }

    private suspend fun saveUpdateInfo(update: UpdateCheckResult.NewUpdate) {
        settingsRepository.setAvailableUpdate(
            update.version,
            update.changelog,
            update.downloadUrl
        )
    }

    private fun showUpdateNotification(version: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationHelper.createAllChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_update_dialog", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_APP_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Update Available: $version")
            .setContentText("A new version of Toolz is ready for deployment.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .build()

        notificationManager.notify(NotificationHelper.ID_APP_UPDATE, notification)
    }

    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "0.0.0"
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
}

sealed class UpdateCheckResult {
    data class NewUpdate(
        val version: String,
        val changelog: String,
        val downloadUrl: String,
        val isCritical: Boolean
    ) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
