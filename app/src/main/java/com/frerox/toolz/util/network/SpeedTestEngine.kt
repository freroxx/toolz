package com.frerox.toolz.util.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedTestEngine @Inject constructor() {

    // Using a 25MB file for more precision
    private val testUrl = "https://speed.cloudflare.com/__down?bytes=26214400"
    private val WARMUP_PERIOD_MS = 500L
    private val WINDOW_SIZE = 5

    fun runDownloadTest(): Flow<Pair<Float, Double>> = flow {
        try {
            val url = URL(testUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val totalBytes = 26214400L // 25MB
            var bytesRead = 0L
            val buffer = ByteArray(16384) // Larger buffer
            
            val startTime = System.currentTimeMillis()
            val speedWindow = mutableListOf<Double>()
            var lastUpdateTime = startTime
            var bytesSinceLastUpdate = 0L
            
            connection.getInputStream().use { input ->
                var read = input.read(buffer)
                while (read != -1) {
                    bytesRead += read
                    bytesSinceLastUpdate += read
                    
                    val currentTime = System.currentTimeMillis()
                    val durationSinceStart = currentTime - startTime
                    
                    // Skip warmup period for measurement
                    if (durationSinceStart > WARMUP_PERIOD_MS) {
                        val updateDuration = (currentTime - lastUpdateTime) / 1000.0
                        if (updateDuration >= 0.2) { // Update every 200ms
                            val currentMbps = (bytesSinceLastUpdate * 8.0) / (updateDuration * 1024 * 1024)
                            
                            speedWindow.add(currentMbps)
                            if (speedWindow.size > WINDOW_SIZE) speedWindow.removeAt(0)
                            
                            val smoothedMbps = speedWindow.average()
                            val progress = (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            
                            emit(progress to smoothedMbps)
                            
                            bytesSinceLastUpdate = 0
                            lastUpdateTime = currentTime
                        }
                    } else {
                        // Just emit progress during warmup with 0 speed
                        val progress = (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        emit(progress to 0.0)
                    }
                    
                    read = input.read(buffer)
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
