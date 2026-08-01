/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
