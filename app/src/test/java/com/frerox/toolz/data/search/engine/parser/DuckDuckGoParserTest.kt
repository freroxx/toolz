/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine.parser

import com.frerox.toolz.data.search.SearchCategory
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the DuckDuckGo HTML-endpoint parser: uddg redirect unwrapping,
 * web-result extraction, and safe-search / request URL construction.
 */
class DuckDuckGoParserTest {

    private val parser = DuckDuckGoParser()

    private val html = """
        <html><body>
          <div class="result">
            <h2 class="result__title"><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fdeveloper.android.com%2F&amp;rut=abc">Android Developers</a></h2>
            <a class="result__snippet" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fdeveloper.android.com%2F&amp;rut=abc">Official site with docs and guides.</a>
          </div>
          <div class="web-result">
            <h2><a class="result__a" href="https://kotlinlang.org/">Kotlin Language</a></h2>
            <div class="result__snippet">Kotlin is a concise multiplatform language.</div>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `web results are parsed with uddg redirect unwrapped`() {
        val doc = Jsoup.parse(html)
        val results = parser.parseHtml(doc, SearchCategory.ALL, adBlockEnabled = false)
        assertEquals(listOf("Android Developers", "Kotlin Language"), results.map { it.title })
        assertEquals("https://developer.android.com/", results[0].url)
        assertEquals("https://kotlinlang.org/", results[1].url)
    }

    @Test
    fun `snippets and display hosts are extracted`() {
        val doc = Jsoup.parse(html)
        val results = parser.parseHtml(doc, SearchCategory.ALL, adBlockEnabled = false)
        assertTrue(results[0].snippet.contains("Official site"))
        assertEquals("developer.android.com", results[0].displayUrl)
        assertEquals("DuckDuckGo", results[0].source)
    }

    @Test
    fun `request targets bare html endpoint with POST form fields`() {
        val urls = parser.buildRequestUrls("kotlin", 0, SearchCategory.ALL, safeSearch = true)
        assertEquals(listOf("https://html.duckduckgo.com/html/"), urls)
        val fields = parser.buildRequestFormFields("kotlin", 0, SearchCategory.ALL, safeSearch = true)
        assertEquals("kotlin", fields["q"])
        // Live-verified: GET is anomaly-challenged; results only come via POST form.
        assertEquals("1", fields["kp"])
        assertEquals("web", fields["ia"])
    }

    @Test
    fun `offset is encoded as the s form field`() {
        val fields = parser.buildRequestFormFields("kotlin", 20, SearchCategory.ALL, safeSearch = false)
        assertEquals("20", fields["s"])
        assertEquals("-1", fields["kp"])
    }
}
