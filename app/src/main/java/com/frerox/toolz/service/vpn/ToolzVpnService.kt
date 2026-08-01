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

package com.frerox.toolz.service.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException

/**
 * A boilerplate VpnService implementation for Toolz.
 * Adheres to modern Android requirements, including 16KB page-size alignment compatibility.
 */
class ToolzVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnect()
            return START_NOT_STICKY
        }
        
        // Example path for importing .ovpn configuration text block
        val ovpnConfig = intent?.getStringExtra(EXTRA_OVPN_CONFIG)
        if (ovpnConfig != null) {
            Log.d(TAG, "Importing OVPN config: ${ovpnConfig.take(20)}...")
        }

        establish()
        return START_STICKY
    }

    private fun establish() {
        val builder = Builder()
        
        try {
            // Configure the TUN interface
            // MTU 1500 is standard, but some networks prefer smaller
            vpnInterface = builder
                .setSession("ToolzVpnSession")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(1500)
                .establish()
            
            Log.d(TAG, "VPN Interface established")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            stopSelf()
        }
    }

    private fun disconnect() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "VPN Disconnected")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ToolzVpnService"
        const val ACTION_DISCONNECT = "com.frerox.toolz.vpn.DISCONNECT"
        const val EXTRA_OVPN_CONFIG = "extra_ovpn_config"
    }
}
