/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.music.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Dedicated MP3 downloader for YouTube videos — reuses CatalogRepository pattern that powers
 * the working Music Catalog downloader (InnerTube → NewPipe → OkHttp direct + FFmpeg).
 * Saves to Music/Toolz Downloads with progress notifications.
 */
@HiltWorker
class YouTubeMp3DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val catalogRepository: CatalogRepository,
    private val musicRepository: MusicRepository,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG_MP3_DOWNLOAD = "toolz_mp3_download"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_TITLE = "title"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_PROGRESS = "progress"
        const val CHANNEL_ID = "music_downloads"
        const val NOTIFICATION_ID_BASE = 3000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "audio"
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL)
        val notificationId = NOTIFICATION_ID_BASE + (title.hashCode() and 0x7fffffff) % 10000

        try {
            createNotificationChannel()
            publishProgress(notificationId, "Preparing MP3...", 0.02f)

            // Delegate to VideoDownloadWorker's MP3 path via catalog fallback: resolve audio stream + download + FFmpeg if needed
            // For simplicity, use same logic as MusicDownloadWorker but with video sourceUrl
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_").take(80)
            val trackId = "mp3_${title.hashCode()}"

            // Try yt-dlp via reflection first (like Music worker), then fallback to CatalogRepository
            val ytdlpFile = downloadWithYtDlp(sourceUrl, trackId, notificationId, title)
            val tempFile = java.io.File(applicationContext.cacheDir, "temp_mp3_${trackId}.source")
            val success = if (ytdlpFile != null) {
                ytdlpFile.copyTo(tempFile, overwrite = true)
                ytdlpFile.delete()
                publishProgress(notificationId, "Download complete", 0.80f)
                true
            } else {
                // Fallback via CatalogRepository (same as Music Catalog — InnerTube/NewPipe)
                val streamUrl = catalogRepository.resolveAudioStream(sourceUrl, "HIGH")
                publishProgress(notificationId, "Downloading MP3...", 0.05f)
                catalogRepository.downloadAudioStream(streamUrl, tempFile) { progress ->
                    val normalized = 0.05f + (progress.coerceIn(0f, 1f) * 0.75f)
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            publishProgress(notificationId, "Downloading MP3...", normalized)
                        }
                    } catch (_: Exception) {}
                }
            }

            if (!success || !tempFile.exists() || tempFile.length() < 1024) {
                showErrorNotification(notificationId, title, "MP3 download failed")
                return@withContext Result.failure()
            }

            // Thumbnail
            val thumbFile = if (!thumbnailUrl.isNullOrEmpty()) {
                val f = java.io.File(applicationContext.cacheDir, "temp_thumb_mp3_${trackId}.jpg")
                try {
                    val req = okhttp3.Request.Builder().url(thumbnailUrl).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.byteStream()?.use { input ->
                            java.io.FileOutputStream(f).use { out -> input.copyTo(out) }
                        }
                    }
                } catch (_: Exception) {}
                if (f.exists() && f.length() > 0) f else null
            } else null

            publishProgress(notificationId, "Processing MP3...", 0.84f)
            val processedFile = java.io.File(applicationContext.cacheDir, "toolz_${trackId}_${System.currentTimeMillis()}.mp3")
            var ok = processAudio(tempFile, thumbFile, processedFile, title, "Unknown")
            if (!ok && thumbFile?.exists() == true) {
                if (processedFile.exists()) processedFile.delete()
                ok = processAudio(tempFile, null, processedFile, title, "Unknown")
            }
            val finalFile = if (ok) processedFile else tempFile
            if (!finalFile.exists() || finalFile.length() < 1024) {
                showErrorNotification(notificationId, title, "Processing failed")
                return@withContext Result.failure()
            }

            publishProgress(notificationId, "Saving MP3...", 0.94f)
            val storedUri = saveToMusic(finalFile, "$safeTitle.mp3") ?: run {
                showErrorNotification(notificationId, title, "Could not save")
                return@withContext Result.failure()
            }

            if (tempFile.exists()) tempFile.delete()
            if (processedFile.exists() && processedFile != finalFile) try { processedFile.delete() } catch (_: Exception) {}
            if (thumbFile?.exists() == true) thumbFile.delete()

            val musicTrack = com.frerox.toolz.data.music.MusicTrack(
                uri = storedUri,
                title = title,
                artist = "YouTube",
                album = "Toolz Downloads",
                duration = 0L,
                thumbnailUri = thumbnailUrl,
                path = storedUri,
                sourceUrl = sourceUrl,
                dateAdded = System.currentTimeMillis(),
                stableId = "${trackId}_${title.hashCode()}"
            )
            musicRepository.upsertDownloadedTrack(musicTrack)

            publishProgress(notificationId, "MP3 complete", 1f)
            showCompletedNotification(notificationId, title)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("YouTubeMp3DownloadWorker", "MP3 failed", e)
            try { showErrorNotification(notificationId, title, e.message ?: "Error") } catch (_: Exception) {}
            Result.failure()
        }
    }

    private fun processAudio(input: java.io.File, thumb: java.io.File?, output: java.io.File, title: String, artist: String): Boolean {
        return try {
            val args = mutableListOf<String>()
            args.addAll(listOf("-i", input.absolutePath))
            if (thumb?.exists() == true) args.addAll(listOf("-i", thumb.absolutePath))
            if (thumb?.exists() == true) args.addAll(listOf("-map", "0:a", "-map", "1:v")) else args.addAll(listOf("-map", "0:a"))
            args.addAll(listOf("-c:a", "libmp3lame", "-b:a", "320k", "-id3v2_version", "3"))
            args.addAll(listOf("-metadata", "title=$title", "-metadata", "artist=$artist"))
            if (thumb?.exists() == true) args.addAll(listOf("-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"))
            args.addAll(listOf("-y", output.absolutePath))
            val session = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(args.toTypedArray())
            com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode)
        } catch (_: Exception) { false }
    }

    private fun saveToMusic(source: java.io.File, displayName: String): String? = try {
        val resolver = applicationContext.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_MUSIC}/Toolz Downloads")
                put(android.provider.MediaStore.Audio.Media.IS_MUSIC, 1)
                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: return null
                values.clear()
                values.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } catch (e: Exception) { try { resolver.delete(uri, null, null) } catch (_: Exception) {}; throw e }
        } else {
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "Toolz Downloads").apply { mkdirs() }
            val f = java.io.File(dir, displayName)
            source.copyTo(f, overwrite = true)
            android.media.MediaScannerConnection.scanFile(applicationContext, arrayOf(f.absolutePath), arrayOf("audio/mpeg"), null)
            android.net.Uri.fromFile(f).toString()
        }
    } catch (e: Exception) { android.util.Log.e("YouTubeMp3DownloadWorker", "save failed", e); null }

    private suspend fun downloadWithYtDlp(sourceUrl: String, trackId: String, notificationId: Int, title: String): java.io.File? = withContext(Dispatchers.IO) {
        val outDir = java.io.File(applicationContext.cacheDir, "yt_dlp_music").apply { mkdirs() }
        outDir.listFiles { f -> f.name.startsWith("toolz_${trackId}_") }?.forEach { it.delete() }
        val tmpl = java.io.File(outDir, "toolz_${trackId}_%(title).80B.%(ext)s").absolutePath
        return@withContext runCatching {
            val ytClass = Class.forName("com.yausername.youtubedl_android.YoutubeDL")
            val reqClass = Class.forName("com.yausername.youtubedl_android.YoutubeDLRequest")
            val req = reqClass.getConstructor(String::class.java).newInstance(sourceUrl)
            val addOpt = reqClass.methods.firstOrNull { it.name == "addOption" && it.parameterTypes.size == 2 } ?: return@runCatching null
            val addFlag = reqClass.methods.firstOrNull { it.name == "addOption" && it.parameterTypes.size == 1 }
            fun opt(n: String, v: String) { addOpt.invoke(req, n, v) }
            fun fl(n: String) { addFlag?.invoke(req, n) }
            fl("--no-playlist"); fl("--newline")
            opt("-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio")
            opt("-o", tmpl)
            fl("--extract-audio"); opt("--audio-format", "mp3"); opt("--audio-quality", "0")
            publishProgress(notificationId, "Downloading with yt-dlp...", 0.08f)
            val ytdl = ytClass.getMethod("getInstance").invoke(null)
            try { ytdl.javaClass.getMethod("init", Context::class.java).invoke(ytdl, applicationContext) } catch (e: Exception) { android.util.Log.e("YouTubeMp3DownloadWorker", "init failed", e) }
            val cbClass = Class.forName("com.yausername.youtubedl_android.DownloadProgressCallback")
            val cb = java.lang.reflect.Proxy.newProxyInstance(cbClass.classLoader, arrayOf(cbClass)) { _, m, args ->
                if (m.name == "onProgressUpdate" && args.isNotEmpty()) {
                    val p = (args[0] as? Float) ?: 0f
                    val norm = 0.08f + (p / 100f * 0.72f)
                    try {
                        CoroutineScope(Dispatchers.IO).launch { publishProgress(notificationId, "Downloading...", norm) }
                    } catch (_: Exception) {}
                }; null
            }
            val exec3 = ytdl.javaClass.methods.firstOrNull { it.name == "execute" && it.parameterTypes.size == 3 && it.parameterTypes[0].isAssignableFrom(reqClass) }
            if (exec3 != null) exec3.invoke(ytdl, req, trackId, cb) else ytdl.javaClass.methods.firstOrNull { it.name == "execute" && it.parameterTypes.size == 1 }?.invoke(ytdl, req) ?: return@runCatching null
            publishProgress(notificationId, "yt-dlp finished", 0.80f)
            outDir.listFiles { f -> f.name.startsWith("toolz_${trackId}_") && f.length() > 0 }?.maxByOrNull { it.lastModified() }
        }.onFailure { android.util.Log.w("YouTubeMp3DownloadWorker", "ytdlp failed", it) }.getOrNull()
    }

    private suspend fun publishProgress(id: Int, title: String, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        try { setProgress(workDataOf(KEY_PROGRESS to clamped)) } catch (_: Exception) {}
        try { setForeground(createForegroundInfo(id, title, (clamped * 100).toInt())) } catch (e: Exception) {
            android.util.Log.w("YouTubeMp3DownloadWorker", "foreground failed ${e.message}")
            try { notificationManager.notify(id, createNotification(id, title, (clamped * 100).toInt())) } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "MP3 Downloads", NotificationManager.IMPORTANCE_LOW).apply { description = "YouTube MP3 downloads" }
            notificationManager.createNotificationChannel(ch)
        }
    }

    private fun createForegroundInfo(id: Int, title: String, progress: Int): ForegroundInfo {
        val n = createNotification(id, title, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(id, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    private fun createNotification(id: Int, title: String, progress: Int): android.app.Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title).setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground)
            .setOngoing(progress in 1..99).setProgress(100, progress.coerceIn(0, 100), progress == 0).setSilent(true).build()
    }

    private fun showCompletedNotification(id: Int, title: String) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID).setContentTitle("✓ MP3 Download complete").setContentText(title).setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground).setAutoCancel(true).build()
        try { notificationManager.notify(id, n) } catch (_: Exception) {}
    }

    private fun showErrorNotification(id: Int, title: String, err: String) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID).setContentTitle("MP3 Download failed: $title").setContentText(err.take(80)).setSmallIcon(com.frerox.toolz.R.drawable.ic_launcher_foreground).setAutoCancel(true).build()
        try { notificationManager.notify(id, n) } catch (_: Exception) {}
    }
}
