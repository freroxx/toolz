/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.safesearch

import com.frerox.toolz.data.search.engine.EngineId
import com.frerox.toolz.data.search.engine.SafeSearchLevel

object SafeSearchMapper {
    fun queryParam(engine: EngineId, level: SafeSearchLevel): String = when (engine) {
        EngineId.DUCKDUCKGO, EngineId.CUSTOM, EngineId.MOJEEK, EngineId.MARGINALIA -> when (level) {
            SafeSearchLevel.STRICT -> "&kp=1"
            SafeSearchLevel.MODERATE -> "&kp=-2"
            SafeSearchLevel.OFF -> "&kp=-1"
        }
        EngineId.BRAVE -> when (level) {
            SafeSearchLevel.STRICT -> "&safesearch=strict"
            SafeSearchLevel.MODERATE -> "&safesearch=moderate"
            SafeSearchLevel.OFF -> "&safesearch=off"
        }
        EngineId.BING, EngineId.ECOSIA -> when (level) {
            SafeSearchLevel.STRICT -> "&adlt=strict"
            SafeSearchLevel.MODERATE -> "&adlt=moderate"
            SafeSearchLevel.OFF -> "&adlt=off"
        }
        EngineId.QWANT -> when (level) {
            SafeSearchLevel.STRICT -> "&safesearch=1"
            SafeSearchLevel.MODERATE -> "&safesearch=1"
            SafeSearchLevel.OFF -> "&safesearch=0"
        }
        EngineId.YAHOO -> when (level) {
            SafeSearchLevel.STRICT -> "&vm=r"
            SafeSearchLevel.MODERATE -> "&vm=r"
            SafeSearchLevel.OFF -> ""
        }
        else -> when (level) {
            SafeSearchLevel.STRICT -> "&safe=active"
            SafeSearchLevel.MODERATE -> "&safe=active"
            else -> "&safe=off"
        }
    }
}
