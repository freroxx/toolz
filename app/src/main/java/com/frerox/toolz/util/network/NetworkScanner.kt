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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext
import kotlin.system.measureTimeMillis
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkScanner @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) {

    /** P4: ARP-table lookup (no privileges needed — kernel populates /proc/net/arp). */
    private fun arpMacFor(ip: String): String? {
        return try {
            java.io.File("/proc/net/arp").readLines()
                .firstOrNull { it.trim().startsWith("$ip ") }
                ?.split(Regex("\\s+"))
                ?.getOrNull(3)
                ?.takeIf { OuiDatabase.normalize(it) != null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * P4: passive mDNS/Bonjour sweep for common service types via NsdManager.
     * No permissions required. Returns ip → friendly label; failures yield empty map.
     */
    suspend fun mdnsDiscover(timeoutMs: Long = 4_000L): Map<String, String> = withContext(Dispatchers.IO) {
        val types = listOf("_googlecast._tcp", "_airplay._tcp", "_ipp._tcp", "_hap._tcp", "_smb._tcp")
        val found = java.util.concurrent.ConcurrentHashMap<String, String>()
        try {
            val nsd = appContext.getSystemService(android.content.Context.NSD_SERVICE) as android.net.nsd.NsdManager
            val listeners = mutableListOf<android.net.nsd.NsdManager.DiscoveryListener>()
            val latch = java.util.concurrent.CountDownLatch(types.size)
            types.forEach { type ->
                val listener = object : android.net.nsd.NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String?) {}
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) { latch.countDown() }
                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                    override fun onDiscoveryStopped(serviceType: String?) {}
                    override fun onServiceFound(serviceInfo: android.net.nsd.NsdServiceInfo?) {
                        runCatching {
                            nsd.resolveService(serviceInfo, object : android.net.nsd.NsdManager.ResolveListener {
                                override fun onResolveFailed(info: android.net.nsd.NsdServiceInfo?, errorCode: Int) {}
                                override fun onServiceResolved(info: android.net.nsd.NsdServiceInfo?) {
                                    val host = info?.host?.hostAddress
                                    if (!host.isNullOrBlank()) found[host] = "${info.serviceName} (${type.substringBefore(".").removePrefix("_")})"
                                }
                            })
                        }
                    }
                    override fun onServiceLost(serviceInfo: android.net.nsd.NsdServiceInfo?) {}
                }
                listeners += listener
                runCatching { nsd.discoverServices(type, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, listener) }
                    .onFailure { latch.countDown() }
            }
            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                // wait until all types either started or failed; resolution keeps filling map meanwhile
                while (latch.count > 0) { kotlinx.coroutines.delay(100) }
            } ?: kotlin.run { /* timeout: proceed with whatever was found */ }
            listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
            // small grace window so late resolutions land in the map
            val deadline = System.currentTimeMillis() + 1_500
            val before = -1
            var lastSize = -1
            while (System.currentTimeMillis() < deadline && System.currentTimeMillis() > 0) {
                if (found.size == lastSize) break
                lastSize = found.size
                kotlinx.coroutines.delay(300)
            }
        } catch (_: Exception) {
            // NSD unavailable (emulator/ROM quirk): silently degrade
        }
        found.toMap()
    }

    /** All ARP entries with a valid MAC — includes devices that block ICMP. */
    private fun arpTable(): Map<String, String> {
        return try {
            java.io.File("/proc/net/arp").readLines()
                .drop(1) // header
                .mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 6) return@mapNotNull null
                    val ip = parts[0]
                    val mac = OuiDatabase.normalize(parts[3]) ?: return@mapNotNull null
                    // skip incomplete entries (0.0.0.0 or flags 0x0 pending)
                    if (ip == "0.0.0.0") return@mapNotNull null
                    ip to mac
                }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun scanSubnetDelta(gateway: String): Flow<NetworkDevice> = channelFlow {
        val subnet = gateway.substringBeforeLast(".", missingDelimiterValue = "")
        if (subnet.isEmpty() || !gateway.contains(".")) return@channelFlow

        // Prime the ARP cache: a cheap TCP touch to common ports makes kernel record
        // neighbors even when they drop ICMP.
        coroutineScope {
            listOf(gateway, "$subnet.1", "$subnet.254").forEach { seed ->
                launch(Dispatchers.IO) {
                    runCatching { InetAddress.getByName(seed).isReachable(120) }
                    measurePortLatency(seed, 80, timeoutMs = 150)
                }
            }
        }

        val arpNow = arpTable()

        val scanDispatcher = Dispatchers.IO.limitedParallelism(24)
        coroutineScope {
            val jobs = (1..254).map { hostIdx ->
                async(scanDispatcher) {
                    if (!isActive) return@async
                    val host = "$subnet.$hostIdx"
                    probeDevice(host, gateway)?.let { trySend(it) }
                }
            }
            jobs.awaitAll()
        }

        // Second pass: devices present in ARP but missed by ping sweep
        arpNow.forEach { (ip, mac) ->
            if (!ip.startsWith("$subnet.")) return@forEach
            trySend(
                NetworkDevice(
                    ip = ip,
                    mac = mac,
                    hostname = runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull()?.takeIf { it != ip } ?: "Unknown",
                    vendor = OuiDatabase.vendor(mac),
                    typeLabel = "ARP only",
                    latencyMs = null,
                    lastSeenEpochMs = System.currentTimeMillis(),
                    isGateway = ip == gateway
                )
            )
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
            val mac = arpMacFor(host)
            NetworkDevice(
                ip = host,
                mac = mac ?: "Unknown",
                hostname = hostname,
                vendor = OuiDatabase.vendor(mac),
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

    /** P9 WoL: send magic packet to [mac] via UDP 9. Returns true if sent (not guaranteed delivery). */
    suspend fun wakeOnLan(mac: String, broadcastIp: String = "255.255.255.255", port: Int = 9): Boolean =
        withContext(Dispatchers.IO) {
            val clean = mac.filter { it.isLetterOrDigit() }
            if (clean.length != 12) return@withContext false
            try {
                val macBytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val packet = ByteArray(6 + 16 * macBytes.size)
                for (i in 0..5) packet[i] = 0xFF.toByte()
                for (i in 0 until 16) System.arraycopy(macBytes, 0, packet, 6 + i * macBytes.size, macBytes.size)
                java.net.DatagramSocket().use { s ->
                    s.broadcast = true
                    s.send(java.net.DatagramPacket(packet, packet.size, java.net.InetAddress.getByName(broadcastIp), port))
                }
                true
            } catch (_: Exception) { false }
        }

    private fun getServiceName(port: Int) = when (port) {
        22 -> "SSH"; 53 -> "DNS"; 80 -> "HTTP"; 123 -> "NTP"; 139 -> "NetBIOS"
        443 -> "HTTPS"; 445 -> "SMB"; 8080 -> "HTTP Alt"; 8443 -> "HTTPS Alt"; 9000 -> "UPnP / App"; else -> "Unknown"
    }
}
