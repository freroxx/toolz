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

package com.frerox.toolz.util.network

import android.content.pm.PackageManager
import com.frerox.toolz.data.network.CellularAuditInfo
import com.frerox.toolz.data.network.DnsCacheAnalytics
import com.frerox.toolz.data.network.IpAuditInfo
import com.frerox.toolz.data.network.PrivilegedState
import com.frerox.toolz.data.network.ProcessNetworkUsage
import com.frerox.toolz.data.network.TraceHop
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivilegedNetworkManager @Inject constructor(
    private val shizukuExecutor: ShizukuShellExecutor
) {

    suspend fun refreshState(
        lastSummary: String = "Idle",
        forceRebind: Boolean = false
    ): PrivilegedState = withContext(Dispatchers.IO) {
        val reachable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val authorized = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val serviceReady = if (reachable && authorized) {
            runCatching { shizukuExecutor.ensureService(forceRebind = forceRebind) }.getOrDefault(false)
        } else {
            false
        }
        PrivilegedState(
            isReachable = reachable,
            isAuthorized = authorized,
            isServiceReady = serviceReady,
            lastCommandSummary = lastSummary
        )
    }

    private fun sanitizeHostname(host: String): String {
        // Allow only hostname-ish characters to prevent shell injection
        require(host.length in 3..253) { "hostname length invalid" }
        require(Regex("^[A-Za-z0-9._-]+$").matches(host)) { "hostname contains illegal characters" }
        require(!host.contains("..")) { "hostname invalid" }
        return host
    }

    suspend fun setPrivateDns(hostname: String?): String = withContext(Dispatchers.IO) {
        if (hostname.isNullOrBlank()) {
            val modeRes = runCommand("settings put global private_dns_mode opportunistic")
            runCommand("settings delete global private_dns_spec")
            if (!modeRes.isSuccess) {
                "Failed to reset Private DNS: ${modeRes.stderr.ifBlank { "Exit code ${modeRes.exitCode}" }}"
            } else {
                "Private DNS reset to automatic (opportunistic)."
            }
        } else {
            val modeRes = runCommand("settings put global private_dns_mode hostname")
            val safe = sanitizeHostname(hostname)
            val hostRes = runCommand("settings put global private_dns_spec '$safe'")
            if (!modeRes.isSuccess || !hostRes.isSuccess) {
                "Failed to set Private DNS: ${(modeRes.stderr + " " + hostRes.stderr).trim()}"
            } else {
                "Private DNS hostname applied: $hostname"
            }
        }
    }

    suspend fun setDoTDns(dotHostname: String): String = withContext(Dispatchers.IO) {
        setPrivateDns(dotHostname)
    }

    suspend fun setDoHDns(dohHostname: String): String = withContext(Dispatchers.IO) {
        // DoH via Android 13+ Private DNS or DoT host fallback
        setPrivateDns(dohHostname)
    }

    suspend fun readPrivateDnsConfig(): Pair<String, String> = withContext(Dispatchers.IO) {
        val mode = runCommand("settings get global private_dns_mode").lineOrBlank().ifBlank { "opportunistic" }
        val host = runCommand("settings get global private_dns_spec").lineOrBlank()
        mode to host
    }

    suspend fun flushDnsCache(): DnsCacheAnalytics = withContext(Dispatchers.IO) {
        runCommand("cmd network dns_cache_clear")
        val analytics = readDnsCacheAnalytics()
        analytics.copy(
            flushSupported = true,
            lastFlushEpochMs = System.currentTimeMillis()
        )
    }

    suspend fun toggleMobileData(enabled: Boolean): String = withContext(Dispatchers.IO) {
        val command = if (enabled) "svc data enable" else "svc data disable"
        runCommand(command)
        runCommand("settings put global mobile_data ${if (enabled) 1 else 0}")
        runCommand("cmd phone data ${if (enabled) "enable" else "disable"}")
        val verified = readMobileDataEnabled()
        when (verified) {
            enabled -> if (enabled) "Mobile data enabled and verified." else "Mobile data disabled and verified."
            null -> if (enabled) "Mobile data command sent." else "Mobile data disable command sent."
            else -> "Mobile data command sent, but system reported ${if (verified) "enabled" else "disabled"}."
        }
    }

    suspend fun setWifiPowerSave(disabled: Boolean): String = withContext(Dispatchers.IO) {
        runCommand("cmd wifi set-power-save-mode ${if (disabled) "disabled" else "enabled"}")
        if (disabled) "Wi-Fi power save disabled for low latency." else "Wi-Fi power save restored."
    }

    suspend fun cycleWifiRadio(): String = withContext(Dispatchers.IO) {
        runCommand("svc wifi disable")
        kotlinx.coroutines.delay(900)
        runCommand("svc wifi enable")
        "Wi-Fi radio cycled."
    }

    suspend fun setCaptivePortalMode(enabled: Boolean): String = withContext(Dispatchers.IO) {
        runCommand("settings put global captive_portal_mode ${if (enabled) 1 else 0}")
        if (enabled) "Captive portal detection enabled." else "Captive portal detection disabled."
    }

    suspend fun readIpAudit(): IpAuditInfo = withContext(Dispatchers.IO) {
        val routes = runCommand("ip route show table all").stdout.lines().filter { it.isNotBlank() }.take(60)
        val neighbors = runCommand("ip neigh show").stdout.lines().filter { it.isNotBlank() }.take(80)
        val interfaces = runCommand("ip -o addr show").stdout.lines().filter { it.isNotBlank() }.take(60)
        val dnsServers = runCommand("getprop | grep -E 'dns[0-9]'").combinedOutput
            .lines()
            .mapNotNull { Regex("\\[(.+?)]\\s*:\\s*\\[(.+?)]").find(it)?.groupValues?.getOrNull(2) }
            .filter { it.isNotBlank() }
            .distinct()
        IpAuditInfo(
            routes = routes,
            neighbors = neighbors,
            interfaces = interfaces,
            defaultRoute = routes.firstOrNull { it.startsWith("default") } ?: routes.firstOrNull().orEmpty(),
            dnsServers = dnsServers,
            isAvailable = routes.isNotEmpty() || neighbors.isNotEmpty() || interfaces.isNotEmpty()
        )
    }

    suspend fun readCellularAudit(): CellularAuditInfo = withContext(Dispatchers.IO) {
        val dump = runCommand("dumpsys telephony.registry").combinedOutput
        if (dump.isBlank()) return@withContext CellularAuditInfo()
        val tech = when {
            dump.contains("nrState=CONNECTED", ignoreCase = true) -> "5G"
            dump.contains("networkType=LTE", ignoreCase = true) -> "LTE"
            dump.contains("networkType=HSPAP", ignoreCase = true) -> "HSPA+"
            else -> "Cellular"
        }
        CellularAuditInfo(
            tech = tech,
            cellId = Regex("mCid=(\\d+)").find(dump)?.groupValues?.getOrNull(1) ?: "Unknown",
            tac = Regex("mTac=(\\d+)").find(dump)?.groupValues?.getOrNull(1) ?: "Unknown",
            snr = Regex("ssRsrq=(-?\\d+)").find(dump)?.groupValues?.getOrNull(1) ?: "Unknown",
            signalStrength = Regex("mSignalStrength=(.+)").find(dump)?.groupValues?.getOrNull(1) ?: "Unknown",
            mobileDataEnabled = readMobileDataEnabled(),
            airplaneModeEnabled = runCommand("settings get global airplane_mode_on").lineOrBlank() == "1",
            preferredNetworkMode = runCommand("settings get global preferred_network_mode").lineOrBlank().ifBlank { "Unknown" },
            dataSaverEnabled = runCommand("cmd netpolicy get restrict-background").combinedOutput.contains("enabled", ignoreCase = true),
            isAvailable = true
        )
    }

    suspend fun readProcessUsage(): List<ProcessNetworkUsage> = withContext(Dispatchers.IO) {
        val output = listOf(
            "ss -tunap",
            "ss -tun",
            "netstat -tunap",
            "netstat -tun"
        ).firstNotNullOfOrNull { command ->
            runCommand(command).combinedOutput.takeIf { it.isNotBlank() }
        }.orEmpty()
        output.lineSequence()
            .filter { it.contains("ESTABLISHED") || it.contains("LISTEN") }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 5) {
                    null
                } else {
                    val protocol = parts.first().uppercase().takeIf { it.startsWith("TCP") || it.startsWith("UDP") } ?: "TCP"
                    val stateIndex = parts.indexOfFirst { it == "ESTABLISHED" || it == "LISTEN" }.takeIf { it >= 0 } ?: 1
                    val local = parts.getOrNull(stateIndex + 2) ?: parts.getOrNull(3) ?: return@mapNotNull null
                    val remote = parts.getOrNull(stateIndex + 3) ?: parts.getOrNull(4) ?: "*:*"
                    val process = Regex("users:\\(\\(\"(.+?)\"").find(line)?.groupValues?.getOrNull(1)
                    ProcessNetworkUsage(
                        pid = Regex("pid=(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1,
                        name = process ?: if (remote.contains(":443")) "TLS session" else "Socket session",
                        localAddr = local,
                        remoteAddr = remote,
                        state = parts.getOrNull(stateIndex) ?: "UNKNOWN",
                        protocol = protocol,
                        countryCode = if (isPrivateAddress(remote.substringBeforeLast(":"))) "LAN" else "WAN"
                    )
                }
            }
            .distinctBy { "${it.protocol}-${it.localAddr}-${it.remoteAddr}-${it.name}" }
            .take(32)
            .toList()
    }

    suspend fun readDnsCacheAnalytics(): DnsCacheAnalytics = withContext(Dispatchers.IO) {
        val dump = runCommand("dumpsys netd").combinedOutput.ifBlank {
            runCommand("cmd netd resolver stats").combinedOutput
        }
        val hits = Regex("(?i)cache\\s*hits?[^0-9]*(\\d+)").find(dump)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val queries = Regex("(?i)(queries|lookups)[^0-9]*(\\d+)").find(dump)?.groupValues?.lastOrNull()?.toIntOrNull()
        val entries = Regex("(?i)(entries|records)[^0-9]*(\\d+)").find(dump)?.groupValues?.lastOrNull()?.toIntOrNull()
        val ratio = if (hits != null && queries != null && queries > 0) {
            ((hits.toFloat() / queries.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else {
            null
        }
        DnsCacheAnalytics(
            hitRatioPercent = ratio,
            entryCount = entries,
            flushSupported = true,
            sourceSummary = if (dump.isBlank()) "Resolver stats unavailable" else "Parsed from netd resolver stats"
        )
    }

    suspend fun runTraceRoute(target: String): List<TraceHop> = withContext(Dispatchers.IO) {
        val output = listOf(
            "traceroute -n -m 20 -w 2 $target",
            "toybox traceroute -n -m 20 -w 2 $target",
            "busybox traceroute -n -m 20 -w 2 $target"
        ).firstNotNullOfOrNull { command ->
            runCommand(command).combinedOutput.takeIf { it.lineSequence().any { line -> Regex("^\\s*\\d+\\s+").containsMatchIn(line) } }
        }.orEmpty()
        val parsed = parseTraceOutput(output)
        parsed.ifEmpty {
            // P4: fire TTL probes concurrently (was serial → up to ~24 s); then trim
            // everything past the first hop that already reached the target.
            kotlinx.coroutines.coroutineScope {
                val limited = Dispatchers.IO.limitedParallelism(6)
                (1..15).map { ttl ->
                    async(limited) {
                        val ping = runCommand("ping -c 1 -W 2 -t $ttl $target").combinedOutput
                        parsePingTtlHop(ttl, ping)
                    }
                }.awaitAll()
            }.filterNotNull().let { hops ->
                val reachedAt = hops.firstOrNull { it.ip == target }?.hop ?: Int.MAX_VALUE
                hops.filter { it.hop <= reachedAt }.sortedBy { it.hop }
            }
        }
    }

    private fun parseTraceOutput(output: String): List<TraceHop> {
        return output.lineSequence().mapNotNull { line ->
            val hop = Regex("^\\s*(\\d+)\\s+").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val ip = Regex("(\\d{1,3}(?:\\.\\d{1,3}){3})").find(line)?.groupValues?.getOrNull(1) ?: "*"
            val latencies = Regex("(\\d+(?:\\.\\d+)?)\\s*ms").findAll(line).mapNotNull {
                it.groupValues.getOrNull(1)?.toDoubleOrNull()?.toLong()
            }.toList()
            TraceHop(
                hop = hop,
                ip = ip,
                label = if (hop == 1) "Local Gateway" else "Hop $hop",
                location = if (isPrivateAddress(ip)) "Internal" else "External",
                latencyMs = latencies.minOrNull(),
                lossPercent = if (latencies.isEmpty()) 100f else ((3 - latencies.size).coerceAtLeast(0) / 3f) * 100f,
                method = "traceroute"
            )
        }.toList()
    }

    private fun parsePingTtlHop(ttl: Int, output: String): TraceHop? {
        val ip = Regex("From\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})").find(output)?.groupValues?.getOrNull(1)
            ?: Regex("from\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})").find(output)?.groupValues?.getOrNull(1)
            ?: Regex("(\\d{1,3}(?:\\.\\d{1,3}){3})").find(output)?.groupValues?.getOrNull(1)
            ?: return null
        val latency = Regex("time=(\\d+(?:\\.\\d+)?)").find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toLong()
        return TraceHop(
            hop = ttl,
            ip = ip,
            label = if (ttl == 1) "Local Gateway" else "Hop $ttl",
            location = if (isPrivateAddress(ip)) "Internal" else "External",
            latencyMs = latency,
            lossPercent = if (latency == null) 100f else 0f,
            method = "ping ttl"
        )
    }

    private suspend fun runCommand(command: String) = shizukuExecutor.executeForResult(command)

    private suspend fun readMobileDataEnabled(): Boolean? {
        val cmdPhone = runCommand("cmd phone data get").combinedOutput
        if (cmdPhone.contains("enabled", ignoreCase = true)) return true
        if (cmdPhone.contains("disabled", ignoreCase = true)) return false
        return when (runCommand("settings get global mobile_data").lineOrBlank()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun isPrivateAddress(host: String): Boolean {
        val h = host.trim()
        if (h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("127.") || h.startsWith("::1") || h == "localhost") return true
        // 172.16.0.0/12
        if (h.startsWith("172.")) {
            val second = h.split(".").getOrNull(1)?.toIntOrNull() ?: return false
            if (second in 16..31) return true
        }
        return false
    }

    private fun com.frerox.toolz.util.shizuku.ShellCommandResult.lineOrBlank(): String {
        return combinedOutput.lineSequence().firstOrNull()?.trim().orEmpty()
    }
}
