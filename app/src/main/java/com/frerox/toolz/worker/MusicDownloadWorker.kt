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
import android.widget.Toast
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class MusicDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val catalogRepository: CatalogRepository,
    private val musicRepository: MusicRepository,
    private val okHttpClient: OkHttpClient
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
        val format = inputData.getString("format") ?: "M4A"
        val quality = inputData.getString("quality") ?: "HIGH"

        val notificationId = NOTIFICATION_ID_BASE + trackId.hashCode()
        createNotificationChannel()

        setForeground(createForegroundInfo(notificationId, "Downloading $title...", 0))

        try {
            val streamUrl = catalogRepository.resolveAudioStream(sourceUrl, quality)
                ?: return@withContext Result.failure()

            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val toolzDir = File(downloadsDir, "Toolz")
            if (!toolzDir.exists()) toolzDir.mkdirs()

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_")
            val safeArtist = artist.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_")
            
            // Download as temporary .m4a first
            val tempFile = File(applicationContext.cacheDir, "temp_download_${trackId}.m4a")
            val thumbFile = if (!thumbnailUrl.isNullOrEmpty()) {
                File(applicationContext.cacheDir, "temp_thumb_${trackId}.jpg")
            } else null
            
            val downloadSuccess = catalogRepository.downloadAudioStream(streamUrl, tempFile) { progress ->
                val progressInt = (progress * 100).toInt()
                notificationManager.notify(
                    notificationId,
                    createNotification(notificationId, "Downloading $title...", progressInt)
                )
                kotlinx.coroutines.runBlocking {
                    setProgress(workDataOf("progress" to progress))
                }
            }

            if (!downloadSuccess) {
                showErrorNotification(notificationId, title, "Download failed")
                return@withContext Result.failure()
            }

            // Download thumbnail if available
            if (thumbFile != null && thumbnailUrl != null) {
                downloadThumbnail(thumbnailUrl, thumbFile)
            }

            // Prepare final file
            val extension = format.lowercase()
            val finalFile = File(toolzDir, "$safeArtist - $safeTitle.$extension")

            // Convert and embed thumbnail
            setForeground(createForegroundInfo(notificationId, "Processing $format...", 100))
            val success = processAudio(tempFile, thumbFile, finalFile, format, quality, title, artist)

            if (success) {
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(finalFile.absolutePath),
                    null
                ) { _, _ -> }

                // Save thumbnail locally for immediate offline access
                var localThumbUri = thumbnailUrl
                if (thumbFile?.exists() == true) {
                    try {
                        val thumbDir = File(applicationContext.filesDir, "thumbnails")
                        if (!thumbDir.exists()) thumbDir.mkdirs()
                        val persistentThumb = File(thumbDir, "${trackId}.jpg")
                        thumbFile.copyTo(persistentThumb, overwrite = true)
                        localThumbUri = Uri.fromFile(persistentThumb).toString()
                    } catch (e: Exception) {
                        android.util.Log.e("MusicDownloadWorker", "Failed to save local thumb", e)
                    }
                }

                if (tempFile.exists()) tempFile.delete()
                if (thumbFile?.exists() == true) thumbFile.delete()

                android.util.Log.d("MusicDownloadWorker", "Download successful. Inserting track: $title, thumb: $localThumbUri")

                val musicTrack = MusicTrack(
                    uri = Uri.fromFile(finalFile).toString(),
                    title = title,
                    artist = artist,
                    album = "Toolz Downloads",
                    duration = duration,
                    thumbnailUri = localThumbUri,
                    path = finalFile.absolutePath,
                    sourceUrl = sourceUrl,
                    dateAdded = System.currentTimeMillis()
                )
                musicRepository.insertTrack(musicTrack)

                showCompletedNotification(notificationId, title)
                Result.success()
            } else {
                if (tempFile.exists()) tempFile.delete()
                if (thumbFile?.exists() == true) thumbFile.delete()
                showErrorNotification(notificationId, title, "Conversion failed")
                Result.failure()
            }
        } catch (e: Exception) {
            showErrorNotification(notificationId, title, e.localizedMessage ?: "Download error")
            Result.failure()
        }
    }

    private fun downloadThumbnail(url: String, file: File) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            response.close()
        } catch (e: Exception) {
            android.util.Log.e("MusicDownloadWorker", "Failed to download thumbnail", e)
        }
    }

    private fun processAudio(
        input: File,
        thumb: File?,
        output: File,
        format: String,
        quality: String,
        title: String,
        artist: String
    ): Boolean {
        val bitrate = when (quality.uppercase()) {
            "LOW" -> "96k"
            "MEDIUM" -> "160k"
            else -> "320k"
        }
        val extension = format.lowercase()
        
        val command = StringBuilder("-i \"${input.absolutePath}\" ")
        if (thumb?.exists() == true) {
            command.append("-i \"${thumb.absolutePath}\" ")
        }

        // Map streams: audio from first input, video (cover) from second input
        if (thumb?.exists() == true) {
            command.append("-map 0:a -map 1:v ")
        } else {
            command.append("-map 0:a ")
        }

        // Codec and bitrate
        when (extension) {
            "mp3" -> command.append("-c:a libmp3lame -b:a $bitrate -id3v2_version 3 ")
            "opus" -> command.append("-c:a libopus -b:a $bitrate ")
            "m4a" -> command.append("-c:a copy ") // M4A usually doesn't need re-encoding from YT stream
            else -> command.append("-c:a copy ")
        }

        // Metadata
        command.append("-metadata title=\"$title\" -metadata artist=\"$artist\" ")
        
        // Attachment disposition for cover art
        if (thumb?.exists() == true) {
            if (extension == "mp3") {
                command.append("-metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover (front)\" ")
            } else {
                command.append("-disposition:v attached_pic ")
            }
        }

        command.append("-y \"${output.absolutePath}\"")

        val session = FFmpegKit.execute(command.toString())
        return ReturnCode.isSuccess(session.returnCode)
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
