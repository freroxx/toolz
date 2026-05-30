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
