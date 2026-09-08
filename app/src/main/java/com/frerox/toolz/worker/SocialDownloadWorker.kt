/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.frerox.toolz.BuildConfig
import com.frerox.toolz.data.downloader.DownloadzClient
import com.frerox.toolz.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads TikTok / Instagram / remote-YouTube media via toolz-downloadz-api.
 *
 * Critical: media CDN URLs are IP-signed to the extractor, so Android must NOT fetch
 * `format.url` directly (would 403). Instead it streams the api's same-instance
 * downloader: GET {base}/api/download?u={pageUrl}&f={formatId}&n={fileName}&key={apiKey}
 * which resolves + streams server-side (see api/index.py download()).
 */
@HiltWorker
class SocialDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    @DownloadzClient private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG_SOCIAL_DOWNLOAD = "toolz_social_download"
        const val KEY_PAGE_URL = "page_url"
        const val KEY_FORMAT_ID = "format_id"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_TITLE = "title"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_IS_AUDIO = "is_audio"
        const val KEY_PROGRESS = "progress"
        /** Output Data keys on success — shared values across all download workers. */
        const val KEY_FILE_URI = "file_uri"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_MIME_TYPE = "mime_type"
        const val CHANNEL_ID = NotificationHelper.CHANNEL_VIDEO_DOWNLOADS
        const val NOTIFICATION_ID_BASE = 3000
    }

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pageUrl = inputData.getString(KEY_PAGE_URL) ?: return@withContext Result.failure()
        val formatId = inputData.getString(KEY_FORMAT_ID)?.ifBlank { "best" } ?: "best"
        val title = inputData.getString(KEY_TITLE) ?: "media"
        val fileName = inputData.getString(KEY_FILE_NAME)?.ifBlank { "$title.mp4" } ?: "$title.mp4"
        val isAudio = inputData.getBoolean(KEY_IS_AUDIO, false)
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-.]"), "_").take(80)
        val notificationId = NOTIFICATION_ID_BASE + (pageUrl.hashCode() and 0x7fffffff) % 10000

        try {
            createNotificationChannel()
            publishProgress(notificationId, "Preparing $safeTitle...", 0.05f)

            val progressChannel = Channel<Float>(Channel.CONFLATED)
            val progressJob = launch {
                for (norm in progressChannel) {
                    try { setProgress(workDataOf(KEY_PROGRESS to norm)) } catch (_: Exception) {}
                }
            }

            val base = BuildConfig.DOWNLOADZ_API_URL.trim().trimEnd('/')
            val apiKey = BuildConfig.DOWNLOADZ_API_KEY
            if (base.isBlank() || apiKey.isBlank()) {
                progressChannel.close(); progressJob.cancel()
                showErrorNotification(notificationId, safeTitle, "Download server not configured")
                return@withContext Result.failure()
            }
            val downloadUrl = "$base/api/download?" +
                "u=${java.net.URLEncoder.encode(pageUrl, "UTF-8")}" +
                "&f=${java.net.URLEncoder.encode(formatId, "UTF-8")}" +
                "&n=${java.net.URLEncoder.encode(fileName, "UTF-8")}" +
                "&key=${java.net.URLEncoder.encode(apiKey, "UTF-8")}"

            publishProgress(notificationId, "Downloading $safeTitle...", 0.08f)
            val ext = if (isAudio) fileName.substringAfterLast('.', "mp3") else fileName.substringAfterLast('.', "mp4")
            val tmp = File(applicationContext.cacheDir, "social_${System.currentTimeMillis()}.$ext")

            val ok = downloadUrlToFile(downloadUrl, tmp) { progress ->
                val normalized = 0.08f + (progress.coerceIn(0f, 1f) * 0.80f)
                val progressInt = (normalized * 100).toInt()
                notificationManager.notify(notificationId, createNotification(notificationId, "Downloading $safeTitle...", progressInt))
                progressChannel.trySend(normalized)
            }

            progressChannel.close()
            progressJob.cancel()

            if (!ok || !tmp.exists() || tmp.length() < 1024) {
                try { tmp.delete() } catch (_: Exception) {}
                showErrorNotification(notificationId, safeTitle, "Server refused the download")
                return@withContext Result.failure()
            }

            publishProgress(notificationId, "Saving file...", 0.92f)
            val mime = if (isAudio) "audio/mpeg" else "video/mp4"
            val savedUri = if (isAudio) {
                saveToMusic(tmp, fileName)
            } else {
                saveToMovies(tmp, fileName)
            }
            try { tmp.delete() } catch (_: Exception) {}

            if (savedUri != null) {
                publishProgress(notificationId, "Download complete", 1.0f)
                showCompletedNotification(notificationId, safeTitle, isAudio)
                Result.success(
                    workDataOf(
                        KEY_FILE_URI to savedUri,
                        KEY_DISPLAY_NAME to fileName,
                        KEY_MIME_TYPE to mime,
                    )
                )
            } else {
                showErrorNotification(notificationId, safeTitle, "Could not save file to gallery")
                Result.failure()
            }
        } catch (e: Exception) {
            android.util.Log.e("SocialDownloadWorker", "Failure downloading $pageUrl", e)
            showErrorNotification(notificationId, safeTitle, e.message ?: "Download failed")
            Result.failure()
        }
    }

    private suspend fun downloadUrlToFile(
        url: String,
        out: File,
        onProgress: suspend (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", "Toolz/1.0 (Android; Media Downloader)")
                .header("Accept", "*/*")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                android.util.Log.w("SocialDownloadWorker", "HTTP ${resp.code} for download")
                resp.close()
                return@withContext false
            }
            val body = resp.body ?: run { resp.close(); return@withContext false }
            val total = body.contentLength()
            var done = 0L
            var lastEmit = 0L
            var synthetic = 0.05f
            withContext(Dispatchers.Main) { onProgress(synthetic) }
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 200) {
                            lastEmit = now
                            val p = if (total > 0) {
                                (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                synthetic = (synthetic + 0.02f).coerceAtMost(0.9f)
                                synthetic
                            }
                            withContext(Dispatchers.Main) { onProgress(p) }
                        }
                    }
                }
            }
            resp.close()
            withContext(Dispatchers.Main) { onProgress(1f) }
            out.exists() && out.length() > 1024
        } catch (e: Exception) {
            android.util.Log.w("SocialDownloadWorker", "download failed: ${e.message}")
            try { out.delete() } catch (_: Exception) {}
            false
        }
    }

    private fun saveToMovies(source: File, displayName: String): String? = try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Toolz")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return null
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
    } catch (e: Exception) {
        android.util.Log.e("SocialDownloadWorker", "MediaStore save failed", e)
        null
    }

    private fun saveToMusic(source: File, displayName: String): String? = try {
        val resolver = applicationContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Toolz Downloads")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return null
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } catch (e: Exception) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                throw e
            }
        } else {
            val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Toolz Downloads").apply { mkdirs() }
            val finalFile = File(musicDir, displayName)
            source.copyTo(finalFile, overwrite = true)
            android.media.MediaScannerConnection.scanFile(applicationContext, arrayOf(finalFile.absolutePath), arrayOf("audio/mpeg"), null)
            android.net.Uri.fromFile(finalFile).toString()
        }
    } catch (e: Exception) {
        android.util.Log.e("SocialDownloadWorker", "MediaStore mp3 save failed", e)
        null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Video Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Social media downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun publishProgress(notificationId: Int, contentTitle: String, progress: Float) {
        val progressInt = (progress.coerceIn(0f, 1f) * 100).toInt()
        try {
            val notification = createNotification(notificationId, contentTitle, progressInt)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setForeground(ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            } else {
                setForeground(ForegroundInfo(notificationId, notification))
            }
        } catch (_: Exception) {
            try {
                notificationManager.notify(notificationId, createNotification(notificationId, contentTitle, progressInt))
            } catch (_: Exception) {}
        }
    }

    private fun createNotification(id: Int, contentTitle: String, progress: Int): android.app.Notification {
        val cancelIntent = Intent(applicationContext, DownloadCancelReceiver::class.java).apply {
            putExtra("work_id", id.toString())
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext, id.hashCode(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(if (progress in 1..99) "$progress% • Toolz" else "")
            .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
            .setLargeIcon(NotificationHelper.toolzLargeIcon(applicationContext))
            .setOngoing(progress in 1..99)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun showCompletedNotification(id: Int, title: String, isAudio: Boolean = false) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("✓ Download complete")
            .setContentText("$title • Toolz")
            .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
            .setLargeIcon(NotificationHelper.toolzLargeIcon(applicationContext))
            .setAutoCancel(true)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { notificationManager.notify(id, notification) } catch (_: Exception) {}
    }

    private fun showErrorNotification(id: Int, title: String, error: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Download failed: $title")
            .setContentText(error.take(80))
            .setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
            .setLargeIcon(NotificationHelper.toolzLargeIcon(applicationContext))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { notificationManager.notify(id, notification) } catch (_: Exception) {}
    }
}
