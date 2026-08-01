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

package com.frerox.toolz.ui.screens.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.network.*
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.network.NetworkMonitor
import com.frerox.toolz.util.network.PrivilegedNetworkManager
import com.frerox.toolz.util.network.SpeedTestEngine
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class WifiTweaksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val shizukuExecutor: ShizukuShellExecutor,
    private val speedTestEngine: SpeedTestEngine,
    private val privilegedNetworkManager: PrivilegedNetworkManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @Suppress("DEPRECATION")
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val tweakCatalog = buildTweaks()
    private val profileCatalog = buildProfiles()
    private var rawScanResults: List<WifiScanResult> = emptyList()
    private var toneGenerator: ToneGenerator? = null
    private var lastBeepTime = 0L

    private val _uiState = MutableStateFlow(
        WifiTweaksUiState(
            tweaks = tweakCatalog,
            profiles = profileCatalog,
            tweakResults = tweakCatalog.associate { it.id to TweakResult(id = it.id) }
        )
    )
    val uiState: StateFlow<WifiTweaksUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            viewModelScope.launch {
                refreshShizukuState(message = "Shizuku permission granted.")
            }
        } else {
            viewModelScope.launch {
                emitMessage("Shizuku permission denied.")
            }
        }
    }

    private val shizukuBinderDeathListener = Shizuku.OnBinderDeadListener {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    shizukuStatus = ShizukuStatus(
                        isReachable = false,
                        detail = "Shizuku binder died."
                    )
                )
            }
            emitMessage("Shizuku connection lost. Retrying in 3s...")
            kotlinx.coroutines.delay(3000)
            refreshShizukuState(refreshTweakStates = true, message = "Attempting to reconnect Shizuku...")
        }
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModelScope.launch {
            refreshShizukuState(message = "Shizuku binder received.")
        }
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateScanResults()
        }
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeathListener)
            Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
        } catch (_: Exception) {
        }

        viewModelScope.launch {
            settingsRepository.networkAutoConnectShizuku.collect { autoConnect ->
                if (autoConnect && shizukuExecutor.isShizukuAvailable() && !uiState.value.shizukuStatus.isServiceReady) {
                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        refreshShizukuState(true)
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(
                settingsRepository.networkBenchmarkServers,
                settingsRepository.networkLastTraceTarget,
                settingsRepository.networkDisclaimerShown
            ) { servers, traceTarget, disclaimerShown ->
                Triple(servers, traceTarget, disclaimerShown)
            }.collect { (servers, traceTarget, disclaimerShown) ->
                val finalServers = if (servers.isEmpty()) dnsProviders.map { it.first }.toSet() else servers
                _uiState.update { it.copy(
                    selectedBenchmarkProviders = finalServers,
                    lastTraceTarget = traceTarget,
                    showDisclaimer = !disclaimerShown
                ) }
            }
        }

        registerScanReceiver()
        startTelemetryRefresh()
        refreshEnvironment()
        startSignalMonitoring()
        startStabilityCheck()
    }

    private fun startTelemetryRefresh() {
        viewModelScope.launch {
            while (isActive) {
                if (uiState.value.shizukuStatus.isServiceReady) {
                    try {
                        val ipAudit = privilegedNetworkManager.readIpAudit()
                        val processes = privilegedNetworkManager.readProcessUsage()
                        val dnsConfig = privilegedNetworkManager.readPrivateDnsConfig()

                        _uiState.update {
                            it.copy(
                                networkConfig = it.networkConfig.copy(
                                    ip = ipAudit.interfaces.firstOrNull() ?: it.networkConfig.ip,
                                    gateway = ipAudit.defaultRoute.ifBlank { it.networkConfig.gateway }
                                ),
                                activeProcesses = processes,
                                privateDnsMode = dnsConfig.first.replaceFirstChar(Char::titlecase),
                                privateDnsHost = dnsConfig.second
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
                delay(10_000)
            }
        }
    }

    fun runSpeedTest() {
        if (uiState.value.speedTest.isRunning) return

        viewModelScope.launch {
            _uiState.update { it.copy(speedTest = it.speedTest.copy(isRunning = true, progress = 0f, phaseLabel = "Warming up...")) }
            try {
                speedTestEngine.runDownloadTest().collect { (progress, speed) ->
                    _uiState.update {
                        it.copy(
                            speedTest = it.speedTest.copy(
                                downloadSpeedMbps = speed,
                                progress = progress,
                                phaseLabel = "Downloading..."
                            )
                        )
                    }
                }
                _uiState.update { it.copy(speedTest = it.speedTest.copy(isRunning = false, progress = 1f, phaseLabel = "Completed")) }
            } catch (e: Exception) {
                _uiState.update { it.copy(speedTest = it.speedTest.copy(isRunning = false, error = e.message, phaseLabel = "Error")) }
            }
        }
    }

    fun runTraceRoute(target: String = "1.1.1.1") {
        viewModelScope.launch {
            if (!uiState.value.shizukuStatus.isServiceReady) {
                emitMessage("Shizuku access is required to run traceroute.")
                return@launch
            }
            
            settingsRepository.setNetworkLastTraceTarget(target)
            
            addLog("TRACE", "Starting trace to $target", LogLevel.INFO)
            _uiState.update { it.copy(traceHops = emptyList(), isTracing = true) }
            
            val hops = privilegedNetworkManager.runTraceRoute(target)
            if (hops.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        traceHops = hops,
                        isTracing = false,
                        traceHistory = (listOf(target) + state.traceHistory).distinct().take(10)
                    )
                }
                addLog("TRACE", "Trace completed with ${hops.size} hops", LogLevel.SUCCESS)
            } else {
                _uiState.update { it.copy(isTracing = false) }
                addLog("TRACE", "Trace failed or returned no results", LogLevel.ERROR)
            }
        }
    }

    fun startScan() {
        if (!hasWifiPermissions()) return
        if (!wifiManager.isWifiEnabled) {
            emitMessage("Enable Wi-Fi first.")
            return
        }
        _uiState.update { it.copy(isScanning = true) }
        @Suppress("DEPRECATION")
        val success = wifiManager.startScan()
        if (!success) {
            _uiState.update { it.copy(isScanning = false) }
            emitMessage("System throttled scan. Try again in a few seconds.")
        }
    }

    fun refreshEnvironment() {
        viewModelScope.launch {
            refreshEnvironmentInternal(refreshTweakStates = true, runFreshScan = true)
        }
    }

    fun setAudioFeedback(enabled: Boolean) {
        _uiState.update { it.copy(audioFeedbackEnabled = enabled) }
        if (!enabled) {
            toneGenerator?.release()
            toneGenerator = null
        }
    }

    fun setShowHiddenNetworks(show: Boolean) {
        _uiState.update { it.copy(showHiddenNetworks = show) }
        updateVisibleScanResults()
    }

    fun setScanSortMode(mode: WifiScanSortMode) {
        _uiState.update { it.copy(scanSortMode = mode) }
        updateVisibleScanResults()
    }

    fun applyTweak(tweak: WifiTweak) {
        viewModelScope.launch {
            applyTweakInternal(tweak)
        }
    }

    fun undoTweak(tweak: WifiTweak) {
        viewModelScope.launch {
            if (!requireShizukuFor(tweak)) return@launch

            setTweakResult(
                tweak.id,
                status = TweakStatus.RUNNING,
                message = "Reverting..."
            )
            val result = runCommands(tweak.revertCommands)
            if (!result.first) {
                setTweakResult(
                    tweak.id,
                    status = TweakStatus.FAILED,
                    message = result.second.ifBlank { "Revert failed." }
                )
                emitMessage("Failed to revert ${tweak.title}.")
                return@launch
            }

            val applied = isTweakApplied(tweak)
            setTweakResult(
                tweak.id,
                status = if (applied) TweakStatus.SUCCESS else TweakStatus.IDLE,
                message = if (applied) "Still active" else "Default restored",
                isApplied = applied
            )
            refreshEnvironmentInternal(refreshTweakStates = true, runFreshScan = false)
            emitMessage("${tweak.title} reverted.")
        }
    }

    fun applyProfile(profile: WifiOptimizationProfile) {
        viewModelScope.launch {
            emitMessage("Applying profile: ${profile.title}...")
            var allSuccess = true
            profile.tweakIds.forEach { id ->
                val tweak = tweakCatalog.find { it.id == id }
                if (tweak != null) {
                    val success = applyTweakInternal(tweak, emitStatusMessage = false)
                    if (!success) allSuccess = false
                }
            }
            if (allSuccess) {
                emitMessage("${profile.title} applied successfully.")
            } else {
                emitMessage("${profile.title} applied with some failures.")
            }
            refreshEnvironmentInternal(refreshTweakStates = true, runFreshScan = false)
        }
    }

    fun restoreAutomaticPrivateDns() {
        viewModelScope.launch {
            if (!_uiState.value.shizukuStatus.isServiceReady) {
                emitMessage("Shizuku required to reset Private DNS.")
                return@launch
            }
            emitMessage("Resetting Private DNS...")
            runCommands(
                listOf(
                    "settings put global private_dns_mode opportunistic",
                    "settings delete global private_dns_spec"
                )
            )
            refreshEnvironmentInternal(refreshTweakStates = true, runFreshScan = false)
            emitMessage("Private DNS reset to Automatic.")
        }
    }

    fun buildDiagnosticSummary(): String {
        val state = _uiState.value
        val config = state.networkConfig
        val sb = StringBuilder()
        sb.append("Wi-Fi Diagnostic Summary\n")
        sb.append("========================\n\n")
        sb.append("Network: ${state.currentSsid}\n")
        sb.append("Signal: ${state.currentRssi} dBm\n")
        sb.append("Standard: ${config.wifiStandard}\n")
        sb.append("Band: ${config.band} (Channel ${config.channel})\n")
        sb.append("Link Speed: ${config.linkSpeed} Mbps\n\n")

        sb.append("Stability Metrics:\n")
        sb.append("- Gateway Ping: ${state.stability.gatewayPingMs ?: "N/A"} ms\n")
        sb.append("- DNS Ping: ${state.stability.dnsPingMs ?: "N/A"} ms\n")
        sb.append("- Public Ping: ${state.stability.publicPingMs ?: "N/A"} ms\n")
        sb.append("- Jitter: ${"%.2f".format(state.stability.jitterMs)} ms\n")
        sb.append("- Packet Loss: ${"%.1f%%".format(state.stability.packetLossRate * 100)}\n\n")

        sb.append("Applied Tweaks:\n")
        val appliedCount = state.tweakResults.values.count { it.isApplied }
        if (appliedCount == 0) sb.append("- None\n")
        else {
            state.tweakResults.forEach { (id, result) ->
                if (result.isApplied) {
                    val tweak = tweakCatalog.find { it.id == id }
                    sb.append("- ${tweak?.title ?: id}\n")
                }
            }
        }
        return sb.toString()
    }

    fun clearLastActionMessage() {
        _uiState.update { it.copy(lastActionMessage = null) }
    }

    fun dismissDisclaimer() {
        viewModelScope.launch {
            settingsRepository.setNetworkDisclaimerShown(true)
        }
    }

    fun fixMyConnection() {
        viewModelScope.launch {
            emitMessage("Starting Emergency Fix...")
            addLog("DIAG", "Initiating 'Fix My Connection' macro", LogLevel.INFO)
            
            // 1. Disable Throttling
            val throttleTweak = tweakCatalog.find { it.id == "scan_throttle" }
            if (throttleTweak != null) {
                applyTweakInternal(throttleTweak, true)
                addLog("FIX", "Disabled scan throttling", LogLevel.SUCCESS)
            }
            
            // 2. Enable Aggressive Roaming
            val roamingTweak = tweakCatalog.find { it.id == "aggressive_roaming" }
            if (roamingTweak != null) {
                applyTweakInternal(roamingTweak, true)
                addLog("FIX", "Enabled aggressive roaming", LogLevel.SUCCESS)
            }
            
            // 3. Apply Quad9 DNS
            val dnsTweak = tweakCatalog.find { it.id == "private_dns_quad9" }
            if (dnsTweak != null) {
                applyTweakInternal(dnsTweak, true)
                addLog("FIX", "Applied Quad9 DNS", LogLevel.SUCCESS)
            }
            
            emitMessage("Connection fixes applied.")
            refreshEnvironment()
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            emitMessage("Resetting all tweaks...")
            addLog("RESET", "Reverting all optimization tweaks", LogLevel.WARNING)
            
            tweakCatalog.forEach { tweak ->
                if (tweak.revertCommands.isNotEmpty()) {
                    undoTweak(tweak)
                }
            }
            
            emitMessage("All settings reset to default.")
            refreshEnvironment()
        }
    }

    fun addLog(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        _uiState.update { state ->
            val newLogs = (listOf(DiagnosticLog(tag = tag, message = message, level = level)) + state.diagnosticLogs)
                .take(100)
            state.copy(diagnosticLogs = newLogs)
        }
    }

    fun executeRawCommand(command: String) {
        viewModelScope.launch {
            if (!_uiState.value.shizukuStatus.isServiceReady) {
                emitMessage("Shizuku service required to run shell commands.")
                return@launch
            }
            addLog("COMMAND", "> $command", LogLevel.INFO)
            val result = runCatching {
                shizukuExecutor.executeForResult(command)
            }.getOrNull()

            val output = if (result?.isSuccess == true) {
                result.stdout.ifBlank { "Command completed (exit code 0)" }
            } else if (result != null) {
                "Failed (exit code ${result.exitCode}):\n${result.stderr.ifBlank { result.stdout }}"
            } else {
                "Command failed to execute."
            }
            addLog("COMMAND", output, if (result?.isSuccess == true) LogLevel.SUCCESS else LogLevel.ERROR)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(diagnosticLogs = emptyList()) }
    }

    fun benchmarkDns() {
        viewModelScope.launch {
            if (!_uiState.value.shizukuStatus.isServiceReady) {
                emitMessage("Shizuku required for benchmarking.")
                return@launch
            }

            val selectedIds = _uiState.value.selectedBenchmarkProviders
            val candidateProviders = dnsProviders.filter { it.first in selectedIds }
            
            _uiState.update { 
                it.copy(
                    isBenchmarkingDns = true,
                    dnsBenchmarkStatus = BenchmarkStatus.RUNNING,
                    dnsBenchmarkResults = candidateProviders.map { p -> WifiDnsBenchmarkResult(p.first, p.second, p.third) }
                )
            }
            
            addLog("DNS", "Starting DNS benchmark with ${candidateProviders.size} providers...", LogLevel.INFO)
            
            val results = mutableListOf<WifiDnsBenchmarkResult>()
            candidateProviders.forEach { (id, name, host) ->
                val latency = pingHost(host)
                results.add(WifiDnsBenchmarkResult(id, name, host, latency))
                _uiState.update { it.copy(dnsBenchmarkResults = results.toList()) }
            }
            
            val best = results.filter { it.latencyMs != null }.minByOrNull { it.latencyMs!! }
            _uiState.update { state ->
                state.copy(
                    isBenchmarkingDns = false,
                    dnsBenchmarkStatus = BenchmarkStatus.COMPLETED,
                    dnsBenchmarkResults = results.map { it.copy(isRecommended = it.providerId == best?.providerId) }
                )
            }
            
            addLog("DNS", "Benchmark complete. Best: ${best?.name ?: "None"}", LogLevel.SUCCESS)
        }
    }

    fun updateBenchmarkSelection(id: String, selected: Boolean) {
        val current = _uiState.value.selectedBenchmarkProviders.toMutableSet()
        if (selected) current.add(id) else current.remove(id)
        viewModelScope.launch {
            settingsRepository.setNetworkBenchmarkServers(current)
        }
    }

    private val dnsProviders = listOf(
        Triple("cloudflare", "Cloudflare", "1.1.1.1"),
        Triple("google", "Google", "8.8.8.8"),
        Triple("quad9", "Quad9", "9.9.9.9"),
        Triple("adguard", "AdGuard", "94.140.14.14"),
        Triple("opendns", "OpenDNS", "208.67.222.222"),
        Triple("mullvad", "Mullvad", "194.242.2.2"),
        Triple("controld", "Control D", "76.76.2.0"),
        Triple("nextdns", "NextDNS", "45.90.28.0"),
        Triple("cleanbrowsing", "CleanBrowsing", "185.228.168.168"),
        Triple("comodo", "Comodo Secure", "8.26.56.26"),
        Triple("neustar", "Neustar Ultra", "156.154.70.1"),
        Triple("gcore", "Gcore", "95.161.212.1")
    )

    fun applyCustomDns(hostname: String) {
        viewModelScope.launch {
            if (!_uiState.value.shizukuStatus.isServiceReady) {
                emitMessage("Shizuku required to set DNS.")
                return@launch
            }
            
            if (hostname.isBlank()) {
                restoreAutomaticPrivateDns()
                return@launch
            }

            emitMessage("Applying custom DNS: $hostname...")
            val success = runCommands(listOf(
                "settings put global private_dns_mode hostname",
                "settings put global private_dns_spec $hostname"
            )).first
            
            if (success) {
                emitMessage("DNS updated.")
                refreshEnvironmentInternal(refreshTweakStates = true, runFreshScan = false)
            } else {
                emitMessage("Failed to apply custom DNS.")
            }
        }
    }

    private fun startStabilityCheck() {
        viewModelScope.launch {
            while (isActive) {
                val config = _uiState.value.networkConfig
                if (!config.isConnected) {
                    delay(5000)
                    continue
                }

                val gateway = config.gateway
                val dns = config.dns1
                
                var gatewayPing: Long? = null
                var dnsPing: Long? = null
                var publicPing: Long? = null

                if (gateway != "-" && gateway != "0.0.0.0") {
                    gatewayPing = pingHost(gateway)
                }
                
                if (dns != "-" && dns != "0.0.0.0") {
                    dnsPing = pingHost(dns)
                }

                publicPing = pingHost("1.1.1.1")

                updateStabilityMetrics(gatewayPing, dnsPing, publicPing)
                
                delay(2000)
            }
        }
    }

    suspend fun pingHost(host: String): Long? {
        // Try Shizuku first if ready
        if (uiState.value.shizukuStatus.isServiceReady) {
            val result = runCatching { shizukuExecutor.executeForResult("ping -c 1 -W 1 $host") }.getOrNull()
            if (result?.isSuccess == true) {
                val match = "time=([\\d.]+)".toRegex().find(result.stdout)
                return match?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
            }
        }
        
        // Fallback to standard ping (works for many hosts even without root/shizuku)
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 $host")
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val match = "time=([\\d.]+)".toRegex().find(output)
            match?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
        } catch (_: Exception) {
            null
        }
    }

    private fun updateStabilityMetrics(gateway: Long?, dns: Long?, public: Long?) {
        _uiState.update { state ->
            val currentHistory = (state.stability.history + (public ?: -1L)).takeLast(20)
            val currentPingHistory = (state.pingHistory + (public ?: 0L)).takeLast(50)
            
            val validPings = currentHistory.filter { it > 0 }
            val jitter = if (validPings.size > 1) {
                validPings.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average()
            } else 0.0

            val packetLoss = if (currentHistory.isNotEmpty()) {
                currentHistory.count { it == -1L }.toDouble() / currentHistory.size
            } else 0.0

            state.copy(
                pingHistory = currentPingHistory,
                stability = state.stability.copy(
                    gatewayPingMs = gateway,
                    dnsPingMs = dns,
                    publicPingMs = public,
                    jitterMs = jitter,
                    packetLossRate = packetLoss,
                    history = currentHistory
                )
            )
        }
    }

    private suspend fun refreshEnvironmentInternal(
        refreshTweakStates: Boolean = true,
        runFreshScan: Boolean = true
    ) {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _uiState.update {
            it.copy(
                isWifiEnabled = wifiManager.isWifiEnabled,
                locationEnabled = isLocationEnabled(),
                hasPartialWifiPermissions = !fine && coarse
            )
        }
        loadNetworkConfig()
        refreshShizukuState(refreshTweakStates = refreshTweakStates)
        if (hasWifiPermissions()) {
            if (runFreshScan) startScan() else updateScanResults()
        }
    }

    private suspend fun refreshShizukuState(
        refreshTweakStates: Boolean = true,
        message: String? = null
    ) {
        val isReachable = try {
            Shizuku.pingBinder() && Shizuku.getVersion() >= 11
        } catch (_: Exception) {
            false
        }
        val isAuthorized = try {
            isReachable && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        val serviceReady = if (isAuthorized) {
            try {
                shizukuExecutor.ensureService()
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        val detail = when {
            !isReachable -> "Shizuku is not running or version too old."
            !isAuthorized -> "Permission needed."
            serviceReady -> "Ready for elevated tweaks."
            else -> "Binding user service..."
        }

        _uiState.update {
            it.copy(
                shizukuStatus = ShizukuStatus(
                    isReachable = isReachable,
                    isAuthorized = isAuthorized,
                    isServiceReady = serviceReady,
                    detail = detail
                )
            )
        }

        if (refreshTweakStates && serviceReady) {
            refreshTweakStates()
        }
        if (message != null) {
            emitMessage(message)
        }
    }

    private suspend fun refreshTweakStates() {
        _uiState.update { it.copy(isRefreshingTweakStates = true) }
        val existing = _uiState.value.tweakResults.toMutableMap()

        tweakCatalog.forEach { tweak ->
            val applied = isTweakApplied(tweak)
            val previous = existing[tweak.id] ?: TweakResult(id = tweak.id)
            existing[tweak.id] = previous.copy(
                status = when {
                    applied -> TweakStatus.SUCCESS
                    previous.status == TweakStatus.MANUAL -> TweakStatus.MANUAL
                    else -> TweakStatus.IDLE
                },
                message = if (applied) "Active" else if (previous.status == TweakStatus.MANUAL) previous.message else "",
                isApplied = applied,
                lastUpdatedMs = System.currentTimeMillis()
            )
        }

        _uiState.update {
            it.copy(
                tweakResults = existing,
                isRefreshingTweakStates = false
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateScanResults() {
        if (!hasWifiPermissions()) {
            _uiState.update { it.copy(isScanning = false) }
            return
        }
        try {
            val results = wifiManager.scanResults.orEmpty()
                .map { accessPoint ->
                    WifiScanResult(
                        ssid = accessPoint.SSID.trim().ifEmpty { "Hidden network" },
                        bssid = accessPoint.BSSID ?: "Unknown",
                        rssi = accessPoint.level,
                        frequency = accessPoint.frequency,
                        channel = frequencyToChannel(accessPoint.frequency),
                        band = frequencyToBand(accessPoint.frequency),
                        security = parseSecurityType(accessPoint.capabilities),
                        isHidden = accessPoint.SSID.isBlank()
                    )
                }
                .distinctBy { it.bssid }

            rawScanResults = results
            loadNetworkConfig()
            calculateCongestion(results)
            updateVisibleScanResults()
            updateAdvice()
        } catch (_: Exception) {
        } finally {
            _uiState.update {
                it.copy(
                    isScanning = false,
                    lastScanTimestamp = System.currentTimeMillis()
                )
            }
        }
    }

    private fun updateVisibleScanResults() {
        val state = _uiState.value
        val visible = rawScanResults
            .filter { state.showHiddenNetworks || !it.isHidden }
            .sortedWith(scanComparator(state.scanSortMode))

        _uiState.update { it.copy(scanResults = visible) }
        updateAdvice()
    }

    private fun calculateCongestion(results: List<WifiScanResult>) {
        val grouped = results.groupBy { it.channel }
        val congestion = grouped.map { (channel, networks) ->
            val count = networks.size
            val totalWeight = networks.sumOf { (it.rssi + 100.0).coerceAtLeast(0.0) / 10.0 }
            val averageRssi = networks.map { it.rssi }.average()
            
            ChannelCongestion(
                channel = channel,
                networkCount = count,
                averageRssi = averageRssi,
                band = networks.firstOrNull()?.band ?: "2.4 GHz",
                isRecommended = false 
            )
        }.sortedBy { it.channel }

        val best24 = congestion
            .filter { it.band == "2.4 GHz" && it.channel in listOf(1, 6, 11) }
            .minByOrNull { it.networkCount * 100 - it.averageRssi.roundToInt() }
        val best5 = congestion
            .filter { it.band == "5 GHz" }
            .minByOrNull { it.networkCount * 100 - it.averageRssi.roundToInt() }

        _uiState.update {
            it.copy(
                congestion = congestion.map { item ->
                    item.copy(
                        isRecommended = item.channel == best24?.channel || item.channel == best5?.channel
                    )
                }
            )
        }
    }

    private fun updateAdvice() {
        val state = _uiState.value
        val strongest = rawScanResults.maxByOrNull { it.rssi }
        val openNetworks = rawScanResults.count { it.security == "Open" }
        val hiddenNetworks = rawScanResults.count { it.isHidden }
        val best24 = state.congestion.firstOrNull { it.isRecommended && it.band == "2.4 GHz" }?.channel
        val best5 = state.congestion.firstOrNull { it.isRecommended && it.band == "5 GHz" }?.channel

        val healthScore = calculateHealthScore(state)

        val summary = when {
            healthScore > 80 -> "Your connection is excellent and stable."
            healthScore > 50 -> "Decent signal, but some congestion detected."
            else -> "Poor connection. Consider moving closer or switching channels."
        }

        _uiState.update {
            it.copy(
                advice = NetworkAdvice(
                    healthScore = healthScore,
                    summary = summary,
                    strongestNetwork = strongest?.ssid ?: "-",
                    best24GhzChannel = best24,
                    best5GhzChannel = best5,
                    openNetworks = openNetworks,
                    hiddenNetworks = hiddenNetworks,
                    totalNetworks = rawScanResults.size,
                    recommendation = "Use Channel ${best5 ?: best24 ?: "Auto"} for best performance."
                )
            )
        }
    }

    private fun calculateHealthScore(state: WifiTweaksUiState): Int {
        val rssiWeight = 0.4
        val jitterWeight = 0.3
        val packetLossWeight = 0.3

        val rssiScore = ((state.currentRssi + 100).coerceIn(0, 60) / 60.0) * 100
        val jitterScore = (1.0 - (state.stability.jitterMs.coerceIn(0.0, 50.0) / 50.0)) * 100
        val packetLossScore = (1.0 - state.stability.packetLossRate) * 100

        return (rssiScore * rssiWeight + jitterScore * jitterWeight + packetLossScore * packetLossWeight).roundToInt()
    }

    private fun startSignalMonitoring() {
        viewModelScope.launch {
            networkMonitor.observeWifiInfo().collect { info ->
                _uiState.update { state ->
                    val history = (state.rssiHistory + RssiHistoryPoint(
                        rssi = info.rssi,
                        timestamp = System.currentTimeMillis()
                    )).takeLast(50)

                    state.copy(
                        currentSsid = info.ssid,
                        currentRssi = info.rssi,
                        rssiHistory = history
                    )
                }
                loadNetworkConfig()
                if (uiState.value.audioFeedbackEnabled) {
                    playSignalTone(info.rssi)
                }
            }
        }
    }

    private fun loadNetworkConfig() {
        viewModelScope.launch {
            val info = networkMonitor.getWifiInfo()
            
            val isThrottling = runCatching {
                shizukuExecutor.executeSingle("settings get global wifi_scan_throttle_enabled") == "1"
            }.getOrDefault(true)

            if (isThrottling) {
                addLog("SYSTEM", "Wi-Fi Scan Throttling is ACTIVE. Scans may be delayed.", LogLevel.WARNING)
            }

            _uiState.update {
                it.copy(
                    currentSsid = info.ssid,
                    currentRssi = info.rssi,
                    networkConfig = it.networkConfig.copy(
                        ip = info.ipAddress,
                        gateway = info.gateway,
                        dns1 = info.dnsServers.getOrNull(0) ?: "-",
                        dns2 = info.dnsServers.getOrNull(1) ?: "-",
                        wifiStandard = info.wifiStandard,
                        band = info.band,
                        channel = info.channel,
                        linkSpeed = info.linkSpeed,
                        frequency = info.frequency,
                        bssid = info.bssid,
                        isConnected = info.ssid != "Unknown" && info.ssid != "Protected/Hidden",
                        wifi6ECapable = info.frequency > 5925,
                        wifi7Capable = info.wifiStandard.contains("be", ignoreCase = true),
                        isThrottlingEnabled = isThrottling
                    )
                )
            }
        }
    }

    private suspend fun applyTweakInternal(
        tweak: WifiTweak,
        emitStatusMessage: Boolean = true
    ): Boolean {
        addLog("TWEAK", "Requesting application of: ${tweak.title}", LogLevel.INFO)
        
        if (tweak.type == TweakType.MANUAL_GUIDE) {
            setTweakResult(
                tweak.id,
                status = TweakStatus.MANUAL,
                message = "Manual steps required.",
                isApplied = false
            )
            if (emitStatusMessage) {
                emitMessage("Manual guide ready for ${tweak.title}.")
            }
            addLog("TWEAK", "Manual guide displayed for ${tweak.title}", LogLevel.SUCCESS)
            return true
        }

        if (!requireShizukuFor(tweak)) {
            addLog("TWEAK", "Shizuku not available for ${tweak.title}", LogLevel.WARNING)
            return false
        }

        setTweakResult(
            tweak.id,
            status = TweakStatus.RUNNING,
            message = "Applying..."
        )
        
        addLog("SHELL", "Executing ${tweak.applyCommands.size} commands for ${tweak.id}", LogLevel.INFO)
        val result = runCommands(tweak.applyCommands)
        if (!result.first) {
            setTweakResult(
                tweak.id,
                status = TweakStatus.FAILED,
                message = result.second.ifBlank { "Command failed." }
            )
            if (emitStatusMessage) {
                emitMessage("${tweak.title} failed to apply.")
            }
            addLog("TWEAK", "Failed to apply ${tweak.title}: ${result.second}", LogLevel.ERROR)
            return false
        }

        val applied = isTweakApplied(tweak, defaultWhenUnverifiable = true)
        setTweakResult(
            tweak.id,
            status = if (applied) TweakStatus.SUCCESS else TweakStatus.FAILED,
            message = if (applied) "Active" else "Applied, but could not verify.",
            isApplied = applied
        )
        refreshEnvironmentInternal(refreshTweakStates = true, runFreshScan = false)
        if (emitStatusMessage) {
            emitMessage("${tweak.title} applied.")
        }
        addLog("TWEAK", "Successfully applied ${tweak.title}", LogLevel.SUCCESS)
        return applied
    }

    private suspend fun requireShizukuFor(tweak: WifiTweak): Boolean {
        val shizuku = _uiState.value.shizukuStatus
        if (shizuku.isServiceReady) return true

        if (tweak.manualSteps.isNotEmpty()) {
            setTweakResult(
                tweak.id,
                status = TweakStatus.MANUAL,
                message = "Use the manual steps below.",
                isApplied = false
            )
            emitMessage("Shizuku is not ready. ${tweak.title} has a manual fallback.")
        } else {
            setTweakResult(
                tweak.id,
                status = TweakStatus.UNSUPPORTED,
                message = "Shizuku required.",
                isApplied = false
            )
            emitMessage("Connect Shizuku to apply ${tweak.title}.")
        }
        return false
    }

    private suspend fun isTweakApplied(
        tweak: WifiTweak,
        defaultWhenUnverifiable: Boolean = false
    ): Boolean {
        val verificationCommand = tweak.verificationCommand ?: return defaultWhenUnverifiable
        if (!_uiState.value.shizukuStatus.isServiceReady) return false
        val result = try {
            shizukuExecutor.executeForResult(verificationCommand)
        } catch (_: Exception) {
            return defaultWhenUnverifiable
        }
        return result.isSuccess
    }

    private suspend fun runCommands(commands: List<String>): Pair<Boolean, String> {
        commands.forEach { command ->
            val result = try {
                shizukuExecutor.executeForResult(command)
            } catch (e: Exception) {
                return false to (e.message ?: "Command execution failed.")
            }
            if (!result.isSuccess) {
                return false to result.stderr.ifBlank {
                    result.stdout.ifBlank { "Exit code ${result.exitCode}" }
                }
            }
        }
        return true to ""
    }

    private fun hasWifiPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return (fine || coarse) && nearby
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun parseSecurityType(capabilities: String): String {
        return when {
            capabilities.contains("WPA3") -> "WPA3"
            capabilities.contains("WPA2") -> "WPA2"
            capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            else -> "Open"
        }
    }

    private fun playSignalTone(rssi: Int) {
        if (toneGenerator == null) {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        }
        val now = System.currentTimeMillis()
        val interval = (kotlin.math.abs(rssi + 30) * 10).coerceIn(200, 2000).toLong()

        if (now - lastBeepTime > interval) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            lastBeepTime = now
        }
    }

    private fun setTweakResult(
        id: String,
        status: TweakStatus,
        message: String,
        isApplied: Boolean = false
    ) {
        val current = _uiState.value.tweakResults.toMutableMap()
        current[id] = TweakResult(
            id = id,
            status = status,
            message = message,
            isApplied = isApplied,
            lastUpdatedMs = System.currentTimeMillis()
        )
        _uiState.update { it.copy(tweakResults = current) }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(message)
            _uiState.update { it.copy(lastActionMessage = message) }
        }
    }

    private fun registerScanReceiver() {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter)
    }

    private fun scanComparator(mode: WifiScanSortMode): Comparator<WifiScanResult> {
        return when (mode) {
            WifiScanSortMode.SIGNAL -> compareByDescending { it.rssi }
            WifiScanSortMode.CHANNEL -> compareBy { it.channel }
            WifiScanSortMode.SECURITY -> compareByDescending { securityRank(it.security) }
            WifiScanSortMode.NAME -> compareBy { it.ssid }
        }
    }

    private fun securityRank(security: String): Int {
        return when (security) {
            "WPA3" -> 4
            "WPA2" -> 3
            "WPA" -> 2
            "WEP" -> 1
            else -> 0
        }
    }

    private fun frequencyToBand(freq: Int): String {
        return when {
            freq in 2400..2500 -> "2.4 GHz"
            freq in 4900..5900 -> "5 GHz"
            freq in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2401..2483 -> (freq - 2407) / 5
            freq in 5170..5825 -> (freq - 5170) / 5 + 34
            freq in 5945..7105 -> (freq - 5945) / 5 + 1
            else -> 0
        }
    }

    private fun buildTweaks(): List<WifiTweak> {
        return listOf(
            WifiTweak(
                id = "scan_throttle",
                title = "Disable scan throttling",
                description = "Lets Android scan more often so roaming and discovery feel snappier.",
                icon = Icons.Rounded.Speed,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.PERFORMANCE,
                applyCommands = listOf("settings put global wifi_scan_throttle_enabled 0"),
                revertCommands = listOf("settings put global wifi_scan_throttle_enabled 1"),
                verificationCommand = "[ \"$(settings get global wifi_scan_throttle_enabled)\" = \"0\" ]",
                manualSteps = listOf(
                    "Open Developer options.",
                    "Find Wi-Fi scan throttling.",
                    "Turn it off."
                ),
                riskNote = "May increase battery use if apps scan often."
            ),
            WifiTweak(
                id = "low_latency_mode",
                title = "Low latency mode",
                description = "Forces the Wi-Fi chip into high-performance mode for gaming/streaming.",
                icon = Icons.Rounded.SportsEsports,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.PERFORMANCE,
                applyCommands = listOf("cmd wifi set-power-save-mode disabled"),
                revertCommands = listOf("cmd wifi set-power-save-mode enabled"),
                riskNote = "Higher battery consumption while active."
            ),
            WifiTweak(
                id = "data_stall_logic",
                title = "Rapid stall recovery",
                description = "Enables faster recovery when the connection 'freezes' or stops passing packets.",
                icon = Icons.Rounded.FlashOn,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global wifi_data_stall_recovery_on 1"),
                revertCommands = listOf("settings put global wifi_data_stall_recovery_on 0"),
                verificationCommand = "[ \"$(settings get global wifi_data_stall_recovery_on)\" = \"1\" ]"
            ),
            WifiTweak(
                id = "scan_interval",
                title = "Optimized scan interval",
                description = "Increases the background scan interval to 5 minutes to reduce interruption and save power.",
                icon = Icons.Rounded.Timer,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.POWER,
                applyCommands = listOf("settings put global wifi_framework_scan_interval_ms 300000"),
                revertCommands = listOf("settings delete global wifi_framework_scan_interval_ms"),
                verificationCommand = "[ \"$(settings get global wifi_framework_scan_interval_ms)\" = \"300000\" ]"
            ),
            WifiTweak(
                id = "force_wpa3",
                title = "Prefer WPA3-SAE",
                description = "Encourages the system to use the latest security protocol where available.",
                icon = Icons.Rounded.VerifiedUser,
                type = TweakType.MANUAL_GUIDE,
                category = TweakCategory.PRIVACY,
                manualSteps = listOf(
                    "Open current network settings.",
                    "Look for 'Security' or 'WPA3' options.",
                    "Select SAE (WPA3) if your router supports it."
                )
            ),
            WifiTweak(
                id = "aggressive_roaming",
                title = "Aggressive AP roaming",
                description = "Pushes the device to hand off faster between access points in a mesh.",
                icon = Icons.Rounded.SwapHoriz,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.PERFORMANCE,
                applyCommands = listOf("settings put global wifi_enable_aggressive_handover 1"),
                revertCommands = listOf("settings put global wifi_enable_aggressive_handover 0"),
                verificationCommand = "[ \"$(settings get global wifi_enable_aggressive_handover)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Developer options.",
                    "Enable Wi-Fi verbose logging if your ROM exposes roaming details.",
                    "Test while walking between access points."
                ),
                riskNote = "Can cause extra roaming on noisy networks."
            ),
            WifiTweak(
                id = "wifi_auto_wakeup",
                title = "Wi-Fi auto wakeup",
                description = "Turns Wi-Fi back on around saved networks so you do not stay on mobile data longer than needed.",
                icon = Icons.Rounded.Sync,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.POWER,
                applyCommands = listOf("settings put global wifi_wakeup_enabled 1"),
                revertCommands = listOf("settings put global wifi_wakeup_enabled 0"),
                verificationCommand = "[ \"$(settings get global wifi_wakeup_enabled)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Wi-Fi settings.",
                    "Go to network preferences.",
                    "Turn on Wi-Fi automatically."
                )
            ),
            WifiTweak(
                id = "avoid_bad_wifi",
                title = "Avoid poor Wi-Fi",
                description = "Allows Android to bail out of weak Wi-Fi sooner instead of clinging to it.",
                icon = Icons.Rounded.SignalWifiStatusbarConnectedNoInternet4,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global network_avoid_bad_wifi 1"),
                revertCommands = listOf("settings put global network_avoid_bad_wifi 0"),
                verificationCommand = "[ \"$(settings get global network_avoid_bad_wifi)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Network settings.",
                    "Turn on Adaptive connectivity or the equivalent vendor option."
                )
            ),
            WifiTweak(
                id = "suspend_optimizations",
                title = "Keep Wi-Fi awake off-screen",
                description = "Stops deeper Wi-Fi sleep so background sync and notifications stay more reliable.",
                icon = Icons.Rounded.PowerSettingsNew,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global wifi_suspend_optimizations_enabled 0"),
                revertCommands = listOf("settings put global wifi_suspend_optimizations_enabled 1"),
                verificationCommand = "[ \"$(settings get global wifi_suspend_optimizations_enabled)\" = \"0\" ]",
                manualSteps = listOf(
                    "Open vendor battery management.",
                    "Exclude Wi-Fi or the affected app from aggressive standby rules."
                ),
                riskNote = "Slightly higher idle battery drain."
            ),
            WifiTweak(
                id = "ip_reachability",
                title = "Relax IP reachability disconnects",
                description = "Useful for routers that trigger false disconnects during idle or multicast traffic.",
                icon = Icons.Rounded.Route,
                type = TweakType.SHIZUKU_ONLY,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("settings put global wifi_ip_reachability_disconnect_enabled 0"),
                revertCommands = listOf("settings put global wifi_ip_reachability_disconnect_enabled 1"),
                verificationCommand = "[ \"$(settings get global wifi_ip_reachability_disconnect_enabled)\" = \"0\" ]",
                riskNote = "Use only if you already see random disconnects."
            ),
            WifiTweak(
                id = "mobile_data_always_on",
                title = "Keep mobile data warm",
                description = "Maintains fast failover when Wi-Fi drops, especially while moving.",
                icon = Icons.Rounded.SettingsSuggest,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.POWER,
                applyCommands = listOf("settings put global mobile_data_always_on 1"),
                revertCommands = listOf("settings put global mobile_data_always_on 0"),
                verificationCommand = "[ \"$(settings get global mobile_data_always_on)\" = \"1\" ]",
                manualSteps = listOf(
                    "Open Developer options.",
                    "Turn on Mobile data always active."
                ),
                riskNote = "Can use more battery and background cellular data."
            ),
            WifiTweak(
                id = "verbose_logging",
                title = "Verbose Wi-Fi logging",
                description = "Turns on deeper Wi-Fi logging so diagnostics and roaming behavior are easier to inspect.",
                icon = Icons.Rounded.NetworkCheck,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.STABILITY,
                applyCommands = listOf("cmd wifi set-wifi-verbose-logging-enabled true"),
                revertCommands = listOf("cmd wifi set-wifi-verbose-logging-enabled false"),
                manualSteps = listOf(
                    "Open Developer options.",
                    "Enable Wi-Fi verbose logging."
                ),
                riskNote = "Diagnostic only. Leave off once testing is done."
            ),
            WifiTweak(
                id = "private_dns_cloudflare",
                title = "Private DNS: Cloudflare",
                description = "A fast privacy baseline using 1.1.1.1 over DNS-over-TLS.",
                icon = Icons.Rounded.Public,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.PRIVACY,
                applyCommands = listOf(
                    "settings put global private_dns_mode hostname",
                    "settings put global private_dns_spec 1dot1dot1dot1.cloudflare-dns.com"
                ),
                revertCommands = listOf(
                    "settings put global private_dns_mode opportunistic",
                    "settings delete global private_dns_spec"
                ),
                verificationCommand = "[ \"$(settings get global private_dns_mode)\" = \"hostname\" ] && [ \"$(settings get global private_dns_spec)\" = \"1dot1dot1dot1.cloudflare-dns.com\" ]",
                manualSteps = listOf(
                    "Open Network and internet settings.",
                    "Tap Private DNS.",
                    "Choose Private DNS provider hostname.",
                    "Enter 1dot1dot1dot1.cloudflare-dns.com."
                )
            ),
            WifiTweak(
                id = "private_dns_google",
                title = "Private DNS: Google",
                description = "Reliable DNS-over-TLS using dns.google.",
                icon = Icons.Rounded.Public,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.PRIVACY,
                applyCommands = listOf(
                    "settings put global private_dns_mode hostname",
                    "settings put global private_dns_spec dns.google"
                ),
                revertCommands = listOf(
                    "settings put global private_dns_mode opportunistic",
                    "settings delete global private_dns_spec"
                ),
                verificationCommand = "[ \"$(settings get global private_dns_mode)\" = \"hostname\" ] && [ \"$(settings get global private_dns_spec)\" = \"dns.google\" ]",
                manualSteps = listOf(
                    "Open Network and internet settings.",
                    "Tap Private DNS.",
                    "Choose Private DNS provider hostname.",
                    "Enter dns.google."
                )
            ),
            WifiTweak(
                id = "private_dns_quad9",
                title = "Private DNS: Quad9",
                description = "Strong privacy with malware filtering on dns.quad9.net.",
                icon = Icons.Rounded.Shield,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.PRIVACY,
                applyCommands = listOf(
                    "settings put global private_dns_mode hostname",
                    "settings put global private_dns_spec dns.quad9.net"
                ),
                revertCommands = listOf(
                    "settings put global private_dns_mode opportunistic",
                    "settings delete global private_dns_spec"
                ),
                verificationCommand = "[ \"$(settings get global private_dns_mode)\" = \"hostname\" ] && [ \"$(settings get global private_dns_spec)\" = \"dns.quad9.net\" ]",
                manualSteps = listOf(
                    "Open Network and internet settings.",
                    "Tap Private DNS.",
                    "Choose Private DNS provider hostname.",
                    "Enter dns.quad9.net."
                )
            ),
            WifiTweak(
                id = "private_dns_adguard",
                title = "Private DNS: AdGuard",
                description = "Blocks many ads and trackers using dns.adguard-dns.com.",
                icon = Icons.Rounded.PrivacyTip,
                type = TweakType.SHIZUKU_OR_GUIDE,
                category = TweakCategory.PRIVACY,
                applyCommands = listOf(
                    "settings put global private_dns_mode hostname",
                    "settings put global private_dns_spec dns.adguard-dns.com"
                ),
                revertCommands = listOf(
                    "settings put global private_dns_mode opportunistic",
                    "settings delete global private_dns_spec"
                ),
                verificationCommand = "[ \"$(settings get global private_dns_mode)\" = \"hostname\" ] && [ \"$(settings get global private_dns_spec)\" = \"dns.adguard-dns.com\" ]",
                manualSteps = listOf(
                    "Open Network and internet settings.",
                    "Tap Private DNS.",
                    "Choose Private DNS provider hostname.",
                    "Enter dns.adguard-dns.com."
                )
            ),
            WifiTweak(
                id = "manual_mac_randomization",
                title = "Use randomized MAC",
                description = "Prevents hotspots and venues from tracking one static Wi-Fi identity.",
                icon = Icons.Rounded.GppGood,
                type = TweakType.MANUAL_GUIDE,
                category = TweakCategory.PRIVACY,
                manualSteps = listOf(
                    "Open the current Wi-Fi network details.",
                    "Open Privacy or MAC address type.",
                    "Choose randomized MAC."
                )
            ),
            WifiTweak(
                id = "manual_forget_open_networks",
                title = "Forget risky open networks",
                description = "Clean out old open hotspots so the phone does not keep probing for them.",
                icon = Icons.Rounded.AutoAwesome,
                type = TweakType.MANUAL_GUIDE,
                category = TweakCategory.PRIVACY,
                manualSteps = listOf(
                    "Open Saved networks.",
                    "Forget old airport, hotel, and cafe hotspots you no longer need."
                )
            ),
            WifiTweak(
                id = "manual_metered_hotspot",
                title = "Mark noisy hotspots as metered",
                description = "A great fallback when one network should stay connected but not burn bandwidth in the background.",
                icon = Icons.Rounded.BatterySaver,
                type = TweakType.MANUAL_GUIDE,
                category = TweakCategory.POWER,
                manualSteps = listOf(
                    "Open the hotspot's Wi-Fi details.",
                    "Find Network usage or Metered.",
                    "Set it to Metered."
                )
            )
        )
    }

    private fun buildProfiles(): List<WifiOptimizationProfile> {
        return listOf(
            WifiOptimizationProfile(
                id = "profile_gaming",
                title = "Gaming Pro",
                description = "Lowest latency possible. Disables power saving and forces high performance.",
                icon = Icons.Rounded.SportsEsports,
                tweakIds = listOf("low_latency_mode", "scan_throttle", "data_stall_logic"),
                accentLabel = "Pro Performance"
            ),
            WifiOptimizationProfile(
                id = "profile_fast_lane",
                title = "Fast Lane",
                description = "Roaming-first profile for mesh routers and busy environments.",
                icon = Icons.Rounded.Speed,
                tweakIds = listOf("scan_throttle", "aggressive_roaming", "mobile_data_always_on"),
                accentLabel = "Speed"
            ),
            WifiOptimizationProfile(
                id = "profile_stability",
                title = "Steady Link",
                description = "Best when Wi-Fi drops in the background or sticks to a bad access point.",
                icon = Icons.Rounded.NetworkCheck,
                tweakIds = listOf("avoid_bad_wifi", "suspend_optimizations", "ip_reachability"),
                accentLabel = "Stability"
            ),
            WifiOptimizationProfile(
                id = "profile_privacy",
                title = "Ghost Mode",
                description = "Maximum privacy with MAC randomization and secure DNS defaults.",
                icon = Icons.Rounded.Shield,
                tweakIds = listOf("manual_mac_randomization", "private_dns_cloudflare", "verbose_logging"),
                accentLabel = "Privacy"
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(scanReceiver)
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeathListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        } catch (_: Exception) {
        }
        toneGenerator?.release()
    }

    companion object {
        const val SHIZUKU_CODE = 1001
    }
}
