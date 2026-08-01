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

package com.frerox.toolz.data.update

import android.os.Build

object UpdateHelper {
    val ABI_FILTERS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

    fun getBestAsset(assets: List<GitHubAsset>, preferredAbi: String): GitHubAsset? {
        val targetAbis = if (preferredAbi == "AUTO") {
            Build.SUPPORTED_ABIS.toList()
        } else {
            listOf(preferredAbi)
        }

        for (abi in targetAbis) {
            val match = assets.find { asset ->
                val name = asset.name.lowercase()
                name.endsWith(".apk") && name.contains(abi.lowercase()) && ABI_FILTERS.any { name.contains(it) }
            }
            if (match != null) return match
        }

        val universalMatch = assets.find { 
            val n = it.name.lowercase()
            n.endsWith(".apk") && n.contains("universal")
        }
        if (universalMatch != null) return universalMatch

        return assets.find { asset ->
            val name = asset.name.lowercase()
            name.endsWith(".apk") && ABI_FILTERS.any { name.contains(it) }
        }
    }

    fun getBestRelease(releases: List<UpdateRelease>, preferredAbi: String): UpdateRelease? {
        val targetAbis = if (preferredAbi == "AUTO") {
            Build.SUPPORTED_ABIS.toList()
        } else {
            listOf(preferredAbi)
        }

        for (abi in targetAbis) {
            val match = releases.find { it.abi.lowercase() == abi.lowercase() }
            if (match != null) return match
        }

        return releases.find { it.abi.lowercase() == "universal" } ?: releases.firstOrNull()
    }
}
