package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.DnsProvider
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsBenchmarkEngine @Inject constructor() {
    var reachableChecker: (String, Int) -> Boolean = { address, timeout ->
        try {
            InetAddress.getByName(address).isReachable(timeout)
        } catch (e: Exception) {
            false
        }
    }

    fun benchmark(provider: DnsProvider): Long {
        val start = System.currentTimeMillis()
        val address = provider.addresses.firstOrNull() ?: return -1L
        val reachable = reachableChecker(address, 1000)
        return if (reachable) System.currentTimeMillis() - start else -1L
    }
}
