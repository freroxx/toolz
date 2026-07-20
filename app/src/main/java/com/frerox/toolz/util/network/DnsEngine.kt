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

    private val providers = listOf(
        DnsProvider(
            id = "cloudflare",
            name = "Cloudflare",
            addresses = listOf("1.1.1.1", "1.0.0.1"),
            hostname = "1dot1dot1dot1.cloudflare-dns.com",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            categories = setOf(DnsCategory.SPEED, DnsCategory.PRIVACY),
            description = "Low-latency global resolver with strong uptime.",
            badge = "Fast path"
        ),
        DnsProvider(
            id = "google",
            name = "Google Public DNS",
            addresses = listOf("8.8.8.8", "8.8.4.4"),
            hostname = "dns.google",
            dohUrl = "https://dns.google/dns-query",
            categories = setOf(DnsCategory.SPEED, DnsCategory.SECURITY),
            description = "Reliable anycast DNS with broad regional coverage.",
            badge = "Balanced"
        ),
        DnsProvider(
            id = "quad9",
            name = "Quad9",
            addresses = listOf("9.9.9.9", "149.112.112.112"),
            hostname = "dns.quad9.net",
            dohUrl = "https://dns.quad9.net/dns-query",
            categories = setOf(DnsCategory.SECURITY, DnsCategory.PRIVACY),
            description = "Threat-filtering resolver tuned for malicious domain blocking.",
            badge = "Shielded"
        ),
        DnsProvider(
            id = "adguard",
            name = "AdGuard",
            addresses = listOf("94.140.14.14", "94.140.15.15"),
            hostname = "dns.adguard.com",
            dohUrl = "https://dns.adguard-dns.com/dns-query",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SECURITY),
            description = "Privacy-forward resolver with filtering variants.",
            badge = "Privacy"
        ),
        DnsProvider(
            id = "nextdns",
            name = "NextDNS",
            addresses = listOf("45.90.28.0", "45.90.30.0"),
            hostname = "dns.nextdns.io",
            dohUrl = "https://dns.nextdns.io",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SECURITY),
            description = "Customizable filtering and analytics platform.",
            badge = "Custom"
        ),
        DnsProvider(
            id = "mullvad",
            name = "Mullvad DNS",
            addresses = listOf("194.242.2.2", "194.242.2.3"),
            hostname = "dns.mullvad.net",
            dohUrl = "https://dns.mullvad.net/dns-query",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SECURITY),
            description = "Minimal-logging resolver from the Mullvad privacy stack.",
            badge = "No logs"
        ),
        DnsProvider(
            id = "opendns",
            name = "OpenDNS",
            addresses = listOf("208.67.222.222", "208.67.220.220"),
            hostname = "doh.opendns.com",
            dohUrl = "https://doh.opendns.com/dns-query",
            categories = setOf(DnsCategory.SECURITY, DnsCategory.FAMILY),
            description = "Cisco-backed resolver with family-safe variants.",
            badge = "Family"
        ),
        DnsProvider(
            id = "cleanbrowsing",
            name = "CleanBrowsing",
            addresses = listOf("185.228.168.168", "185.228.169.168"),
            hostname = "security-filter-dns.cleanbrowsing.org",
            dohUrl = "https://doh.cleanbrowsing.org/doh/security-filter/",
            categories = setOf(DnsCategory.FAMILY, DnsCategory.SECURITY),
            description = "Family and security filtering built into the resolver.",
            badge = "Guardrail"
        ),
        DnsProvider(
            id = "controld",
            name = "Control D",
            addresses = listOf("76.76.2.0", "76.76.10.0"),
            hostname = "freedns.controld.com",
            dohUrl = "https://freedns.controld.com/p2",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.FAMILY),
            description = "Flexible profiles for privacy, ad blocking, and family controls.",
            badge = "Flexible"
        ),
        DnsProvider(
            id = "mullvad_extended",
            name = "Mullvad Extended",
            addresses = listOf("194.242.2.3", "194.242.2.4"),
            hostname = "extended.dns.mullvad.net",
            dohUrl = "https://extended.dns.mullvad.net/dns-query",
            categories = setOf(DnsCategory.PRIVACY, DnsCategory.SECURITY),
            description = "Privacy resolver with aggressive ad and tracker blocking.",
            badge = "Full Block"
        ),
        DnsProvider(
            id = "cleanbrowsing_security",
            name = "CleanBrowsing Security",
            addresses = listOf("185.228.168.168", "185.228.169.168"),
            hostname = "security-filter-dns.cleanbrowsing.org",
            dohUrl = "https://doh.cleanbrowsing.org/doh/security-filter/",
            categories = setOf(DnsCategory.SECURITY),
            description = "Filters malware, phishing, and other malicious sites.",
            badge = "Hardened"
        ),
        DnsProvider(
            id = "comodo",
            name = "Comodo Secure DNS",
            addresses = listOf("8.26.56.26", "8.20.247.20"),
            categories = setOf(DnsCategory.SECURITY),
            protocols = setOf(DnsProtocol.DOH),
            description = "Legacy malware-focused filtering resolver.",
            badge = "Security"
        )
    )

    fun providerLibrary(): List<DnsProvider> = providers

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
    ): Int {
        if (latencyMs == null) return 0
        val latencyScore = (100 - ((latencyMs - 10) * 0.85f)).coerceIn(0f, 100f)
        val jitterScore = (100 - ((jitterMs ?: 0L) * 3f)).coerceIn(0f, 100f)
        val packetScore = (100 - (packetLossPercent * 2f)).coerceIn(0f, 100f)
        return (
            latencyScore * 0.60f +
                jitterScore * 0.25f +
                packetScore * 0.15f
            ).toInt().coerceIn(0, 100)
    }

    private fun List<Long>.averageOrNull(): Double? {
        if (isEmpty()) return null
        return average()
    }
}
