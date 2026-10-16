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
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Resumable APK downloader over OkHttp with progress, speed,
 * size and SHA-256 verification. No DownloadManager involved.
 */
@Singleton
class UpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    sealed interface DownloadResult {
        data class Success(val file: File, val sha256: String?) : DownloadResult
        data class Error(val message: String) : DownloadResult
    }

    /**
     * Download [url] into [destFile], resuming via `Range` when a partial
     * file and a known [expectedSize] exist.
     *
     * @param onProgress invoked throttled (~200ms) plus once at completion.
     * @param isCancelled polled each buffer; on true the partial file is
     *        KEPT for resume and [DownloadResult.Error]("Cancelled") returns.
     */
    suspend fun download(
        url: String,
        destFile: File,
        expectedSize: Long?,
        expectedSha256: String?,
        onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
        isCancelled: () -> Boolean
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            destFile.parentFile?.mkdirs()
            var existing = if (destFile.exists()) destFile.length() else 0L
            val knownSize = expectedSize?.takeIf { it > 0 }
            var resume = existing > 0 && knownSize != null && existing < knownSize

            val client = okHttpClient.newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .build()

            val requestBuilder = Request.Builder().url(url).get()
            if (resume) requestBuilder.addHeader("Range", "bytes=$existing-")
            val response = client.newCall(requestBuilder.build()).execute()
            response.use { res ->
                if (!res.isSuccessful) {
                    return@withContext DownloadResult.Error("Download failed: HTTP ${res.code}")
                }
                // Server ignored Range (200 instead of 206): restart clean.
                if (resume && res.code != 206) {
                    destFile.delete()
                    existing = 0L
                    resume = false
                }
                val body = res.body ?: return@withContext DownloadResult.Error("Empty response body")
                val remaining = body.contentLength() // -1 when unknown
                val total = knownSize ?: if (remaining > 0) existing + remaining else -1L

                val digest = MessageDigest.getInstance("SHA-256")
                if (resume && existing > 0) {
                    // Seed the hash with the already-written prefix so the
                    // final digest covers the whole file.
                    try {
                        destFile.inputStream().use { input ->
                            val buf = ByteArray(8192)
                            var read: Int
                            var left = existing
                            while (left > 0) {
                                read = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                                if (read == -1) break
                                digest.update(buf, 0, read)
                                left -= read
                            }
                        }
                    } catch (e: IOException) {
                        destFile.delete()
                        return@withContext DownloadResult.Error("Corrupt partial file: ${e.message}")
                    }
                }

                var downloaded = existing
                var lastEmitAt = 0L
                var windowBytes = 0L
                var windowStartNs = System.nanoTime()
                var speedBps = 0L
                try {
                    body.byteStream().use { input ->
                        FileOutputStream(destFile, resume).use { output ->
                            val buf = ByteArray(8192)
                            while (true) {
                                if (isCancelled()) {
                                    return@withContext DownloadResult.Error("Cancelled")
                                }
                                val read = input.read(buf)
                                if (read == -1) break
                                output.write(buf, 0, read)
                                digest.update(buf, 0, read)
                                downloaded += read
                                windowBytes += read
                                val nowMs = System.currentTimeMillis()
                                if (nowMs - lastEmitAt >= PROGRESS_THROTTLE_MS) {
                                    val elapsedNs = System.nanoTime() - windowStartNs
                                    if (elapsedNs > 0) {
                                        speedBps = (windowBytes * 1_000_000_000L / elapsedNs)
                                    }
                                    windowBytes = 0L
                                    windowStartNs = System.nanoTime()
                                    lastEmitAt = nowMs
                                    onProgress(downloaded, total, speedBps)
                                }
                            }
                            output.fd.sync()
                        }
                    }
                } catch (e: IOException) {
                    if (isCancelled()) return@withContext DownloadResult.Error("Cancelled")
                    return@withContext DownloadResult.Error("Network error: ${e.message}")
                }
                onProgress(downloaded, total, speedBps)

                if (knownSize != null && downloaded != knownSize) {
                    destFile.delete()
                    return@withContext DownloadResult.Error(
                        "Size mismatch: got ${formatBytes(downloaded)}, expected ${formatBytes(knownSize)}"
                    )
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (!expectedSha256.isNullOrBlank() && !hex.equals(expectedSha256, ignoreCase = true)) {
                    destFile.delete()
                    return@withContext DownloadResult.Error("SHA-256 mismatch — file rejected")
                }
                DownloadResult.Success(destFile, hex)
            }
        } catch (e: IOException) {
            DownloadResult.Error(e.message ?: "Download failed")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DownloadResult.Error(e.message ?: "Download failed")
        }
    }

    companion object {
        private const val TAG = "UpdateDownloader"
        private const val PROGRESS_THROTTLE_MS = 200L

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
            }?.maxByOrNull { it.lastModified() }?.takeIf { it.exists() && it.length() > 0 }
        } catch (e: Exception) {
            Log.w(TAG, "legacy scan failed: ${e.message}")
            null
        }

        /** Lowercase hex SHA-256 of [file]; null on any error. */
        fun sha256Of(file: File): String? = try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            String.format(Locale.US, "%064x", java.math.BigInteger(1, digest.digest()))
        } catch (e: Exception) {
            Log.w(TAG, "sha256 failed: ${e.message}")
            null
        }

        /** Delete every cached update APK except [keep]. */
        fun pruneCache(context: Context, keep: File?, maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000) {
            try {
                val dir = File(context.cacheDir, "updates")
                val files = dir.listFiles { f ->
                    f.isFile && f.name.startsWith("toolz_update_")
                } ?: return
                val now = System.currentTimeMillis()
                for (f in files) {
                    if (keep != null && f.absolutePath == keep.absolutePath) continue
                    if (now - f.lastModified() > maxAgeMs) {
                        f.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "prune failed: ${e.message}")
            }
        }
    }
}
