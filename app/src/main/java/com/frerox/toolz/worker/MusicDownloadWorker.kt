package com.frerox.toolz.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.frerox.toolz.R
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class MusicDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val catalogRepository: CatalogRepository,
    private val musicRepository: MusicRepository
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID = "music_downloads"
        const val NOTIFICATION_ID_BASE = 1000
        
        const val KEY_TRACK_ID = "track_id"
        const val KEY_TRACK_TITLE = "track_title"
        const val KEY_TRACK_ARTIST = "track_artist"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_DURATION = "duration"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TRACK_TITLE) ?: "Unknown"
        val artist = inputData.getString(KEY_TRACK_ARTIST) ?: "Unknown"
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return@withContext Result.failure()
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL)
        val duration = inputData.getLong(KEY_DURATION, 0L)

        val notificationId = NOTIFICATION_ID_BASE + trackId.hashCode()
        createNotificationChannel()

        setForeground(createForegroundInfo(notificationId, "Downloading $title...", 0))

        try {
            val streamUrl = catalogRepository.resolveAudioStream(sourceUrl)
                ?: return@withContext Result.failure()

            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val toolzDir = File(downloadsDir, "Toolz")
            if (!toolzDir.exists()) toolzDir.mkdirs()

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_")
            val safeArtist = artist.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_")
            val file = File(toolzDir, "$safeArtist - $safeTitle.m4a")

            val success = catalogRepository.downloadAudioStream(streamUrl, file) { progress ->
                val progressInt = (progress * 100).toInt()
                notificationManager.notify(
                    notificationId,
                    createNotification(notificationId, "Downloading $title...", progressInt)
                )
                kotlinx.coroutines.runBlocking {
                    setProgress(workDataOf("progress" to progress))
                }
            }

            if (success) {
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(file.absolutePath),
                    null
                ) { _, _ -> }

                val musicTrack = MusicTrack(
                    uri = Uri.fromFile(file).toString(),
                    title = title,
                    artist = artist,
                    album = "Toolz Downloads",
                    duration = duration,
                    thumbnailUri = thumbnailUrl,
                    path = file.absolutePath,
                    sourceUrl = sourceUrl,
                    dateAdded = System.currentTimeMillis()
                )
                musicRepository.insertTrack(musicTrack)

                showCompletedNotification(notificationId, title)
                Result.success()
            } else {
                showErrorNotification(notificationId, title, "Download failed")
                Result.failure()
            }
        } catch (e: Exception) {
            showErrorNotification(notificationId, title, e.localizedMessage ?: "Download error")
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for music downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(id: Int, title: String, progress: Int): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, createNotification(id, title, progress), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, createNotification(id, title, progress))
        }
    }

    private fun createNotification(id: Int, contentTitle: String, progress: Int): android.app.Notification {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: id.toString()
        val cancelIntent = Intent(applicationContext, DownloadCancelReceiver::class.java).apply {
            putExtra("work_id", trackId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            trackId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun showCompletedNotification(id: Int, title: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }

    private fun showErrorNotification(id: Int, title: String, error: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText("$title: $error")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(id, notification)
    }
}
