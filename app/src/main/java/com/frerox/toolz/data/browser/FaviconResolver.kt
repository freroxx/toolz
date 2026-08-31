/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser

import android.graphics.Bitmap
import androidx.collection.LruCache
import java.net.URI

/**
 * Multi-tier favicon resolver:
 * 1. In-memory cache of bitmaps received directly from WebView.onReceivedIcon.
 * 2. Direct site favicon URL resolution (https://$host/favicon.ico).
 * 3. Stable Material fallback icon (Icons.Rounded.Language / Icons.Rounded.Public).
 *
 * Completely replaces external favicon proxy dependencies (such as DuckDuckGo).
 */
object FaviconResolver {
    private val iconCache = LruCache<String, Bitmap>(100)

    fun cacheIcon(url: String, icon: Bitmap) {
        val host = extractHost(url)
        if (host.isNotBlank()) {
            iconCache.put(host, icon)
        }
    }

    fun getCachedIcon(url: String): Bitmap? {
        val host = extractHost(url)
        return if (host.isNotBlank()) iconCache.get(host) else null
    }

    fun directFaviconUrl(url: String): String? {
        val host = extractHost(url)
        if (host.isBlank() || host == "about:blank" || host.startsWith("localhost")) return null
        return "https://$host/favicon.ico"
    }

    fun extractHost(url: String): String = runCatching {
        val parsed = if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            URI("https://$url")
        } else {
            URI(url)
        }
        parsed.host?.removePrefix("www.")?.lowercase().orEmpty()
    }.getOrDefault("")
}
