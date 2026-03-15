package com.frerox.toolz.worker

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.ui.screens.utils.InstallHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class UpdateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == -1L) return

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = cursor.getString(localUriIndex)
                    val apkFile = File(android.net.Uri.parse(localUri).path ?: "")

                    CoroutineScope(Dispatchers.IO).launch {
                        val autoUpdate = settingsRepository.autoUpdateEnabled.first()
                        if (autoUpdate && apkFile.exists()) {
                            // In a real scenario, we'd verify SHA-256 here if we had it stored from the manifest
                            // For now, if auto-update is on, we trigger install
                            InstallHelper.installApk(context, apkFile)
                        }
                    }
                }
            }
            cursor.close()
        }
    }
}
