package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.DnsProvider
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsBenchmarkEngine @Inject constructor() {
    fun benchmark(provider: DnsProvider): Long {
        val start = System.currentTimeMillis()
        // Using existing DnsProvider with primaryAddress as requested by Task 2
        // Based on existing DnsProvider in NetworkModels.kt, it has primaryAddress
        val reachable = InetAddress.getByName(provider.primaryAddress).isReachable(1000)
        return if (reachable) System.currentTimeMillis() - start else -1L
    }
}
