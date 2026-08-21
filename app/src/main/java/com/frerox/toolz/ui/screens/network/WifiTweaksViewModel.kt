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
import com.frerox.toolz.util.network.DnsRealBenchmark
import com.frerox.toolz.util.network.NetworkHealthEngine
import com.frerox.toolz.util.network.NetworkMonitor
import com.frerox.toolz.util.network.PrivilegedNetworkManager
import com.frerox.toolz.util.network.SpeedTestEngine
import com.frerox.toolz.util.network.WifiSpectrumAnalyzer
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class WifiTweaksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val shizukuExecutor: ShizukuShellExecutor,
    private val speedTestEngine: SpeedTestEngine,
    private val privilegedNetworkManager: PrivilegedNetworkManager,
    private val healthEngine: NetworkHealthEngine,
    private val spectrumAnalyzer: WifiSpectrumAnalyzer,
    private val dnsRealBenchmark: DnsRealBenchmark,
    private val speedHistoryDao: com.frerox.toolz.data.network.SpeedHistoryDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    @Suppress("DEPRECATION")
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val tweakCatalog = TweakCatalog.tweaks
    private val profileCatalog = TweakCatalog.profiles
    private var rawScanResults: List<WifiScanResult> = emptyList()
    private var toneGenerator: ToneGenerator? = null
    private var lastBeepTime = 0L

    // ── Apply journal (P2): {tweakId -> {settingKey -> previousValue|null}} ──
    // Previous values are captured via `settings get` BEFORE any write, so revert
    // restores what the device actually had — not an assumed AOSP default.
    private data class JournalEntry(
        val previousValues: Map<String, String?>,
        val ts: Long = System.currentTimeMillis()
    )

    @Volatile private var tweakJournal: Map<String, JournalEntry> = emptyMap()

    /** P6: loops pause when the suite screen is not visible (battery). */
    @Volatile private var screenActive = true

    fun setScreenActive(active: Boolean) {
        screenActive = active
    }


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
            settingsRepository.networkTweakJournal.collect { json ->
                tweakJournal = parseJournal(json)
            }
        }

        viewModelScope.launch {
            settingsRepository.networkConsoleEnabled.collect { enabled ->
                _uiState.update { it.copy(consoleEnabled = enabled) }
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
                if (!screenActive) { delay(3_000); continue }
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
            _uiState.update {
                it.copy(
                    speedTest = SpeedTestResult(isRunning = true, phaseLabel = "Starting…")
                )
            }
            try {
                speedTestEngine.runFullTest().collect { event ->
                    when (event) {
                        is com.frerox.toolz.util.network.SpeedEvent.Progress -> _uiState.update {
                            it.copy(speedTest = it.speedTest.copy(progress = event.progress, phaseLabel = event.phaseLabel))
                        }
                        is com.frerox.toolz.util.network.SpeedEvent.Done -> {
                            val grade = com.frerox.toolz.util.network.SpeedTestEngine.grade(event.idleLatencyMs, event.loadedLatencyMs)
                            _uiState.update {
                                it.copy(
                                    speedTest = it.speedTest.copy(
                                        isRunning = false,
                                        progress = 1f,
                                        downloadSpeedMbps = event.downloadMbps,
                                        uploadSpeedMbps = event.uploadMbps,
                                        idleLatencyMs = event.idleLatencyMs,
                                        loadedLatencyMs = event.loadedLatencyMs,
                                        bloatGrade = grade,
                                        phaseLabel = "Complete",
                                        error = null
                                    )
                                )
                            }
                            addLog("SPEED", "Down ${"%.1f".format(event.downloadMbps)} / Up ${"%.1f".format(event.uploadMbps)} Mbps · bloat ${grade?.letter ?: "n/a"}", LogLevel.SUCCESS)
                            runCatching {
                                speedHistoryDao.insert(
                                    com.frerox.toolz.data.network.SpeedHistoryEntity(
                                        timestampMs = System.currentTimeMillis(),
                                        downloadMbps = event.downloadMbps,
                                        uploadMbps = event.uploadMbps,
                                        idleLatencyMs = event.idleLatencyMs,
                                        loadedLatencyMs = event.loadedLatencyMs,
                                        bloatGrade = grade?.letter,
                                        ssid = _uiState.value.currentSsid
                                    )
                                )
                                speedHistoryDao.purgeOlderThan(System.currentTimeMillis() - com.frerox.toolz.data.network.SpeedHistoryDao.RETENTION_MS)
                            }
                        }
                        is com.frerox.toolz.util.network.SpeedEvent.Failed -> _uiState.update {
                            it.copy(speedTest = it.speedTest.copy(isRunning = false, error = event.message, phaseLabel = "Failed"))
                        }
                    }
                }
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
            val ok = revertTweakInternal(tweak)
            refreshEnvironmentInternal(refreshTweakStates = true, runFreshScan = false)
            if (ok) emitMessage("${tweak.title} reverted.") else emitMessage("Failed to revert ${tweak.title}.")
        }
    }

    /**
     * Transactional profile apply (P2): any hard failure rolls back the tweaks
     * already applied in this run using the journal, leaving no half-state.
     */
    fun applyProfile(profile: WifiOptimizationProfile) {
        viewModelScope.launch {
            emitMessage("Applying profile: ${profile.title}...")
            val appliedThisRun = mutableListOf<WifiTweak>()
            var failures = 0
            profile.tweakIds.forEach { id ->
                val tweak = TweakCatalog.byId(id) ?: return@forEach
                val success = applyTweakInternal(tweak, emitStatusMessage = false)
                if (success) appliedThisRun += tweak else failures++
            }
            if (failures > 0 && appliedThisRun.isNotEmpty()) {
                addLog("PROFILE", "Rolling back ${appliedThisRun.size} tweak(s) after $failures failure(s)", LogLevel.WARNING)
                appliedThisRun.reversed().forEach { revertTweakInternal(it) }
                emitMessage("${profile.title}: failed mid-way — rolled back cleanly.")
            } else if (failures > 0) {
                emitMessage("${profile.title}: could not apply ($failures failed). Check Shizuku.")
            } else {
                emitMessage("${profile.title} applied & verified (${appliedThisRun.size} tweaks).")
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

    val speedHistory: StateFlow<List<com.frerox.toolz.data.network.SpeedHistoryEntity>> =
        speedHistoryDao.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearSpeedHistory() {
        viewModelScope.launch { speedHistoryDao.clearAll() }
    }

    /** p50/p95 of loaded-latency samples over the last 30 days (pure Kotlin percentile math). */
    suspend fun latencyStats30d(): com.frerox.toolz.data.network.LatencyStats {
        val since = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val rows = runCatching { speedHistoryDao.since(since) }.getOrDefault(emptyList())
        val lat = rows.mapNotNull { it.loadedLatencyMs ?: it.idleLatencyMs }.sorted()
        if (lat.isEmpty()) return com.frerox.toolz.data.network.LatencyStats(0, null, null)
        fun pct(p: Double): Long = lat[((lat.size - 1).coerceAtLeast(0) * p).toInt()]
        return com.frerox.toolz.data.network.LatencyStats(lat.size, pct(0.50), pct(0.95))
    }

    fun exportScanCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("ssid,bssid,rssi,frequency,channel,band,security,hidden")
        _uiState.value.scanResults.forEach { r ->
            sb.appendLine("\"${r.ssid}\",${r.bssid},${r.rssi},${r.frequency},${r.channel},${r.band},${r.security},${r.isHidden}")
        }
        return sb.toString()
    }

    fun exportDiagnosticJson(): String {
        val s = _uiState.value
        return """
        {
          "ssid": "${s.currentSsid}",
          "rssi": ${s.currentRssi},
          "health": ${s.advice.healthScore},
          "gateway": "${s.networkConfig.gateway}",
          "channel": ${s.networkConfig.channel},
          "band": "${s.networkConfig.band}",
          "scanCount": ${s.scanResults.size},
          "timestamp": ${System.currentTimeMillis()}
        }
        """.trimIndent()
    }

    fun clearLastActionMessage() {
        _uiState.update { it.copy(lastActionMessage = null) }
    }

    fun dismissDisclaimer() {
        viewModelScope.launch {
            settingsRepository.setNetworkDisclaimerShown(true)
        }
    }

    /**
     * P0: diagnose-first. Inspects live state, proposes only relevant fixes,
     * applies each with journal capture + verification, reports honestly.
     */
    fun quickFixDiagnoses(): List<SmartFixRecommendation> {
        val s = _uiState.value
        val fixes = mutableListOf<SmartFixRecommendation>()
        if (s.networkConfig.isThrottlingEnabled) {
            fixes += SmartFixRecommendation(
                id = "scan_throttle",
                title = "Scan throttling is on",
                description = "Background scans are delayed; discovery and roaming feel sluggish.",
                tweakIds = listOf("scan_throttle"),
                severity = RecommendationSeverity.WARNING
            )
        }
        if (s.currentRssi < -70 && s.currentRssi > -100) {
            fixes += SmartFixRecommendation(
                id = "weak_signal",
                title = "Weak signal (${s.currentRssi} dBm)",
                description = "Let the system leave bad Wi-Fi sooner instead of clinging to it.",
                tweakIds = listOf("avoid_bad_wifi", "data_stall_logic"),
                severity = RecommendationSeverity.CRITICAL
            )
        }
        if (s.privateDnsHost.isBlank() && s.privateDnsMode.equals("Automatic", true)) {
            fixes += SmartFixRecommendation(
                id = "dns_hardening",
                title = "Private DNS is automatic",
                description = "Enable encrypted DNS (Quad9) to stop ISP plaintext snooping.",
                tweakIds = listOf("private_dns_quad9"),
                severity = RecommendationSeverity.INFO
            )
        }
        return fixes
    }

    fun fixMyConnection() {
        viewModelScope.launch {
            val diagnoses = quickFixDiagnoses()
            if (diagnoses.isEmpty()) {
                emitMessage("Nothing to fix — connection looks healthy.")
                return@launch
            }
            addLog("QUICKFIX", "Diagnosed ${diagnoses.size} issue(s)", LogLevel.INFO)
            var ok = 0
            var failed = 0
            diagnoses.forEach { rec ->
                rec.tweakIds.forEach { id ->
                    val tweak = TweakCatalog.byId(id) ?: return@forEach
                    addLog("QUICKFIX", "Applying: ${tweak.title}", LogLevel.INFO)
                    if (applyTweakInternal(tweak, emitStatusMessage = false)) ok++ else failed++
                }
            }
            emitMessage(
                when {
                    failed == 0 -> "Quick fixes applied ($ok verified)."
                    ok == 0 -> "Quick fixes failed — check Shizuku and try again."
                    else -> "$ok applied, $failed failed. See console."
                }
            )
            refreshEnvironmentInternal(refreshTweakStates = true, runFreshScan = false)
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            emitMessage("Resetting all tweaks...")
            addLog("RESET", "Reverting all optimization tweaks", LogLevel.WARNING)
            tweakCatalog.forEach { tweak ->
                if (tweak.revertCommands.isNotEmpty()) {
                    // directly await revert instead of fire-and-forget
                    val res = runCatching { revertTweakInternal(tweak) }.getOrDefault(false)
                    if (!res) addLog("RESET", "Revert skipped/failed for ${tweak.title}", LogLevel.WARNING)
                }
            }
            emitMessage("All settings reset to default.")
            refreshEnvironment()
        }
    }
    private suspend fun revertTweakInternal(tweak: WifiTweak): Boolean {
        if (!requireShizukuFor(tweak)) return false
        setTweakResult(tweak.id, status = TweakStatus.RUNNING, message = "Reverting...")

        // Prefer the journal: restore exactly what the device had before apply.
        val entry = tweakJournal[tweak.id]
        var success = true
        var detail = ""
        if (entry != null && entry.previousValues.isNotEmpty()) {
            addLog("REVERT", "Restoring captured values for ${tweak.id}", LogLevel.INFO)
            entry.previousValues.forEach { (key, prev) ->
                val cmd = if (prev == null) {
                    "settings delete global $key"
                } else {
                    "settings put global $key \"${prev.replace("\"", "")}\""
                }
                addLog("SHELL", cmd, LogLevel.INFO)
                val res = runCatching { shizukuExecutor.executeForResult(cmd, timeoutMs = 5_000) }.getOrNull()
                if (res?.isSuccess != true) {
                    success = false
                    detail = res?.stderr?.ifBlank { res.stdout } ?: "timeout"
                }
            }
            // static reverts for non-`settings` commands (cmd wifi … etc.)
            runCommands(tweak.revertCommands.filterNot { it.startsWith("settings put global") || it.startsWith("settings delete global") })
            if (success) consumeJournal(tweak.id)
        } else {
            // no journal → fall back to catalog defaults
            val result = runCommands(tweak.revertCommands)
            success = result.first
            detail = result.second
        }

        if (!success) {
            setTweakResult(tweak.id, status = TweakStatus.FAILED, message = detail.ifBlank { "Revert failed." })
            return false
        }
        when (probeTweakState(tweak)) {
            VerifyState.APPLIED_VERIFIED -> setTweakResult(tweak.id, TweakStatus.SUCCESS, message = "Still active · verified", isApplied = true, verified = true)
            VerifyState.APPLIED_UNVERIFIED -> setTweakResult(tweak.id, TweakStatus.SUCCESS, message = "Still active · unverified", isApplied = true, verified = null)
            VerifyState.NOT_APPLIED -> setTweakResult(tweak.id, TweakStatus.IDLE, message = "Default restored", isApplied = false, verified = null)
        }
        return true
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

    fun setConsoleEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNetworkConsoleEnabled(enabled) }
        addLog("SYSTEM", if (enabled) "Developer console enabled" else "Developer console disabled", LogLevel.WARNING)
    }

    fun clearLogs() {
        _uiState.update { it.copy(diagnosticLogs = emptyList()) }
    }

    fun benchmarkDns() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedBenchmarkProviders
            val candidateProviders = dnsProviders.filter { it.first in selectedIds }
            if (candidateProviders.isEmpty()) { emitMessage("Select at least one provider."); return@launch }
            _uiState.update {
                it.copy(
                    isBenchmarkingDns = true,
                    dnsBenchmarkStatus = BenchmarkStatus.RUNNING,
                    dnsBenchmarkResults = candidateProviders.map { p -> WifiDnsBenchmarkResult(p.first, p.second, p.third) }
                )
            }
            addLog("DNS", "Starting real DoH + TCP benchmark: ${candidateProviders.size} providers", LogLevel.INFO)
            val results = kotlinx.coroutines.coroutineScope {
                candidateProviders.map { (id, name, host) ->
                    async {
                        val stub = com.frerox.toolz.data.network.DnsProvider(id, name, listOf(host), host, "https://$host/dns-query")
                        val bench = runCatching { dnsRealBenchmark.benchmark(stub, samples = 2) }.getOrNull()
                        val latency = bench?.metrics?.latencyMs ?: pingHost(host)
                        // P4: DoT capability probe (what Private DNS actually uses)
                        val dot = runCatching { dnsRealBenchmark.dotProbe(if (host.matches(Regex("[0-9.]+"))) null else host) }.getOrNull()
                        WifiDnsBenchmarkResult(id, name, host, latency, dotLatencyMs = dot)
                    }
                }.awaitAll()
            }
            val best = results.filter { it.latencyMs != null }.minByOrNull { it.latencyMs!! }
            _uiState.update { state ->
                state.copy(
                    isBenchmarkingDns = false,
                    dnsBenchmarkStatus = BenchmarkStatus.COMPLETED,
                    dnsBenchmarkResults = results.map { it.copy(isRecommended = it.providerId == best?.providerId) }
                )
            }
            addLog("DNS", "Benchmark complete. Best: ${best?.name ?: "None"} (${best?.latencyMs ?: "?"}ms)", LogLevel.SUCCESS)
        }
    }

    fun updateBenchmarkSelection(id: String, selected: Boolean) {
        val current = _uiState.value.selectedBenchmarkProviders.toMutableSet()
        if (selected) current.add(id) else current.remove(id)
        viewModelScope.launch {
            settingsRepository.setNetworkBenchmarkServers(current)
        }
    }

    private val dnsProviders get() = DnsProviderLibrary.benchmarkTriples

    fun benchmarkProviders(): List<Triple<String, String, String>> = dnsProviders

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
            val safeHost = try { sanitizeHost(hostname) } catch (e: Exception) { emitMessage("Invalid hostname: ${e.message}"); return@launch }
            emitMessage("Applying custom DNS: $safeHost...")
            val success = runCommands(listOf(
                "settings put global private_dns_mode hostname",
                "settings put global private_dns_spec '$safeHost'"
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
                if (!screenActive) { delay(5_000); continue }
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

    private fun sanitizeHost(host: String): String {
        require(host.isNotBlank() && host.length <= 253) { "invalid host" }
        require(Regex("^[A-Za-z0-9._:-]+$").matches(host)) { "host illegal chars" }
        return host
    }
    suspend fun pingHost(host: String): Long? {
        val safeHost = try { sanitizeHost(host) } catch (_: Exception) { return null }
        // Try Shizuku first if ready
        if (uiState.value.shizukuStatus.isServiceReady) {
            val result = runCatching { shizukuExecutor.executeForResult("ping -c 1 -W 1 $safeHost") }.getOrNull()
            if (result?.isSuccess == true) {
                val match = "time=([\\d.]+)".toRegex().find(result.stdout)
                return match?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
            }
        }
        
        // Fallback to standard ping
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 1 $safeHost")
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
            if (tweak.type == TweakType.MANUAL_GUIDE) return@forEach
            val state = probeTweakState(tweak)
            val previous = existing[tweak.id] ?: TweakResult(id = tweak.id)
            existing[tweak.id] = when (state) {
                VerifyState.APPLIED_VERIFIED -> previous.copy(
                    status = TweakStatus.SUCCESS, message = "Active · verified",
                    isApplied = true, verified = true, lastUpdatedMs = System.currentTimeMillis()
                )
                VerifyState.APPLIED_UNVERIFIED -> previous.copy(
                    status = if (previous.status == TweakStatus.MANUAL) TweakStatus.MANUAL else TweakStatus.IDLE,
                    message = if (previous.status == TweakStatus.MANUAL) previous.message else "",
                    isApplied = false, verified = null, lastUpdatedMs = System.currentTimeMillis()
                )
                VerifyState.NOT_APPLIED -> previous.copy(
                    status = if (previous.status == TweakStatus.MANUAL) TweakStatus.MANUAL else TweakStatus.IDLE,
                    message = if (previous.status == TweakStatus.MANUAL) previous.message else "",
                    isApplied = false, verified = false, lastUpdatedMs = System.currentTimeMillis()
                )
            }
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
        val bands = spectrumAnalyzer.analyze(results)
        val flat = bands.flatMap { it.channels }
        // Enrich with utilizationScore + maxRssi
        val enriched = flat.map { c ->
            val netsOnCh = results.filter { it.channel == c.channel && it.band == c.band }
            val maxR = netsOnCh.maxOfOrNull { it.rssi } ?: -100
            val util = ((1.0 - (netsOnCh.size / 12.0).coerceIn(0.0,1.0))*0.4 + ((maxR+90)/60.0).coerceIn(0.0,1.0)*0.6)
            c.copy(maxRssi = maxR, utilizationScore = (util*100).toInt().coerceIn(0,100))
        }
        _uiState.update { it.copy(congestion = enriched.sortedBy { it.channel }) }
    }

    private fun updateAdvice() {
        val state = _uiState.value
        val strongest = rawScanResults.maxByOrNull { it.rssi }
        val openNetworks = rawScanResults.count { it.security == "Open" }
        val hiddenNetworks = rawScanResults.count { it.isHidden }
        val best24 = state.congestion.firstOrNull { it.isRecommended && it.band == "2.4 GHz" }?.channel
        val best5 = state.congestion.firstOrNull { it.isRecommended && it.band == "5 GHz" }?.channel
        val breakdown = healthEngine.calculate(state.currentRssi, state.stability, state.networkConfig.isThrottlingEnabled, openNetworks)
        val summary = when {
            breakdown.score >= 85 -> "Excellent — your link is clean and responsive."
            breakdown.score >= 70 -> "Good — minor congestion or jitter detected."
            breakdown.score >= 50 -> "Fair — crowded spectrum or weak signal."
            else -> "Poor — move closer, switch channel, or enable fixes."
        }
        _uiState.update {
            it.copy(
                advice = NetworkAdvice(
                    healthScore = breakdown.score,
                    summary = summary,
                    strongestNetwork = strongest?.ssid ?: "-",
                    best24GhzChannel = best24,
                    best5GhzChannel = best5,
                    openNetworks = openNetworks,
                    hiddenNetworks = hiddenNetworks,
                    totalNetworks = rawScanResults.size,
                    recommendation = buildRecommendation(best24, best5, breakdown)
                )
            )
        }
    }
    private fun buildRecommendation(best24: Int?, best5: Int?, bd: NetworkHealthEngine.HealthBreakdown): String {
        val ch = best5 ?: best24
        return when {
            ch != null -> "Switch to channel $ch for lower contention (${bd.label})."
            bd.penalties.isNotEmpty() -> bd.penalties.joinToString("; ")
            else -> "Scan nearby networks to get channel guidance."
        }
    }

    private fun calculateHealthScore(state: WifiTweaksUiState): Int =
        healthEngine.calculate(state.currentRssi, state.stability, state.networkConfig.isThrottlingEnabled).score

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

            // throttling log is emitted at most once per refresh to avoid spam
            if (isThrottling && _uiState.value.diagnosticLogs.none { it.message.contains("Scan Throttling is ACTIVE") && System.currentTimeMillis() - it.timestamp < 60_000 }) {
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
                        isConnected = info.isConnected || (
                            info.ssid != "Unknown" && info.ssid.isNotBlank() && info.rssi > -99
                        ),
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

        // OEM guard-rails: surface known vendor quirks before touching anything.
        val mfr = Build.MANUFACTURER.lowercase()
        tweak.oemNotes.entries.firstOrNull { mfr.contains(it.key) }?.let { (vendor, note) ->
            emitMessage("Note for $vendor devices: $note")
            addLog("OEM", "${tweak.title}: $note", LogLevel.WARNING)
        }

        if (tweak.type == TweakType.MANUAL_GUIDE) {
            setTweakResult(tweak.id, TweakStatus.MANUAL, "Manual steps required.", isApplied = false)
            if (emitStatusMessage) emitMessage("Manual guide ready for ${tweak.title}.")
            addLog("TWEAK", "Manual guide displayed for ${tweak.title}", LogLevel.SUCCESS)
            return true
        }

        if (!requireShizukuFor(tweak)) {
            addLog("TWEAK", "Shizuku not available for ${tweak.title}", LogLevel.WARNING)
            return false
        }

        // Capture BEFORE writing so revert can restore reality.
        val captured = capturePreviousValues(tweak)
        if (captured.isNotEmpty()) persistJournalEntry(tweak.id, captured)

        setTweakResult(tweak.id, TweakStatus.RUNNING, "Applying...")
        addLog("SHELL", "Executing ${tweak.applyCommands.size} commands for ${tweak.id}", LogLevel.INFO)
        val result = runCommands(tweak.applyCommands)
        if (!result.first) {
            setTweakResult(tweak.id, TweakStatus.FAILED, result.second.ifBlank { "Command failed." })
            if (emitStatusMessage) emitMessage("${'$'}{tweak.title} failed to apply.")
            addLog("TWEAK", "Failed to apply ${tweak.title}: ${result.second}", LogLevel.ERROR)
            return false
        }

        return when (val state = probeTweakState(tweak)) {
            VerifyState.APPLIED_VERIFIED -> {
                setTweakResult(tweak.id, TweakStatus.SUCCESS, "Active · verified", isApplied = true, verified = true)
                if (emitStatusMessage) emitMessage("${tweak.title} applied & verified.")
                addLog("TWEAK", "Verified ${tweak.title}", LogLevel.SUCCESS)
                true
            }
            VerifyState.APPLIED_UNVERIFIED -> {
                setTweakResult(tweak.id, TweakStatus.SUCCESS, "Applied · could not verify", isApplied = true, verified = null)
                if (emitStatusMessage) emitMessage("${tweak.title} applied (unverified).")
                addLog("TWEAK", "Unverifiable write: ${tweak.title}", LogLevel.WARNING)
                true
            }
            VerifyState.NOT_APPLIED -> {
                setTweakResult(tweak.id, TweakStatus.FAILED, "Write accepted but setting NOT active (likely OEM override).", isApplied = false, verified = false)
                if (emitStatusMessage) emitMessage("${tweak.title}: system did not accept the value.")
                addLog("TWEAK", "Verification mismatch: ${tweak.title}", LogLevel.ERROR)
                false
            }
        }
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

    /** Read-back probe returning an honest tri-state instead of optimistic booleans. */
    private suspend fun probeTweakState(tweak: WifiTweak): VerifyState {
        val verifyCmd = tweak.verificationCommand ?: return VerifyState.APPLIED_UNVERIFIED
        if (!_uiState.value.shizukuStatus.isServiceReady) return VerifyState.APPLIED_UNVERIFIED
        val result = withTimeoutOrNull(4_000) {
            runCatching { shizukuExecutor.executeForResult(verifyCmd, timeoutMs = 4_000) }.getOrNull()
        } ?: return VerifyState.APPLIED_UNVERIFIED
        return if (result.isSuccess) VerifyState.APPLIED_VERIFIED else VerifyState.NOT_APPLIED
    }

    private fun setTweakResult(
        id: String,
        status: TweakStatus,
        message: String,
        isApplied: Boolean = false,
        verified: Boolean? = null
    ) {
        val current = _uiState.value.tweakResults.toMutableMap()
        current[id] = TweakResult(
            id = id,
            status = status,
            message = message,
            isApplied = isApplied,
            verified = verified,
            lastUpdatedMs = System.currentTimeMillis()
        )
        _uiState.update { it.copy(tweakResults = current) }
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
            toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() ?: return
        }
        val now = System.currentTimeMillis()
        val interval = (kotlin.math.abs(rssi + 30) * 10).coerceIn(200, 2000).toLong()

        if (now - lastBeepTime > interval) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            lastBeepTime = now
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(message)
            _uiState.update { it.copy(lastActionMessage = message) }
        }
    }

    private var scanReceiverRegistered = false
    private fun registerScanReceiver() {
        if (scanReceiverRegistered) return
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(scanReceiver, filter)
            }
            scanReceiverRegistered = true
        } catch (_: Exception) {}
    }
    private fun unregisterScanReceiver() {
        if (!scanReceiverRegistered) return
        try { context.unregisterReceiver(scanReceiver) } catch (_: Exception) {}
        scanReceiverRegistered = false
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

    // ── Journal plumbing ──

    private fun parseJournal(json: String): Map<String, JournalEntry> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            root.keys().asSequence().mapNotNull { tweakId ->
                val obj = root.optJSONObject(tweakId) ?: return@mapNotNull null
                val vals = mutableMapOf<String, String?>()
                val arr = obj.optJSONArray("values") ?: return@mapNotNull null
                for (i in 0 until arr.length()) {
                    val pair = arr.optJSONObject(i) ?: continue
                    val key = pair.optString("key")
                    if (key.isBlank()) continue
                    vals[key] = if (pair.isNull("value")) null else pair.optString("value")
                }
                tweakId to JournalEntry(vals, obj.optLong("ts"))
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun serializeJournal(): String {
        val root = JSONObject()
        tweakJournal.forEach { (id, entry) ->
            val obj = JSONObject()
            obj.put("ts", entry.ts)
            val arr = org.json.JSONArray()
            entry.previousValues.forEach { (k, v) ->
                val p = JSONObject()
                p.put("key", k)
                if (v != null) p.put("value", v) else p.put("value", JSONObject.NULL)
                arr.put(p)
            }
            obj.put("values", arr)
            root.put(id, obj)
        }
        return root.toString()
    }

    private suspend fun persistJournalEntry(tweakId: String, values: Map<String, String?>) {
        if (values.isEmpty()) return
        tweakJournal = tweakJournal + (tweakId to JournalEntry(values))
        settingsRepository.setNetworkTweakJournal(serializeJournal())
        addLog("JOURNAL", "Captured ${values.size} previous value(s) for $tweakId", LogLevel.INFO)
    }

    private suspend fun consumeJournal(tweakId: String) {
        if (!tweakJournal.containsKey(tweakId)) return
        tweakJournal = tweakJournal - tweakId
        settingsRepository.setNetworkTweakJournal(serializeJournal())
    }

    /** Reads current values of every `settings put global KEY …` target so revert can restore truth. */
    private suspend fun capturePreviousValues(tweak: WifiTweak): Map<String, String?> {
        val keys = tweak.applyCommands.mapNotNull { cmd ->
            Regex("^settings put global (\\S+) ").find(cmd)?.groupValues?.get(1)
        }.distinct()
        if (keys.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, String?>()
        keys.forEach { key ->
            val res = withTimeoutOrNull(5_000) {
                runCatching { shizukuExecutor.executeForResult("settings get global $key", timeoutMs = 4_000) }.getOrNull()
            }
            val v = res?.stdout?.trim()
            out[key] = if (res?.isSuccess == true && !v.isNullOrBlank() && v != "null" && v != "-1") v else null
        }
        return out
    }

    override fun onCleared() {
        super.onCleared()
        unregisterScanReceiver()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
            Shizuku.removeBinderDeadListener(shizukuBinderDeathListener)
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        } catch (_: Exception) {}
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    companion object {
        const val SHIZUKU_CODE = 1001
    }
}
