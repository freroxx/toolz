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
import com.frerox.toolz.util.VideoQualityPolicy
import com.frerox.toolz.util.YtVideoMerge
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
        /** Output Data keys on success — shared values across all download workers. */
        const val KEY_FILE_URI = "file_uri"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_MIME_TYPE = "mime_type"
        /** Honest-quality keys: requested vs actually saved height (-1 = unknown, e.g. yt-dlp). */
        const val KEY_REQUESTED_QUALITY = "requested_quality"
        const val KEY_ACTUAL_HEIGHT = "actual_height"
        /** Failure reason for UI banners (Result.failure carries no message otherwise). */
        const val KEY_ERROR = "error"
        const val CHANNEL_ID = NotificationHelper.CHANNEL_VIDEO_DOWNLOADS
        const val NOTIFICATION_ID_BASE = NotificationHelper.ID_VIDEO_BASE
    }

    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Volatile private var lastFgAt = 0L
    @Volatile private var lastFgPct = -1

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sourceUrl = inputData.getString(KEY_SOURCE_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "video"
        val quality = inputData.getString(KEY_QUALITY) ?: "720p"
        val isMp3 = quality.equals("MP3", ignoreCase = true) || quality.equals("AUDIO", ignoreCase = true)
        val maxHeight = quality.replace("p", "", ignoreCase = true).toIntOrNull() ?: 720
        val notificationId = NotificationHelper.downloadId(NOTIFICATION_ID_BASE, "$sourceUrl|$quality")
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9 \\-\\.]"), "_").take(80)

        try {
            createNotificationChannel()
            publishProgress(notificationId, "Preparing $safeTitle...", 0.05f)

            val progressChannel = Channel<Float>(Channel.CONFLATED)
            val progressJob = launch {
                for (norm in progressChannel) {
                    try { setProgress(workDataOf(KEY_PROGRESS to norm)) } catch (_: Exception) {}
                    // Single throttled foreground owner for chunk/yt-dlp callbacks.
                    val pct = (norm.coerceIn(0f, 1f) * 100).toInt()
                    val now = System.currentTimeMillis()
                    if (!NotificationHelper.shouldPublishProgress(lastFgAt, lastFgPct, now, pct)) continue
                    lastFgAt = now
                    lastFgPct = pct
                    try {
                        val n = createNotification(notificationId, "Downloading $safeTitle ($quality)", pct)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setForeground(ForegroundInfo(notificationId, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                        } else {
                            setForeground(ForegroundInfo(notificationId, n))
                        }
                    } catch (_: Exception) {}
                }
            }

            var downloaded: File? = null
            var actualHeight: Int? = null
            // Muxed/progressive streams cap at 360p on most videos (itag 22 gone),
            // so any request >= 480p must try the DASH video+audio merge FIRST.
            // Previously only >720p did, letting 720p silently save a 360p file.
            val wantsHdMerge = !isMp3 && maxHeight >= 480

            suspend fun downloadHdPair(ceiling: Int, label: String): Boolean {
                publishProgress(notificationId, "Preparing HD $safeTitle ($label)...", 0.06f)
                return try {
                    val pair = catalogRepository.resolveHdVideoPair(sourceUrl, ceiling)
                    if (pair == null) {
                        android.util.Log.w("VideoDownloadWorker", "No DASH pair <= ${ceiling}p for $sourceUrl")
                        return false
                    }
                    // Reject pairs far below the request — a 144p DASH for a 1080p
                    // request is not "HD"; let yt-dlp try instead of mislabeling.
                    // Accept if >= ~50% of requested height or >= 480p for HD asks.
                    val acceptable = VideoQualityPolicy.isHdPairAcceptable(pair.height, ceiling)
                    if (!acceptable) {
                        android.util.Log.w("VideoDownloadWorker", "DASH pair ${pair.height}p too low for $label request — trying yt-dlp instead")
                        return false
                    }
                    val hdOut = File(applicationContext.cacheDir, "toolz_hd_${System.currentTimeMillis()}.mp4")
                    publishProgress(notificationId, "Downloading HD $safeTitle (${pair.height}p)...", 0.08f)
                    val muxed = YtVideoMerge.downloadAndMux(
                        applicationContext, okHttpClient,
                        pair.videoUrl, pair.audioUrl,
                        File(applicationContext.cacheDir, "hd_merge"), hdOut,
                    ) { progress ->
                        val normalized = 0.08f + (progress.coerceIn(0f, 1f) * 0.77f)
                        progressChannel.trySend(normalized)
                    }
                    if (muxed && hdOut.exists() && hdOut.length() > 1024) {
                        // Drop any previous low-res file before promoting HD.
                        try { downloaded?.delete() } catch (_: Exception) {}
                        downloaded = hdOut
                        actualHeight = pair.height
                        android.util.Log.i("VideoDownloadWorker", "HD merge succeeded (${pair.height}p for $label): ${hdOut.length()} bytes")
                        true
                    } else {
                        try { hdOut.delete() } catch (_: Exception) {}
                        false
                    }
                } catch (e: Exception) {
                    android.util.Log.w("VideoDownloadWorker", "HD merge path failed, falling back", e)
                    false
                }
            }

            // HD path first for 480p+ requests: muxed streams cap at 360p, so a direct
            // resolve would silently return the wrong (lower) quality. Resolve the DASH
            // video+audio pair and mux on-device for true HD with audio.
            if (wantsHdMerge) {
                downloadHdPair(maxHeight, quality)
            }

            // Primary: High-speed direct stream resolution via CatalogRepository.
            // For HD requests this is only a fallback when DASH merge failed, and a
            // low muxed result is HELD (not saved) until yt-dlp has had its chance —
            // otherwise a 360p muxed would shadow a real 720p/1080p yt-dlp result.
            var heldLowRes: File? = null
            var heldLowHeight: Int? = null
            val directStream = try {
                if (isMp3) {
                    catalogRepository.resolveAudioStream(sourceUrl, "HIGH")?.let { CatalogRepository.VideoStream(it, 0) }
                } else {
                    catalogRepository.resolveVideoStream(sourceUrl, maxHeight)
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoDownloadWorker", "Catalog stream resolution failed, will try fallback", e)
                null
            }

            if (!directStream?.url.isNullOrBlank()) {
                val directHeight = directStream!!.height
                val isAcceptable = VideoQualityPolicy.isDirectAcceptable(directHeight, maxHeight, isMp3) ||
                    downloaded != null // HD already won; direct is just a backup
                if (isAcceptable && downloaded == null) {
                    val ext = if (isMp3) "mp3" else "mp4"
                    val tempFile = File(applicationContext.cacheDir, "toolz_dl_${System.currentTimeMillis()}.$ext")
                    publishProgress(notificationId, "Downloading $safeTitle ($quality)...", 0.08f)
                    val ok = catalogRepository.downloadAudioStream(directStream.url, tempFile) { progress ->
                        val normalized = 0.08f + (progress.coerceIn(0f, 1f) * 0.77f)
                        progressChannel.trySend(normalized)
                    }
                    if (ok && tempFile.exists() && tempFile.length() > 1024) {
                        downloaded = tempFile
                        if (!isMp3) actualHeight = directHeight
                        android.util.Log.i("VideoDownloadWorker", "Direct stream download succeeded (${directHeight}p for $quality): ${tempFile.length()} bytes")
                    } else {
                        try { tempFile.delete() } catch (_: Exception) {}
                    }
                } else if (!isMp3 && downloaded == null) {
                    // Hold the low-res muxed (e.g. 360p for a 1080p ask) as LAST resort:
                    // download it now so we have something, but keep trying yt-dlp first.
                    android.util.Log.w("VideoDownloadWorker", "Direct muxed ${directHeight}p too low for $quality — holding as last resort, trying yt-dlp first")
                    val tempFile = File(applicationContext.cacheDir, "toolz_low_${System.currentTimeMillis()}.mp4")
                    val ok = try {
                        catalogRepository.downloadAudioStream(directStream.url, tempFile) { _ -> }
                    } catch (_: Exception) { false }
                    if (ok && tempFile.exists() && tempFile.length() > 1024) {
                        heldLowRes = tempFile
                        heldLowHeight = directHeight
                    } else {
                        try { tempFile.delete() } catch (_: Exception) {}
                    }
                }
            }

            // HD merge fallback for SD requests too: if the direct muxed resolve failed,
            // a DASH pair at the same ceiling often still works (then muxed with audio).
            if ((downloaded?.length() ?: 0) < 1024 && !isMp3 && !wantsHdMerge) {
                if (downloadHdPair(maxHeight, quality)) {
                    // downloaded + actualHeight set inside helper
                }
            }

            // Secondary fallback: yt-dlp reflection if direct resolution was not available
            // (or was held back as too-low-res). yt-dlp handles DASH merge itself for
            // true HD with audio when on-device merge failed.
            if ((downloaded?.length() ?: 0) < 1024) {
                android.util.Log.w("VideoDownloadWorker", "Attempting yt-dlp fallback for $sourceUrl ($quality)")
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
                        // Merged format: DASH video+audio when available (true HD with audio —
                        // the bundled yt-dlp ffmpeg handles the merge), else best progressive.
                        // Cap scope at 1080p H264: higher VP9/AV1 would need re-encode.
                        val format = "bestvideo[height<=$maxHeight][height<=1080][ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/bestvideo[height<=$maxHeight][height<=1080][ext=mp4]+bestaudio/bestvideo[height<=$maxHeight][height<=1080]+bestaudio/best[height<=$maxHeight][ext=mp4]/best[height<=$maxHeight]/best"
                        addOption.invoke(request, "-f", format)
                        addOption.invoke(request, "-o", outputTemplate)
                        addFlag?.invoke(request, "--no-playlist")
                        // Fresh player clients: the bundled yt-dlp may default to a stale
                        // client that only sees 360p. Request android+web so HD formats
                        // appear even on older binaries.
                        try { addOption.invoke(request, "--extractor-args", "youtube:player_client=android,web") } catch (_: Exception) {}
                        try { addOption.invoke(request, "--merge-output-format", "mp4") } catch (_: Exception) {}
                        try { addOption.invoke(request, "--concurrent-fragments", "4") } catch (_: Exception) {}
                        try { addOption.invoke(request, "--retries", "3") } catch (_: Exception) {}
                    }

                    val youtubeDl = youtubeDlClass.getMethod("getInstance").invoke(null)
                    try {
                        youtubeDl.javaClass.getMethod("init", Context::class.java).invoke(youtubeDl, applicationContext)
                    } catch (_: Exception) {}
                    // Best-effort binary refresh: an outdated bundled yt-dlp is the
                    // classic cause of "only 360p" (misses SABR/player fixes).
                    // Never blocks the download on failure (offline / rate-limited).
                    try {
                        val updateMethod = youtubeDl.javaClass.methods.firstOrNull { it.name == "updateYoutubeDL" }
                        if (updateMethod != null) {
                            val status = runCatching {
                                if (updateMethod.parameterTypes.size == 2) updateMethod.invoke(youtubeDl, applicationContext, null)
                                else updateMethod.invoke(youtubeDl, applicationContext)
                            }.getOrNull()
                            android.util.Log.i("VideoDownloadWorker", "yt-dlp update check: $status")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("VideoDownloadWorker", "yt-dlp update check failed (continuing with bundled): ${e.message}")
                    }
                    try {
                        val versionMethod = youtubeDl.javaClass.methods.firstOrNull { it.name == "version" || it.name == "versionName" }
                        android.util.Log.i("VideoDownloadWorker", "yt-dlp version probe: ${runCatching { versionMethod?.invoke(youtubeDl, applicationContext) }.getOrNull()}")
                    } catch (_: Exception) {}

                    val execResponse = try {
                        // Prefer the 3-arg overload with a progress callback so the
                        // long yt-dlp phase moves the bar (previously it sat at 8%
                        // for minutes: the "stuck progress" bug).
                        val cbClass = try {
                            Class.forName("com.yausername.youtubedl_android.DownloadProgressCallback")
                        } catch (_: Exception) { null }
                        val exec3 = youtubeDl.javaClass.methods.firstOrNull {
                            it.name == "execute" && it.parameterTypes.size == 3 &&
                                it.parameterTypes[0].isAssignableFrom(requestClass)
                        }
                        if (exec3 != null && cbClass != null) {
                            val cb = java.lang.reflect.Proxy.newProxyInstance(
                                cbClass.classLoader, arrayOf(cbClass),
                            ) { _, m, args ->
                                if (m.name == "onProgressUpdate" && !args.isNullOrEmpty()) {
                                    val p = (args[0] as? Float) ?: (args[0] as? Number)?.toFloat() ?: 0f
                                    // yt-dlp reports 0..100; map into the 8%..85% band.
                                    // Single owner: feed the conflated channel only.
                                    // The progressJob below owns setForeground.
                                    val norm = 0.08f + (p.coerceIn(0f, 100f) / 100f * 0.77f)
                                    progressChannel.trySend(norm)
                                }
                                null
                            }
                            exec3.invoke(youtubeDl, request, "ytdlp_${System.currentTimeMillis()}", cb)
                        } else {
                            val exec = youtubeDl.javaClass.methods.firstOrNull { it.name == "execute" && it.parameterTypes.size >= 1 }
                            exec?.invoke(youtubeDl, request)
                        }
                    } catch (e: Exception) {
                        val cause = e.cause?.message ?: e.message
                        android.util.Log.w("VideoDownloadWorker", "yt-dlp execute threw for $quality: ${cause?.take(300)}")
                        throw e
                    }
                    try {
                        val err = execResponse?.javaClass?.methods?.firstOrNull { it.name == "getErr" }?.invoke(execResponse) as? String
                        if (!err.isNullOrBlank()) android.util.Log.w("VideoDownloadWorker", "yt-dlp stderr tail: ${err.takeLast(500)}")
                    } catch (_: Exception) {}

                    val now = System.currentTimeMillis()
                    outputDir.listFiles()?.filter { it.length() > 1024 && (now - it.lastModified()) < 120_000 }?.maxByOrNull { it.lastModified() }
                }

                if (ytdlpResult.isSuccess && ytdlpResult.getOrNull() != null) {
                    try { downloaded?.delete() } catch (_: Exception) {}
                    downloaded = ytdlpResult.getOrNull()
                    // yt-dlp format string caps at maxHeight; trust it, but we don't know
                    // the exact height without probing — leave actualHeight null (= "≈ request").
                    android.util.Log.i("VideoDownloadWorker", "yt-dlp fallback succeeded for $quality: ${downloaded?.length()} bytes")
                } else {
                    // Try direct stream fetch from OkHttp fallback
                    val fb = tryFallbackDirectDownload(sourceUrl, isMp3, maxHeight, notificationId, safeTitle)
                    if (fb != null && fb.length() > 1024) {
                        try { downloaded?.delete() } catch (_: Exception) {}
                        downloaded = fb
                    } else {
                        fb?.delete()
                    }
                }
            }

            // Last resort: the held low-res muxed (e.g. 360p for a 1080p ask). Only now,
            // after DASH + yt-dlp both failed, do we accept a downgrade — and we label it.
            if ((downloaded?.length() ?: 0) < 1024 && heldLowRes != null && (heldLowRes?.length() ?: 0) > 1024) {
                android.util.Log.w("VideoDownloadWorker", "All HD paths failed for $quality — using held ${heldLowHeight}p file as last resort")
                try { downloaded?.delete() } catch (_: Exception) {}
                downloaded = heldLowRes
                actualHeight = heldLowHeight
                heldLowRes = null
            } else {
                try { heldLowRes?.delete() } catch (_: Exception) {}
                heldLowRes = null
            }

            progressChannel.close()
            progressJob.cancel()

            val finalFile = downloaded
            if (finalFile == null || finalFile.length() < 1024) {
                showErrorNotification(notificationId, safeTitle, "Could not resolve or download stream")
                finalFile?.delete()
                return@withContext Result.failure(workDataOf(KEY_ERROR to "Could not resolve or download stream"))
            }

            val downgraded = !isMp3 && VideoQualityPolicy.isDowngrade(actualHeight, maxHeight)
            if (downgraded) {
                android.util.Log.w("VideoDownloadWorker", "Quality downgrade: requested $quality but saved ${actualHeight}p for $sourceUrl")
            }

            publishProgress(notificationId, "Saving file...", 0.92f)
            val displayName = if (isMp3) "$safeTitle.mp3" else "$safeTitle.mp4"
            val mime = if (isMp3) "audio/mpeg" else "video/mp4"
            val savedUri = if (isMp3) {
                saveToMusic(finalFile, displayName, safeTitle)
            } else {
                saveToMovies(finalFile, displayName)
            }

            try { finalFile.delete() } catch (_: Exception) {}

            if (savedUri != null) {
                try { setProgress(workDataOf(KEY_PROGRESS to 1f)) } catch (_: Exception) {}
                lastFgPct = 100
                showCompletedNotification(notificationId, safeTitle, isMp3, requestedQuality = if (isMp3) null else quality, actualHeight = actualHeight)
                Result.success(
                    workDataOf(
                        KEY_FILE_URI to savedUri,
                        KEY_DISPLAY_NAME to displayName,
                        KEY_MIME_TYPE to mime,
                        KEY_REQUESTED_QUALITY to quality,
                        KEY_ACTUAL_HEIGHT to (actualHeight ?: -1),
                    )
                )
            } else {
                showErrorNotification(notificationId, safeTitle, "Could not save file to gallery")
                Result.failure(workDataOf(KEY_ERROR to "Could not save file to gallery"))
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoDownloadWorker", "Failure downloading $sourceUrl", e)
            try {
                showErrorNotification(notificationId, title, e.message ?: "Download failed")
            } catch (_: Exception) {}
            Result.failure(workDataOf(KEY_ERROR to (e.message?.take(120) ?: "Download failed")))
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
            val cappedHeight = minOf(maxHeight, 1080)
            val fmt = if (isMp3) "bestaudio[ext=m4a]/bestaudio" else "bestvideo[height<=$cappedHeight][height<=1080][ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/bestvideo[height<=$cappedHeight][height<=1080]+bestaudio/best[height<=$cappedHeight][ext=mp4]/best[height<=$cappedHeight]/best"
            addOption.invoke(request, "-f", fmt)
            try { addOption.invoke(request, "--extractor-args", "youtube:player_client=android,web") } catch (_: Exception) {}
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
        android.util.Log.e("VideoDownloadWorker", "MediaStore save failed", e)
        null
    }

    private fun saveToMusic(source: File, displayName: String, title: String): String? = try {
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
        android.util.Log.e("VideoDownloadWorker", "MediaStore mp3 save failed", e)
        null
    }

    private fun createNotificationChannel() {
        NotificationHelper.createAllChannels(applicationContext)
    }

    /**
     * Single reporting path for BOTH surfaces: the system notification AND
     * WorkManager progress (observed by the web-search banner + downloader rows).
     * Previously only setForeground was called here, so in-app progress froze
     * at the last download-callback value and never reached completion.
     */
    private suspend fun publishProgress(notificationId: Int, contentTitle: String, progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        try { setProgress(workDataOf(KEY_PROGRESS to clamped)) } catch (_: Exception) {}
        val progressInt = (clamped * 100).toInt()
        val now = System.currentTimeMillis()
        if (!NotificationHelper.shouldPublishProgress(lastFgAt, lastFgPct, now, progressInt)) return
        lastFgAt = now
        lastFgPct = progressInt
        try {
            val notification = createNotification(notificationId, contentTitle, progressInt)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setForeground(ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            } else {
                setForeground(ForegroundInfo(notificationId, notification))
            }
        } catch (_: Exception) {}
    }

    private fun createNotification(id: Int, contentTitle: String, progress: Int): android.app.Notification {
        // Cancel targets this exact WorkRequest id (the receiver cancels by UUID).
        // Previously the notification id was sent, which matched no work tag and
        // made the Cancel action a no-op.
        val cancelIntent = Intent(applicationContext, DownloadCancelReceiver::class.java).apply {
            putExtra("work_id", this@VideoDownloadWorker.id.toString())
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            applicationContext, id.hashCode(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationHelper.progressBuilder(
            applicationContext, CHANNEL_ID, contentTitle,
            if (progress in 1..99) "$progress%" else null, progress
        )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .build()
    }

    private fun showCompletedNotification(id: Int, title: String, isMp3: Boolean = false, requestedQuality: String? = null, actualHeight: Int? = null) {
        val qualitySuffix = when {
            isMp3 -> " (MP3)"
            actualHeight != null && requestedQuality != null -> {
                val reqH = requestedQuality.replace("p", "", ignoreCase = true).toIntOrNull()
                if (reqH != null && actualHeight < reqH - 60) " (${actualHeight}p, requested $requestedQuality)"
                else " (${actualHeight}p)"
            }
            requestedQuality != null -> " ($requestedQuality)"
            else -> ""
        }
        val text = "$title$qualitySuffix"
        val titleText = if (isMp3) "MP3 download complete" else "Download complete"
        val notification = NotificationHelper.terminalBuilder(applicationContext, CHANNEL_ID, titleText, text)
            .build()
        try { notificationManager.notify(id, notification) } catch (_: Exception) {}
    }

    private fun showErrorNotification(id: Int, title: String, error: String) {
        val notification = NotificationHelper.terminalBuilder(
            applicationContext, CHANNEL_ID, "Download failed", "$title: ${error.take(80)}", highPriority = true
        )
            .build()
        try { notificationManager.notify(id, notification) } catch (_: Exception) {}
    }
}
