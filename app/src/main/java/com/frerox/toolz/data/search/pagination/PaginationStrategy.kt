/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.pagination

import com.frerox.toolz.data.search.engine.EngineId

sealed interface PaginationStrategy {
    fun offsetParam(offset: Int, engine: EngineId): String
    fun nextOffset(currentOffset: Int, pageSize: Int, returnedCount: Int, hasMoreHint: Boolean): Int?
    fun hasMore(returnedCount: Int, pageSize: Int, hasMoreHint: Boolean): Boolean
}
object OffsetBasedPagination : PaginationStrategy {
    override fun offsetParam(offset: Int, engine: EngineId): String = when (engine) {
        EngineId.BING -> if (offset > 0) "&first=$offset" else ""
        EngineId.YAHOO -> if (offset > 0) "&b=${offset + 1}" else ""
        EngineId.QWANT -> if (offset > 0) "&offset=$offset" else ""
        EngineId.MARGINALIA -> if (offset > 0) "&page=${offset / 10 + 1}" else ""
        else -> if (offset > 0) "&offset=$offset" else ""
    }
    override fun nextOffset(currentOffset: Int, pageSize: Int, returnedCount: Int, hasMoreHint: Boolean): Int? {
        if (!hasMore(returnedCount, pageSize, hasMoreHint)) return null
        return currentOffset + returnedCount
    }
    override fun hasMore(returnedCount: Int, pageSize: Int, hasMoreHint: Boolean): Boolean {
        if (returnedCount == 0) return false
        if (!hasMoreHint) return false
        return returnedCount >= pageSize.coerceAtLeast(10) * 0.5
    }
}
object NoPagination : PaginationStrategy {
    override fun offsetParam(offset: Int, engine: EngineId): String = ""
    override fun nextOffset(currentOffset: Int, pageSize: Int, returnedCount: Int, hasMoreHint: Boolean): Int? = null
    override fun hasMore(returnedCount: Int, pageSize: Int, hasMoreHint: Boolean): Boolean = false
}
object OffsetTranslator {
    fun translate(engine: EngineId, offset: Int, baseQuery: String, extra: String = ""): String = when (engine) {
        EngineId.BING -> "${baseQuery}${if (offset > 0) "&first=$offset" else ""}$extra"
        EngineId.YAHOO -> "${baseQuery}${if (offset > 0) "&b=${offset + 1}" else ""}$extra"
        EngineId.QWANT -> "${baseQuery}${if (offset > 0) "&offset=$offset" else ""}$extra"
        EngineId.MARGINALIA -> "${baseQuery}${if (offset > 0) "&page=${offset / 10 + 1}" else ""}$extra"
        else -> "${baseQuery}${if (offset > 0) "&offset=$offset" else ""}$extra"
    }
}
