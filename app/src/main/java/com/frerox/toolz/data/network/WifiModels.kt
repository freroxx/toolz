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

import androidx.compose.ui.graphics.vector.ImageVector

data class WifiScanResult(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val channel: Int,
    val band: String,
    val security: String,
    val isHidden: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class RssiHistoryPoint(
    val rssi: Int,
    val timestamp: Long
)

data class StabilityMetrics(
    val gatewayPingMs: Long? = null,
    val dnsPingMs: Long? = null,
    val publicPingMs: Long? = null,
    val jitterMs: Double = 0.0,
    val packetLossRate: Double = 0.0,
    val history: List<Long> = emptyList()
)

data class WifiTweak(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val type: TweakType,
    val category: TweakCategory = TweakCategory.PERFORMANCE,
    val applyCommands: List<String> = emptyList(),
    val revertCommands: List<String> = emptyList(),
    val verificationCommand: String? = null,
    val manualSteps: List<String> = emptyList(),
    val riskNote: String? = null,
    /** Known vendor quirks keyed by lowercase manufacturer substring. */
    val oemNotes: Map<String, String> = emptyMap()
)

data class WifiOptimizationProfile(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val tweakIds: List<String>,
    val accentLabel: String,
    val requiresShizuku: Boolean = true
)

enum class TweakType {
    SHIZUKU_ONLY,
    MANUAL_GUIDE,
    SHIZUKU_OR_GUIDE
}

enum class TweakCategory {
    PERFORMANCE,
    STABILITY,
    PRIVACY,
    POWER
}

enum class TweakStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
    UNSUPPORTED,
    MANUAL
}

enum class WifiScanSortMode {
    SIGNAL,
    CHANNEL,
    SECURITY,
    NAME
}

data class TweakResult(
    val id: String,
    val status: TweakStatus = TweakStatus.IDLE,
    val message: String = "",
    val isApplied: Boolean = false,
    /** true = read-back confirmed · false = read-back mismatched · null = not verifiable */
    val verified: Boolean? = null,
    val lastUpdatedMs: Long = 0L
)

/** Honest tri-state outcome of probing a tweak's real system state. */
enum class VerifyState { APPLIED_VERIFIED, APPLIED_UNVERIFIED, NOT_APPLIED }

data class ChannelCongestion(
    val channel: Int,
    val networkCount: Int,
    val averageRssi: Double,
    val isRecommended: Boolean = false,
    val band: String = "2.4 GHz",
    val maxRssi: Int = -100,
    val utilizationScore: Int = 0 // 0-100
)

data class WifiSecurityAudit(
    val grade: Int, // 0-100
    val label: String, // Excellent/Bad/Open
    val findings: List<String>,
    val isPmfSupported: Boolean = false
)

data class WifiChannelReport(
    val congestion: List<ChannelCongestion>,
    val spectrumBands: List<com.frerox.toolz.util.network.WifiSpectrumAnalyzer.SpectrumBand> = emptyList(),
    val best24Ghz: Int? = null,
    val best5Ghz: Int? = null,
    val best6Ghz: Int? = null
)

data class NetworkConfigInfo(
    val ip: String = "-",
    val gateway: String = "-",
    val subnet: String = "-",
    val dns1: String = "-",
    val dns2: String = "-",
    val wifiStandard: String = "-",
    val band: String = "-",
    val channel: Int = 0,
    val linkSpeed: Int = 0,
    val frequency: Int = 0,
    val bssid: String = "-",
    val macAddress: String = "-",
    val security: String = "-",
    val channelWidthMhz: Int = 20,
    val securityAudit: WifiSecurityAudit? = null,
    val privateDnsActive: Boolean = false,
    val privateDnsServerName: String = "-",
    val isConnected: Boolean = false,
    val wifi6ECapable: Boolean = false,
    val wifi7Capable: Boolean = false,
    val isThrottlingEnabled: Boolean = false
)

data class ShizukuStatus(
    val isReachable: Boolean = false,
    val isAuthorized: Boolean = false,
    val isServiceReady: Boolean = false,
    val detail: String = "Not connected"
)

data class NetworkAdvice(
    val healthScore: Int = 0,
    val summary: String = "Start a scan to map the room.",
    val strongestNetwork: String = "-",
    val best24GhzChannel: Int? = null,
    val best5GhzChannel: Int? = null,
    val openNetworks: Int = 0,
    val hiddenNetworks: Int = 0,
    val totalNetworks: Int = 0,
    val recommendation: String = "Scan nearby networks to get channel guidance."
)

data class DiagnosticLog(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel {
    INFO, WARNING, ERROR, SUCCESS
}

data class WifiDnsBenchmarkResult(
    val providerId: String,
    val name: String,
    val hostname: String,
    val latencyMs: Long? = null,
    val isRecommended: Boolean = false
)

enum class BenchmarkStatus {
    IDLE, RUNNING, COMPLETED
}

data class WifiTweaksUiState(
    val currentSsid: String = "Not connected",
    val currentRssi: Int = -100,
    val isWifiEnabled: Boolean = false,
    val locationEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isRefreshingTweakStates: Boolean = false,
    val hasPartialWifiPermissions: Boolean = false,
    val isBenchmarkingDns: Boolean = false,
    val dnsBenchmarkStatus: BenchmarkStatus = BenchmarkStatus.IDLE,
    val dnsBenchmarkResults: List<WifiDnsBenchmarkResult> = emptyList(),
    val audioFeedbackEnabled: Boolean = false,
    val rssiHistory: List<RssiHistoryPoint> = emptyList(),
    val pingHistory: List<Long> = emptyList(),
    val scanResults: List<WifiScanResult> = emptyList(),
    val congestion: List<ChannelCongestion> = emptyList(),
    val networkConfig: NetworkConfigInfo = NetworkConfigInfo(),
    val advice: NetworkAdvice = NetworkAdvice(),
    val shizukuStatus: ShizukuStatus = ShizukuStatus(),
    val tweaks: List<WifiTweak> = emptyList(),
    val profiles: List<WifiOptimizationProfile> = emptyList(),
    val tweakResults: Map<String, TweakResult> = emptyMap(),
    val stability: StabilityMetrics = StabilityMetrics(),
    val diagnosticLogs: List<DiagnosticLog> = emptyList(),
    val showHiddenNetworks: Boolean = false,
    val scanSortMode: WifiScanSortMode = WifiScanSortMode.SIGNAL,
    val lastScanTimestamp: Long? = null,
    val lastActionMessage: String? = null,
    val speedTest: SpeedTestResult = SpeedTestResult(),
    val traceHops: List<TraceHop> = emptyList(),
    val isTracing: Boolean = false,
    val activeProcesses: List<ProcessNetworkUsage> = emptyList(),
    val privateDnsMode: String = "Automatic",
    val privateDnsHost: String = "",
    val selectedBenchmarkProviders: Set<String> = emptySet(),
    val traceHistory: List<String> = emptyList(),
    val lastTraceTarget: String = "1.1.1.1",
    val showDisclaimer: Boolean = false,
    val consoleEnabled: Boolean = false
)
