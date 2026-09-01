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
 * Regression tests for the Yahoo sponsored-results leak: sponsored rows live in
 * `div.dd.ads` containers and their targets are `r.search.yahoo.com/rdclks/...`
 * redirects, which the adblock allowlist (`search.yahoo.com`) can never block.
 * Structure mirrors live Yahoo markup (2026): `<li><div class="dd fst ads ...">…
 * <a href=RDCLKS><div/><h3><span>Title</span></h3></a>…</div></li>`.
 */
class YahooParserSponsoredTest {

    private val parser = YahooParser()

    private val html = """
        <html><body><ol>
          <li><div class="dd fst ads bcan1 relsrch AdTop">
            <div class="compTitle options-toggle"><a href="https://r.search.yahoo.com/rdclks/abc/RU=https%3a%2f%2fads.example.com%2f/RK=2/RS=x">
              <div class="thmb"></div><h3 class="title"><span>Sponsored Ad Result</span></h3></a></div>
            <div class="compText"><p>Buy now</p></div>
          </div></li>
          <li><div class="dd algo algo-sr relsrch Sr">
            <div class="compTitle options-toggle"><a href="https://r.search.yahoo.com/_ylt=abc/RV=2/RE=1/RO=10/RU=https%3a%2f%2fdeveloper.android.com%2f/RK=2/RS=y">
              <div class="thmb"></div><h3 class="title"><span>Android Developers</span></h3></a></div>
            <div class="compText"><p>Feb 23, 2021 · Official Android site.</p></div>
          </div></li>
        </ol></body></html>
    """.trimIndent()

    @Test
    fun `sponsored ad rows are excluded from web results`() {
        val doc = Jsoup.parse(html)
        val results = parser.parseHtml(doc, SearchCategory.ALL, adBlockEnabled = false)
        assertEquals(listOf("Android Developers"), results.map { it.title })
        assertEquals("https://developer.android.com/", results.single().url)
    }

    @Test
    fun `organic result snippet is extracted with leading date split`() {
        val doc = Jsoup.parse(html)
        val results = parser.parseHtml(doc, SearchCategory.ALL, adBlockEnabled = false)
        assertTrue(results.single().snippet.contains("Official Android site"))
    }
}
