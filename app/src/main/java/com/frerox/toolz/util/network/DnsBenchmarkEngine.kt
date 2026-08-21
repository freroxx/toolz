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

import com.frerox.toolz.data.network.DnsProvider
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsBenchmarkEngine @Inject constructor() {
    var reachableChecker: (String, Int) -> Boolean = { address, timeout ->
        try {
            java.net.Socket().use { s -> s.connect(java.net.InetSocketAddress(address, 53), timeout); true }
        } catch (_: Exception) { false }
    }

    fun benchmark(provider: DnsProvider): Long {
        val start = System.currentTimeMillis()
        val address = provider.addresses.firstOrNull() ?: return -1L
        val reachable = reachableChecker(address, 1000)
        return if (reachable) System.currentTimeMillis() - start else -1L
    }
}
