package com.frerox.toolz.data.browser

import android.net.Uri
import java.net.URLEncoder

/**
 * One canonical interpretation of browser-bar input. Keeping this outside Compose
 * prevents the search screen, browser chrome and quick actions from disagreeing
 * about whether text is a URL or a search.
 */
sealed class AddressDestination {
    data class DirectUrl(val url: String) : AddressDestination()
    data class SearchQuery(val query: String) : AddressDestination()
}

/**
 * Canonical interpretation of browser-bar input.
 * Strictly separates direct URL destinations from search queries,
 * eliminating DuckDuckGo fallbacks.
 */
object BrowserAddressResolver {
    fun resolveDestination(raw: String): AddressDestination {
        val input = raw.trim()
        if (input.isBlank()) return AddressDestination.DirectUrl("about:blank")
        if (input.startsWith("about:", true) || input.startsWith("file:", true)) {
            return AddressDestination.DirectUrl(input)
        }
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) {
            return AddressDestination.DirectUrl(input)
        }
        if (isAddress(input)) {
            return AddressDestination.DirectUrl("https://$input")
        }
        return AddressDestination.SearchQuery(input)
    }

    fun resolve(raw: String, engine: String): String {
        return when (val dest = resolveDestination(raw)) {
            is AddressDestination.DirectUrl -> dest.url
            is AddressDestination.SearchQuery -> {
                val encoded = URLEncoder.encode(dest.query, "UTF-8")
                when (engine.uppercase()) {
                    "YAHOO" -> "https://search.yahoo.com/search?p=$encoded"
                    "QWANT" -> "https://www.qwant.com/?q=$encoded&t=web"
                    "MARGINALIA" -> "https://search.marginalia.nu/search?query=$encoded"
                    "BING" -> "https://www.bing.com/search?q=$encoded"
                    else -> "https://search.yahoo.com/search?p=$encoded"
                }
            }
        }
    }

    fun isAddress(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return false
        val hostCandidate = trimmed.substringBefore('/').substringBefore('?')
        return hostCandidate.equals("localhost", true) ||
            hostCandidate.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?")) ||
            (hostCandidate.contains('.') && hostCandidate.substringAfterLast('.').length in 2..10) ||
            (hostCandidate.contains(':') && hostCandidate.substringAfterLast(':').all { it.isDigit() })
    }

    fun displayHost(url: String): String = runCatching {
        // about:blank is Toolz's internal home/new-tab destination — never surface
        // the scheme string in tabs UI or the address bar.
        if (url.equals("about:blank", ignoreCase = true)) return "Home"
        Uri.parse(url).host?.removePrefix("www.") ?: url
    }.getOrDefault(url)
}
