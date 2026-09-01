/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

import com.frerox.toolz.data.search.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for META merge semantics: consensus ranking and — critically — tag
 * attribution, where the result's `source` must be the engine that ranked the
 * URL highest, NOT the first engine in fan-out order (the "Yahoo tags
 * everywhere" bug: Yahoo is queried first, so without this rule every deduped
 * result inherited Yahoo's label).
 */
class MetaMergerTest {

    private val merger = MetaMerger()

    private fun result(url: String, rank: Int, engine: EngineId) = SearchResult(
        title = "Title $engine", snippet = "Snippet for $url",
        url = url, displayUrl = url, source = engine.label, engineRank = rank,
    )

    @Test
    fun `source tag goes to the engine that ranked the url highest, not the first fan-out engine`() {
        // Same URL: Yahoo has it at rank 4, Qwant at rank 0. Qwant ranked it
        // highest → the merged result must be tagged Qwant even though Yahoo
        // was queried (and iterated) first.
        val merged = merger.merge(
            mapOf(
                EngineId.YAHOO to listOf(
                    result("https://a.com/1", 0, EngineId.YAHOO),
                    result("https://shared.com/page", 4, EngineId.YAHOO),
                ),
                EngineId.QWANT to listOf(
                    result("https://shared.com/page", 0, EngineId.QWANT),
                ),
                EngineId.MARGINALIA to listOf(
                    result("https://shared.com/page", 1, EngineId.MARGINALIA),
                ),
            )
        )
        val shared = merged.first { it.url.contains("shared.com") }
        assertEquals("Qwant", shared.source)
        // Consensus badge still lists every contributing engine.
        assertEquals(setOf("Yahoo", "Qwant", "Marginalia"), shared.engines.toSet())
    }

    @Test
    fun `consensus result outranks single-engine top result`() {
        val merged = merger.merge(
            mapOf(
                EngineId.YAHOO to listOf(
                    result("https://only-yahoo.com/", 0, EngineId.YAHOO),
                    result("https://consensus.com/", 5, EngineId.YAHOO),
                ),
                EngineId.QWANT to listOf(result("https://consensus.com/", 2, EngineId.QWANT)),
                EngineId.MARGINALIA to listOf(result("https://consensus.com/", 3, EngineId.MARGINALIA)),
            )
        )
        assertEquals("https://consensus.com/", merged.first().url)
    }

    @Test
    fun `canonical url dedupes tracking params and www variants`() {
        val a = merger.canonicalUrl("https://www.example.com/Page/?utm_source=x&id=5")
        val b = merger.canonicalUrl("https://example.com/Page?id=5")
        assertEquals(a, b)
    }

    @Test
    fun `merged result keeps the richest field set across engines`() {
        val yahooOnly = SearchResult(
            title = "T", snippet = "", url = "https://x.com/",
            displayUrl = "x.com", source = EngineId.YAHOO.label, engineRank = 0,
        )
        val qwantOnly = SearchResult(
            title = "T", snippet = "rich snippet", url = "https://x.com/",
            displayUrl = "x.com", source = EngineId.QWANT.label, engineRank = 1,
            date = "Jun 12, 2025",
        )
        val merged = merger.merge(
            mapOf(
                EngineId.YAHOO to listOf(yahooOnly),
                EngineId.QWANT to listOf(qwantOnly),
            )
        )
        val out = merged.single()
        // Qwant ranked it highest (rank 1 vs... no — Yahoo rank 0 wins rank; what matters
        // is the field merge: snippet comes from Qwant, date comes from Qwant).
        assertTrue(out.snippet.isNotBlank())
        assertEquals("Jun 12, 2025", out.date)
    }
}
