/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class EngineRegistry @Inject constructor(
    private val duckDuckGoEngine: DuckDuckGoEngine,
    private val braveEngine: BraveEngine,
    private val bingEngine: BingEngine,
    private val yahooEngine: YahooEngine,
    private val mojeekEngine: MojeekEngine,
    private val qwantEngine: QwantEngine,
    private val marginaliaEngine: MarginaliaEngine,
    private val presearchEngine: PresearchEngine,
) {
    private data class Cooldown(val until: Long)
    private val cooldowns = ConcurrentHashMap<EngineId, Cooldown>()
    private val COOLDOWN_MS = 30000L
    private val FALLBACK_ORDER = listOf(EngineId.BING, EngineId.BRAVE, EngineId.YAHOO)
    fun isAvailable(id: EngineId): Boolean { val c=cooldowns[id] ?: return true; return System.currentTimeMillis() >= c.until }
    fun cooldown(id: EngineId){ cooldowns[id]=Cooldown(System.currentTimeMillis()+COOLDOWN_MS) }
    fun resolve(engineSetting: String): List<EngineId> = when(engineSetting.uppercase()){
        "META"->listOf(EngineId.BING, EngineId.BRAVE, EngineId.YAHOO)
        "CUSTOM"->listOf(EngineId.CUSTOM)
        else->listOf(EngineId.fromString(engineSetting))
    }
    fun fallbacksFor(primary: EngineId): List<EngineId> = FALLBACK_ORDER.filter{ it!=primary && isAvailable(it) }
    fun engineFor(id: EngineId): SearchEngine? = when(id){
        EngineId.DUCKDUCKGO->duckDuckGoEngine
        EngineId.BRAVE->braveEngine
        EngineId.BING->bingEngine
        EngineId.YAHOO->yahooEngine
        EngineId.MOJEEK->mojeekEngine
        EngineId.QWANT->qwantEngine
        EngineId.MARGINALIA->marginaliaEngine
        EngineId.PRESEARCH->presearchEngine
        else->null
    }
}
