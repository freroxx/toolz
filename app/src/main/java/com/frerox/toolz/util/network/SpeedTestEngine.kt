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

package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.BloatGrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Phased progress events for the full (download + upload + bufferbloat) test. */
sealed class SpeedEvent {
    data class Progress(val phaseLabel: String, val value: Double, val progress: Float) : SpeedEvent()
    data class Done(
        val downloadMbps: Double,
        val uploadMbps: Double,
        val idleLatencyMs: Long?,
        val loadedLatencyMs: Long?
    ) : SpeedEvent()

    data class Failed(val message: String) : SpeedEvent()
}

@Singleton
class SpeedTestEngine @Inject constructor() {

    private val downloadMirrors = listOf(
        "https://speed.cloudflare.com/__down?bytes=25000000",
        "https://proof.ovh.net/files/10Mb.dat",
        "https://speed.hetzner.de/100MB.bin"
    )

    companion object {
        const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
        const val UPLOAD_BYTES = 8 * 1024 * 1024          // 8 MiB
        const val LATENCY_HOST = "1.1.1.1"
        const val LATENCY_PORT = 443
        const val DOWNLOAD_TARGET_MS = 10_000L
        const val WARMUP_MS = 700L
        const val EMIT_TICK_MS = 120L

        /** Pure, testable bufferbloat grading. */
        fun grade(idle: Long?, loaded: Long?): BloatGrade? =
            BloatGrade.fromDelta(if (idle != null && loaded != null) loaded - idle else null)
    }

    /** Latency observed during the most recent download run (cross-thread handoff). */
    @Volatile
    var lastLoadedLatency: Long? = null
        private set

