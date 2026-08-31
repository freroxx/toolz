/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

enum class EngineId(val label: String) {
    YAHOO("Yahoo"),
    QWANT("Qwant"),
    MARGINALIA("Marginalia"),
    BING("Bing"),
    CUSTOM("Custom"),
    META("Meta");

    companion object {
        fun fromString(raw: String): EngineId {
            val normalized = raw.trim().uppercase()
            return when (normalized) {
                "YAHOO" -> YAHOO
                "QWANT" -> QWANT
                "MARGINALIA" -> MARGINALIA
                "BING" -> BING
                "CUSTOM" -> CUSTOM
                "META" -> META
                // Explicit migrations from removed engines to META
                "DUCKDUCKGO", "BRAVE", "GOOGLE", "STARTPAGE",
                "ECOSIA", "SWISSCOWS", "MOJEEK", "PRESEARCH" -> META
                else -> entries.find { it.name == normalized } ?: META
            }
        }
    }
}
