package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.StabilityMetrics
import com.frerox.toolz.data.network.WifiInfoState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Production-grade network health calculator.
 * Single source of truth for 0-100 score. Used by both WifiTweaks and PowerSuite
 * to avoid the previous dual-formula drift.
 *
 * Weighted: RSSI 40% / PacketLoss 35% / Jitter 25%
 * Extra penalties: throttling, weak security, congestion.
 */
@Singleton
class NetworkHealthEngine @Inject constructor() {

    data class HealthBreakdown(
        val score: Int,
        val label: String,
        val rssiScore: Int,
        val lossScore: Int,
        val jitterScore: Int,
        val penalties: List<String>
    )

    fun calculate(
        rssi: Int,
        stability: StabilityMetrics,
        isThrottling: Boolean = false,
        openNetworksNearby: Int = 0
    ): HealthBreakdown {
        // RSSI curve: -30 best -> -90 worst, sigmoid-like
        val rssiNorm = ((-30 - rssi.coerceIn(-90, -30)) / 60f).coerceIn(0f, 1f)
        val rssiScore = ((1f - rssiNorm) * 100f).roundToInt()

        val lossScore = ((1.0 - stability.packetLossRate.coerceIn(0.0, 1.0)) * 100.0).roundToInt()
        val jitterScore = ((1.0 - (stability.jitterMs.coerceIn(0.0, 80.0) / 80.0)) * 100.0).roundToInt()

        var raw = rssiScore * 0.40 + lossScore * 0.35 + jitterScore * 0.25
        val penalties = mutableListOf<String>()
        if (isThrottling) { raw -= 8; penalties += "Scan throttling" }
        if (openNetworksNearby > 3) { raw -= 5; penalties += "Noisy environment" }
        if (stability.publicPingMs != null && stability.publicPingMs > 180) { raw -= 6; penalties += "High latency" }

        val final = raw.roundToInt().coerceIn(0, 100)
        val label = when {
            final >= 85 -> "Excellent"
            final >= 70 -> "Good"
            final >= 50 -> "Fair"
            final >= 30 -> "Poor"
            else -> "Critical"
        }
        return HealthBreakdown(final, label, rssiScore, lossScore, jitterScore, penalties)
    }

    fun calculateWifiState(wifi: WifiInfoState, stability: StabilityMetrics, throttling: Boolean, openNets: Int) =
        calculate(wifi.rssi, stability, throttling, openNets)
}
