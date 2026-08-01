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

import com.frerox.toolz.data.network.NetworkDevice
import com.frerox.toolz.data.network.ScannedPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkScanner @Inject constructor() {

    fun scanSubnetDelta(gateway: String): Flow<NetworkDevice> = channelFlow {
        val subnet = gateway.substringBeforeLast(".", missingDelimiterValue = gateway)
        if (!gateway.contains(".") || subnet == gateway) return@channelFlow

        coroutineScope {
            (1..254).map { hostIndex ->
                async(Dispatchers.IO.limitedParallelism(32)) {
                    val host = "$subnet.$hostIndex"
                    val device = probeDevice(host, gateway)
                    if (device != null) {
                        trySend(device)
                    }
                }
            }.awaitAll()
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
                async(Dispatchers.IO.limitedParallelism(24)) {
                    val latency = measurePortLatency(ip, port)
                    ScannedPort(
                        port = port,
                        isOpen = latency != null,
                        service = getServiceName(port),
                        latencyMs = latency
                    )
                }
            }.awaitAll().sortedBy { it.port }
        }
    }

    private suspend fun probeDevice(host: String, gateway: String): NetworkDevice? = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(host)
            var isReachable = false
            val reachableLatency = measureTimeMillis {
                isReachable = address.isReachable(350)
            }.takeIf { isReachable }
            if (reachableLatency == null) return@withContext null

            val hostname = address.canonicalHostName.takeIf { it != host } ?: "Unknown"
            val type = identifyDeviceType(host)
            NetworkDevice(
                ip = host,
                hostname = hostname,
                typeLabel = type,
                latencyMs = reachableLatency.coerceAtLeast(1L),
                lastSeenEpochMs = System.currentTimeMillis(),
                isGateway = host == gateway
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun identifyDeviceType(ip: String): String {
        val commonPorts = mapOf(
            80 to "Router / web UI",
            443 to "Secure service",
            22 to "Linux / NAS",
            631 to "Printer",
            8008 to "Chromecast",
            8080 to "Dev service",
            32400 to "Media server",
            9100 to "Printer"
        )
        for ((port, label) in commonPorts) {
            if (measurePortLatency(ip, port, timeoutMs = 150) != null) {
                return label
            }
        }
        return "Client device"
    }

    private suspend fun measurePortLatency(
        ip: String,
        port: Int,
        timeoutMs: Int = 220
    ): Long? = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                val elapsed = measureTimeMillis {
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                }
                elapsed.coerceAtLeast(1L)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getServiceName(port: Int): String {
        return when (port) {
            22 -> "SSH"
            53 -> "DNS"
            80 -> "HTTP"
            123 -> "NTP"
            139 -> "NetBIOS"
            443 -> "HTTPS"
            445 -> "SMB"
            8080 -> "HTTP Alt"
            8443 -> "HTTPS Alt"
            9000 -> "UPnP / App"
            else -> "Unknown"
        }
    }
}
