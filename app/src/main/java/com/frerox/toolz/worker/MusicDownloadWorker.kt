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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.Locale

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
        const val KEY_FORMAT = "format"
        const val KEY_QUALITY = "quality"
        const val KEY_PROGRESS = "progress"
        
        const val TAG_MUSIC_DOWNLOAD = "music_download"
        const val TAG_DOWNLOAD_PREFIX = "download_"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TRACK_TITLE) ?: "Unknown"
        val artist = inputData.getString(KEY_TRACK_ARTIST) ?: "Unknown"
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return@withContext Result.failure()
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL)
        val duration = inputData.getLong(KEY_DURATION, 0L)
        val format = inputData.getString(KEY_FORMAT) ?: "M4A"
        val quality = inputData.getString(KEY_QUALITY) ?: "HIGH"

        val notificationId = NOTIFICATION_ID_BASE + trackId.hashCode()
        createNotificationChannel()

        publishProgress(notificationId, "Preparing $title...", 0.02f)

        try {
            val streamUrl = catalogRepository.resolveAudioStream(sourceUrl, quality)
            publishProgress(notificationId, "Downloading $title...", 0.05f)

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_")
            val safeArtist = artist.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_")
            
            val tempFile = File(applicationContext.cacheDir, "temp_download_${trackId}.source")
            val extension = format.lowercase(Locale.US)
            val processedFile = File(applicationContext.cacheDir, "toolz_${trackId}_${System.currentTimeMillis()}.$extension")
            val thumbFile = if (!thumbnailUrl.isNullOrEmpty()) {
                File(applicationContext.cacheDir, "temp_thumb_${trackId}.jpg")
            } else null
            
            val ytdlpFile = downloadWithYtDlp(
                sourceUrl = sourceUrl,
                trackId = trackId,
                requestedFormat = extension,
                quality = quality,
                notificationId = notificationId,
                title = title
            )

            val downloadSuccess = if (ytdlpFile != null) {
                ytdlpFile.copyTo(tempFile, overwrite = true)
                ytdlpFile.delete()
                publishProgress(notificationId, "Download complete", 0.80f)
                true
            } else catalogRepository.downloadAudioStream(streamUrl, tempFile) { progress ->
                publishProgressBlocking(
                    notificationId = notificationId,
                    contentTitle = "Downloading $title...",
                    progress = 0.05f + (progress.coerceIn(0f, 1f) * 0.75f)
                )
            }

            if (!downloadSuccess) {
                showErrorNotification(notificationId, title, "Download failed")
                return@withContext Result.failure()
            }

            // Download thumbnail if available
            if (thumbFile != null && thumbnailUrl != null) {
                downloadThumbnail(thumbnailUrl, thumbFile)
            }

            publishProgress(notificationId, "Processing $format...", 0.84f)
            var success = processAudio(tempFile, thumbFile, processedFile, format, quality, title, artist)
            if (!success && thumbFile?.exists() == true) {
                android.util.Log.w("MusicDownloadWorker", "Conversion with cover failed. Retrying audio-only.")
                if (processedFile.exists()) processedFile.delete()
                success = processAudio(tempFile, null, processedFile, format, quality, title, artist)
            }

            if (success) {
                publishProgress(notificationId, "Saving $title...", 0.94f)
                val storedUri = savePlayableFile(
                    sourceFile = processedFile,
                    displayName = "$safeArtist - $safeTitle.$extension"
                ) ?: run {
                    showErrorNotification(notificationId, title, "Could not save file")
                    return@withContext Result.failure()
                }

                // P2: dedup thumbnail persist + track insert
                var localThumbUri = persistThumbnail(thumbFile, trackId, thumbnailUrl)

                if (tempFile.exists()) tempFile.delete()
                if (processedFile.exists()) processedFile.delete()
                if (thumbFile?.exists() == true) thumbFile.delete()

                android.util.Log.d("MusicDownloadWorker", "Download successful. Inserting track: $title, thumb: $localThumbUri")

                val musicTrack = MusicTrack(
                    uri = storedUri,
                    title = title,
                    artist = artist,
                    album = "Toolz Downloads",
                    duration = duration,
                    thumbnailUri = localThumbUri,
                    path = storedUri,
                    sourceUrl = sourceUrl,
                    dateAdded = System.currentTimeMillis(),
                    stableId = "${trackId}_${title.hashCode()}"
                )
                musicRepository.upsertDownloadedTrack(musicTrack)

                publishProgress(notificationId, "Download complete", 1f)
                showCompletedNotification(notificationId, title)
                Result.success()
            } else {
                android.util.Log.w("MusicDownloadWorker", "Conversion failed. Saving downloaded stream without conversion.")
                publishProgress(notificationId, "Saving original audio...", 0.94f)
                val rawExtension = rawExtensionForStream(streamUrl, extension)
                val storedUri = savePlayableFile(
                    sourceFile = tempFile,
                    displayName = "$safeArtist - $safeTitle.$rawExtension"
                ) ?: run {
                    if (tempFile.exists()) tempFile.delete()
                    if (processedFile.exists()) processedFile.delete()
                    if (thumbFile?.exists() == true) thumbFile.delete()
                    showErrorNotification(notificationId, title, "Could not save file")
                    return@withContext Result.failure()
                }

                var localThumbUri = persistThumbnail(thumbFile, trackId, thumbnailUrl)

                val musicTrack = MusicTrack(
                    uri = storedUri,
                    title = title,
                    artist = artist,
                    album = "Toolz Downloads",
                    duration = duration,
                    thumbnailUri = localThumbUri,
                    path = storedUri,
                    sourceUrl = sourceUrl,
                    dateAdded = System.currentTimeMillis(),
                    stableId = "${trackId}_${title.hashCode()}"
                )
                musicRepository.upsertDownloadedTrack(musicTrack)

                if (tempFile.exists()) tempFile.delete()
                if (processedFile.exists()) processedFile.delete()
                if (thumbFile?.exists() == true) thumbFile.delete()
                publishProgress(notificationId, "Download complete", 1f)
                showCompletedNotification(notificationId, title)
                Result.success()
            }
        } catch (e: Exception) {
            showErrorNotification(notificationId, title, e.localizedMessage ?: "Download error")
            Result.failure()
        }
    }

    private suspend fun publishProgress(notificationId: Int, contentTitle: String, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        // Always emit WorkData progress so the WorkInfo observer can read it
        setProgress(workDataOf(KEY_PROGRESS to clamped))
        // Foreground info updates the notification; wrap so a transient failure doesn't
        // prevent future setProgress calls from reaching observers.
        try {
            val progressInt = (clamped * 100).toInt()
            setForeground(createForegroundInfo(notificationId, contentTitle, progressInt))
        } catch (e: Exception) {
            android.util.Log.w("MusicDownloadWorker", "setForeground failed (non-fatal): ${e.message}")
        }
    }

    private fun publishProgressBlocking(notificationId: Int, contentTitle: String, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val progressInt = (clamped * 100).toInt()
        try {
            notificationManager.notify(notificationId, createNotification(notificationId, contentTitle, progressInt))
        } catch (e: Exception) {
            android.util.Log.w("MusicDownloadWorker", "notification update failed (non-fatal): ${e.message}")
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                setProgress(workDataOf(KEY_PROGRESS to clamped))
            } catch (e: Exception) {
                android.util.Log.w("MusicDownloadWorker", "setProgress failed (non-fatal): ${e.message}")
            }
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
        // P0-04 fix: use arg array (not shell string) to prevent title injection.
        val safeFormat = format.lowercase().let { if (it in setOf("m4a","mp3","opus")) it else "m4a" }
        val bitrate = when (quality.uppercase()) {
            "LOW" -> "96k"
            "MEDIUM" -> "160k"
            else -> "320k"
        }
        val extension = safeFormat
        
        val includeCover = thumb?.exists() == true && extension != "opus"
        val args = mutableListOf<String>()
        args.addAll(listOf("-i", input.absolutePath))
        if (includeCover) {
            args.addAll(listOf("-i", thumb!!.absolutePath))
        }

        // Map streams: audio from first input, video (cover) from second input
        if (includeCover) {
            args.addAll(listOf("-map", "0:a", "-map", "1:v"))
        } else {
            args.addAll(listOf("-map", "0:a"))
        }

        // Codec and bitrate — avoid re-encoding m4a->m4a when codec already aac
        when (extension) {
            "mp3" -> args.addAll(listOf("-c:a", "libmp3lame", "-b:a", bitrate, "-id3v2_version", "3"))
            "opus" -> args.addAll(listOf("-vn", "-c:a", "libopus", "-b:a", bitrate))
            "m4a" -> args.addAll(listOf("-c:a", "aac", "-b:a", bitrate))
            else -> args.addAll(listOf("-c:a", "copy"))
        }

        // Metadata — passed as single arg values, no shell escaping needed
        args.addAll(listOf("-metadata", "title=$title", "-metadata", "artist=$artist"))
        
        // Attachment disposition for cover art
        if (includeCover) {
            if (extension == "mp3") {
                args.addAll(listOf("-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"))
            } else {
                args.addAll(listOf("-disposition:v", "attached_pic"))
            }
        }

        args.addAll(listOf("-y", output.absolutePath))

        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        return ReturnCode.isSuccess(session.returnCode)
    }

    private fun savePlayableFile(sourceFile: File, displayName: String): String? {
        return try {
            val extension = displayName.substringAfterLast('.', "m4a")
            val mimeType = mimeTypeFor(extension)
            val resolver = applicationContext.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Toolz Downloads")
                    put(MediaStore.Audio.Media.IS_MUSIC, 1)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                try {
                    resolver.openOutputStream(uri)?.use { output ->
                        sourceFile.inputStream().use { input -> input.copyTo(output) }
                    } ?: return null
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri.toString()
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val musicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Toolz Downloads"
                ).apply { mkdirs() }
                val finalFile = File(musicDir, displayName)
                sourceFile.copyTo(finalFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(finalFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
                Uri.fromFile(finalFile).toString()
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicDownloadWorker", "Failed to save playable file", e)
            null
        }
    }

    private suspend fun downloadWithYtDlp(
        sourceUrl: String,
        trackId: String,
        requestedFormat: String,
        quality: String,
        notificationId: Int,
        title: String
    ): File? = withContext(Dispatchers.IO) {
        val outputDir = File(applicationContext.cacheDir, "yt_dlp_music").apply { mkdirs() }
        outputDir.listFiles { file -> file.name.startsWith("toolz_${trackId}_") }?.forEach { it.delete() }
        val outputTemplate = File(outputDir, "toolz_${trackId}_%(title).80B.%(ext)s").absolutePath
        return@withContext runCatching {
            val youtubeDlClass = Class.forName("com.yausername.youtubedl_android.YoutubeDL")
            val requestClass = Class.forName("com.yausername.youtubedl_android.YoutubeDLRequest")
            val request = requestClass.getConstructor(String::class.java).newInstance(sourceUrl)
            val addOption = requestClass.methods.firstOrNull {
                it.name == "addOption" &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == String::class.java &&
                    it.parameterTypes[1] == String::class.java
            } ?: return@runCatching null
            val addFlag = requestClass.methods.firstOrNull {
                it.name == "addOption" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == String::class.java
            }

            fun option(name: String, value: String) {
                addOption.invoke(request, name, value)
            }
            fun flag(name: String) {
                addFlag?.invoke(request, name)
            }

            flag("--no-playlist")
            flag("--newline")
            option("-f", "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio")
            option("-o", outputTemplate)
            if (requestedFormat in setOf("m4a", "mp3", "opus")) {
                flag("--extract-audio")
                option("--audio-format", requestedFormat)
                option("--audio-quality", when (quality.uppercase(Locale.US)) {
                    "LOW" -> "96K"
                    "MEDIUM" -> "160K"
                    else -> "0"
                })
            }

            publishProgress(notificationId, "Downloading with yt-dlp...", 0.08f)
            val youtubeDl = youtubeDlClass.getMethod("getInstance").invoke(null)
            try {
                youtubeDl.javaClass.getMethod("init", Context::class.java).invoke(youtubeDl, applicationContext)
            } catch (e: Exception) {
                android.util.Log.e("MusicDownloadWorker", "yt-dlp init failed", e)
            }

            val callbackClass = Class.forName("com.yausername.youtubedl_android.DownloadProgressCallback")
            val callback = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                if (method.name == "onProgressUpdate" && args.isNotEmpty()) {
                    val progressVal = (args[0] as? Float) ?: 0f
                    // Progress is 0.0 to 100.0. Scale it within our 0.08 to 0.80 window.
                    val normalized = 0.08f + ((progressVal / 100f) * 0.72f)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        publishProgress(notificationId, "Downloading...", normalized)
                    }
                }
                null
            }

            val executeMethod = youtubeDl.javaClass.methods.firstOrNull {
                it.name == "execute" && 
                it.parameterTypes.size == 3 && 
                it.parameterTypes[0].isAssignableFrom(requestClass) &&
                it.parameterTypes[1] == String::class.java &&
                it.parameterTypes[2] == callbackClass
            }

            if (executeMethod != null) {
                executeMethod.invoke(youtubeDl, request, trackId, callback)
            } else {
                // Fallback to 1-arg if 3-arg not found
                youtubeDl.javaClass.methods.firstOrNull {
                    it.name == "execute" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(requestClass)
                }?.invoke(youtubeDl, request) ?: return@runCatching null
            }

            publishProgress(notificationId, "yt-dlp finished", 0.80f)
            outputDir.listFiles { file -> file.name.startsWith("toolz_${trackId}_") && file.length() > 0L }
                ?.maxByOrNull { it.lastModified() }
        }.onFailure {
            android.util.Log.w("MusicDownloadWorker", "yt-dlp download failed; falling back to extractor download", it)
        }.getOrNull()
    }

    private fun rawExtensionForStream(streamUrl: String, requestedExtension: String): String {
        val decoded = runCatching { URLDecoder.decode(streamUrl, "UTF-8") }.getOrDefault(streamUrl)
        return when {
            decoded.contains("audio/mp4", ignoreCase = true) ||
                decoded.contains("mime=audio%2Fmp4", ignoreCase = true) -> "m4a"
            decoded.contains("audio/webm", ignoreCase = true) ||
                decoded.contains("mime=audio%2Fwebm", ignoreCase = true) -> "webm"
            decoded.contains("opus", ignoreCase = true) -> "opus"
            requestedExtension in setOf("m4a", "mp3", "opus") -> requestedExtension
            else -> "webm"
        }
    }

    private fun mimeTypeFor(extension: String): String = when (extension.lowercase(Locale.US)) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/ogg"
        else -> "audio/*"
    }

    private fun ffmpegEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\\$")
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

    private fun persistThumbnail(thumbFile: File?, trackId: String, fallbackUrl: String?): String? {
        if (thumbFile?.exists() == true) {
            return try {
                val thumbDir = File(applicationContext.filesDir, "thumbnails")
                if (!thumbDir.exists()) thumbDir.mkdirs()
                val persistentThumb = File(thumbDir, "${trackId}.jpg")
                thumbFile.copyTo(persistentThumb, overwrite = true)
                Uri.fromFile(persistentThumb).toString()
            } catch (e: Exception) {
                android.util.Log.e("MusicDownloadWorker", "Failed to save local thumb", e)
                fallbackUrl
            }
        }
        return fallbackUrl
    }
}
