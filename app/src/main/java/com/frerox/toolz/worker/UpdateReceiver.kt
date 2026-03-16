package com.frerox.toolz.worker

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
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

                    if (apkFile.exists()) {
                        showReadyToInstallNotification(context, apkFile)
                    }
                }
            }
            cursor.close()
        } else if (intent.action == "com.frerox.toolz.INSTALL_UPDATE") {
            val apkPath = intent.getStringExtra("apk_path") ?: return
            val apkFile = File(apkPath)
            if (apkFile.exists()) {
                InstallHelper.installApk(context, apkFile)
            }
        }
    }

    private fun showReadyToInstallNotification(context: Context, apkFile: File) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "app_updates"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val installIntent = Intent(context, UpdateReceiver::class.java).apply {
            action = "com.frerox.toolz.INSTALL_UPDATE"
            putExtra("apk_path", apkFile.absolutePath)
        }
        val installPendingIntent = PendingIntent.getBroadcast(context, 0, installIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val laterIntent = Intent() // Just dismisses
        val laterPendingIntent = PendingIntent.getBroadcast(context, 1, laterIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
            .setContentTitle("Update Ready")
            .setContentText("Would you like to update?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .addAction(com.frerox.toolz.R.drawable.ic_launcher_foreground, "Yes", installPendingIntent)
            .addAction(com.frerox.toolz.R.drawable.ic_launcher_foreground, "Later", laterPendingIntent)
            .build()

        notificationManager.notify(1002, notification)
    }
}
