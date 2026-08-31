package com.frerox.toolz.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconResolverTest {

    @Test
    fun `extractHost returns host without port or subdomains preserved`() {
        assertEquals("example.com", FaviconResolver.extractHost("https://example.com/path?query=1"))
        assertEquals("sub.domain.org", FaviconResolver.extractHost("http://sub.domain.org:8080/"))
        assertEquals("localhost", FaviconResolver.extractHost("http://localhost:3000/"))
    }

    @Test
    fun `directFaviconUrl generates standard favicon path`() {
        assertEquals("https://example.com/favicon.ico", FaviconResolver.directFaviconUrl("https://example.com/search?q=test"))
        assertEquals("https://docs.kotlinlang.org/favicon.ico", FaviconResolver.directFaviconUrl("https://docs.kotlinlang.org/spec/"))
    }

    @Test
    fun `invalid or blank url returns null for directFaviconUrl`() {
        assertNull(FaviconResolver.directFaviconUrl(""))
        assertNull(FaviconResolver.directFaviconUrl("about:blank"))
    }
}
