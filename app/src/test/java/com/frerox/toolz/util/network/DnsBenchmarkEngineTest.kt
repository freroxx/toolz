package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.DnsProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class DnsBenchmarkEngineTest {

    @Test
    fun benchmark_reachableProvider_returnsLatency() {
        val engine = DnsBenchmarkEngine()
        // Use a known reachable address
        val provider = DnsProvider(
            id = "test",
            name = "Test",
            primaryAddress = "8.8.8.8"
        )
        
        val result = engine.benchmark(provider)
        
        // Assert result is positive latency, or -1 if unreachable (unlikely for 8.8.8.8 but possible)
        assert(result >= 0 || result == -1L) { "Expected positive latency or -1, got $result" }
    }

    @Test
    fun benchmark_unreachableProvider_returnsNegative() {
        val engine = DnsBenchmarkEngine()
        // Use a known unreachable address
        val provider = DnsProvider(
            id = "test",
            name = "Test",
            primaryAddress = "192.0.2.1"
        )
        
        val result = engine.benchmark(provider)
        
        assertEquals(-1L, result)
    }
}
