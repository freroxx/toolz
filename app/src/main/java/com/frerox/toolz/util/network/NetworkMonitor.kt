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
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    trySend(getWifiInfo())
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                trySend(getWifiInfo())
            }

            override fun onLost(network: Network) {
                trySend(WifiInfoState())
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        trySend(getWifiInfo())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    fun getWifiInfo(): WifiInfoState {
        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }

        val linkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
        val gateway = linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "0.0.0.0"
        val dnsServers = linkProperties?.dnsServers?.mapNotNull(InetAddress::getHostAddress).orEmpty()

        val frequency = wifiInfo?.frequency ?: 0
        val band = when {
            frequency in 2400..2500 -> "2.4 GHz"
            frequency in 4900..5900 -> "5 GHz"
            frequency in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }

        val wifiStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (wifiInfo?.wifiStandard) {
                ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi 1/2/3"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (n)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (ac)"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (ax)"
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (be)"
                else -> "Legacy/Auto"
            }
        } else "Legacy/Auto"

        val channel = frequencyToChannel(frequency)
        val signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.calculateSignalLevel(wifiInfo?.rssi ?: -100)
        } else {
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(wifiInfo?.rssi ?: -100, 5)
        }

        // SSID Fix: On Android 10+, SSID is <unknown ssid> if location permission is not granted
        var ssid = wifiInfo?.ssid?.removeSurrounding("\"") ?: "Unknown"
        if (ssid == "<unknown ssid>" || ssid == "0x") {
            // Try to get from NetworkCapabilities if possible (requires location)
            ssid = "Protected/Hidden"
        }

        return WifiInfoState(
            rssi = wifiInfo?.rssi ?: 0,
            linkSpeed = wifiInfo?.linkSpeed ?: 0,
            gateway = gateway,
            ssid = ssid,
            bssid = wifiInfo?.bssid ?: "Unavailable",
            frequency = frequency,
            ipAddress = formatIpAddress(wifiInfo?.ipAddress ?: 0),
            wifiStandard = wifiStandard,
            band = band,
            channel = channel,
            signalLevel = signalLevel,
            dnsServers = dnsServers
        )
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

    private fun formatIpAddress(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }
}
