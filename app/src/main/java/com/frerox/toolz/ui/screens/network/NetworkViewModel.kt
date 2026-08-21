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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.network.DnsProvider
import com.frerox.toolz.data.network.NetworkDevice
import com.frerox.toolz.data.network.NetworkPowerUiState
import com.frerox.toolz.data.network.PingSample
import com.frerox.toolz.data.network.PublicIpInfo
import com.frerox.toolz.data.network.SpeedTestResult
import com.frerox.toolz.data.network.StabilityInfo
import com.frerox.toolz.data.network.TopologyEdge
import com.frerox.toolz.data.network.TopologyNode
import com.frerox.toolz.data.network.NetworkTopology
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.network.DnsEngine
import com.frerox.toolz.util.network.NetworkMonitor
import com.frerox.toolz.util.network.NetworkScanner
import com.frerox.toolz.util.network.PrivilegedNetworkManager
import com.frerox.toolz.util.network.SpeedTestEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetAddress
import java.net.URL
import javax.inject.Inject
import kotlin.math.abs
import kotlin.system.measureTimeMillis

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val dnsEngine: DnsEngine,
    private val networkScanner: NetworkScanner,
    private val speedTestEngine: SpeedTestEngine,
    private val privilegedNetworkManager: PrivilegedNetworkManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val performanceMode = settingsRepository.performanceMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    private val _uiState = MutableStateFlow(
        NetworkPowerUiState(
            privateDnsMode = "Automatic",
            dnsResults = dnsEngine.providerLibrary().map { provider ->
                com.frerox.toolz.data.network.DnsBenchmarkResult(provider = provider)
            }
        )
    )
    val uiState: StateFlow<NetworkPowerUiState> = _uiState.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var dnsJob: Job? = null

    /** P6: privileged/ping telemetry pauses when suite is backgrounded. */
    @Volatile private var screenActive = true
    fun setScreenActive(active: Boolean) { screenActive = active }
    private var deviceScanJob: Job? = null

    init {
        observeWifi()
        startPrivilegedStateMonitor()
        startPingMonitor()
        startTelemetryRefresh()
        refreshSuite()
    }

    fun dnsProviders(): List<DnsProvider> = dnsEngine.providerLibrary()

    fun refreshSuite() {
        refreshDns()
        fetchPublicIp()
        viewModelScope.launch { refreshPrivilegedSnapshot(forceRebind = true) }
        runTraceRoute()
    }

    fun refreshDns(includeCustom: DnsProvider? = null) {
        dnsJob?.cancel()
        dnsJob = viewModelScope.launch {
            updateState { copy(isRefreshingDns = true) }
            appendLog("> dns benchmark: top providers")
            val providers = buildList {
                addAll(dnsEngine.providerLibrary())
                if (includeCustom != null) add(0, includeCustom)
            }
            val results = dnsEngine.benchmarkTopProviders(providers.distinctBy { it.id }, limit = 10)
            val recommendation = dnsEngine.buildRecommendation(results)
            updateState {
                copy(
                    isRefreshingDns = false,
                    dnsResults = results,
                    dnsRecommendation = recommendation
                )
            }
        }
    }

    fun benchmarkCustomDns(
        label: String,
        primaryAddress: String,
        secondaryAddress: String?,
        hostname: String?
    ) {
        val customProvider = dnsEngine.customProvider(
            label = label.ifBlank { "Custom DNS" },
            addresses = listOfNotNull(primaryAddress, secondaryAddress),
            hostname = hostname
        )
        refreshDns(includeCustom = customProvider)
    }

    fun applyDnsProvider(provider: DnsProvider) {
        viewModelScope.launch {
            if (!ensurePrivilegedAccess("Shizuku access is required to apply Private DNS.")) {
                return@launch
            }
            if (provider.hostname.isNullOrBlank()) {
                emitEvent("This provider needs a Private DNS hostname for one-tap apply.")
                return@launch
            }
            val safe = provider.hostname.replace("'",""); appendLog("> settings put global private_dns_spec $safe")
            val summary = privilegedNetworkManager.setPrivateDns(provider.hostname)
            val cache = privilegedNetworkManager.flushDnsCache()
            updateState {
                copy(
                    privateDnsMode = "Provider hostname",
                    privateDnsHost = provider.hostname,
                    cacheAnalytics = cache
                )
            }
            emitEvent(summary)
            refreshPrivilegedSnapshot()
        }
    }

    fun applyCustomPrivateDns(hostname: String) {
        viewModelScope.launch {
            if (!ensurePrivilegedAccess("Shizuku access is required to apply a custom Private DNS hostname.")) {
                return@launch
            }
            if (hostname.isBlank()) {
                emitEvent("Private DNS hostname cannot be empty.")
                return@launch
            }
            val safeH = hostname.replace("'",""); appendLog("> settings put global private_dns_spec $safeH")
            val summary = privilegedNetworkManager.setPrivateDns(hostname)
            val cache = privilegedNetworkManager.flushDnsCache()
            updateState {
                copy(
                    privateDnsMode = "Custom hostname",
                    privateDnsHost = hostname,
                    cacheAnalytics = cache
                )
            }
            emitEvent(summary)
            refreshPrivilegedSnapshot()
        }
    }

    fun resetPrivateDns() {
        viewModelScope.launch {
            if (!ensurePrivilegedAccess("Shizuku access is required to reset Private DNS.")) {
                return@launch
            }
            appendLog("> settings put global private_dns_mode opportunistic")
            val summary = privilegedNetworkManager.setPrivateDns(null)
            updateState { copy(privateDnsMode = "Automatic", privateDnsHost = "") }
            emitEvent(summary)
            refreshPrivilegedSnapshot()
        }
    }

    fun flushDnsCache() {
        viewModelScope.launch {
            if (!ensurePrivilegedAccess("Shizuku access is required to flush the system DNS cache.")) {
                return@launch
            }
            appendLog("> cmd network dns_cache_clear")
            val analytics = privilegedNetworkManager.flushDnsCache()
            updateState { copy(cacheAnalytics = analytics) }
            emitEvent("DNS cache flushed.")
            refreshPrivilegedSnapshot()
        }
    }

    fun fetchPublicIp() {
        viewModelScope.launch {
            updateState { copy(isRefreshingPublicIp = true) }
            val apis = listOf("https://api.ipify.org?format=json", "https://ipapi.co/json/")
            var resolved: PublicIpInfo? = null
            for (apiUrl in apis) {
                resolved = runCatching {
                    withContext(Dispatchers.IO) {
                        val conn = (URL(apiUrl).openConnection() as java.net.HttpURLConnection).apply {
                            connectTimeout = 3500; readTimeout = 3500; setRequestProperty("User-Agent","Toolz/2.0")
                        }
                        if (conn.responseCode in 200..299) parsePublicIp(conn.inputStream.bufferedReader().readText()) else null
                    }
                }.getOrNull()
                if (resolved != null) break
            }
            updateState { copy(publicIpInfo = resolved ?: PublicIpInfo(ip = "Check Internet", isp = "Unavailable"), isRefreshingPublicIp = false) }
        }
    }

    /** MACs seen on previous scans → powers "new device joined" detection. */
    private var previouslySeenMacs: Set<String> = emptySet()

    fun scanSubnet() {
        deviceScanJob?.cancel()
        deviceScanJob = viewModelScope.launch {
            val gateway = uiState.value.wifiState.gateway
            if (gateway == "0.0.0.0") {
                emitEvent("Connect to Wi-Fi before scanning the subnet.")
                return@launch
            }
            updateState { copy(isScanningDevices = true, scannedDevices = emptyList(), newDeviceIps = emptySet(), topology = NetworkTopology()) }
            appendLog("> subnet scan: $gateway/24")
            val knownDevices = linkedMapOf<String, NetworkDevice>()
            val freshIps = mutableSetOf<String>()
            networkScanner.scanSubnetDelta(gateway).collect { device ->
                // P4 delta detection: flag devices whose MAC we never saw in prior scans
                if (device.mac != "Unknown" && previouslySeenMacs.isNotEmpty() && device.mac !in previouslySeenMacs) {
                    appendLog("DISCOVERY · New device joined: ${device.hostname.ifBlank { device.ip }} (${device.vendor})")
                    emitEvent("New device: ${device.hostname.ifBlank { device.ip }} · ${device.vendor}")
                    freshIps += device.ip
                }
                knownDevices[device.ip] = device
                val merged = knownDevices.values.sortedWith(
                    compareByDescending<NetworkDevice> { it.isGateway }.thenBy { it.ip }
                )
                updateState {
                    copy(
                        scannedDevices = merged,
                        topology = buildTopology(merged)
                    )
                }
            }

            // P4: passive mDNS sweep enriches labels for discovered hosts
            runCatching {
                networkScanner.mdnsDiscover().forEach { (ip, label) ->
                    knownDevices[ip]?.let { dev ->
                        knownDevices[ip] = dev.copy(
                            hostname = if (dev.hostname == "Unknown") label.substringBefore(" (") else dev.hostname,
                            typeLabel = label.substringAfter(" (").removeSuffix(")").ifBlank { dev.typeLabel }
                        )
                        appendLog("mDNS · $ip → $label")
                    } ?: run {
                        // host not found by ICMP but visible via mDNS — add it anyway
                        knownDevices[ip] = NetworkDevice(ip = ip, hostname = label.substringBefore(" ("), typeLabel = label)
                        appendLog("mDNS · host added: $ip → $label")
                    }
                }
            }
            previouslySeenMacs = knownDevices.values.mapNotNull { d -> d.mac.takeIf { it != "Unknown" } }.toSet()

            val mergedFinal = knownDevices.values.sortedWith(
                compareByDescending<NetworkDevice> { it.isGateway }.thenBy { it.ip }
            )
            updateState {
                copy(
                    scannedDevices = mergedFinal,
                    newDeviceIps = freshIps.toSet(),
                    topology = buildTopology(mergedFinal),
                    isScanningDevices = false
                )
            }
        }
    }

    fun scanGatewayPorts() {
        viewModelScope.launch {
            val gateway = uiState.value.wifiState.gateway
            if (gateway == "0.0.0.0") {
                emitEvent("Gateway unavailable.")
                return@launch
            }
            updateState { copy(isScanningPorts = true) }
            appendLog("> port scan: $gateway")
            val ports = networkScanner.scanPorts(gateway)
            updateState { copy(scannedPorts = ports, isScanningPorts = false) }
        }
    }

    fun runSpeedTest() {
        viewModelScope.launch {
            updateState {
                copy(
                    speedTestResult = SpeedTestResult(
                        isRunning = true,
                        phaseLabel = "Downloading test payload"
                    )
                )
            }
            try {
                speedTestEngine.runDownloadTest().collect { (progress, speed) ->
                    updateState {
                        copy(
                            speedTestResult = SpeedTestResult(
                                downloadSpeedMbps = speed,
                                isRunning = true,
                                progress = progress,
                                phaseLabel = if (progress < 1f) "Measuring throughput" else "Finalizing"
                            )
                        )
                    }
                }
                updateState {
                    copy(
                        speedTestResult = speedTestResult.copy(
                            isRunning = false,
                            progress = 1f,
                            phaseLabel = "Complete"
                        )
                    )
                }
            } catch (error: Exception) {
                updateState {
                    copy(
                        speedTestResult = SpeedTestResult(
                            isRunning = false,
                            error = error.message ?: "Speed test failed.",
                            phaseLabel = "Failed"
                        )
                    )
                }
            }
        }
    }

    fun toggleMobileData(enabled: Boolean) {
        viewModelScope.launch {
            if (!ensurePrivilegedAccess("Shizuku access is required to toggle mobile data.")) {
                return@launch
            }
            appendLog("> svc data ${if (enabled) "enable" else "disable"}")
            val summary = privilegedNetworkManager.toggleMobileData(enabled)
            updateState { copy(isDataEnabled = enabled) }
            emitEvent(summary)
            refreshPrivilegedSnapshot()
        }
    }

    fun runTraceRoute(target: String = "1.1.1.1") {
        val safeTarget = target.trim().takeIf { Regex("^[0-9A-Za-z._-]+$").matches(it) } ?: "1.1.1.1"
        viewModelScope.launch {
            if (!ensurePrivilegedAccess("Shizuku access is required to run traceroute.")) {
                return@launch
            }
            val hops = privilegedNetworkManager.runTraceRoute(safeTarget)
            if (hops.isNotEmpty()) {
                updateState { copy(traceHops = hops) }
            }
        }
    }

    fun verifyPrivilegedAccess() {
        viewModelScope.launch {
            refreshPrivilegedSnapshot(forceRebind = true)
            if (uiState.value.privilegedState.isServiceReady) {
                emitEvent("Shizuku verified and ready.")
            } else {
                emitEvent("Shizuku is still unavailable. Start the service in the Shizuku app, then try again.")
            }
        }
    }

    private fun observeWifi() {
        viewModelScope.launch {
            networkMonitor.observeWifiInfo().collect { wifi ->
                val history = (_uiState.value.wifiState.rssiHistory + wifi.rssi).takeLast(60)
                updateState {
                    copy(
                        wifiState = wifi.copy(rssiHistory = history),
                        topology = buildTopology(scannedDevices, wifi.ssid)
                    )
                }
                updateHealthScore()
            }
        }
    }

    private fun startPrivilegedStateMonitor() {
        viewModelScope.launch {
            while (isActive) {
                if (!screenActive) { delay(5_000); continue }
                refreshPrivilegedSnapshot()
                delay(3_500)
            }
        }
    }

    private fun startTelemetryRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refreshPrivilegedSnapshot()
                if (uiState.value.privilegedState.isServiceReady) {
                    supervisorScope {
                        val cacheDeferred = async { privilegedNetworkManager.readDnsCacheAnalytics() }
                        val ipDeferred = async { privilegedNetworkManager.readIpAudit() }
                        val cellDeferred = async { privilegedNetworkManager.readCellularAudit() }
                        val procDeferred = async { privilegedNetworkManager.readProcessUsage() }
                        val dnsConfigDeferred = async { privilegedNetworkManager.readPrivateDnsConfig() }

                        val cache = cacheDeferred.await()
                        val ipAudit = ipDeferred.await()
                        val cellular = cellDeferred.await()
                        val processes = procDeferred.await()
                        val dnsConfig = dnsConfigDeferred.await()

                        updateState {
                            copy(
                                cacheAnalytics = cache,
                                ipAudit = ipAudit,
                                cellularAudit = cellular,
                                // keep the switch truthful once the system tells us the real state
                                isDataEnabled = cellular.mobileDataEnabled ?: isDataEnabled,
                                activeProcesses = processes,
                                privateDnsMode = dnsConfig.first.replaceFirstChar(Char::titlecase),
                                privateDnsHost = dnsConfig.second
                            )
                        }
                    }
                }
                delay(if (performanceMode.value) 15_000 else 8_000)
            }
        }
    }

    private fun startPingMonitor() {
        viewModelScope.launch {
            while (isActive) {
                if (!screenActive) { delay(4_000); continue }
                val gateway = uiState.value.wifiState.gateway
                if (gateway != "0.0.0.0") {
                    val latency = measurePing(gateway)
                    val history = (uiState.value.pingSamples + PingSample(System.currentTimeMillis(), latency)).takeLast(60)
                    val stability = buildStability(history)
                    updateState {
                        copy(
                            pingSamples = history,
                            stabilityInfo = stability
                        )
                    }
                    updateHealthScore()
                }
                delay(1_000)
            }
        }
    }

    private suspend fun refreshPrivilegedSnapshot(forceRebind: Boolean = false) {
        val state = privilegedNetworkManager.refreshState(
            lastSummary = uiState.value.privilegedState.lastCommandSummary,
            forceRebind = forceRebind
        )
        updateState { copy(privilegedState = state) }
    }

    private suspend fun measurePing(host: String): Long? = withContext(Dispatchers.IO) {
        if (host == "0.0.0.0" || host.isBlank()) return@withContext null
        runCatching {
            val address = InetAddress.getByName(host)
            var reachable = false
            val elapsed = measureTimeMillis { reachable = address.isReachable(650) }
            if (reachable) elapsed.coerceAtLeast(1L) else null
        }.getOrNull()
    }

    private fun buildStability(samples: List<PingSample>): StabilityInfo {
        val recent = samples.takeLast(12)
        val latencies = recent.mapNotNull { it.latencyMs }
        if (recent.isEmpty() || latencies.isEmpty()) {
            return StabilityInfo(isTesting = true, samplesCollected = recent.size)
        }
        val average = latencies.average().toLong()
        val jitter = if (latencies.size > 1) {
            latencies.zipWithNext { a, b -> abs(a - b) }.average().toLong()
        } else {
            0L
        }
        val losses = recent.count { it.latencyMs == null }
        val packetLoss = (losses / recent.size.toFloat()) * 100f
        val score = calculateNetworkScore(average, jitter, packetLoss)
        return StabilityInfo(
            target = "Gateway",
            avgLatency = average,
            jitter = jitter,
            packetLoss = packetLoss,
            isTesting = false,
            score = score,
            samplesCollected = recent.size
        )
    }

    private fun calculateNetworkScore(
        latency: Long,
        jitter: Long,
        packetLoss: Float
    ): Int {
        val latencyPenalty = (latency / 4).toInt().coerceIn(0, 35)
        val jitterPenalty = (jitter * 2).toInt().coerceIn(0, 25)
        val lossPenalty = (packetLoss * 0.5f).toInt().coerceIn(0, 40)
        return (100 - latencyPenalty - jitterPenalty - lossPenalty).coerceIn(0, 100)
    }

    private fun updateHealthScore() {
        val wifi = uiState.value.wifiState
        val stability = uiState.value.stabilityInfo
        val signalPenalty = when {
            wifi.rssi >= -55 -> 0
            wifi.rssi >= -62 -> 10
            wifi.rssi >= -70 -> 20
            else -> 35
        }
        val lossPenalty = (stability.packetLoss * 0.45f).toInt().coerceIn(0, 30)
        val jitterPenalty = (stability.jitter / 3).toInt().coerceIn(0, 20)
        updateState {
            copy(networkHealthScore = (100 - signalPenalty - lossPenalty - jitterPenalty).coerceIn(0, 100))
        }
    }

    private fun buildTopology(
        devices: List<NetworkDevice>,
        networkLabel: String = uiState.value.wifiState.ssid
    ): NetworkTopology {
        val nodes = mutableListOf(
            TopologyNode(
                id = "phone",
                label = "Phone",
                detail = networkLabel.ifBlank { "Client" },
                tier = 0,
                xBias = 0.5f,
                yBias = 0.16f,
                isPrimary = true
            )
        )
        val edges = mutableListOf<TopologyEdge>()
        val gateway = devices.firstOrNull { it.isGateway }
        if (gateway != null) {
            nodes += TopologyNode(
                id = gateway.ip,
                label = "Gateway",
                detail = gateway.ip,
                tier = 1,
                xBias = 0.5f,
                yBias = 0.44f,
                isPrimary = true
            )
            edges += TopologyEdge("phone", gateway.ip, strength = 1f)
        }
        devices.filterNot { it.isGateway }.take(6).forEachIndexed { index, device ->
            val spread = listOf(0.14f, 0.32f, 0.50f, 0.68f, 0.86f, 0.24f)
            nodes += TopologyNode(
                id = device.ip,
                label = device.hostname.ifBlank { device.typeLabel },
                detail = device.ip,
                tier = 2,
                xBias = spread.getOrElse(index) { 0.5f },
                yBias = if (index < 3) 0.74f else 0.86f
            )
            edges += TopologyEdge(gateway?.ip ?: "phone", device.ip, strength = 0.72f)
        }
        return NetworkTopology(nodes = nodes, edges = edges)
    }

    private fun parsePublicIp(payload: String): PublicIpInfo {
        val json = JSONObject(payload)
        return PublicIpInfo(
            ip = json.optString("ip", json.optString("query", "Unknown")),
            isp = json.optString("org", json.optString("asn_org", "Unknown")),
            city = json.optString("city", "Unknown"),
            country = json.optString("country_name", json.optString("country", "Unknown")),
            asn = json.optString("asn", json.optString("org", "Unknown"))
        )
    }

    private fun updateState(transform: NetworkPowerUiState.() -> NetworkPowerUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun appendLog(message: String) {
        _terminalLogs.value = (_terminalLogs.value + message).takeLast(120)
    }

    private suspend fun emitEvent(message: String) {
        _events.emit(message)
    }

    private suspend fun ensurePrivilegedAccess(message: String): Boolean {
        refreshPrivilegedSnapshot(forceRebind = true)
        val ready = uiState.value.privilegedState.isAuthorized && uiState.value.privilegedState.isServiceReady
        if (!ready) {
            emitEvent(message)
        }
        return ready
    }
}
