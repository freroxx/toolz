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

package com.frerox.toolz.data.update

import android.content.Context
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Resumable APK downloader over OkHttp with progress, speed, size and
 * SHA-256 verification. No DownloadManager involved.
 *
 * Resume state lives in a `.meta` sidecar next to the partial file
 * (source URL, total size, ETag/Last-Modified, expected digest). A range
 * request is only sent when the sidecar belongs to the same URL, and
 * `If-Range` protects against a changed file being spliced onto an old
 * prefix. When the server ignores the range, the partial is truncated and
 * the download restarts cleanly.
 */
@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    sealed interface DownloadResult {
        data class Success(val file: File, val sha256: String?) : DownloadResult
        data class Failure(
            val kind: FailureKind,
            val httpCode: Int? = null,
            val detail: String? = null,
        ) : DownloadResult
    }

    enum class FailureKind {
        Cancelled,
        Network,
        Timeout,
        Http,
        SizeMismatch,
        Integrity,
        Storage,
    }

    private data class Meta(
        val url: String,
        val totalBytes: Long?,
        val etag: String?,
        val lastModified: String?,
        val sha256: String?,
    )

    /**
     * Download [url] into [destFile], resuming when a matching partial and
     * sidecar exist.
     *
     * @param onProgress throttled (~200 ms) plus once at completion.
     * @param isCancelled polled during the copy; a cancelled download keeps
     *        the partial file for the next attempt.
     */
    suspend fun download(
        url: String,
        destFile: File,
        expectedSize: Long?,
        expectedSha256: String?,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
        isCancelled: () -> Boolean,
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext DownloadResult.Failure(
                FailureKind.Network, detail = "The update has no download link."
            )
        }
        try {
            if (!destFile.parentFile.exists() && !destFile.parentFile.mkdirs()) {
                return@withContext DownloadResult.Failure(
                    FailureKind.Storage, detail = "Couldn't create the update folder."
                )
            }
        } catch (e: Exception) {
            return@withContext DownloadResult.Failure(FailureKind.Storage, detail = e.message)
        }

        val metaFile = metaFileFor(destFile)
        val meta = readMeta(metaFile)?.takeIf { it.url == url }
        // A sidecar from another URL means a different build: start over.
        if (meta == null && destFile.exists()) {
            destFile.delete()
        }

        var existing = if (destFile.exists()) destFile.length() else 0L
        val knownSize = expectedSize?.takeIf { it > 0L } ?: meta?.totalBytes?.takeIf { it > 0L }

        // Completed file from a previous attempt: verify and reuse.
        if (knownSize != null && existing == knownSize) {
            val sha = sha256Of(destFile)
            if (expectedSha256.isNullOrBlank() || sha.equals(expectedSha256, ignoreCase = true)) {
                metaFile.delete()
                return@withContext DownloadResult.Success(destFile, sha)
            }
            destFile.delete()
            metaFile.delete()
            existing = 0L
        }
        // A partial bigger than the expected file is unusable.
        if (knownSize != null && existing > knownSize) {
            destFile.delete()
            metaFile.delete()
            existing = 0L
        }

        var resume = existing > 0L && knownSize != null && existing < knownSize
        val requestBuilder = Request.Builder().url(url).get()
        if (resume) {
            requestBuilder.addHeader("Range", "bytes=$existing-")
            val validator = meta?.etag?.takeIf { it.isNotBlank() }
                ?: meta?.lastModified?.takeIf { it.isNotBlank() }
            if (validator != null) requestBuilder.addHeader("If-Range", validator)
        } else {
            // No usable partial: make sure a stale file cannot be appended to.
            if (existing > 0L) {
                destFile.delete()
                metaFile.delete()
                existing = 0L
                resume = false
            }
        }

        val client = okHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val response = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: SocketTimeoutException) {
            return@withContext DownloadResult.Failure(FailureKind.Timeout, detail = e.message)
        } catch (e: IOException) {
            return@withContext DownloadResult.Failure(FailureKind.Network, detail = e.message)
        }

        response.use { res ->
            if (isCancelled()) {
                return@withContext DownloadResult.Failure(FailureKind.Cancelled)
            }
            if (!res.isSuccessful) {
                if (res.code == 416) {
                    // Range no longer satisfiable (file changed server-side).
                    destFile.delete()
                    metaFile.delete()
                }
                return@withContext DownloadResult.Failure(
                    FailureKind.Http,
                    httpCode = res.code,
                    detail = res.message.ifBlank { "HTTP ${res.code}" },
                )
            }

            // Server ignored the range: truncate and write the full body.
            if (resume && res.code != 206) {
                destFile.delete()
                existing = 0L
                resume = false
            }

            val body = res.body
                ?: return@withContext DownloadResult.Failure(
                    FailureKind.Network, detail = "The server sent an empty response."
                )
            val remaining = body.contentLength()
            val total = when {
                resume && res.code == 206 -> knownSize ?: (existing + remaining).takeIf { remaining > 0 }
                remaining > 0 -> remaining
                else -> knownSize
            }

            // Persist the resume contract before bytes land on disk.
            writeMeta(
                metaFile,
                Meta(
                    url = url,
                    totalBytes = total,
                    etag = res.header("ETag"),
                    lastModified = res.header("Last-Modified"),
                    sha256 = expectedSha256,
                )
            )

            var downloaded = existing
            var speedBps = 0L
            var windowBytes = 0L
            var windowStartNs = System.nanoTime()
            var lastEmitAt = 0L

            try {
                body.byteStream().use { input ->
                    FileOutputStream(destFile, resume).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            if (isCancelled()) {
                                return@withContext DownloadResult.Failure(FailureKind.Cancelled)
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            windowBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitAt >= PROGRESS_THROTTLE_MS) {
                                val elapsedNs = System.nanoTime() - windowStartNs
                                if (elapsedNs > 0L) {
                                    speedBps = windowBytes * 1_000_000_000L / elapsedNs
                                }
                                windowBytes = 0L
                                windowStartNs = System.nanoTime()
                                lastEmitAt = now
                                onProgress(downloaded, total ?: 0L, speedBps)
                            }
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
            } catch (e: IOException) {
                if (isCancelled()) {
                    return@withContext DownloadResult.Failure(FailureKind.Cancelled)
                }
                val message = e.message.orEmpty()
                val storageIssue = message.contains("space", ignoreCase = true) ||
                    message.contains("ENOSPC", ignoreCase = true) ||
                    message.contains("read-only", ignoreCase = true)
                return@withContext DownloadResult.Failure(
                    if (storageIssue) FailureKind.Storage else FailureKind.Network,
                    detail = message.ifBlank { "Connection lost." },
                )
            }

            onProgress(downloaded, total ?: 0L, speedBps)

            if (total != null && downloaded != total) {
                destFile.delete()
                metaFile.delete()
                return@withContext DownloadResult.Failure(
                    FailureKind.SizeMismatch,
                    detail = "Got ${formatBytes(downloaded)}, expected ${formatBytes(total)}.",
                )
            }

            val sha = sha256Of(destFile)
            if (!expectedSha256.isNullOrBlank() && !sha.equals(expectedSha256, ignoreCase = true)) {
                destFile.delete()
                metaFile.delete()
                return@withContext DownloadResult.Failure(
                    FailureKind.Integrity,
                    detail = "The checksum didn't match the release manifest.",
                )
            }
            if (sha == null) {
                destFile.delete()
                metaFile.delete()
                return@withContext DownloadResult.Failure(
                    FailureKind.Storage, detail = "Couldn't read the downloaded file."
                )
            }

            metaFile.delete()
            DownloadResult.Success(destFile, sha)
        }
    }

    companion object {
        private const val TAG = "UpdateDownloader"
        private const val PROGRESS_THROTTLE_MS = 200L

        private fun metaFileFor(destFile: File): File = File(destFile.parentFile, "${destFile.name}.meta")

        /** Cache-home for sideloaded APKs: `<cache>/updates/toolz_update_<version>.apk`. */
        fun defaultDestFile(context: Context, version: String): File {
            val dir = File(context.cacheDir, "updates")
            if (!dir.exists()) dir.mkdirs()
            val safe = version.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
            return File(dir, "toolz_update_$safe.apk")
        }

        /**
         * Newest APK left behind by the legacy DownloadManager flow
         * (`<externalFiles>/Download/toolz_update*.apk`), if any.
         */
        fun legacyDownloadManagerFile(context: Context): File? = try {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: return null
            dir.listFiles { f ->
                f.isFile && f.name.startsWith("toolz_update_") && f.name.endsWith(".apk")
            }?.maxByOrNull { it.lastModified() }?.takeIf { it.exists() && it.length() > 0L }
        } catch (e: Exception) {
            Log.w(TAG, "legacy scan failed: ${e.message}")
            null
        }

        /** Lowercase hex SHA-256 of [file]; null on any error. */
        fun sha256Of(file: File): String? = try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
        } catch (e: Exception) {
            Log.w(TAG, "sha256 failed: ${e.message}")
            null
        }

        /** Removes the resume sidecar for [file] (if any). */
        fun deleteSidecar(file: File) {
            try {
                metaFileFor(file).delete()
            } catch (_: Exception) {
            }
        }

        /**
         * Delete cached update files except [keep] and its sidecar.
         *
         * [maxAgeMs] defaults to 0: everything else is removed immediately.
         */
        fun pruneCache(context: Context, keep: File?, maxAgeMs: Long = 0L) {
            try {
                val dir = File(context.cacheDir, "updates")
                val files = dir.listFiles() ?: return
                val keepMeta = keep?.let { metaFileFor(it).name }
                val now = System.currentTimeMillis()
                for (file in files) {
                    if (keep != null && file.absolutePath == keep.absolutePath) continue
                    if (keep != null && file.name == keepMeta) continue
                    if (now - file.lastModified() >= maxAgeMs) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "prune failed: ${e.message}")
            }
        }

        private fun readMeta(file: File): Meta? = try {
            if (!file.isFile) null
            else {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                Meta(
                    url = json.optString("url"),
                    totalBytes = json.optLong("total", 0L).takeIf { it > 0L },
                    etag = json.optString("etag").takeIf { it.isNotBlank() },
                    lastModified = json.optString("lastModified").takeIf { it.isNotBlank() },
                    sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "meta read failed: ${e.message}")
            null
        }

        private fun writeMeta(file: File, meta: Meta) {
            try {
                val json = JSONObject()
                    .put("url", meta.url)
                    .put("total", meta.totalBytes ?: 0L)
                    .put("etag", meta.etag ?: "")
                    .put("lastModified", meta.lastModified ?: "")
                    .put("sha256", meta.sha256 ?: "")
                file.writeText(json.toString(), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "meta write failed: ${e.message}")
            }
        }
    }
}
