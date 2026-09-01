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
 * Fixture tests for the Bing media parsers — mirrors live 2026 markup where
 * results are embedded as HTML-escaped `m="{...}"` JSON blobs (murl/turl/purl/t
 * for images; murl/vt/du/thid for videos).
 */
class BingMediaParserTest {

    private val parser = BingParser()

    private val imagesHtml = """
        <html><body>
        <li data-idx="1"><div class="iusc" m="{&quot;purl&quot;:&quot;https://enggkatta.com/introduction-to-kotlin/&quot;,&quot;murl&quot;:&quot;https://enggkatta.com/wp-content/kotlin.jpg&quot;,&quot;turl&quot;:&quot;https://ts3.mm.bing.net/th?id=OIP.123&quot;,&quot;t&quot;:&quot;Introduction to Kotlin Programming Language&quot;}"></div></li>
        <li data-idx="2"><div class="iusc" m="{&quot;purl&quot;:&quot;https://example.org/img&quot;,&quot;murl&quot;:&quot;https://example.org/img.png&quot;,&quot;turl&quot;:&quot;https://ts3.mm.bing.net/th?id=OIP.456&quot;,&quot;t&quot;:&quot;Another image&quot;}"></div></li>
        </body></html>
    """.trimIndent()

    private val videosHtml = """
        <html><body>
        <div m="{&quot;du&quot;:&quot;06:37&quot;,&quot;murl&quot;:&quot;https://www.youtube.com/watch?v=Qcjrb5wpniA&quot;,&quot;thid&quot;:&quot;OVF.2wKupawEmcWzRtDKa8t5Ww&quot;,&quot;vt&quot;:&quot;Create a Professional Mobile App with Gemini&quot;}"></div>
        <div m="{&quot;du&quot;:&quot;12:00&quot;,&quot;murl&quot;:&quot;https://www.youtube.com/watch?v=juDSEHBtay0&quot;,&quot;thid&quot;:&quot;OVF.NBwBycTz7ydqQ1vyPxNclA&quot;,&quot;vt&quot;:&quot;Kotlin coroutines guide&quot;}"></div>
        </body></html>
    """.trimIndent()

    @Test
    fun `image results parsed from escaped m-attribute blobs`() {
        val doc = Jsoup.parse(imagesHtml)
        val results = parser.parseHtml(doc, SearchCategory.IMAGES, adBlockEnabled = false)
        assertEquals(listOf("Introduction to Kotlin Programming Language", "Another image"), results.map { it.title })
        // url = page target (purl), imageUrl = thumbnail (turl)
        assertEquals("https://enggkatta.com/introduction-to-kotlin/", results[0].url)
        assertEquals("https://ts3.mm.bing.net/th?id=OIP.123", results[0].imageUrl)
        assertEquals("Bing", results[0].source)
    }

    @Test
    fun `video results parsed with watch url title duration and thumb`() {
        val doc = Jsoup.parse(videosHtml)
        val results = parser.parseHtml(doc, SearchCategory.VIDEOS, adBlockEnabled = false)
        assertEquals(2, results.size)
        assertEquals("https://www.youtube.com/watch?v=Qcjrb5wpniA", results[0].url)
        assertEquals("Create a Professional Mobile App with Gemini", results[0].title)
        assertEquals("06:37", results[0].snippet)
        assertTrue(results[0].imageUrl!!.startsWith("https://"))
    }
}
