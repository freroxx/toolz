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

package com.frerox.toolz.data.network

data class WifiInfoState(
    val rssi: Int = 0,
    val linkSpeed: Int = 0,
    val gateway: String = "0.0.0.0",
    val ssid: String = "Unknown",
    val bssid: String = "Unknown",
    val frequency: Int = 0,
    val ipAddress: String = "0.0.0.0",
    val wifiStandard: String = "Unknown",
    val band: String = "Unknown",
    val channel: Int = 0,
    val signalLevel: Int = 0,
    val dnsServers: List<String> = emptyList(),
    val rssiHistory: List<Int> = emptyList()
)

data class ProcessNetworkUsage(
    val pid: Int,
    val name: String,
    val localAddr: String,
    val remoteAddr: String,
    val state: String,
    val protocol: String = "TCP",
    val rxKbps: Double = 0.0,
    val txKbps: Double = 0.0,
    val countryCode: String = "LAN",
    val countryFlag: String = ""
)

/** Waveform-style bufferbloat grade from latency increase under load. */
enum class BloatGrade(val letter: String, val maxDeltaMs: Long) {
    A_PLUS("A+", 5),
    A("A", 15),
    B("B", 25),
    C("C", 40),
    D("D", 70),
    F("F", Long.MAX_VALUE);

    companion object {
        /** Pure & testable: grade = f(loadedLatency − idleLatency). Null-safe for missing samples. */
        fun fromDelta(deltaMs: Long?): BloatGrade? {
            if (deltaMs == null) return null
            return entries.firstOrNull { deltaMs <= it.maxDeltaMs } ?: F
        }
    }
}

data class SpeedTestResult(
    val downloadSpeedMbps: Double = 0.0,
    val uploadSpeedMbps: Double = 0.0,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val phaseLabel: String = "Idle",
    val error: String? = null,
    val idleLatencyMs: Long? = null,
    val loadedLatencyMs: Long? = null,
    val bloatGrade: BloatGrade? = null
)

data class StabilityInfo(
    val target: String = "Gateway",
    val avgLatency: Long = 0,
    val jitter: Long = 0,
    val packetLoss: Float = 0f,
    val isTesting: Boolean = false,
    val score: Int = 0,
    val samplesCollected: Int = 0
)

data class PingSample(
    val timestampMillis: Long,
    val latencyMs: Long?
)

data class PublicIpInfo(
    val ip: String = "Unknown",
    val isp: String = "Unknown",
    val city: String = "Unknown",
    val country: String = "Unknown",
    val asn: String = "Unknown"
)

data class NetworkDevice(
    val ip: String,
    val mac: String = "Unknown",
    val hostname: String = "Unknown",
    val vendor: String = "Unknown",
    val typeLabel: String = "Generic Device",
    val latencyMs: Long? = null,
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
    val isGateway: Boolean = false
)

data class ScannedPort(
    val port: Int,
    val isOpen: Boolean,
    val service: String = "Unknown",
    val latencyMs: Long? = null
)

data class DnsLatency(
    val name: String,
    val address: String,
    val latencyMs: Long? = null,
    val isChecking: Boolean = false,
    val hostname: String? = null
)

enum class DnsCategory {
    PRIVACY,
    SPEED,
    SECURITY,
    FAMILY
}

enum class DnsProtocol {
    DOH,
    DOT
}

data class DnsProvider(
    val id: String,
    val name: String,
    val addresses: List<String>,
    val hostname: String? = null,
    val dohUrl: String? = null,
    val icon: String? = null,
    val categories: Set<DnsCategory> = setOf(DnsCategory.SPEED),
    val protocols: Set<DnsProtocol> = setOf(DnsProtocol.DOH, DnsProtocol.DOT),
    val description: String = "",
    val badge: String = "",
    val isCustom: Boolean = false
)

data class DnsBenchmarkMetrics(
    val latencyMs: Long? = null,
    val jitterMs: Long? = null,
    val packetLossPercent: Float = 0f,
    val weightedScore: Int = 0,
    val samples: List<Long?> = emptyList()
)

data class DnsBenchmarkResult(
    val provider: DnsProvider,
    val metrics: DnsBenchmarkMetrics = DnsBenchmarkMetrics(),
    val rank: Int = Int.MAX_VALUE,
    val isRecommended: Boolean = false
)

