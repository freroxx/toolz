/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

/**
 * Identity for every search backend Toolz can query.
 *
 * This is the single source of truth for engine metadata — display label,
 * settings-string parsing, and which concrete engines [META] fans out to.
 * Other files (repository, merger, pagination) should read those facts from
 * here rather than re-declaring their own `when` blocks over engine names.
 */
enum class EngineId(val label: String) {
    YAHOO("Yahoo"),

    /**
     * Qwant slot. Live-verified 2026: Qwant's own API/HTML are DataDome-walled and
     * unscrapable, so this slot resolves to **Brave Search** (independent,
     * privacy-first index). The historical label is kept so persisted settings,
     * META consensus badges, and engine-health tracking stay stable.
     */
    QWANT("Qwant"),
    MARGINALIA("Marginalia"),
    BING("Bing"),
    DUCKDUCKGO("DuckDuckGo"),

    /** Meta-search: fans out to every entry in [META_MEMBERS] and merges results. */
    META("Meta");

    companion object {
        /** Engines META queries and merges. Single source of truth for fan-out order. */
        val META_MEMBERS: List<EngineId> = listOf(YAHOO, QWANT, MARGINALIA, DUCKDUCKGO, BING)

        /** All concrete (non-META) engines, in fallback-rotation order. */
        val CONCRETE: List<EngineId> = listOf(YAHOO, QWANT, MARGINALIA, BING, DUCKDUCKGO)

        /**
         * Parses a persisted settings string into an [EngineId].
         * Unknown or retired values (previously-supported engines that have since
         * been dropped) fall back to [META] rather than crashing settings load.
         */
        fun fromString(raw: String): EngineId {
            val normalized = raw.trim().uppercase()
            entries.find { it.name == normalized }?.let { return it }
            // Retired engines migrate silently to META instead of erroring.
            return META
        }
    }
}
