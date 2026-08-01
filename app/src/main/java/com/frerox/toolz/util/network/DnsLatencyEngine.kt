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

import com.frerox.toolz.data.network.DnsLatency
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsLatencyEngine @Inject constructor(
    private val dnsEngine: DnsEngine
) {

    fun getTargets(): List<DnsLatency> {
        return dnsEngine.providerLibrary().map { provider ->
            DnsLatency(
                name = provider.name,
                address = provider.addresses.firstOrNull() ?: "",
                hostname = provider.hostname
            )
        }
    }

    suspend fun checkAllLatencies(): List<DnsLatency> {
        return dnsEngine.benchmarkTopProviders().map { result ->
            DnsLatency(
                name = result.provider.name,
                address = result.provider.addresses.firstOrNull() ?: "",
                latencyMs = result.metrics.latencyMs,
                hostname = result.provider.hostname
            )
        }
    }

    suspend fun checkSingleLatency(address: String): Long? = dnsEngine.checkSingleLatency(address)
}
