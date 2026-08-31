/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.safesearch

import com.frerox.toolz.data.search.engine.EngineId
import com.frerox.toolz.data.search.engine.SafeSearchLevel

object SafeSearchMapper {
    fun queryParam(engine: EngineId, level: SafeSearchLevel): String = when (engine) {
        EngineId.MARGINALIA, EngineId.CUSTOM -> when (level) {
            SafeSearchLevel.STRICT -> "&kp=1"
            SafeSearchLevel.MODERATE -> "&kp=-2"
            SafeSearchLevel.OFF -> "&kp=-1"
        }
        EngineId.BING -> when (level) {
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
            SafeSearchLevel.STRICT -> "&adlt=strict"
            SafeSearchLevel.MODERATE -> "&adlt=moderate"
            else -> "&adlt=off"
        }
    }
}
