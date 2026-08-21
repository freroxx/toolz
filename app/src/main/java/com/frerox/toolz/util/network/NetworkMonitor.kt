/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.util.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.frerox.toolz.data.network.WifiInfoState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun observeWifiInfo(): Flow<WifiInfoState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(getWifiInfo())
            }
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                trySend(getWifiInfo())
            }
            override fun onLost(network: Network) {
                trySend(WifiInfoState())
            }
            override fun onAvailable(network: Network) {
                trySend(getWifiInfo())
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            // fallback: still try to emit initial state
        }
        trySend(getWifiInfo())
        awaitClose {
            try { connectivityManager.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        }
    }.distinctUntilChanged { a, b ->
        // Ignore rssiHistory for distinct check to allow frequent updates
        a.copy(rssiHistory = emptyList()) == b.copy(rssiHistory = emptyList())
    }

    fun getWifiInfo(): WifiInfoState {
        val wifiInfo: WifiInfo? = runCatching { resolveWifiInfo() }.getOrNull()
        val linkProperties = runCatching { connectivityManager.getLinkProperties(connectivityManager.activeNetwork) }.getOrNull()
        val gateway = linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "0.0.0.0"
        val dnsServers = linkProperties?.dnsServers?.mapNotNull { runCatching { it.hostAddress }.getOrNull() }.orEmpty()

        val frequency = wifiInfo?.frequency ?: 0
        val band = frequencyToBand(frequency)
        val wifiStandard = resolveWifiStandard(wifiInfo)
        val channel = frequencyToChannel(frequency)
        val signalLevel = resolveSignalLevel(wifiInfo)

        var ssid = wifiInfo?.ssid?.removeSurrounding("\"") ?: "Unknown"
        val isUnknownSsid = ssid == "<unknown ssid>" || ssid == "0x" || ssid.isBlank()
        if (isUnknownSsid) ssid = "Unknown"

        val ipString = formatIpAddress(wifiInfo?.ipAddress ?: 0).takeIf { it != "0.0.0.0" } ?: "0.0.0.0"
        val bssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" && it.isNotBlank() } ?: "Unavailable"

        val activeCaps = runCatching {
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        }.getOrNull()
        val wifiConnected = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        return WifiInfoState(
            isConnected = wifiConnected,
            rssi = if (wifiConnected) wifiInfo?.rssi ?: -100 else -100,
            linkSpeed = if (wifiConnected) wifiInfo?.linkSpeed ?: 0 else 0,
            gateway = if (wifiConnected) gateway else "0.0.0.0",
            ssid = ssid,
            bssid = bssid,
            frequency = frequency,
            ipAddress = ipString,
            wifiStandard = wifiStandard,
            band = band,
            channel = channel,
            signalLevel = signalLevel,
            dnsServers = dnsServers
        )
    }

    private fun resolveWifiInfo(): WifiInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val network = connectivityManager.activeNetwork ?: return legacyWifiInfo()
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return legacyWifiInfo()
            (caps.transportInfo as? WifiInfo) ?: legacyWifiInfo()
        } else {
            legacyWifiInfo()
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyWifiInfo(): WifiInfo? = runCatching { wifiManager.connectionInfo }.getOrNull()

    private fun resolveWifiStandard(wifiInfo: WifiInfo?): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return when (wifiInfo?.wifiStandard) {
                ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi 1/2/3"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (n)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (ac)"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (ax)"
                ScanResult.WIFI_STANDARD_11AD -> "Wi-Fi AD"
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (be)"
                else -> "Legacy/Auto"
            }
        }
        return "Legacy/Auto"
    }

    private fun resolveSignalLevel(wifiInfo: WifiInfo?): Int {
        val rssi = wifiInfo?.rssi ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { wifiManager.calculateSignalLevel(rssi) }.getOrDefault(legacyLevel(rssi))
        } else {
            legacyLevel(rssi)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyLevel(rssi: Int) = WifiManager.calculateSignalLevel(rssi, 5)

    private fun frequencyToBand(freq: Int): String = when {
        freq in 2400..2500 -> "2.4 GHz"
        freq in 4900..5900 -> "5 GHz"
        freq in 5925..7125 -> "6 GHz"
        else -> "Unknown"
    }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2401..2483 -> (freq - 2407) / 5
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        freq in 5945..7105 -> (freq - 5945) / 5 + 1
        else -> 0
    }

    private fun formatIpAddress(ip: Int): String {
        if (ip == 0) return "0.0.0.0"
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }
}
