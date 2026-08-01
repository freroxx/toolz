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
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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

    fun getSmartName(url: String?, originalName: String): String {
        if (url.isNullOrBlank()) return originalName
        
        if (url.startsWith("android://")) {
            return originalName
        }

        // Handle package names directly if they look like one
        if (url.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$"))) {
            return originalName
        }

        return try {
            val uri = if (!url.startsWith("http")) URI("https://$url") else URI(url)
            val host = uri.host?.removePrefix("www.") ?: return originalName
            popularWebsites[host] ?: host.split(".")[0].replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            originalName
        }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    fun getAppName(context: Context, packageName: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            null
        }
    }
}
