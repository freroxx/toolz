package com.frerox.toolz.util.network

import android.content.Context
import android.content.Intent
import android.net.VpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Checks if VPN preparation is needed.
     * Returns an Intent if preparation is required, null otherwise.
     */
    fun prepareVpn(): Intent? {
        return VpnService.prepare(context)
    }

    /**
     * Placeholder for starting the VPN service with a configuration.
     */
    fun startVpn(ovpnConfig: String) {
        val intent = Intent(context, com.frerox.toolz.service.ToolzVpnService::class.java).apply {
            putExtra("EXTRA_OVPN_CONFIG", ovpnConfig)
            action = "START_VPN"
        }
        context.startService(intent)
    }

    fun stopVpn() {
        val intent = Intent(context, com.frerox.toolz.service.ToolzVpnService::class.java).apply {
            action = "STOP_VPN"
        }
        context.startService(intent)
    }
}
