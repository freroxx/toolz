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

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class BlocklistManagerTest {
    private val blocklistManager = BlocklistManager(OkHttpClient())

    @Test
    fun `parseData filters blank lines and comments`() {
        val input = "# comment\n\n127.0.0.1 example.com\nexample.org\n"
        val expected = listOf("127.0.0.1 example.com", "example.org")
        assertEquals(expected, blocklistManager.parseData(input))
    }

    @Test
    fun `parseData returns empty for empty input`() {
        assertEquals(emptyList<String>(), blocklistManager.parseData(""))
    }

    @Test
    fun `parseData returns empty for only comments`() {
        val input = "# comment1\n# comment2"
        assertEquals(emptyList<String>(), blocklistManager.parseData(input))
    }
}
