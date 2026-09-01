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
 * Fixture tests for the Marginalia old-search HTML parser — markup mirrors the
 * live 2026 `section.card.search-result` structure on old-search.marginalia.nu.
 */
class MarginaliaParserTest {

    private val parser = MarginaliaParser()

    private val html = """
        <html><body>
          <section data-ms-rank="1" class="card search-result">
            <div class="url"><a rel="nofollow external" href="https://zserge.com/posts/kotlin/">https://zserge.com/posts/kotlin/</a></div>
            <h2> <a tabindex="-1" class="title" rel="nofollow external" href="https://zserge.com/posts/kotlin/">kotlin - a new hope</a> </h2>
            <p class="description">kotlin. a new hope. Ive been looking for Java alternatives since my first days.</p>
          </section>
          <section data-ms-rank="2" class="card search-result">
            <div class="url"><a rel="nofollow external" href="https://www.scottbrady.io/kotlin">https://www.scottbrady.io/kotlin</a></div>
            <h2> <a tabindex="-1" class="title" rel="nofollow external" href="https://www.scottbrady.io/kotlin">Kotlin</a> </h2>
            <p class="description">Kotlin. 30 November 2019 Ive been using the Java library, Nimbus JOSE + JWT.</p>
          </section>
        </body></html>
    """.trimIndent()

    @Test
    fun `web results are parsed from section cards`() {
        val doc = Jsoup.parse(html)
        val results = parser.parseHtml(doc, SearchCategory.ALL, adBlockEnabled = false)
        assertEquals(listOf("kotlin - a new hope", "Kotlin"), results.map { it.title })
        assertEquals("https://zserge.com/posts/kotlin/", results[0].url)
        assertEquals("Marginalia", results[0].source)
        assertTrue(results[0].snippet.contains("Java alternatives"))
    }

    @Test
    fun `request targets old-search endpoint with page offset`() {
        val urls = parser.buildRequestUrls("kotlin", 0, SearchCategory.ALL, safeSearch = false)
        assertEquals(listOf("https://old-search.marginalia.nu/search?query=kotlin"), urls)
        val paged = parser.buildRequestUrls("kotlin", 20, SearchCategory.ALL, safeSearch = false)
        assertTrue(paged.single().contains("&page=3"))
    }
}
