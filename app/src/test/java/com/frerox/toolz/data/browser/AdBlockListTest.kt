/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the default-allowlist leak that made WebView ad-blocking
 * feel "dead": platform roots like "facebook.com" were suffix-allowlisted, which
 * silently whitelisted connect.facebook.net / an.facebook.com — the static ad
 * rules for those hosts could never fire.
 */
class AdBlockListTest {

    @Test
    fun `dedicated meta ad endpoints are blocked`() {
        assertTrue(AdBlockList.isBlocked("https://connect.facebook.net/en_US/fbevents.js"))
        assertTrue(AdBlockList.isBlocked("https://an.facebook.com/v2.1/pixel"))
        assertTrue(AdBlockList.isBlocked("https://pixel.facebook.com/tr/"))
    }

    @Test
    fun `dedicated twitter and google ad hosts are blocked`() {
        assertTrue(AdBlockList.isBlocked("https://static.ads-twitter.com/uwt.js"))
        assertTrue(AdBlockList.isBlocked("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlockList.isBlocked("https://www.googletagmanager.com/gtm.js?id=GTM-XYZ"))
    }

    @Test
    fun `main site pages are never blocked`() {
        assertFalse(AdBlockList.isBlocked("https://www.facebook.com/somepage"))
        assertFalse(AdBlockList.isBlocked("https://x.com/user/status/1"))
        assertFalse(AdBlockList.isBlocked("https://www.reddit.com/r/Kotlin/"))
        assertFalse(AdBlockList.isBlocked("https://en.wikipedia.org/wiki/Kotlin"))
    }

    @Test
    fun `known tracking telemetry is blocked`() {
        assertTrue(AdBlockList.isBlocked("https://www.clarity.ms/tag/abc"))
        assertTrue(AdBlockList.isBlocked("https://cdn.taboola.com/libtrc/loader.js"))
    }

    @Test
    fun `css and fonts are protected from pattern blocking`() {
        assertFalse(AdBlockList.isBlocked("https://example.com/styles/main.css"))
        assertFalse(AdBlockList.isBlocked("https://fonts.example.com/fonts/inter.woff2"))
    }
}
