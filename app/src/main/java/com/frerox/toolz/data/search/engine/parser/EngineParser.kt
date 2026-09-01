/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine.parser

import com.frerox.toolz.data.search.SearchCategory
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.engine.EngineId
import org.jsoup.nodes.Document

/**
 * Everything engine-specific about querying one search backend: how to build
 * its request URLs for a given category/offset, and how to turn its response
 * (HTML or JSON) into [SearchResult]s.
 *
 * Implementations should be pure/stateless — no I/O, no mutable state — so they're
 * trivially testable against saved fixture HTML/JSON. Fetching is [com.frerox.toolz.data.search.http.SearchHttpClient]'s
 * job; this interface only builds URLs and parses bytes that already came back.
 */
interface EngineParser {
    val id: EngineId

    /**
     * Candidate URLs to try, in order, for [query] (already URL-encoded) at [offset]
     * within [category]. Most engines return a single URL; a list allows a parser to
     * offer a fallback endpoint (e.g. an alternate host) without the caller needing
     * to know engine-specific details.
     */
    fun buildRequestUrls(query: String, offset: Int, category: SearchCategory, safeSearch: Boolean): List<String>

    /** True if [body] looks like this engine's JSON API response rather than HTML. */
    fun looksLikeJson(body: String): Boolean = body.trimStart().startsWith("{")

    /** Parses a JSON API response. Only called when [looksLikeJson] returned true. */
    fun parseJson(body: String, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult> = emptyList()

    /** Parses an HTML response. */
    fun parseHtml(doc: Document, category: SearchCategory, adBlockEnabled: Boolean): List<SearchResult>
}
