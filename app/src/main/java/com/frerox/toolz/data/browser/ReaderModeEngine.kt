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
        doc.select("script,style,nav,footer,header,aside,.ads,.sidebar,#cookie-banner,.ad-container").remove()
        val article = doc.select("article,main,.content,.post-content,#content,.article-body").firstOrNull() ?: doc.body()
        return article?.text()?.take(10000) ?: ""
    }
}
