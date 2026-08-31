package com.frerox.toolz.data.search.engine

import com.frerox.toolz.data.search.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaSearchRankingTest {

    private val merger = MetaMerger()

    @Test
    fun `consensus results from Yahoo and Qwant are boosted above single source Bing results`() {
        val yahooResults = listOf(
            SearchResult(
                title = "Consensus Article",
                url = "https://example.com/article?utm_source=yahoo",
                snippet = "Snippet from Yahoo",
                displayUrl = "example.com",
                source = "Yahoo",
            )
        )
        val qwantResults = listOf(
            SearchResult(
                title = "Consensus Article",
                url = "https://example.com/article",
                snippet = "Snippet from Qwant",
                displayUrl = "example.com",
                source = "Qwant",
            )
        )
        val bingResults = listOf(
            SearchResult(
                title = "Bing Only Result",
                url = "https://bing-exclusive.org/page",
                snippet = "Snippet from Bing only",
                displayUrl = "bing-exclusive.org",
                source = "Bing",
            )
        )

        val merged = merger.merge(
            mapOf(
                "YAHOO" to yahooResults,
                "QWANT" to qwantResults,
                "MARGINALIA" to emptyList(),
                "BING" to bingResults,
            )
        )

        // Deduplication should leave 2 results
        assertEquals(2, merged.size)
        // First result must be the consensus result (Yahoo + Qwant)
        assertEquals("https://example.com/article?utm_source=yahoo", merged[0].url)
        assertTrue(merged[0].engines.contains("Yahoo"))
        assertTrue(merged[0].engines.contains("Qwant"))
        // Second result must be the Bing-only result
        assertEquals("https://bing-exclusive.org/page", merged[1].url)
        assertEquals(listOf("Bing"), merged[1].engines)
    }

    @Test
    fun `canonical url normalization strips tracking parameters and trailing slashes`() {
        val canonical1 = merger.canonicalUrl("https://example.com/test/?utm_source=twitter&ref=123")
        val canonical2 = merger.canonicalUrl("https://example.com/test")
        assertEquals(canonical1, canonical2)
    }

    @Test
    fun `marginalia results contribute to consensus ranking`() {
        val marginaliaResults = listOf(
            SearchResult(
                title = "Indie Blog Post",
                url = "https://indie.blog/post/",
                snippet = "Text-first indie content",
                displayUrl = "indie.blog",
                source = "Marginalia",
            )
        )
        val yahooResults = listOf(
            SearchResult(
                title = "Indie Blog Post",
                url = "https://indie.blog/post",
                snippet = "Indexed via Yahoo",
                displayUrl = "indie.blog",
                source = "Yahoo",
            )
        )

        val merged = merger.merge(
            mapOf(
                "YAHOO" to yahooResults,
                "MARGINALIA" to marginaliaResults,
            )
        )

        assertEquals(1, merged.size)
        assertTrue(merged[0].engines.contains("Marginalia"))
        assertTrue(merged[0].engines.contains("Yahoo"))
    }
}
