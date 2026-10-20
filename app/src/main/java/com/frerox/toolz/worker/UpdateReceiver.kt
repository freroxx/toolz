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
import android.net.Uri
import android.util.Log
import com.frerox.toolz.R
import com.frerox.toolz.data.update.InstallStatus
import com.frerox.toolz.data.update.PendingUpdate
import com.frerox.toolz.data.update.UpdateError
import com.frerox.toolz.data.update.UpdateInstaller
import com.frerox.toolz.data.update.UpdateManager
import com.frerox.toolz.data.update.UpdateNotifications
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Notification actions, install status callbacks and — for files enqueued by
 * older app versions — DownloadManager completion events. New downloads are
 * always handled by [UpdateManager]; this receiver never enqueues one.
 */
@AndroidEntryPoint
class UpdateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var updateManager: UpdateManager

    @Inject
    lateinit var installer: UpdateInstaller

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(appContext, intent)
            } catch (e: Exception) {
                Log.w(TAG, "receiver failed for ${intent.action}: ${e.message}")
            } finally {
                try {
                    pendingResult.finish()
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE ->
                adoptLegacyDownload(context, intent)

            UpdateNotifications.ACTION_INSTALL_UPDATE,
            "com.frerox.toolz.INSTALL_UPDATE" -> {
                val apkPath = intent.getStringExtra("apk_path") ?: return
                updateManager.installDownloaded(File(apkPath))
            }

            UpdateNotifications.ACTION_DOWNLOAD_UPDATE,
            "com.frerox.toolz.DOWNLOAD_UPDATE" -> {
                val url = intent.getStringExtra("url") ?: return
                val version = intent.getStringExtra("version").orEmpty()
                updateManager.downloadFromNotification(url, version)
            }

            UpdateNotifications.ACTION_CANCEL_UPDATE -> {
                updateManager.cancelDownload()
                UpdateNotifications.cancelDownload(context)
            }

            UpdateInstaller.ACTION_INSTALL_STATUS -> handleInstallStatus(context, intent)
        }
    }

    private suspend fun handleInstallStatus(context: Context, intent: Intent) {
        val status = try {
            installer.statusFromIntent(intent)
        } catch (e: Exception) {
            Log.w(TAG, "bad install status: ${e.message}")
            return
        }
        val version = updateManager.pendingVersionOrNull().orEmpty()
        when (status) {
            is InstallStatus.Success -> updateManager.onInstallSuccess(version)

            is InstallStatus.Aborted -> updateManager.onInstallAborted()

            is InstallStatus.PendingUserAction -> {
                val userAction = status.intent
                if (userAction == null) {
                    updateManager.onInstallAborted()
                } else {
                    try {
                        userAction.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(userAction)
                    } catch (e: Exception) {
                        Log.w(TAG, "couldn't open installer UI: ${e.message}")
                        updateManager.onInstallFailure(
                            UpdateError.InstallFailed(
                                overrideMessageRes = R.string.st_Update_ErrorInstallerOpen,
                            )
                        )
                    }
                }
            }

            is InstallStatus.Blocked ->
                updateManager.onInstallFailure(status.msg)

            is InstallStatus.Failed ->
                updateManager.onInstallFailure(status.msg)
        }
    }

    /**
     * Older builds handed downloads to DownloadManager. Adopt their finished
     * files so users mid-download during an upgrade do not lose the APK.
     * Nothing is enqueued from here anymore.
     */
    private suspend fun adoptLegacyDownload(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE)
            as? DownloadManager ?: return
        val cursor = try {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        } catch (e: Exception) {
            null
        } ?: return

        cursor.use { c ->
            if (!c.moveToFirst()) return
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return
            val localUri = runCatching {
                c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            }.getOrNull() ?: return
            val apkFile = File(Uri.parse(localUri).path ?: return)
            if (!apkFile.isFile || apkFile.length() <= 0L) return

            val trackedId = context.getSharedPreferences("toolz_update_dl", Context.MODE_PRIVATE)
                .getLong("update_download_id", -1L)
            val title = runCatching {
                c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
            }.getOrNull().orEmpty()
            val sourceUrl = runCatching {
                c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
            }.getOrNull().orEmpty()
            val looksLikeToolzUpdate = downloadId == trackedId ||
                title.contains("Toolz", ignoreCase = true) ||
                apkFile.name.startsWith("toolz_update_")
            if (!looksLikeToolzUpdate) return

            val target = updateManager.currentPendingOrNull()
                ?: updateManager.pendingFromStore()
                ?: PendingUpdate(
                    version = guessVersion(apkFile.name),
                    versionCode = 0,
                    changelog = "",
                    downloadUrl = sourceUrl,
                    sizeBytes = apkFile.length(),
                    sha256 = null,
                    isCritical = false,
                    abi = "",
                    source = "download-manager",
                )
            updateManager.onLegacyDownloadReady(target, apkFile)
        }
    }

    private fun guessVersion(fileName: String): String {
        val core = fileName
            .removePrefix("toolz_update_")
            .removeSuffix(".apk")
            .removePrefix("v")
        return core
    }

    companion object {
        private const val TAG = "UpdateReceiver"

        /** Re-exported for callers that reference the legacy constant. */
        const val EXTRA_STATUS = PackageInstaller.EXTRA_STATUS
    }
}
