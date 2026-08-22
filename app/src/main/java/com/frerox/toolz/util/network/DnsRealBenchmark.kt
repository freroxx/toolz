package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.DnsBenchmarkMetrics
import com.frerox.toolz.data.network.DnsBenchmarkResult
import com.frerox.toolz.data.network.DnsProvider
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * True DNS benchmark: issues a real DoH JSON query (google.com A) to the
 * provider's dohUrl and measures TTFB. Falls back to TCP:53 socket if DoH fails.
 * This reflects real resolver performance, not just SYN latency.
 */
@Singleton
class DnsRealBenchmark @Inject constructor(
    private val dnsEngine: DnsEngine
) {
    suspend fun benchmark(
        provider: DnsProvider,
        samples: Int = 3,
        timeoutMs: Int = 2500
    ): DnsBenchmarkResult = withContext(Dispatchers.IO) {
        val latencies = (0 until samples).mapNotNull {
            val doh = dohQuery(provider, timeoutMs) ?: tcpFallback(provider.addresses.firstOrNull() ?: return@mapNotNull null, timeoutMs)
            doh
        }
        val sorted = latencies.sorted()
        val median = sorted.getOrNull(sorted.size / 2)
        val jitter = if (sorted.size > 1) sorted.zipWithNext { a, b -> abs(a - b) }.average().toLong() else 0L
        val loss = ((samples - latencies.size) / samples.toFloat()) * 100f
        val score = score(median, jitter, loss)
        DnsBenchmarkResult(
            provider = provider,
            metrics = DnsBenchmarkMetrics(
                latencyMs = median,
                jitterMs = jitter,
                packetLossPercent = loss,
                weightedScore = score,
                samples = latencies.map { it as Long? } + List(samples - latencies.size) { null }
            )
        )
    }

    /**
     * P4: DNS-over-TLS reachability — TLS handshake on :853, returns RTT ms or null.
     * Non-null ⇒ provider is "DoT-capable" (what Android Private DNS actually uses).
     */
    suspend fun dotProbe(hostname: String?, timeoutMs: Int = 1500): Long? = withContext(Dispatchers.IO) {
        if (hostname.isNullOrBlank()) return@withContext null
        try {
            val factory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
            val socket = factory.createSocket(hostname, 853) as javax.net.ssl.SSLSocket
            try {
                val t0 = System.nanoTime()
                socket.soTimeout = timeoutMs
                socket.startHandshake()
                ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            } finally {
                runCatching { socket.close() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun dohQuery(provider: DnsProvider, timeoutMs: Int): Long? {
        val doh = provider.dohUrl ?: return null
        // Use ?name=google.com&type=A minimal query; add cache-bust
        val urlStr = when {
            doh.contains("cloudflare") -> "$doh?name=google.com&type=A"
            doh.contains("dns.google") -> "$doh?name=google.com&type=1"
            else -> "$doh?name=google.com"
        }
        return try {
            val t0 = System.nanoTime()
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("accept", "application/dns-json")
                setRequestProperty("User-Agent", "Toolz-DNS/1.0")
                // Force no-cache
                setRequestProperty("Cache-Control", "no-cache")
            }
            conn.connect()
            val ok = conn.responseCode in 200..299
            conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            if (!ok) null else ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) { null }
    }

    private fun tcpFallback(address: String, timeoutMs: Int): Long? {
        return try {
            val t0 = System.nanoTime()
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(address, 53), timeoutMs) }
            ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        } catch (_: Exception) { null }
    }

    private fun score(latency: Long?, jitter: Long?, loss: Float): Int =
        com.frerox.toolz.data.network.DnsProviderLibrary.weightedScore(latency, jitter, loss)
}
