package com.frerox.toolz.util.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedTestEngine @Inject constructor() {

    // 50MB test payload URL from Cloudflare edge
    private val testUrl = "https://speed.cloudflare.com/__down?bytes=52428800"

    fun runDownloadTest(): Flow<Pair<Float, Double>> = flow {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            val url = URL(testUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 Toolz/2.0")
            connection.connect()

            val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: 52428800L
            inputStream = connection.inputStream

            val buffer = ByteArray(32768) // 32KB buffer
            var totalBytesRead = 0L
            val testStartTime = System.currentTimeMillis()
            var lastEmitTime = testStartTime
            var bytesSinceLastEmit = 0L

            var smoothedSpeedMbps = 0.0
            val alpha = 0.1 // Smoother EMA for speed calculation

            // Target duration: 15 seconds test length for high precision
            val targetDurationMs = 15_000L
            val warmupMs = 800L

            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                totalBytesRead += read
                bytesSinceLastEmit += read

                val now = System.currentTimeMillis()
                val elapsedSinceStart = now - testStartTime
                val elapsedSinceEmit = now - lastEmitTime

                // Emit progress every 50ms for ultra-smooth UI progress updates
                if (elapsedSinceEmit >= 50L) {
                    val durationSeconds = elapsedSinceEmit / 1000.0
                    val currentSpeedMbps = (bytesSinceLastEmit * 8.0) / (durationSeconds * 1_000_000.0)

                    // Skip warmup period from throughput calculation
                    if (elapsedSinceStart > warmupMs) {
                        smoothedSpeedMbps = if (smoothedSpeedMbps == 0.0) {
                            currentSpeedMbps
                        } else {
                            alpha * currentSpeedMbps + (1 - alpha) * smoothedSpeedMbps
                        }
                    }

                    // Progress calculation: take the higher of byte progress or time progress for "moving" feel
                    // but capped at 1.0. We use a linear interpolation for a smoother feel.
                    val byteProgress = totalBytesRead.toFloat() / contentLength.toFloat()
                    val timeProgress = (elapsedSinceStart.toFloat() / targetDurationMs.toFloat())
                    val smoothProgress = (byteProgress * 0.7f + timeProgress * 0.3f).coerceIn(0f, 0.99f)

                    emit(smoothProgress to (smoothedSpeedMbps.coerceAtLeast(0.0)))

                    bytesSinceLastEmit = 0L
                    lastEmitTime = now
                }

                if (elapsedSinceStart >= targetDurationMs) {
                    break
                }
            }

            // Emit final result at 100% progress
            emit(1.0f to (smoothedSpeedMbps.coerceAtLeast(0.0)))
        } catch (e: Exception) {
            throw e
        } finally {
            runCatching { inputStream?.close() }
            runCatching { connection?.disconnect() }
        }
    }.flowOn(Dispatchers.IO)
}
