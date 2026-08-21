package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.NetworkDevice
import com.frerox.toolz.data.network.ScannedPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkScanner @Inject constructor() {

    fun scanSubnetDelta(gateway: String): Flow<NetworkDevice> = channelFlow {
        val subnet = gateway.substringBeforeLast(".", missingDelimiterValue = "")
        if (subnet.isEmpty() || !gateway.contains(".")) return@channelFlow
        // Only /24 scanning - for other masks we'd need netmask detection via LinkProperties, omitted for safety
        coroutineScope {
            val jobs = (1..254).map { hostIdx ->
                async(Dispatchers.IO) {
                    if (!isActive) return@async
                    val host = "$subnet.$hostIdx"
                    probeDevice(host, gateway)?.let { trySend(it) }
                }
            }
            jobs.awaitAll()
        }
    }

    suspend fun scanSubnet(gateway: String): List<NetworkDevice> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NetworkDevice>()
        scanSubnetDelta(gateway).collect { results += it }
        results.sortedWith(compareByDescending<NetworkDevice> { it.isGateway }.thenBy { it.ip })
    }

    suspend fun scanPorts(
        ip: String,
        ports: List<Int> = listOf(22, 53, 80, 123, 139, 443, 445, 8080, 8443, 9000)
    ): List<ScannedPort> = withContext(Dispatchers.IO) {
        coroutineScope {
            ports.map { port ->
                async(Dispatchers.IO) {
                    val latency = measurePortLatency(ip, port)
                    ScannedPort(port = port, isOpen = latency != null, service = getServiceName(port), latencyMs = latency)
                }
            }.awaitAll().sortedBy { it.port }
        }
    }

    private suspend fun probeDevice(host: String, gateway: String): NetworkDevice? = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(host)
            var reachable = false
            val latency = measureTimeMillis { reachable = addr.isReachable(400) }
            if (!reachable) return@withContext null
            val hostname = runCatching { addr.canonicalHostName }.getOrNull()?.takeIf { it != host } ?: "Unknown"
            NetworkDevice(
                ip = host,
                hostname = hostname,
                typeLabel = identifyDeviceType(host),
                latencyMs = latency.coerceAtLeast(1L),
                lastSeenEpochMs = System.currentTimeMillis(),
                isGateway = host == gateway
            )
        } catch (_: Exception) { null }
    }

    private suspend fun identifyDeviceType(ip: String): String {
        val probes = mapOf(80 to "Router / web UI", 443 to "Secure service", 22 to "Linux / NAS", 631 to "Printer", 9100 to "Printer", 8008 to "Chromecast")
        for ((port, label) in probes) {
            if (measurePortLatency(ip, port, timeoutMs = 180) != null) return label
        }
        return "Client device"
    }

    private suspend fun measurePortLatency(ip: String, port: Int, timeoutMs: Int = 250): Long? = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                val elapsed = measureTimeMillis { s.connect(InetSocketAddress(ip, port), timeoutMs) }
                elapsed.coerceAtLeast(1L)
            }
        } catch (_: Exception) { null }
    }

    private fun getServiceName(port: Int) = when (port) {
        22 -> "SSH"; 53 -> "DNS"; 80 -> "HTTP"; 123 -> "NTP"; 139 -> "NetBIOS"
        443 -> "HTTPS"; 445 -> "SMB"; 8080 -> "HTTP Alt"; 8443 -> "HTTPS Alt"; 9000 -> "UPnP / App"; else -> "Unknown"
    }
}
