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
    val riskNote: String? = null
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
    val lastUpdatedMs: Long = 0L
)

data class ChannelCongestion(
    val channel: Int,
    val networkCount: Int,
    val averageRssi: Double,
    val isRecommended: Boolean = false,
    val band: String = "2.4 GHz"
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
    val lastActionMessage: String? = null
)
