/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.util

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Shared on-device YouTube HD merge helper.
 *
 * Used by [com.frerox.toolz.worker.VideoDownloadWorker] and the Media Downloader
 * engine: downloads a DASH video-only + audio-only pair, then muxes them into a
 * single MP4 with audio (true 1080p, not the silent video-only file).
 */
object YtVideoMerge {

    /**
     * Downloads both streams to [workDir] with progress in 0..1 across both files,
     * then muxes to [outputMp4] via FFmpegKit (`-c:v copy -c:a aac -movflags +faststart`).
     * Returns true on success; cleans up partials on failure (keeps caller-owned [outputMp4]
     * only when true).
     */
    suspend fun downloadAndMux(
        context: Context,
        okHttpClient: OkHttpClient,
        videoUrl: String,
        audioUrl: String,
        workDir: File,
        outputMp4: File,
        onProgress: suspend (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val stamp = System.currentTimeMillis()
        val videoFile = File(workDir, "hdv_${stamp}.mp4")
        val audioFile = File(workDir, "hda_${stamp}.m4a")
        try {
            withContext(Dispatchers.Main) { onProgress(0.02f) }
            val okV = downloadStream(okHttpClient, videoUrl, videoFile) { p ->
                // video = first 55% of bar
                withContext(Dispatchers.Main) { onProgress(0.02f + p * 0.55f) }
            }
            if (!okV) return@withContext false
            val okA = downloadStream(okHttpClient, audioUrl, audioFile) { p ->
                // audio = next 25%
                withContext(Dispatchers.Main) { onProgress(0.57f + p * 0.25f) }
            }
            if (!okA) return@withContext false

            withContext(Dispatchers.Main) { onProgress(0.84f) }
            val muxed = mux(videoFile, audioFile, outputMp4)
            withContext(Dispatchers.Main) { onProgress(if (muxed) 1f else 0.84f) }
            muxed
        } finally {
            try { videoFile.delete() } catch (_: Exception) {}
            try { audioFile.delete() } catch (_: Exception) {}
        }
    }

    suspend fun downloadStream(
        okHttpClient: OkHttpClient,
        streamUrl: String,
        outputFile: File,
        onProgress: suspend (Float) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) { response.close(); return@withContext false }
            val body = response.body ?: run { response.close(); return@withContext false }
            val total = body.contentLength()
            var done = 0L
            var lastEmit = 0L
            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit > 200) {
                            lastEmit = now
                            val p = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0.5f
                            withContext(Dispatchers.Main) { onProgress(p) }
                        }
                    }
                }
            }
            response.close()
            withContext(Dispatchers.Main) { onProgress(1f) }
            outputFile.exists() && outputFile.length() > 1024
        } catch (e: Exception) {
            android.util.Log.w("YtVideoMerge", "downloadStream failed: ${e.message}")
            try { outputFile.delete() } catch (_: Exception) {}
            false
        }
    }

    /** Muxes [videoFile] (video-only) + [audioFile] into [outputMp4]. No re-encode of video. */
    fun mux(videoFile: File, audioFile: File, outputMp4: File): Boolean {
        return try {
            if (!videoFile.exists() || videoFile.length() < 1024) return false
            if (!audioFile.exists() || audioFile.length() < 1024) return false
            try { if (outputMp4.exists()) outputMp4.delete() } catch (_: Exception) {}
            val args = arrayOf(
                "-i", videoFile.absolutePath,
                "-i", audioFile.absolutePath,
                "-map", "0:v:0",
                "-map", "1:a:0",
                "-c:v", "copy",
                "-c:a", "aac",
                "-b:a", "160k",
                "-movflags", "+faststart",
                "-shortest",
                "-y", outputMp4.absolutePath,
            )
            val session = FFmpegKit.executeWithArguments(args)
            val ok = ReturnCode.isSuccess(session.returnCode) &&
                outputMp4.exists() && outputMp4.length() > 1024
            if (!ok) {
                android.util.Log.w("YtVideoMerge", "mux failed rc=${session.returnCode} out=${session.output?.takeLast(300)}")
                try { outputMp4.delete() } catch (_: Exception) {}
            }
            ok
        } catch (e: Exception) {
            android.util.Log.w("YtVideoMerge", "mux exception: ${e.message}")
            false
        }
    }
}
