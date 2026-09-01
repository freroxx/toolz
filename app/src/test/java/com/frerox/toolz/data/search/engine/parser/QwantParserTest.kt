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
 * Fixture tests for the Brave-backed Qwant-slot parser — markup mirrors live
 * 2026 search.brave.com HTML (`div.snippet[data-type=web]`, `.title`,
 * `.generic-snippet`). Verifies the slot still labels results "Qwant" so META
 * consensus badges and engine settings stay coherent.
 */
class QwantParserTest {

    private val parser = QwantParser()

    private val html = """
        <html><body><section id="mixed-main">
          <div class="snippet svelte-jmfu5f" data-pos="1" data-type="web">
            <div class="result-body"><div class="result-content">
              <a href="https://kotlinlang.org/" class="svelte-14r20fy l1">
                <div class="site-name-wrapper">
                  <cite class="snippet-url">kotlinlang.org</cite>
                </div>
                <div class="title search-snippet-title" title="Kotlin Programming Language">Kotlin Programming Language</div>
              </a>
              <div class="generic-snippet"><div class="content">Kotlin is <strong>a concise multiplatform language</strong> by JetBrains.</div></div>
            </div></div>
          </div>
          <div class="snippet svelte-1ajsqxo" data-pos="2" data-type="web">
            <div class="result-content">
              <a href="https://developer.android.com/kotlin"><div class="title">Android Kotlin docs</div></a>
              <div class="snippet-description">Official Android documentation for Kotlin.</div>
            </div>
          </div>
        </section></body></html>
    """.trimIndent()

    @Test
    fun `web results parsed with Qwant slot label`() {
        val doc = Jsoup.parse(html)
        val results = parser.parseHtml(doc, SearchCategory.ALL, adBlockEnabled = false)
        assertEquals(listOf("Kotlin Programming Language", "Android Kotlin docs"), results.map { it.title })
        assertEquals("Qwant", results[0].source)
        assertEquals("https://kotlinlang.org/", results[0].url)
        assertTrue(results[0].snippet.contains("multiplatform"))
    }

    @Test
    fun `request urls target brave with offset`() {
        val urls = parser.buildRequestUrls("kotlin", 20, SearchCategory.ALL, safeSearch = false)
        assertEquals(listOf("https://search.brave.com/search?q=kotlin&offset=20"), urls)
        val images = parser.buildRequestUrls("kotlin", 0, SearchCategory.IMAGES, safeSearch = true)
        assertEquals(listOf("https://search.brave.com/images?q=kotlin&safesearch=strict"), images)
    }
}
