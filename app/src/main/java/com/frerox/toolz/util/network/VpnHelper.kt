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
