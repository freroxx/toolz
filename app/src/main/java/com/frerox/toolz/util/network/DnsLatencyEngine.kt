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
