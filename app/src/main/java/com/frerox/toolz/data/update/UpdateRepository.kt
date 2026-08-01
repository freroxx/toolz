/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    private var updateApkUrlInternal: String? = null

    private suspend fun saveUpdateInfo(update: UpdateCheckResult.NewUpdate) {
        updateApkUrlInternal = update.downloadUrl
        settingsRepository.setAvailableUpdate(
            update.version,
            update.changelog,
            update.downloadUrl
        )
    }

    private fun showUpdateNotification(version: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationHelper.createAllChannels(context)

        // 1. Content Intent: Open Update Screen
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_update_dialog", true)
            putExtra("navigate_to", "update_settings")
        }
        val contentPendingIntent = PendingIntent.getActivity(context, 8001, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 2. Action: Download Now
        val downloadUrl = updateApkUrlInternal ?: ""
        val downloadIntent = Intent(context, com.frerox.toolz.worker.UpdateReceiver::class.java).apply {
            action = "com.frerox.toolz.DOWNLOAD_UPDATE"
            putExtra("url", downloadUrl)
            putExtra("version", version)
        }
        val downloadPendingIntent = PendingIntent.getBroadcast(context, 8002, downloadIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // 3. Action: View Changelog
        val changelogIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "update_settings")
        }
        val changelogPendingIntent = PendingIntent.getActivity(context, 8003, changelogIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationHelper.baseBuilder(context, NotificationHelper.CHANNEL_APP_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New Version Available: $version")
            .setContentText("A new version of Toolz is ready for deployment. Tap to see what's new.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Toolz $version is available with bug fixes and new features. Download now to stay up to date."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .addAction(R.drawable.ic_launcher_foreground, "Download", downloadPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Details", changelogPendingIntent)
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
