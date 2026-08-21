package com.frerox.toolz.util.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedTestEngine @Inject constructor() {

    private val mirrors = listOf(
        "https://speed.cloudflare.com/__down?bytes=25000000",
        "https://proof.ovh.net/files/10Mb.dat",
        "https://speed.hetzner.de/100MB.bin"
    )

    fun runDownloadTest(): Flow<Pair<Float, Double>> = flow {
        var lastError: Exception? = null
        for (urlString in mirrors) {
            try {
                emitAllFromUrl(urlString)
                return@flow
            } catch (e: Exception) {
                lastError = e
                if (!currentCoroutineContext().isActive) throw e
                // try next mirror
            }
        }
        throw lastError ?: IllegalStateException("No mirror available")
    }.flowOn(Dispatchers.IO)

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Pair<Float, Double>>.emitAllFromUrl(urlString: String) {
        var connection: HttpURLConnection? = null
        var input: InputStream? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Toolz/2.0 (SpeedTest)")
                instanceFollowRedirects = true
                connect()
            }
            if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")

            val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: 25_000_000L
            input = connection.inputStream
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            val start = System.currentTimeMillis()
            var lastEmit = start
            var windowBytes = 0L
            var ema = 0.0
            val alpha = 0.18
            val warmupMs = 700L
            val targetMs = 12_000L

            while (currentCoroutineContext().isActive) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                windowBytes += read
                val now = System.currentTimeMillis()
                val elapsedStart = now - start
                val elapsedWindow = now - lastEmit
                if (elapsedWindow >= 120L) {
                    val secs = elapsedWindow / 1000.0
                    val instant = (windowBytes * 8.0) / (secs * 1_000_000.0)
                    if (elapsedStart > warmupMs) {
                        ema = if (ema == 0.0) instant else alpha * instant + (1 - alpha) * ema
                    }
                    val byteProgress = (total.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                    val timeProgress = (elapsedStart.toFloat() / targetMs).coerceIn(0f, 1f)
                    val progress = (byteProgress * 0.65f + timeProgress * 0.35f).coerceIn(0f, 0.99f)
                    emit(progress to ema.coerceAtLeast(0.0))
                    windowBytes = 0L
                    lastEmit = now
                }
                if (elapsedStart >= targetMs) break
            }
            // final emit: compute total average excluding warmup for stability
            val totalSecs = ((System.currentTimeMillis() - start - warmupMs).coerceAtLeast(500L)) / 1000.0
            val avg = if (totalSecs > 0) (total * 8.0) / (totalSecs * 1_000_000.0) else ema
            emit(1f to (ema.takeIf { it > 0 } ?: avg).coerceAtLeast(0.0))
        } finally {
            runCatching { input?.close() }
            runCatching { connection?.disconnect() }
        }
    }
}
