/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

enum class EngineId(val label: String) {
    DUCKDUCKGO("DuckDuckGo"),
    BRAVE("Brave"),
    BING("Bing"),
    YAHOO("Yahoo"),
    MOJEEK("Mojeek"),
    QWANT("Qwant"),
    MARGINALIA("Marginalia"),
    PRESEARCH("Presearch"),
    ECOSIA("Ecosia"),
    SWISSCOWS("Swisscows"),
    STARTPAGE("Startpage"),
    CUSTOM("Custom"),
    META("Meta");
    companion object {
        fun fromString(raw: String): EngineId = entries.find { it.name == raw.uppercase() } ?: DUCKDUCKGO

        /** Real, queryable engines — excludes virtual META and user-defined CUSTOM. */
        val CONCRETE: List<EngineId> = entries.filter { it != META && it != CUSTOM }

        /** Engines fanned out for META mode (mirrors EngineRegistry.resolve("META")).
         *  Only engines with a registered [EngineParser] may be listed here —
         *  Brave/Mojeek/Presearch have no parser yet (see EngineParserRegistry),
         *  so listing them wastes a fan-out slot and yields zero results. */
        val META_MEMBERS: List<EngineId> = listOf(BING, YAHOO, QWANT, DUCKDUCKGO)

        /** Dedicated AI set: diverse, parseable, independent of the user's Search-tab setting. */
        val AI_META_MEMBERS: List<EngineId> = listOf(BING, YAHOO, QWANT, DUCKDUCKGO)
    }
}
