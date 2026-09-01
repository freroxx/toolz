/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine.parser

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Small pure helpers shared by every [EngineParser] implementation — URL
 * decoding/unwrapping, host extraction, and splitting a leading date off a
 * search-result snippet. Kept dependency-free so parsers stay unit-testable
 * without Android or network mocks.
 */
object ParserSupport {

    fun decodeUrl(raw: String): String = try {
        URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        raw
    }

    fun safeHost(url: String): String = try {
        URI(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }

    /**
     * Unwraps a redirect-tracking URL of the form `.../RU=<encoded-target>/RK=...`
     * (Yahoo's link-tracking format) to the real target URL. Returns [rawHref]
     * unchanged if it doesn't match that shape.
     */
    fun unwrapYahooRedirect(rawHref: String): String {
        if (!rawHref.contains("RU=")) return rawHref
        return try {
            val encoded = rawHref.substringAfter("RU=").substringBefore("/RK=").substringBefore("&")
            URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            rawHref
        }
    }

    /**
     * Splits a leading date/relative-time prefix off a result snippet, e.g.
     * `"Jun 12, 2025 — Some article text"` → `("Jun 12, 2025", "Some article text")`.
     * Returns `(null, snippet)` unchanged if no recognized date prefix is present.
     */
    fun extractLeadingDate(snippet: String): Pair<String?, String> {
        for (pattern in DATE_PREFIX_PATTERNS) {
            val match = pattern.find(snippet.trim()) ?: continue
            return match.groupValues[1].trim() to match.groupValues[2].trim()
        }
        return null to snippet
    }

    private val DATE_PREFIX_PATTERNS = listOf(
        Regex("""^([A-Z][a-z]{2} \d{1,2}, \d{4})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        Regex("""^(\d{1,2} [A-Z][a-z]{2} \d{4})\s*[-–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        Regex("""^(\d+ (?:day|hour|week|month|year)s? ago)\s*[·•]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
        Regex("""^(\d{4}-\d{2}-\d{2})\s*[–—·]\s*(.*)""", RegexOption.DOT_MATCHES_ALL),
    )
}
