package com.frerox.toolz.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.UpdateManifest
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manifestUrl = "https://your-github-pages-url.com/update.json" // Replace with actual URL
        val request = Request.Builder().url(manifestUrl).build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string() ?: return Result.failure()
                val manifest = moshi.adapter(UpdateManifest::class.java).fromJson(json) ?: return Result.failure()

                val currentVersion = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).longVersionCode
                if (manifest.versionCode > currentVersion) {
                    showUpdateNotification(manifest)
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

    private fun showUpdateNotification(manifest: UpdateManifest) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "app_updates"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "App Updates", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_update", true)
            putExtra("version_name", manifest.versionName)
            putExtra("changelog", manifest.changelog)
            putExtra("apk_url", manifest.apkUrl)
            putExtra("sha256", manifest.sha256)
        }
        
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
            .setContentTitle("New Update Available: ${manifest.versionName}")
            .setContentText("Tap to see what's new and update.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
