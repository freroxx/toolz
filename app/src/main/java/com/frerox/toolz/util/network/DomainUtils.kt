package com.frerox.toolz.util.network

import android.net.Uri

object DomainUtils {
    private val publicSuffixes = setOf(
        "com", "org", "net", "edu", "gov", "mil", "int", "io", "me", "ai", "app", "dev",
        "co.uk", "org.uk", "me.uk", "gov.uk", "ac.uk",
        "co.jp", "ne.jp", "ac.jp",
        "co.kr", "ne.kr", "ac.kr",
        "com.au", "net.au", "org.au",
        "com.br", "net.br", "org.br",
        "com.cn", "net.cn", "org.cn",
        "com.de", "com.fr", "com.es", "com.it",
        "info", "biz", "tv", "cc", "fm"
    )

    /**
     * Extracts the root domain (e.g. brave.com) from a host string.
     */
    fun getRootDomain(host: String?): String? {
        if (host == null || host.isBlank()) return null
        val parts = host.lowercase().trim().split('.')
        if (parts.size < 2) return host

        // Check for double-part TLDs like co.uk
        if (parts.size >= 3) {
            val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
            if (publicSuffixes.contains(lastTwo)) {
                return "${parts[parts.size - 3]}.$lastTwo"
            }
        }

        return "${parts[parts.size - 2]}.${parts.last()}"
    }

    /**
     * Normalizes a URL and extracts its host.
     */
    fun getHost(url: String?): String? {
        if (url == null || url.isBlank()) return null
        return try {
            val normalized = when {
                url.startsWith("//") -> "https:$url"
                !url.contains("://") -> "https://$url"
                else -> url
            }
            Uri.parse(normalized).host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if two URLs belong to the same root domain.
     */
    fun isSameRootDomain(url1: String, url2: String): Boolean {
        val root1 = getRootDomain(getHost(url1)) ?: return false
        val root2 = getRootDomain(getHost(url2)) ?: return false
        return root1 == root2
    }
}
