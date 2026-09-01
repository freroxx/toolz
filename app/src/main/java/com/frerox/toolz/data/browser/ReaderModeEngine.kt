/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser

import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderModeEngine @Inject constructor() {
    fun extractReadable(html: String, baseUrl: String): String {
        val doc = Jsoup.parse(html, baseUrl)
        doc.select("script,style,nav,footer,header,aside,.ads,.sidebar,#cookie-banner,.ad-container,.comments,.share").remove()
        val article = doc.select("article,main,.content,.post-content,#content,.article-body,.entry-content").firstOrNull() ?: doc.body()
        val htmlFragment = article?.html() ?: ""
        return BrowserReaderExtractor.htmlToMarkdown(htmlFragment).take(15000).ifBlank { article?.text()?.take(10000) ?: "" }
    }

    fun htmlToMarkdown(html: String): String = BrowserReaderExtractor.htmlToMarkdown(html)
}
