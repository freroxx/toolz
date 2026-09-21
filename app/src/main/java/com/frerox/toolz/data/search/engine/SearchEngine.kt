/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

import com.frerox.toolz.data.search.SearchResult

interface SearchEngine {
    val id: EngineId
    val displayName: String
    val supportsPagination: Boolean get() = true
    val supportsSafeSearch: Boolean get() = true
    suspend fun search(request: SearchRequest): SearchResponse
    fun buildSearchUrl(request: SearchRequest): List<String>
    fun parse(html: String, baseUrl: String, request: SearchRequest): List<SearchResult>
}
