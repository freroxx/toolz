/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.data.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.frerox.toolz.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float, val bytesPerSec: Long = 0L) : DownloadState
    data object Verifying : DownloadState
    data object Done : DownloadState
    data class Failed(val message: String, val retryable: Boolean = true) : DownloadState
}

/**
 * Atomic, verified model downloader for Background Remover.
 *
 * - Streams to .tmp then atomic rename
 * - Verifies expectedSizeBytes / etag length sanity (not hard-fail on etag drift)
 * - Cleans .tmp on cancel/failure
 * - Exposes per-model Flow state for UI
 */
class ModelDownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val _states = mutableMapOf<String, MutableStateFlow<DownloadState>>()
    private val lock = Any()

    fun stateFor(model: BackgroundModel): StateFlow<DownloadState> = synchronized(lock) {
        _states.getOrPut(model.id) { MutableStateFlow(IdleOrDoneFor(model)) }
    }

    /** Last observed bytes/sec for the active (or last) download of this model. */
    fun lastSpeedBps(model: BackgroundModel): Long = synchronized(lock) {
        (_states[model.id]?.value as? DownloadState.Downloading)?.bytesPerSec ?: 0L
    }

    private fun IdleOrDoneFor(model: BackgroundModel): DownloadState {
        return if (isVerified(model)) DownloadState.Done else DownloadState.Idle
    }

    fun isVerified(model: BackgroundModel): Boolean {
        val file = modelFile(model)
        if (!file.exists()) return false
        if (file.length() < 1024) return false
        if (model.expectedSizeBytes > 0) {
            val len = file.length()
            if (len != model.expectedSizeBytes) {
                // Allow more slack for large HF models (re-compression can shift a few KB)
                val slack = if (model.expectedSizeBytes > 5_000_000) 8192 else 2048
                if (kotlin.math.abs(len - model.expectedSizeBytes) > slack) return false
            }
        }
        // Pinned SHA-256 (new lineup): verified once at download, recorded via marker
        // file so we never re-hash 100+ MB on every cold start. Missing marker with a
        // pinned hash triggers one re-verification, then the marker is rewritten.
        val pinned = model.expectedSha256
        if (pinned != null && !hasHashMarker(model)) {
            if (!verifySha256(file, pinned)) return false
            writeHashMarker(model)
        }
        // Basic TFLite magic: first 4 bytes not HTML "<!DO" — check file header
        return try {
            file.inputStream().use { ins ->
                val header = ByteArray(16)
                val n = ins.read(header)
                if (n < 4) return false
                val text = String(header, 0, minOf(n, 8))
                // HTML error pages start with "<!" or "<html"
                !text.trimStart().startsWith("<!")
                        && !text.trimStart().startsWith("<html", ignoreCase = true)
                        && !text.contains("<HTML")
            }
        } catch (_: Exception) {
            false
        }
    }

    /** True when the connection is metered (cellular etc.) — big models ask first. */
    fun isMeteredConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (_: Exception) {
            false
        }
    }

    /** Deletes leftover files from the retired pre-revamp lineup (one-time reclaim). */
    fun deleteLegacyFiles(fileNames: List<String>): Long {
        var reclaimed = 0L
        val dir = File(context.filesDir, "models")
        for (name in fileNames) {
            for (suffix in listOf("", ".tmp", ".sha256.ok")) {
                val f = File(dir, name + suffix)
                if (f.exists()) {
                    reclaimed += f.length()
                    runCatching { f.delete() }
                }
            }
        }
        return reclaimed
    }

    fun modelFile(model: BackgroundModel): File =
        File(File(context.filesDir, "models"), model.fileName)

    fun tmpFile(model: BackgroundModel): File =
        File(File(context.filesDir, "models"), "${model.fileName}.tmp")

    suspend fun download(
        model: BackgroundModel,
        allowMetered: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            // Metered-network gate for large models (user confirms in UI).
            if (model.gatedOnWifi && !allowMetered && isMeteredConnection()) {
                val msg = context.getString(
                    R.string.st_BackgroundRemover_MeteredBlocked,
                    model.shortName,
                    model.sizeLabel,
                )
                synchronized(lock) {
                    _states.getOrPut(model.id) { MutableStateFlow(DownloadState.Idle) }.value =
                        DownloadState.Failed(msg, retryable = true)
                }
                return@withContext Result.failure(Exception(msg))
            }
            val flow = synchronized(lock) { _states.getOrPut(model.id) { MutableStateFlow(DownloadState.Idle) } }
            try {
                flow.value = DownloadState.Downloading(0.01f)
                val dir = File(context.filesDir, "models")
                if (!dir.exists()) dir.mkdirs()

                val dest = modelFile(model)
                val tmp = tmpFile(model)

                // Resume: keep a partial .tmp and ask the server for the remainder.
                // Servers that ignore Range answer 200 → we restart from scratch.
                val resumeFrom = if (tmp.exists() && tmp.length() > 1024) tmp.length() else 0L
                if (resumeFrom == 0L && tmp.exists()) tmp.delete()

                val requestBuilder = Request.Builder()
                    .url(model.downloadUrl)
                    .header("Accept", "*/*")
                    .header("User-Agent", "Toolz-ModelHub/1.0")
                if (resumeFrom > 0) requestBuilder.header("Range", "bytes=$resumeFrom-")
                val request = requestBuilder.build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        val detail = when (response.code) {
                            404 -> "Model not found on server (404). Try again later."
                            401, 403 -> "Download blocked (auth ${response.code}). Try again."
                            416 -> {
                                // Range unsatisfiable — stale partial; restart cleanly.
                                tmp.delete()
                                "Download interrupted — tap to retry."
                            }
                            429 -> "Server rate-limited (429). Wait a minute and retry."
                            503, 502 -> "Server busy (${response.code}). Try again shortly."
                            else -> "Network error ${response.code}: ${response.message}"
                        }
                        flow.value = DownloadState.Failed(detail, retryable = response.code != 404)
                        // don't throw for 404 — caller handles message
                        return@withContext Result.failure(Exception(detail))
                    }
                    val resumed = response.code == 206 && resumeFrom > 0
                    if (!resumed && tmp.exists()) tmp.delete()
                    val body = response.body ?: run {
                        val msg = "Empty response body"
                        flow.value = DownloadState.Failed(msg)
                        return@withContext Result.failure(Exception(msg))
                    }
                    val total = body.contentLength().let { if (it > 0 && resumed) it + resumeFrom else it }
                    // quick HTML detection via content-type
                    val ct = body.contentType()?.toString()?.lowercase() ?: ""
                    if (ct.contains("text/html")) {
                        val msg = "Server returned HTML instead of model (URL may be expired)"
                        flow.value = DownloadState.Failed(msg)
                        return@withContext Result.failure(Exception(msg))
                    }
                    FileOutputStream(tmp, resumed).use { out ->
                        val ins = body.byteStream()
                        val buf = ByteArray(16384)
                        var read: Int
                        var totalRead = if (resumed) resumeFrom else 0L
                        var lastEmit = 0L
                        var lastBytes = 0L
                        val t0 = System.currentTimeMillis()
                        val startBytes = totalRead
                        while (ins.read(buf).also { read = it } != -1) {
                            // Cooperative cancellation check — if flow was reset elsewhere, abort via exception
                            if (Thread.interrupted()) throw InterruptedException("Download cancelled")
                            out.write(buf, 0, read)
                            totalRead += read
                            if (total > 0) {
                                val p = (totalRead.toFloat() / total).coerceIn(0f, 1f)
                                // throttle emissions to 100ms
                                val now = System.currentTimeMillis()
                                if (now - lastEmit > 100 || p >= 1f) {
                                    val bps = if (now > t0) (totalRead - startBytes) * 1000 / (now - t0 + 1) else 0L
                                    flow.value = DownloadState.Downloading(p, bps)
                                    onProgress(p)
                                    lastEmit = now
                                    lastBytes = totalRead
                                }
                            } else {
                                // indeterminate — pulse
                                if (totalRead - lastBytes > 256 * 1024) {
                                    flow.value = DownloadState.Downloading(0.5f, 0L)
                                    lastBytes = totalRead
                                }
                            }
                        }
                    }
                    // Verify size if known
                    if (model.expectedSizeBytes > 0) {
                        val len = tmp.length()
                        if (kotlin.math.abs(len - model.expectedSizeBytes) > 4096) {
                            // Don't hard fail on small drift but warn; delete if wildly off (HTML)
                            if (len < 1024 || len < model.expectedSizeBytes / 2) {
                                tmp.delete()
                                val msg = "Download corrupted (size ${len} vs expected ${model.expectedSizeBytes})"
                                flow.value = DownloadState.Failed(msg)
                                return@withContext Result.failure(Exception(msg))
                            }
                        }
                    }
                    // HTML sniff second line after download (some CDNs return 200 with HTML body)
                    if (!looksLikeTflite(tmp)) {
                        tmp.delete()
                        val msg = "Downloaded file is not a valid model (HTML detected). URL may have expired."
                        flow.value = DownloadState.Failed(msg)
                        return@withContext Result.failure(Exception(msg))
                    }
                    // Pinned SHA-256 (new lineup): quarantine on mismatch — a wrong
                    // hash means a corrupt or substituted file, never ship it.
                    val pinned = model.expectedSha256
                    if (pinned != null && !verifySha256(tmp, pinned)) {
                        tmp.delete()
                        val msg = "Download failed integrity check — tap to retry."
                        flow.value = DownloadState.Failed(msg)
                        return@withContext Result.failure(Exception(msg))
                    }
                    flow.value = DownloadState.Verifying
                    // atomic rename
                    if (dest.exists()) dest.delete()
                    val ok = tmp.renameTo(dest)
                    if (!ok) {
                        // fallback copy
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                    if (pinned != null) writeHashMarker(model)
                    flow.value = DownloadState.Done
                    onProgress(1f)
                    return@withContext Result.success(Unit)
                }
            } catch (ce: InterruptedException) {
                flow.value = DownloadState.Failed("Download cancelled", retryable = true)
                // cleanup tmp
                try { tmpFile(model).delete() } catch (_: Exception) {}
                return@withContext Result.failure(ce)
            } catch (e: kotlinx.coroutines.CancellationException) {
                flow.value = DownloadState.Idle
                try { tmpFile(model).delete() } catch (_: Exception) {}
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = e.localizedMessage ?: "Download failed"
                // Map common
                val friendly = when {
                    msg.contains("Unable to resolve host", true) -> "No internet connection"
                    msg.contains("timeout", true) -> "Connection timed out — try again"
                    else -> msg
                }
                flow.value = DownloadState.Failed(friendly, retryable = true)
                try { tmpFile(model).delete() } catch (_: Exception) {}
                return@withContext Result.failure(Exception(friendly, e))
            }
        }

    // ── SHA-256 pinning ──

    private fun hashMarkerFile(model: BackgroundModel): File =
        File(File(context.filesDir, "models"), "${model.fileName}.sha256.ok")

    private fun hasHashMarker(model: BackgroundModel): Boolean =
        hashMarkerFile(model).exists()

    private fun writeHashMarker(model: BackgroundModel) {
        runCatching { hashMarkerFile(model).writeText(model.expectedSha256 ?: "") }
    }

    private fun verifySha256(file: File, expectedHex: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(65536)
                var n: Int
                while (ins.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
                .equals(expectedHex.trim(), ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun looksLikeTflite(file: File): Boolean {        return try {
            file.inputStream().use { ins ->
                val b = ByteArray(32)
                val n = ins.read(b)
                if (n < 4) return false
                val head = String(b, 0, minOf(n, 20))
                // TFLite flatbuffer starts with "TFL3" at offset 4, but we just rule out HTML
                !(head.contains("<!DOCTYPE") || head.contains("<html") || head.contains("<HTML") || head.trimStart().startsWith("<"))
            }
        } catch (_: Exception) { false }
    }

    fun delete(model: BackgroundModel): Boolean {
        val f = modelFile(model)
        val tmp = tmpFile(model)
        var ok = true
        if (f.exists()) ok = f.delete() && ok
        if (tmp.exists()) tmp.delete()
        runCatching { hashMarkerFile(model).delete() }
        synchronized(lock) { _states[model.id]?.value = DownloadState.Idle }
        return ok
    }

    /** Refresh states after manual file deletion elsewhere */
    fun refresh(model: BackgroundModel) {
        synchronized(lock) { _states[model.id]?.value = IdleOrDoneFor(model) }
    }

    fun allDownloadedIds(): Set<String> =
        BackgroundModel.entries.filter { isVerified(it) }.map { it.id }.toSet()
}
