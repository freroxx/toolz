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

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.browser.BrowserHistoryItem
import com.frerox.toolz.data.search.SearchHistoryEntry
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.engine.EngineHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * DEV-SPOOF: RAM-only search sandbox. Every fabricated URL lives under
 * `.demo` — they intentionally do not resolve, so a stray tap can never
 * touch the real web. Results, suggestions, history, browser-visit trail
 * and engine health all come from here; the metasearch engines, the DNS
 * benchmark probes and the Room history table are bypassed entirely.
 */
object FakeSearchSandbox {

    private val history = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    val fixtureHistory: StateFlow<List<SearchHistoryEntry>> = history.asStateFlow()

    private var nextHistoryId = 1L

    /** Seeded browser-visit trail — non-resolving `.demo` domains, RAM only. */
    private val browserVisits = MutableStateFlow<List<BrowserHistoryItem>>(emptyList())
    val fixtureBrowserHistory: StateFlow<List<BrowserHistoryItem>> =
        browserVisits.asStateFlow()

    fun arm() {
        nextHistoryId = 1L
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        history.value = listOf(
            SearchHistoryEntry(nextHistoryId++, "how to keep a laptop cool", now - 3 * hour),
            SearchHistoryEntry(nextHistoryId++, "best espresso grind size", now - 9 * hour),
            SearchHistoryEntry(nextHistoryId++, "jetbrains compose tutorial", now - 26 * hour),
            SearchHistoryEntry(nextHistoryId++, "trail running shoes 2026", now - 2 * 24 * hour),
        )
        browserVisits.value = listOf(
            BrowserHistoryItem(
                "https://guides.toolz.demo/thermal",
                "Thermal tuning guide · Toolz", now - 2 * hour, visitCount = 4,
            ),
            BrowserHistoryItem(
                "https://forum.devstack.demo/t/compose-basics",
                "Compose basics thread · DevStack", now - 20 * hour, visitCount = 2,
            ),
            BrowserHistoryItem(
                "https://wiki.openproject.demo/latte",
                "Latte dial-in wiki · OpenProject", now - 2 * 24 * hour, visitCount = 1,
            ),
        )
    }

    /** DEV-SPOOF: RAM history append — incognito semantics handled by caller. */
    fun addHistory(query: String) {
        if (query.isBlank()) return
        history.value = history.value + SearchHistoryEntry(nextHistoryId++, query)
    }

    fun deleteHistory(id: Long) {
        history.value = history.value.filterNot { it.id == id }
    }

    fun clearHistory() {
        history.value = emptyList()
    }

    /**
     * DEV-SPOOF: believable result tiles for any query. Titles weave the query
     * in, snippets read like real SEO write-ups, ranks/engines mimic the merged-
     * results look — all on non-resolving `.demo` domains.
     */
    fun resultsFor(query: String, page: Int, count: Int = 10): List<SearchResult> {
        val q = query.trim()
        val base = q.ifBlank { "your search" }.replaceFirstChar { it.uppercase(Locale.ROOT) }
        val domains = listOf(
            "guides.toolz.demo", "wiki.openproject.demo", "answers.stackline.demo",
            "forum.devstack.demo", "blog.millpost.demo", "tools.review.demo",
            "news.atlas.demo", "docs.redirect.demo",
        )
        val engines = listOf("duckduckgo", "brave", "mojeek", "startpage")
        val start = page * count
        return (0 until count).map { i ->
            val global = start + i
            val domain = domains[global % domains.size]
            val engineA = engines[global % engines.size]
            val engineB = engines[(global + 1) % engines.size]
            SearchResult(
                title = when (global % 5) {
                    0 -> "$base — a practical overview"
                    1 -> "The complete $base guide for 2026"
                    2 -> "$base: what actually works"
                    3 -> "10 things nobody tells you about $base"
                    else -> "$base explained in plain language"
                },
                snippet = "Everything about $base " +
                    "— plain-English breakdown, common pitfalls, and a field-tested " +
                    "checklist. (Fixture: this article does not exist and the " +
                    "link intentionally does not resolve.)",
                url = "https://$domain/${slug(q)}-${(global + 3)}",
                displayUrl = "$domain › articles",
                date = when (global % 3) {
                    0 -> "3 days ago"
                    1 -> "1 week ago"
                    else -> "Jun 12, 2026"
                },
                breadcrumb = "$domain › guides › ${slug(q)}",
                imageUrl = null,
                engineRank = (global % 9) + 1,
                engines = if (global % 2 == 0) listOf(engineA, engineB) else listOf(engineA),
            )
        }
    }

    private fun slug(q: String) =
        q.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(28)
            .ifBlank { "topic" }

    /** DEV-SPOOF: healthy engines with one believable cooldown. */
    fun engineHealthNames(): Map<String, EngineHealth> = mapOf(
        "DUCKDUCKGO" to EngineHealth.OK,
        "BRAVE" to EngineHealth.OK,
        "MOJEEK" to EngineHealth.OK,
        "BING" to EngineHealth.COOLDOWN,
    )

    /** DEV-SPOOF: suggestions built from fixture history + canned suffixes. */
    fun suggestions(query: String, historyQueries: List<String>): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val local = historyQueries.filter {
            it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true)
        }.take(3)
        val canned = listOf(
            "$q tutorial", "$q vs alternatives", "$q 2026 review",
            "is $q worth it", "$q explained simply",
        )
        return (local + canned).distinctBy { it.lowercase(Locale.ROOT) }.take(8)
    }

    /** DEV-SPOOF: plausible per-provider DNS latencies, no UDP packets sent. */
    fun dnsLatency(index: Int): Long? = when (index % 5) {
        0 -> 12L
        1 -> 21L
        2 -> 34L
        3 -> 46L
        else -> null // believable "unreachable" entry
    }

    fun reset() {
        history.value = emptyList()
        browserVisits.value = emptyList()
        nextHistoryId = 1L
    }
}
