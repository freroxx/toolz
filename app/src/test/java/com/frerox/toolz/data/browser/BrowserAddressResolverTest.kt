package com.frerox.toolz.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAddressResolverTest {
    @Test
    fun `resolves a bare host as a secure address`() {
        assertEquals("https://example.com/path", BrowserAddressResolver.resolve("example.com/path", "META"))
    }

    @Test
    fun `routes queries through the maintained providers`() {
        assertEquals(
            "https://search.yahoo.com/search?p=privacy+browser",
            BrowserAddressResolver.resolve("privacy browser", "YAHOO"),
        )
        assertEquals(
            "https://www.bing.com/search?q=privacy+browser",
            BrowserAddressResolver.resolve("privacy browser", "BING"),
        )
        assertEquals(
            "https://www.qwant.com/?q=privacy+browser&t=web",
            BrowserAddressResolver.resolve("privacy browser", "QWANT"),
        )
        assertEquals(
            "https://search.marginalia.nu/search?query=privacy+browser",
            BrowserAddressResolver.resolve("privacy browser", "MARGINALIA"),
        )
    }

    @Test
    fun `custom providers receive encoded query in template`() {
        assertEquals(
            "https://search.example/?q=hello+world",
            BrowserAddressResolver.resolve("hello world", "CUSTOM", "https://search.example/?q=%s"),
        )
    }

    @Test
    fun `resolveDestination identifies direct URLs and search queries`() {
        val dest1 = BrowserAddressResolver.resolveDestination("https://example.org/test")
        assertTrue(dest1 is AddressDestination.DirectUrl)
        assertEquals("https://example.org/test", (dest1 as AddressDestination.DirectUrl).url)

        val dest2 = BrowserAddressResolver.resolveDestination("wikipedia.org")
        assertTrue(dest2 is AddressDestination.DirectUrl)
        assertEquals("https://wikipedia.org", (dest2 as AddressDestination.DirectUrl).url)

        val dest3 = BrowserAddressResolver.resolveDestination("kotlin coroutines guide")
        assertTrue(dest3 is AddressDestination.SearchQuery)
        assertEquals("kotlin coroutines guide", (dest3 as AddressDestination.SearchQuery).query)
    }

    @Test
    fun `legacy engines fallback to Meta without DDG`() {
        val resolved = BrowserAddressResolver.resolve("test query", "DUCKDUCKGO")
        assertTrue(resolved.contains("yahoo.com") || resolved.contains("qwant.com") || resolved.contains("bing.com"))
        assertTrue(!resolved.contains("duckduckgo.com"))
    }
}
