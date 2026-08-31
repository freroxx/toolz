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
    }
}
