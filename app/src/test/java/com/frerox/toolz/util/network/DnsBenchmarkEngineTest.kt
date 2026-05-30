package com.frerox.toolz.util.network

import com.frerox.toolz.data.network.DnsProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class DnsBenchmarkEngineTest {

    @Test
    fun `benchmark returns latency when reachable`() {
        val engine = DnsBenchmarkEngine()
        engine.reachableChecker = { _, _ -> true }
        val provider = DnsProvider(id = "1", name = "Test", addresses = listOf("1.1.1.1"))

        val result = engine.benchmark(provider)
        assert(result >= 0)
    }

    @Test
    fun `benchmark returns -1 when not reachable`() {
        val engine = DnsBenchmarkEngine()
        engine.reachableChecker = { _, _ -> false }
        val provider = DnsProvider(id = "1", name = "Test", addresses = listOf("1.1.1.1"))

        val result = engine.benchmark(provider)
        assertEquals(-1L, result)
    }
}
