/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

import com.frerox.toolz.data.search.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges per-engine result lists for META search into one ranked list.
 *
 * Ranking rewards two things: how highly a result was ranked by the engine(s)
 * that returned it, and how many independent engines agreed on it (consensus).
 * A result four engines agree on beats one engine's #1 pick.
 */
@Singleton
class MetaMerger @Inject constructor() {

    private data class Appearance(val engine: EngineId, val rank: Int)

    /** Public wrapper so the ViewModel can reuse the same dedup normalization for load-more. */
    fun canonical(url: String): String = canonicalUrl(url)

        fun merge(resultsByEngine: Map<EngineId, List<SearchResult>>): List<SearchResult> {
            val appearancesByUrl = mutableMapOf<String, MutableList<Appearance>>()
            val bestResultByUrl = mutableMapOf<String, SearchResult>()

            for ((engine, results) in resultsByEngine) {
                results.forEachIndexed { rank, result ->
                    val canonical = canonicalUrl(result.url)
                    appearancesByUrl.getOrPut(canonical) { mutableListOf() } += Appearance(engine, rank)
                    bestResultByUrl[canonical] = mergeBest(bestResultByUrl[canonical], result)
                }
            }

            return bestResultByUrl.entries
            .map { (canonicalUrl, result) ->
                val appearances = appearancesByUrl.getValue(canonicalUrl)
                score(result, appearances)
            }
            .sortedByDescending { it.score }
            .map { it.result }
        }

        /**
         * Picks the richer of two candidate results for the same URL, keeping
         * the best field from each rather than discarding one wholesale — a
         * result with a snippet from engine A but a date only from engine B
         * should end up with both, not lose the date because A "won" on snippet.
         */
        private fun mergeBest(current: SearchResult?, candidate: SearchResult): SearchResult {
            if (current == null) return candidate
                return current.copy(
                    snippet = current.snippet.ifBlank { candidate.snippet },
                    date = current.date ?: candidate.date,
                    breadcrumb = current.breadcrumb ?: candidate.breadcrumb,
                    imageUrl = current.imageUrl ?: candidate.imageUrl,
                )
        }

        private data class Scored(val result: SearchResult, val score: Double)

            private fun score(result: SearchResult, appearances: List<Appearance>): Scored {
                val engines = appearances.map { it.engine }.distinct()

                // Sum of 1/(rank+1) across every engine that surfaced this URL — an engine's
                // #1 result contributes 1.0, its #10 result contributes ~0.09.
                val rankScore = appearances.sumOf { 1.0 / (it.rank + 1) }

                // Independent-engine agreement is the strongest freshness/relevance signal
                // META has over a single engine — weight it more heavily than rank alone.
                val consensusBonus = when (engines.size) {
                    1 -> 1.0
                    2 -> 2.0
                    else -> 3.0
                }

                val snippetBonus = if (result.snippet.isNotBlank()) 1.15 else 1.0
                val dateBonus = if (result.date != null) 1.05 else 1.0

                val total = rankScore * consensusBonus * snippetBonus * dateBonus

                // Tag attribution: the result is labelled by the engine that ranked it
                // HIGHEST, not the first engine in fan-out order — otherwise every
                // deduped result inherits Yahoo's tag just because Yahoo is queried
                // first. Ties break toward the engine with more appearances.
                val bestAppearance = appearances.minWithOrNull(
                    compareBy({ it.rank }, { -appearances.count { a -> a.engine == it.engine } })
                ) ?: appearances.first()
                val bestSource = bestAppearance.engine.label

                return Scored(result.copy(engines = engines.map { it.label }, source = bestSource), total)
            }

            /**
             * Normalizes a URL for cross-engine dedup: forces https, strips `www.`,
             * trailing slash, and known tracking params (utm_*, ref=) so the same
             * page returned by different engines with different tracking tags
             * collapses to one result instead of appearing twice.
             */
            fun canonicalUrl(url: String): String = try {
                val normalizedScheme = url.trim().let {
                    if (it.startsWith("http://", ignoreCase = true)) "https://" + it.substring(7) else it
                }
                val uri = java.net.URI(normalizedScheme)
                val host = uri.host?.lowercase()?.removePrefix("www.") ?: ""
                val path = (uri.path ?: "").removeSuffix("/")
                val query = uri.query
                ?.split("&")
                ?.filterNot { it.startsWith("utm_", ignoreCase = true) || it.startsWith("ref=", ignoreCase = true) }
                ?.joinToString("&")
                ?.takeIf { it.isNotEmpty() }
                ?.let { "?$it" }
                .orEmpty()

                if (host.isNotEmpty()) "https://$host$path$query" else normalizedScheme.lowercase().removeSuffix("/")
            } catch (_: Exception) {
                url.trim().removeSuffix("/").lowercase()
            }
}
