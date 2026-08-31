package com.frerox.toolz.data.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserAddressResolverTest {
    @Test fun `resolves a bare host as a secure address`() {
        assertEquals("https://example.com/path", BrowserAddressResolver.resolve("example.com/path", "DUCKDUCKGO"))
    }

    @Test fun `routes queries through the selected provider`() {
        assertEquals(
            "https://search.brave.com/search?q=privacy+browser",
            BrowserAddressResolver.resolve("privacy browser", "BRAVE"),
        )
    }

    @Test fun `custom providers receive encoded query in template`() {
        assertEquals(
            "https://search.example/?q=hello+world",
            BrowserAddressResolver.resolve("hello world", "CUSTOM", "https://search.example/?q=%s"),
        )
    }
}
