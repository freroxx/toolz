/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine.parser

import com.frerox.toolz.data.search.engine.EngineId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up the [EngineParser] for a given [EngineId]. Adding a new engine means
 * writing a new [EngineParser] implementation and adding one line here — nothing
 * in [com.frerox.toolz.data.search.WebSearchRepository] needs to change.
 */
@Singleton
class EngineParserRegistry @Inject constructor(
    bing: BingParser,
    yahoo: YahooParser,
    qwant: QwantParser,
    marginalia: MarginaliaParser,
) {
    private val parsers: Map<EngineId, EngineParser> = mapOf(
        EngineId.BING to bing,
        EngineId.YAHOO to yahoo,
        EngineId.QWANT to qwant,
        EngineId.MARGINALIA to marginalia,
    )

    /** Returns the parser for [engine], or null for [EngineId.META] (which has no parser of its own). */
    operator fun get(engine: EngineId): EngineParser? = parsers[engine]
}
