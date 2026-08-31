/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

import androidx.compose.runtime.Immutable
import com.frerox.toolz.data.search.SearchResult

@Immutable
data class SearchRequest(val query: String, val offset: Int = 0, val pageSize: Int = 20, val category: SearchCategory = SearchCategory.ALL, val safeSearch: SafeSearchLevel = SafeSearchLevel.MODERATE, val region: String = "wt-wt", val customUrlTemplate: String = "", val adBlockEnabled: Boolean = true)
@Immutable
data class SearchResponse(val results: List<SearchResult>, val nextOffset: Int?, val hasMore: Boolean, val engineId: EngineId, val rawHasMoreHint: Boolean = hasMore)
