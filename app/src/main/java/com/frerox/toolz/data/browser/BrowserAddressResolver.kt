package com.frerox.toolz.data.browser

import android.net.Uri
import java.net.URLEncoder

/**
 * One canonical interpretation of browser-bar input. Keeping this outside Compose
 * prevents the search screen, browser chrome and quick actions from disagreeing
 * about whether text is a URL or a search.
 */
object BrowserAddressResolver {
    fun resolve(raw: String, engine: String, customTemplate: String = ""): String {
        val input = raw.trim()
        if (input.isBlank()) return ""
        if (input.startsWith("about:", true) || input.startsWith("file:", true)) return input
        if (input.startsWith("http://", true) || input.startsWith("https://", true)) return input

        if (isAddress(input)) return "https://$input"

        val encoded = URLEncoder.encode(input, "UTF-8")
        return when (engine.uppercase()) {
            "GOOGLE" -> "https://www.google.com/search?q=$encoded"
            "BING" -> "https://www.bing.com/search?q=$encoded"
            "BRAVE" -> "https://search.brave.com/search?q=$encoded"
            "STARTPAGE" -> "https://www.startpage.com/sp/search?query=$encoded"
            "SEARXNG" -> "https://searx.be/search?q=$encoded"
            "CUSTOM" -> customTemplate.takeIf { it.contains("%s") }
                ?.replace("%s", encoded) ?: "https://duckduckgo.com/?q=$encoded"
            else -> "https://duckduckgo.com/?q=$encoded"
        }
    }

    fun isAddress(input: String): Boolean {
        val hostCandidate = input.substringBefore('/').substringBefore('?')
        return !input.contains(' ') && (
            hostCandidate.equals("localhost", true) ||
                hostCandidate.matches(Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")) ||
                hostCandidate.contains('.') || hostCandidate.contains(':')
            )
    }

    fun displayHost(url: String): String = runCatching {
        Uri.parse(url).host?.removePrefix("www.") ?: url
    }.getOrDefault(url)
}
