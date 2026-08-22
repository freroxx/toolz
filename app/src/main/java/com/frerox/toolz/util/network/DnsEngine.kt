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

import com.frerox.toolz.data.network.DnsBenchmarkMetrics
import com.frerox.toolz.data.network.DnsBenchmarkResult
import com.frerox.toolz.data.network.DnsCategory
import com.frerox.toolz.data.network.DnsProtocol
import com.frerox.toolz.data.network.DnsProvider
import com.frerox.toolz.data.network.DnsRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DnsEngine @Inject constructor() {

    private val providers get() = com.frerox.toolz.data.network.DnsProviderLibrary.providers

fun providerLibrary(): List<DnsProvider> = providers.distinctBy { it.hostname ?: it.id }

    fun customProvider(
        label: String,
        addresses: List<String>,
        hostname: String?
    ): DnsProvider {
        return DnsProvider(
            id = "custom-${label.lowercase().replace("\\s+".toRegex(), "-")}",
            name = label,
            addresses = addresses,
            hostname = hostname,
            categories = setOf(DnsCategory.SPEED, DnsCategory.PRIVACY),
            protocols = buildSet {
                add(DnsProtocol.DOH)
                if (!hostname.isNullOrBlank()) add(DnsProtocol.DOT)
            },
            description = "Custom provider configured by the user.",
            badge = "Custom",
            isCustom = true
        )
    }

    suspend fun benchmarkTopProviders(
        candidates: List<DnsProvider> = providers,
        limit: Int = 10,
        samplesPerProvider: Int = 4,
        timeoutMs: Int = 1200
    ): List<DnsBenchmarkResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            candidates
                .take(limit)
                .map { provider ->
                    async { benchmarkProvider(provider, samplesPerProvider, timeoutMs) }
                }
                .awaitAll()
                .sortedWith(
                    compareByDescending<DnsBenchmarkResult> { it.metrics.weightedScore }
                        .thenBy { it.metrics.latencyMs ?: Long.MAX_VALUE }
                )
                .mapIndexed { index, result ->
                    result.copy(rank = index + 1, isRecommended = index == 0)
                }
        }
    }

    suspend fun buildRecommendation(results: List<DnsBenchmarkResult>): DnsRecommendation? {
        val winner = results.maxByOrNull { it.metrics.weightedScore } ?: return null
        val metrics = winner.metrics
        val rationale = buildString {
            append("Weighted ")
            append(metrics.weightedScore)
            append("/100 from ")
            append(metrics.latencyMs ?: "timeout")
            append(" ms latency, ")
            append(metrics.jitterMs ?: "n/a")
            append(" ms jitter, ")
            append(metrics.packetLossPercent.toInt())
            append("% loss.")
        }
        return DnsRecommendation(
            provider = winner.provider,
            score = winner.metrics.weightedScore,
            rationale = rationale
        )
    }

    suspend fun benchmarkProvider(
        provider: DnsProvider,
        samplesPerProvider: Int = 3,
        timeoutMs: Int = 1500
    ): DnsBenchmarkResult = withContext(Dispatchers.IO) {
        // Initial warm-up ping
        measureLatency(provider.addresses.first(), timeoutMs = timeoutMs)
        
        val samples = (0 until samplesPerProvider).map {
            measureLatency(provider.addresses.first(), timeoutMs = timeoutMs)
        }
        val successfulSamples = samples.filterNotNull().sorted()
        
        // Use median for robustness
        val latency = if (successfulSamples.isNotEmpty()) {
            successfulSamples[successfulSamples.size / 2]
        } else {
            null
        }

        val jitter = if (successfulSamples.size > 1) {
            successfulSamples.zipWithNext { a, b -> abs(a - b) }.averageOrNull()?.toLong()
        } else {
            0L
        }
        val packetLoss = ((samplesPerProvider - successfulSamples.size) / samplesPerProvider.toFloat()) * 100f
        val weightedScore = calculateWeightedScore(latency, jitter, packetLoss)
        DnsBenchmarkResult(
            provider = provider,
            metrics = DnsBenchmarkMetrics(
                latencyMs = latency,
                jitterMs = jitter,
                packetLossPercent = packetLoss,
                weightedScore = weightedScore,
                samples = samples
            )
        )
    }

    suspend fun checkSingleLatency(address: String, timeoutMs: Int = 1500): Long? =
        withContext(Dispatchers.IO) {
            // Take 3 samples and return median
            val samples = (0 until 3).map { measureLatency(address, timeoutMs = timeoutMs) }
            val valid = samples.filterNotNull().sorted()
            if (valid.isNotEmpty()) valid[valid.size / 2] else null
        }

    private fun measureLatency(
        address: String,
        port: Int = 53,
        timeoutMs: Int
    ): Long? {
        return try {
            Socket().use { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(address, port), timeoutMs)
                ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateWeightedScore(
        latencyMs: Long?,
        jitterMs: Long?,
        packetLossPercent: Float
    ): Int = com.frerox.toolz.data.network.DnsProviderLibrary.weightedScore(latencyMs, jitterMs, packetLossPercent)

    private fun List<Long>.averageOrNull(): Double? {
        if (isEmpty()) return null
        return average()
    }
}
