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

package com.frerox.toolz.worker

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.PendingUpdate
import com.frerox.toolz.data.update.UpdateInstaller
import com.frerox.toolz.data.update.UpdateManager
import com.frerox.toolz.data.update.UpdateNotifications
import com.frerox.toolz.ui.screens.utils.InstallHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class UpdateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    lateinit var installer: UpdateInstaller

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> handleDownloadComplete(context, intent)
            UpdateNotifications.ACTION_INSTALL_UPDATE,
            "com.frerox.toolz.INSTALL_UPDATE" -> {
                val apkPath = intent.getStringExtra("apk_path") ?: return
                val apkFile = File(apkPath)
                if (apkFile.exists()) {
                    // Prefer session install; falls back to FileProvider VIEW.
                    try {
                        when (installer.install(apkFile)) {
                            is com.frerox.toolz.data.update.InstallLaunch.NeedsPermission ->
                                InstallHelper.installApk(context, apkFile)
                            else -> Unit
                        }
                    } catch (_: Exception) {
                        try { InstallHelper.installApk(context, apkFile) } catch (_: Exception) {}
                    }
                }
            }
            UpdateNotifications.ACTION_DOWNLOAD_UPDATE,
            "com.frerox.toolz.DOWNLOAD_UPDATE" -> {
                val url = intent.getStringExtra("url") ?: return
                val version = intent.getStringExtra("version") ?: "New"
                val pending = updateManager.currentPendingOrNull()
                if (pending != null && pending.downloadUrl == url) {
                    updateManager.download(pending, autoInstall = true)
                } else {
                    // Notification arrived without live manager state (process restart):
                    // fall back to system DownloadManager so the tap never dead-ends.
                    downloadApk(context, version, url)
                }
            }
            UpdateNotifications.ACTION_CANCEL_UPDATE -> {
                try { updateManager.cancelDownload() } catch (_: Exception) {}
                try {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as android.app.NotificationManager
                    nm.cancel(UpdateNotifications.ID_UPDATE_DOWNLOAD)
                } catch (_: Exception) {}
            }
            UpdateInstaller.ACTION_INSTALL_STATUS -> handleInstallStatus(context, intent)
        }
    }

    private fun handleInstallStatus(context: Context, intent: Intent) {
        val status = try { installer.statusFromIntent(intent) } catch (_: Exception) { return }
        val version = updateManager.currentPendingOrNull()?.version
            ?: runBlocking {
                try { settingsRepository.updateAvailableVersion.first() } catch (_: Exception) { null }
            } ?: "new version"
        when (status) {
            is com.frerox.toolz.data.update.InstallStatus.Success ->
                updateManager.onInstallSuccess(version)
            is com.frerox.toolz.data.update.InstallStatus.Aborted -> {
                // User cancelled the system installer — back to ready, no error noise.
                Log.i(TAG, "install aborted by user")
            }
            is com.frerox.toolz.data.update.InstallStatus.Blocked ->
                updateManager.onInstallFailure(status.msg)
            is com.frerox.toolz.data.update.InstallStatus.Failed ->
                updateManager.onInstallFailure(status.msg)
        }
        // Extra: PackageInstaller also sends MY_PACKAGE_REPLACED on success;
        // state cleanup already handled above.
        Unit.also { context } // keep context referenced for future use
    }

    private fun handleDownloadComplete(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) return
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        try {
            if (!cursor.moveToFirst()) return
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (cursor.getInt(statusIdx) != DownloadManager.STATUS_SUCCESSFUL) return
            val tracked = context.getSharedPreferences("toolz_update_dl", Context.MODE_PRIVATE)
                .getLong("update_download_id", -1L)
            val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
            val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val src = runCatching { cursor.getString(uriIdx) }.getOrNull().orEmpty()
            val title = runCatching { cursor.getString(titleIdx) }.getOrNull().orEmpty()
            val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val localUri = runCatching { cursor.getString(localUriIdx) }.getOrNull() ?: return
            val apkFile = File(android.net.Uri.parse(localUri).path ?: return)
            val looksLikeToolzUpdate = downloadId == tracked ||
                src.contains("toolz", ignoreCase = true) ||
                title.contains("Toolz", ignoreCase = true) ||
                apkFile.name.startsWith("toolz_update_")
            if (!apkFile.exists() || !looksLikeToolzUpdate) return

            val autoUpdate = runBlocking {
                try { settingsRepository.autoUpdateEnabled.first() } catch (_: Exception) { false }
            }
            // Auto-install when allowed: the "way better updater" promise —
            // download finishes → system installer opens without another tap.
            if (autoUpdate || installer.canInstall()) {
                try {
                    val pending = updateManager.currentPendingOrNull()
                    if (pending != null) {
                        // Hand the file to the manager so UI shows Installing.
                        updateManager.onLegacyDownloadReady(pending, apkFile)
                    } else {
                        installer.install(apkFile)
                    }
                    return
                } catch (_: Exception) {
                    // Fall through to the ready notification.
                }
            }
            val fallback = updateManager.currentPendingOrNull()
                ?: PendingUpdate(
                    version = guessVersion(apkFile.name),
                    versionCode = 0,
                    changelog = "",
                    downloadUrl = src.ifBlank { localUri },
                    sizeBytes = apkFile.length().takeIf { it > 0 },
                    sha256 = null,
                    isCritical = false,
                    abi = "",
                    source = "download-manager"
                )
            UpdateNotifications.showReady(context, fallback, apkFile)
        } finally {
            cursor.close()
        }
    }

    private fun guessVersion(fileName: String): String {
        // toolz_update_1.2.3.apk or toolz_update_v1.2.3.apk
        val core = fileName.removePrefix("toolz_update_").removeSuffix(".apk").removePrefix("v")
        return core.ifBlank { "new version" }
    }

    private fun downloadApk(context: Context, version: String, url: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle("Toolz System Update")
            .setDescription("Downloading version $version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, "toolz_update_$version.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val id = downloadManager.enqueue(request)
        context.getSharedPreferences("toolz_update_dl", Context.MODE_PRIVATE)
            .edit().putLong("update_download_id", id).apply()
    }

    companion object {
        private const val TAG = "UpdateReceiver"
        // Re-export for callers that reference the legacy constant.
        const val EXTRA_STATUS = PackageInstaller.EXTRA_STATUS
    }
}
