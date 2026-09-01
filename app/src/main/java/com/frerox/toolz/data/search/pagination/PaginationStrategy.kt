/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.pagination

import com.frerox.toolz.data.search.engine.EngineId

/**
 * Strategy for translating a logical result offset into a query string
 * fragment, and for deciding whether another page is worth requesting.
 *
 * [WebSearchRepository] resolves one instance per engine via [PaginationStrategy.forEngine]
 * and uses it both to build the request URL and to compute the next offset
 * after a page comes back.
 */
sealed interface PaginationStrategy {
    /** Query-string fragment (including leading `&`) encoding [offset] for [engine]. Empty for offset 0. */
    fun offsetParam(offset: Int, engine: EngineId): String

    /** Next offset to request, or null if pagination should stop. */
    fun nextOffset(currentOffset: Int, pageSize: Int, returnedCount: Int, hasMoreHint: Boolean): Int?

    /** Whether a further page is worth requesting given the last page's yield. */
    fun hasMore(returnedCount: Int, pageSize: Int, hasMoreHint: Boolean): Boolean

    companion object {
        /** Resolves the strategy to use for a given engine. META has no offset of its own — it's a fan-out. */
        fun forEngine(engine: EngineId): PaginationStrategy =
        if (engine == EngineId.META) NoPagination else OffsetBasedPagination
    }
}

/**
 * Offset-based pagination shared by all concrete engines. Each engine encodes
 * "skip N results" differently in its query string — this is the one place
 * that mapping lives; nothing else in the codebase should hardcode it.
 */
object OffsetBasedPagination : PaginationStrategy {
    override fun offsetParam(offset: Int, engine: EngineId): String {
        if (offset <= 0) return ""
            return when (engine) {
                EngineId.BING -> "&first=$offset"
                EngineId.YAHOO -> "&b=${offset + 1}"
                EngineId.QWANT -> "&offset=$offset"
                EngineId.MARGINALIA -> "&page=${offset / 10 + 1}"
                EngineId.META -> "" // META has no single offset — see NoPagination
            }
    }

    override fun nextOffset(currentOffset: Int, pageSize: Int, returnedCount: Int, hasMoreHint: Boolean): Int? {
        if (!hasMore(returnedCount, pageSize, hasMoreHint)) return null
            return currentOffset + returnedCount
    }

    /**
     * A page is worth requesting again only if the engine both signaled more
     * results are available AND actually returned a healthy fraction of a
     * full page — a near-empty page usually means we've hit the tail even if
     * the engine's "has more" hint says otherwise.
     */
    override fun hasMore(returnedCount: Int, pageSize: Int, hasMoreHint: Boolean): Boolean {
        if (returnedCount == 0 || !hasMoreHint) return false
            return returnedCount >= pageSize.coerceAtLeast(10) * 0.5
    }
}

/** Used for META (fan-out queries, no single offset) and any engine with no pagination support. */
object NoPagination : PaginationStrategy {
    override fun offsetParam(offset: Int, engine: EngineId): String = ""
    override fun nextOffset(currentOffset: Int, pageSize: Int, returnedCount: Int, hasMoreHint: Boolean): Int? = null
    override fun hasMore(returnedCount: Int, pageSize: Int, hasMoreHint: Boolean): Boolean = false
}