    /**
     * Full production test:
     *  1. idle latency ×5 via TCP connect to 1.1.1.1:443 (permission-free, ICMP-independent)
     *  2. download while sampling loaded latency every ~900 ms
     *  3. upload of 8 MiB to Cloudflare __up
     * Fully cancellable at every read/write/tick.
     */
    fun runFullTest(): Flow<SpeedEvent> = flow {
        try {
            emit(SpeedEvent.Progress("Measuring idle latency…", 0.0, 0.02f))
            val idleSamples = (1..5).mapNotNull { probeLatency() }
            val idleLatency = idleSamples.takeIf { it.size >= 3 }?.average()?.toLong()

            lastLoadedLatency = null

            var downloadMbps = 0.0
            streamDownload(sampleLoadedLatency = true) { progress, speed, label ->
                downloadMbps = speed
                emit(
                    SpeedEvent.Progress(
                        label,
                        speed,
                        0.05f + progress * 0.60f
                    )
                )
            }

            var uploadMbps = 0.0
            streamUpload { progress, speed ->
                uploadMbps = speed
                emit(
                    SpeedEvent.Progress(
                        "Upload · ${"%.1f".format(speed)} Mbps",
                        speed,
                        0.65f + progress * 0.33f
                    )
                )
            }

            emit(SpeedEvent.Done(downloadMbps, uploadMbps, idleLatency, lastLoadedLatency))
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                emit(SpeedEvent.Failed(e.message ?: "Speed test failed"))
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Legacy single-sided flow kept for callers that only want a download figure. */
    fun runDownloadTest(): Flow<Pair<Float, Double>> = flow {
        var lastError: Exception? = null
        for (urlString in downloadMirrors) {
            try {
                streamDownload(urlString = urlString) { progress, speed, _ ->
                    emit(progress to speed)
                }
                return@flow
            } catch (e: Exception) {
                lastError = e
                if (!currentCoroutineContext().isActive) throw e
            }
        }
        throw lastError ?: IllegalStateException("No mirror available")
    }.flowOn(Dispatchers.IO)

    // ── internals ───────────────────────────────────────────────────────────

    private suspend fun streamDownload(
        urlString: String? = null,
        sampleLoadedLatency: Boolean = false,
        onTick: suspend (progress: Float, mbps: Double, label: String) -> Unit
    ) {
        val mirrors = listOfNotNull(urlString) .ifEmpty { downloadMirrors }
        var lastError: Exception? = null
        for (mirror in mirrors) {
            var connection: HttpURLConnection? = null
            var input: InputStream? = null
            try {
                connection = (URL(mirror).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", "Toolz/2.0 (SpeedTest)")
                    connect()
                }
                check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
                val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: 25_000_000L
                input = connection.inputStream

                val buffer = ByteArray(64 * 1024)
                var total = 0L
                var windowBytes = 0L
                var ema = 0.0
                val start = System.currentTimeMillis()
                var lastEmit = start
                var lastProbe = 0L

                while (currentCoroutineContext().isActive) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    windowBytes += read
                    val now = System.currentTimeMillis()

                    if (sampleLoadedLatency && now - lastProbe > 900L) {
                        lastProbe = now
                        probeLatency()?.let { lastLoadedLatency = it }
                    }

                    if (now - lastEmit >= EMIT_TICK_MS) {
                        val secs = (now - lastEmit) / 1000.0
                        val instant = (windowBytes * 8.0) / (secs * 1_000_000.0)
                        if (now - start > WARMUP_MS) {
                            ema = if (ema == 0.0) instant else 0.18 * instant + 0.82 * ema
                        }
                        val byteProgress = (total.toFloat() / contentLength).coerceIn(0f, 1f)
                        val timeProgress = ((now - start).toFloat() / DOWNLOAD_TARGET_MS).coerceIn(0f, 1f)
                        val combined = (byteProgress * 0.65f + timeProgress * 0.35f).coerceIn(0f, 0.99f)
                        onTick(combined, ema.coerceAtLeast(0.0), "Download · ${"%.1f".format(ema)} Mbps")
                        windowBytes = 0
                        lastEmit = now
                    }
                    if (now - start >= DOWNLOAD_TARGET_MS) break
                }
                return // success
            } catch (e: Exception) {
                lastError = e
                if (!currentCoroutineContext().isActive) throw e
            } finally {
                runCatching { input?.close() }
                runCatching { connection?.disconnect() }
            }
        }
        throw lastError ?: IllegalStateException("No mirror available")
    }

    private suspend fun streamUpload(onTick: suspend (progress: Float, mbps: Double) -> Unit) {
        val payload = ByteArray(UPLOAD_BYTES)
        var seed = System.nanoTime()
        for (i in payload.indices step 4096) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            payload[i] = seed.toByte()
        }

        var connection: HttpURLConnection? = null
        var output: OutputStream? = null
        try {
            connection = (URL(UPLOAD_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 10_000
                setFixedLengthStreamingMode(payload.size)
                setRequestProperty("Content-Type", "application/octet-stream")
                setRequestProperty("User-Agent", "Toolz/2.0 (SpeedTest)")
                connect()
            }
            output = connection.outputStream

            var written = 0L
            var windowBytes = 0L
            var ema = 0.0
            val start = System.nanoTime()
            var lastEmitNs = start

            while (written < payload.size && currentCoroutineContext().isActive) {
                val n = minOf(64 * 1024, (payload.size - written).toInt())
                output.write(payload, written.toInt(), n)
                written += n
                windowBytes += n

                val now = System.nanoTime()
                if (now - lastEmitNs >= EMIT_TICK_MS * 1_000_000L) {
                    val secs = (now - lastEmitNs) / 1e9
                    val instant = (windowBytes * 8.0) / (secs * 1_000_000.0)
                    ema = if (ema == 0.0) instant else 0.20 * instant + 0.80 * ema
                    val progress = (written.toFloat() / payload.size).coerceIn(0f, 0.99f)
                    onTick(progress, ema.coerceAtLeast(0.0))
                    windowBytes = 0
                    lastEmitNs = now
                }
            }
            output.flush()
            runCatching { connection.inputStream.read() } // drain response
            onTick(1f, ema.coerceAtLeast(0.0))
        } finally {
            runCatching { output?.flush(); output?.close() }
            runCatching { connection?.disconnect() }
        }
    }

    /** TCP-connect latency probe; null when unreachable within timeout. */
    internal fun probeLatency(timeoutMs: Int = 1_000): Long? {
        return try {
            Socket().use { s ->
                val t0 = System.nanoTime()
                s.connect(InetSocketAddress(LATENCY_HOST, LATENCY_PORT), timeoutMs)
                ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            }
        } catch (_: Exception) {
            null
        }
    }
}