data class DnsRecommendation(
    val provider: DnsProvider,
    val score: Int,
    val rationale: String
)

data class DnsCacheAnalytics(
    val hitRatioPercent: Int? = null,
    val entryCount: Int? = null,
    val flushSupported: Boolean = false,
    val lastFlushEpochMs: Long? = null,
    val sourceSummary: String = "Unavailable"
)

data class TopologyNode(
    val id: String,
    val label: String,
    val detail: String,
    val tier: Int,
    val xBias: Float,
    val yBias: Float,
    val isPrimary: Boolean = false
)

data class TopologyEdge(
    val from: String,
    val to: String,
    val strength: Float = 1f
)

data class NetworkTopology(
    val nodes: List<TopologyNode> = emptyList(),
    val edges: List<TopologyEdge> = emptyList()
)

data class TraceHop(
    val hop: Int,
    val ip: String,
    val label: String = "Hop",
    val location: String = "Unknown",
    val latencyMs: Long? = null,
    val lossPercent: Float = 0f,
    val method: String = "Unknown"
)

data class RouterCredential(
    val brand: String,
    val username: String,
    val password: String,
    val model: String = "Default"
)

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class CellularAuditInfo(
    val tech: String = "Unknown",
    val cellId: String = "Unknown",
    val tac: String = "Unknown",
    val snr: String = "Unknown",
    val signalStrength: String = "Unknown",
    val mobileDataEnabled: Boolean? = null,
    val airplaneModeEnabled: Boolean? = null,
    val preferredNetworkMode: String = "Unknown",
    val dataSaverEnabled: Boolean? = null,
    val isAvailable: Boolean = false
)

data class IpAuditInfo(
    val routes: List<String> = emptyList(),
    val neighbors: List<String> = emptyList(),
    val interfaces: List<String> = emptyList(),
    val defaultRoute: String = "",
    val dnsServers: List<String> = emptyList(),
    val isAvailable: Boolean = false
)

enum class RecommendationSeverity {
    INFO, WARNING, CRITICAL
}

data class SmartFixRecommendation(
    val id: String,
    val title: String,
    val description: String = "",
    val reason: String = "",
    val tweakIds: List<String> = emptyList(),
    val severity: RecommendationSeverity = RecommendationSeverity.INFO,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val isActionable: Boolean = true
)

data class PrivilegedState(
    val isReachable: Boolean = false,
    val isAuthorized: Boolean = false,
    val isServiceReady: Boolean = false,
    val lastCommandSummary: String = "Idle"
)

data class NetworkPowerUiState(
    val wifiState: WifiInfoState = WifiInfoState(),
    val publicIpInfo: PublicIpInfo = PublicIpInfo(),
    val isRefreshingPublicIp: Boolean = false,
    val dnsResults: List<DnsBenchmarkResult> = emptyList(),
    val dnsRecommendation: DnsRecommendation? = null,
    val isRefreshingDns: Boolean = false,
    val cacheAnalytics: DnsCacheAnalytics = DnsCacheAnalytics(),
    val scannedDevices: List<NetworkDevice> = emptyList(),
    val topology: NetworkTopology = NetworkTopology(),
    val isScanningDevices: Boolean = false,
    val scannedPorts: List<ScannedPort> = emptyList(),
    val isScanningPorts: Boolean = false,
    val speedTestResult: SpeedTestResult = SpeedTestResult(),
    val stabilityInfo: StabilityInfo = StabilityInfo(),
    val pingSamples: List<PingSample> = emptyList(),
    val cellularAudit: CellularAuditInfo = CellularAuditInfo(),
    val ipAudit: IpAuditInfo = IpAuditInfo(),
    val activeProcesses: List<ProcessNetworkUsage> = emptyList(),
    val networkHealthScore: Int = 100,
    val isDataEnabled: Boolean = true,
    val vpnStatus: VpnStatus = VpnStatus.DISCONNECTED,
    val privilegedState: PrivilegedState = PrivilegedState(),
    val traceHops: List<TraceHop> = emptyList(),
    val privateDnsMode: String = "Automatic",
    val privateDnsHost: String = ""
)
