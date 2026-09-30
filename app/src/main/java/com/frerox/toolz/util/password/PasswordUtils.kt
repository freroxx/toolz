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

package com.frerox.toolz.util.password

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import java.net.URI

object PasswordUtils {
    private val popularWebsites = mapOf(
        "google.com" to "Google",
        "accounts.google.com" to "Google Account",
        "facebook.com" to "Facebook",
        "twitter.com" to "Twitter",
        "x.com" to "X",
        "github.com" to "GitHub",
        "microsoft.com" to "Microsoft",
        "outlook.com" to "Outlook",
        "apple.com" to "Apple ID",
        "icloud.com" to "iCloud",
        "amazon.com" to "Amazon",
        "netflix.com" to "Netflix",
        "spotify.com" to "Spotify",
        "instagram.com" to "Instagram",
        "linkedin.com" to "LinkedIn",
        "reddit.com" to "Reddit",
        "discord.com" to "Discord",
        "paypal.com" to "PayPal",
        "steampowered.com" to "Steam",
        "twitch.tv" to "Twitch"
    )

    private val packageNameRegex = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")

    /**
     * Normalize a vault URL/host to its registrable domain for autofill ranking.
     * Strips scheme, port, www/mobile subdomains and lowercases. Never throws.
     */
    fun normalizeHost(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.startsWith("android://")) return trimmed.removePrefix("android://").lowercase()
        return try {
            val uri = if (trimmed.contains("://")) URI(trimmed) else URI("https://$trimmed")
            val host = uri.host?.lowercase() ?: return null
            host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        } catch (_: Exception) {
            null
        }
    }

    fun getSmartName(url: String?, originalName: String): String {
        if (url.isNullOrBlank()) return originalName

        if (url.startsWith("android://")) {
            return originalName
        }

        // Handle package names directly if they look like one
        if (url.matches(packageNameRegex)) {
            return originalName
        }

        return try {
            val trimmed = url.trim()
            val uri = if (!trimmed.lowercase().startsWith("http")) URI("https://$trimmed") else URI(trimmed)
            val rawHost = uri.host?.lowercase() ?: return originalName
            val host = rawHost.removePrefix("www.")
            // Avoid phishing mislabels: only use the popular map on exact host match.
            popularWebsites[host]?.let { return it }
            val parts = host.split(".")
            // Handle multi-part TLDs (co.uk, com.au): use the label before the TLD.
            val label = when {
                parts.size >= 3 && parts.last().length == 2 -> parts[parts.size - 3]
                parts.size >= 2 -> parts[parts.size - 2]
                else -> parts.firstOrNull() ?: return originalName
            }
            label.replaceFirstChar { it.uppercase() }
        } catch (_: Exception) {
            originalName
        }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }

    fun getAppName(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(packageName, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            null
        }
    }
}
