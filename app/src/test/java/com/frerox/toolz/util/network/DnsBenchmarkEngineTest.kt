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
