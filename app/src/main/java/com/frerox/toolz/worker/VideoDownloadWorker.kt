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
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.util.NotificationHelper
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * YouTube video downloader with live progress notifications.
 * Uses CatalogRepository (InnerTube + NewPipeExtractor) for high-speed direct stream downloads,
 * with optional yt-dlp fallback. Saves MP4 videos to Movies/Toolz and MP3s to Music/Toolz Downloads.
 */
@HiltWorker
class VideoDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val catalogRepository: CatalogRepository,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG_VIDEO_DOWNLOAD = "toolz_video_download"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_TITLE = "title"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_QUALITY = "quality"
        const val KEY_PROGRESS = "progress"
        const val CHANNEL_ID = NotificationHelper.CHANNEL_VIDEO_DOWNLOADS
        const val NOTIFICATION_ID_BASE = 2000
    }

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "video"
        val quality = inputData.getString(KEY_QUALITY) ?: "720p"
        val isMp3 = quality.equals("MP3", ignoreCase = true) || quality.equals("AUDIO", ignoreCase = true)
        val maxHeight = quality.replace("p", "", ignoreCase = true).toIntOrNull() ?: 720
        val notificationId = NOTIFICATION_ID_BASE + (title.hashCode() and 0x7fffffff) % 10000
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_").take(80)

        try {
            createNotificationChannel()
            publishProgress(notificationId, "Preparing $safeTitle...", 0.05f)

            val progressChannel = Channel<Float>(Channel.CONFLATED)
            val progressJob = launch {
                for (norm in progressChannel) {
                    try { setProgress(workDataOf(KEY_PROGRESS to norm)) } catch (_: Exception) {}
                }
            }

            var downloaded: File? = null

            // Primary: High-speed direct stream resolution via CatalogRepository
            val directStreamUrl = try {
                if (isMp3) {
                    catalogRepository.resolveAudioStream(sourceUrl, "HIGH")
                } else {
                    catalogRepository.resolveVideoStream(sourceUrl, maxHeight)
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoDownloadWorker", "Catalog stream resolution failed, will try fallback", e)
                null
            }

            if (!directStreamUrl.isNullOrBlank()) {
                val ext = if (isMp3) "mp3" else "mp4"
                val tempFile = File(applicationContext.cacheDir, "toolz_dl_${System.currentTimeMillis()}.$ext")
                publishProgress(notificationId, "Downloading $safeTitle ($quality)...", 0.08f)
                val ok = catalogRepository.downloadAudioStream(directStreamUrl, tempFile) { progress ->
                    val normalized = 0.08f + (progress.coerceIn(0f, 1f) * 0.77f)
                    val progressInt = (normalized * 100).toInt()
                    notificationManager.notify(notificationId, createNotification(notificationId, "Downloading $safeTitle...", progressInt))
                    progressChannel.trySend(normalized)
                }
                if (ok && tempFile.exists() && tempFile.length() > 1024) {
                    downloaded = tempFile
                    android.util.Log.i("VideoDownloadWorker", "Direct stream download succeeded: ${tempFile.length()} bytes")
                } else {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }

            // Secondary fallback: yt-dlp reflection if direct resolution was not available
            if (downloaded == null || downloaded.length() < 1024) {
                android.util.Log.w("VideoDownloadWorker", "Attempting secondary fallback for $sourceUrl")
                val outputDir = if (isMp3) File(applicationContext.cacheDir, "yt_dlp_mp3") else File(applicationContext.cacheDir, "yt_dlp_video")
                outputDir.mkdirs()
                val outputTemplate = File(outputDir, "toolz_dl_${System.currentTimeMillis()}_.%(ext)s").absolutePath

                val ytdlpResult = runCatching {
                    val youtubeDlClass = Class.forName("com.yausername.youtubedl_android.YoutubeDL")
                    val requestClass = Class.forName("com.yausername.youtubedl_android.YoutubeDLRequest")
                    val request = requestClass.getConstructor(String::class.java).newInstance(sourceUrl)
                    val addOption = requestClass.methods.firstOrNull {
                        it.name == "addOption" && it.parameterTypes.size == 2 &&
                            it.parameterTypes[0] == String::class.java && it.parameterTypes[1] == String::class.java
                    } ?: throw IllegalStateException("addOption not found")
                    val addFlag = requestClass.methods.firstOrNull {
                        it.name == "addOption" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
                    }

                    if (isMp3) {
                        addFlag?.invoke(request, "--no-playlist")
                        addOption.invoke(request, "-f", "bestaudio[ext=m4a]/bestaudio")
                        addOption.invoke(request, "-o", outputTemplate)
                    } else {
                        val format = "best[height<=$maxHeight][ext=mp4]/best[height<=$maxHeight]/best"
                        addOption.invoke(request, "-f", format)
                        addOption.invoke(request, "-o", outputTemplate)
                        addFlag?.invoke(request, "--no-playlist")
                    }

                    val youtubeDl = youtubeDlClass.getMethod("getInstance").invoke(null)
                    try {
                        youtubeDl.javaClass.getMethod("init", Context::class.java).invoke(youtubeDl, applicationContext)
                    } catch (_: Exception) {}

                    val exec = youtubeDl.javaClass.methods.firstOrNull { it.name == "execute" && it.parameterTypes.size >= 1 }
                    exec?.invoke(youtubeDl, request)

                    val now = System.currentTimeMillis()
                    outputDir.listFiles()?.filter { it.length() > 1024 && (now - it.lastModified()) < 120_000 }?.maxByOrNull { it.lastModified() }
                }

                if (ytdlpResult.isSuccess && ytdlpResult.getOrNull() != null) {
                    downloaded = ytdlpResult.getOrNull()
                } else {
                    // Try direct stream fetch from OkHttp fallback
                    downloaded = tryFallbackDirectDownload(sourceUrl, isMp3, maxHeight, notificationId, safeTitle)
                }
            }

            progressChannel.close()
            progressJob.cancel()

            if (downloaded == null || downloaded.length() < 1024) {
                showErrorNotification(notificationId, safeTitle, "Could not resolve or download stream")
                downloaded?.delete()
                return@withContext Result.failure()
            }

            publishProgress(notificationId, "Saving file...", 0.92f)
            val saved = if (isMp3) {
                saveToMusic(downloaded, "$safeTitle.mp3", safeTitle)
            } else {
                saveToMovies(downloaded, "$safeTitle.mp4")
            }

            try { downloaded.delete() } catch (_: Exception) {}

            if (saved) {
                publishProgress(notificationId, "Download complete", 1.0f)
                showCompletedNotification(notificationId, safeTitle, isMp3)
                Result.success()
            } else {
                showErrorNotification(notificationId, safeTitle, "Could not save file to gallery")
                Result.failure()
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoDownloadWorker", "Failure downloading $sourceUrl", e)
            showErrorNotification(notificationId, safeTitle, e.message ?: "Download failed")
            Result.failure()
        }
    }

    private suspend fun tryFallbackDirectDownload(sourceUrl: String, isMp3: Boolean, maxHeight: Int, notificationId: Int, safeTitle: String): File? = withContext(Dispatchers.IO) {
        return@withContext try {
            val youtubeDlClass = Class.forName("com.yausername.youtubedl_android.YoutubeDL")
            val requestClass = Class.forName("com.yausername.youtubedl_android.YoutubeDLRequest")
            val request = requestClass.getConstructor(String::class.java).newInstance(sourceUrl)
            val addOption = requestClass.methods.firstOrNull { it.name == "addOption" && it.parameterTypes.size == 2 } ?: return@withContext null
            val addFlag = requestClass.methods.firstOrNull { it.name == "addOption" && it.parameterTypes.size == 1 }
            try { addFlag?.invoke(request, "--no-playlist") } catch (_: Exception) {}
            val fmt = if (isMp3) "bestaudio[ext=m4a]/bestaudio" else "best[height<=$maxHeight][ext=mp4]/best[height<=$maxHeight]/best"
            addOption.invoke(request, "-f", fmt)
            try { addOption.invoke(request, "--print", "urls") } catch (_: Exception) {
                try { addFlag?.invoke(request, "-g") ?: addOption.invoke(request, "-g", "") } catch (_: Exception) {}
            }
            val youtubeDl = youtubeDlClass.getMethod("getInstance").invoke(null)
            try { youtubeDl.javaClass.getMethod("init", Context::class.java).invoke(youtubeDl, applicationContext) } catch (_: Exception) {}
            val exec1 = youtubeDl.javaClass.methods.firstOrNull { it.name == "execute" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(requestClass) }
                ?: youtubeDl.javaClass.methods.firstOrNull { it.name == "execute" && it.parameterTypes.size == 2 } ?: return@withContext null
            val response = try {
                if (exec1.parameterTypes.size == 1) exec1.invoke(youtubeDl, request) else exec1.invoke(youtubeDl, request, "fallback_${safeTitle.hashCode()}")
            } catch (e: Exception) {
                return@withContext null
            }
            val out = try {
                response?.javaClass?.getMethod("getOut")?.invoke(response) as? String ?: response?.toString()
            } catch (_: Exception) { response?.toString() } ?: return@withContext null
            val url = out.lineSequence().firstOrNull { it.trim().startsWith("http") && it.contains("googlevideo") }
                ?: out.lineSequence().firstOrNull { it.trim().startsWith("http") }
                ?: return@withContext null
            val cleanUrl = url.trim()

            val client = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true).followSslRedirects(true).build()
            val req = Request.Builder().url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Referer", "https://www.youtube.com/")
                .header("Origin", "https://www.youtube.com")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) { resp.close(); return@withContext null }
            val ext = if (isMp3) "mp3" else "mp4"
            val tmp = File(applicationContext.cacheDir, "fallback_${System.currentTimeMillis()}.$ext")
            val body = resp.body ?: run { resp.close(); return@withContext null }
            body.byteStream().use { input ->
                java.io.FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
            }
            resp.close()
            if (tmp.length() > 1024) tmp else null
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToMovies(source: File, displayName: String): Boolean = try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Toolz")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return false
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoDownloadWorker", "MediaStore save failed", e)
        false
    }

    private fun saveToMusic(source: File, displayName: String, title: String): Boolean = try {
        val resolver = applicationContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Toolz Downloads")
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return false
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } catch (e: Exception) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                throw e
            }
        } else {
            val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Toolz Downloads").apply { mkdirs() }
            val finalFile = File(musicDir, displayName)
            source.copyTo(finalFile, overwrite = true)
            android.media.MediaScannerConnection.scanFile(applicationContext, arrayOf(finalFile.absolutePath), arrayOf("audio/mpeg"), null)
            true
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoDownloadWorker", "MediaStore mp3 save failed", e)
        false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Video Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "YouTube video downloads"
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
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
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
        return builder.build()
    }

    private fun showCompletedNotification(id: Int, title: String, isMp3: Boolean = false) {
        val text = if (isMp3) "$title • MP3 • Toolz" else "$title • Toolz"
        val titleText = if (isMp3) "✓ MP3 Download complete" else "✓ Download complete"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(text)
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
