/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine
import com.frerox.toolz.data.search.SearchResult
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class MetaMerger @Inject constructor() {
    fun merge(resultsByEngine: Map<String, List<SearchResult>>): List<SearchResult> {
        val urlToAppearances = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        val canonicalToResult = mutableMapOf<String, SearchResult>()

        for ((eng, list) in resultsByEngine) {
            val canonicalEng = when (eng.uppercase()) {
                "YAHOO" -> "Yahoo"
                "QWANT" -> "Qwant"
                "MARGINALIA" -> "Marginalia"
                "BING" -> "Bing"
                else -> eng.lowercase().replaceFirstChar { it.uppercase() }
            }
            list.forEachIndexed { rank, r ->
                val canUrl = canonicalUrl(r.url)
                urlToAppearances.getOrPut(canUrl) { mutableListOf() }.add(canonicalEng to rank)
                if (!canonicalToResult.containsKey(canUrl) || (r.snippet.isNotBlank() && canonicalToResult[canUrl]?.snippet.isNullOrBlank())) {
                    canonicalToResult[canUrl] = r
                }
            }
        }

        data class Scored(val result: SearchResult, val score: Double)

        val scored = canonicalToResult.map { (canUrl, r) ->
            val appearances = urlToAppearances[canUrl] ?: emptyList()
            val engineNames = appearances.map { it.first }.distinct()
            val hasNonBing = engineNames.any { it != "Bing" }
            val nonBingCount = engineNames.count { it != "Bing" }
            val isBingOnly = engineNames.size == 1 && engineNames.first() == "Bing"

            // Base rank score: sum of 1 / (rank + 1)
            val rankScore = appearances.sumOf { (_, rank) -> 1.0 / (rank + 1) }

            // Multi-engine consensus bonus
            val consensusBonus = when {
                engineNames.size >= 3 -> 2.5
                engineNames.size >= 2 -> 1.8
                else -> 1.0
            }

            // Consensus from Yahoo / Qwant / Marginalia ranked above Bing-only
            val sourceWeight = when {
                nonBingCount >= 2 -> 1.6
                nonBingCount == 1 && engineNames.size >= 2 -> 1.3
                hasNonBing -> 1.15
                isBingOnly -> 0.75
                else -> 1.0
            }

            val snippetBonus = if (r.snippet.isNotBlank()) 1.1 else 1.0
            val freshnessBonus = if (r.date != null) 1.05 else 1.0

            val totalScore = rankScore * consensusBonus * sourceWeight * snippetBonus * freshnessBonus
            Scored(r.copy(engines = engineNames), totalScore)
        }

        return scored.sortedByDescending { it.score }.map { it.result }
    }

    fun canonicalUrl(url: String): String = try {
        var u = url.trim()
        if (u.startsWith("http://", ignoreCase = true)) {
            u = "https://" + u.substring(7)
        }
        val uri = java.net.URI(u)
        val host = (uri.host ?: "").lowercase().removePrefix("www.")
        val path = (uri.path ?: "").removeSuffix("/")
        val query = uri.query?.let { q ->
            q.split("&")
                .filterNot { it.startsWith("utm_", ignoreCase = true) || it.startsWith("ref=", ignoreCase = true) }
                .joinToString("&")
                .takeIf { it.isNotEmpty() }
        }
        val cleanQuery = if (query != null) "?$query" else ""
        if (host.isNotEmpty()) "https://$host$path$cleanQuery" else u.lowercase().removeSuffix("/")
    } catch (_: Exception) {
        url.trim().removeSuffix("/").lowercase()
    }
}
