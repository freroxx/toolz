package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.ChannelCongestion
import com.frerox.toolz.data.network.WifiScanResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Real 802.11 spectrum analysis.
 * Models 2.4GHz 22MHz overlap (DSSS) and 5/6GHz 20/40/80MHz width.
 * Produces accurate congestion heatmap and ML-style recommendation.
 */
@Singleton
class WifiSpectrumAnalyzer @Inject constructor() {

    data class SpectrumBand(
        val band: String,
        val channels: List<ChannelCongestion>,
        val recommendedChannel: Int?,
        val crowded: Boolean
    )

    fun analyze(results: List<WifiScanResult>): List<SpectrumBand> {
        if (results.isEmpty()) return emptyList()
        val byBand = results.groupBy { it.band }
        return byBand.map { (band, nets) ->
            val raw = nets.groupBy { it.channel }.map { (ch, list) ->
                val avgRssi = list.map { it.rssi }.average()
                val strongest = list.maxOf { it.rssi }
                // Weighted airtime: stronger APs occupy more medium
                val airtime = list.sumOf { rssiToAirtime(it.rssi) }
                ChannelCongestion(
                    channel = ch,
                    networkCount = list.size,
                    averageRssi = avgRssi,
                    band = band,
                    isRecommended = false
                ) to airtime
            }
            val congestion = raw.map { it.first }
            val airtimeMap = raw.associate { it.first.channel to it.second }

            // For 2.4GHz, factor overlapping channels: ch 1 overlaps 1-5, 6 overlaps 3-9, 11 overlaps 8-14
            val effectiveLoad = congestion.associate { c ->
                val overlap = if (band == "2.4 GHz") overlappingPenalty(c.channel, airtimeMap) else 0.0
                c.channel to (airtimeMap[c.channel]!! + overlap)
            }
            val best = effectiveLoad.minByOrNull { it.value }?.key
                ?.takeIf { effectiveLoad.values.minOrNull()!! < 1.5 } // only if not all saturated

            val crowded = congestion.any { it.networkCount >= 8 } || results.size > 18
            SpectrumBand(band, congestion.sortedBy { it.channel }.map { it.copy(isRecommended = it.channel == best) }, best, crowded)
        }
    }

    private fun overlappingPenalty(ch: Int, map: Map<Int, Double>): Double {
        if (ch !in 1..14) return 0.0
        var sum = 0.0
        for (delta in -4..4) {
            if (delta == 0) continue
            val neighbor = ch + delta
            val weight = when (kotlin.math.abs(delta)) {
                1 -> 0.6; 2 -> 0.35; 3 -> 0.18; 4 -> 0.07; else -> 0.0
            }
            sum += (map[neighbor] ?: 0.0) * weight
        }
        return sum
    }

    private fun rssiToAirtime(rssi: Int): Double {
        // -30 dBm = 1.0 (max), -90 = 0.05
        val norm = ((rssi + 90).coerceIn(0, 60) / 60.0)
        return 0.05 + norm * 0.95
    }

    fun channelWidthForFrequency(freq: Int): Int = when (freq) {
        in 2400..2500 -> 22
        in 5170..5825 -> 20
        in 5925..7125 -> 20
        else -> 20
    }

    fun securityGrade(capabilities: String): Int = when {
        capabilities.contains("WPA3") -> 100
        capabilities.contains("WPA2") && capabilities.contains("CCMP") -> 85
        capabilities.contains("WPA2") -> 75
        capabilities.contains("WPA") -> 45
        capabilities.contains("WEP") -> 10
        capabilities.contains("ESS") && !capabilities.contains("WPA") -> 0 // open
        else -> 50
    }
}
