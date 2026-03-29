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
