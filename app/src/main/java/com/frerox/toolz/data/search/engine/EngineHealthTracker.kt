/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing health state for a single engine. */
enum class EngineHealth { OK, COOLDOWN, FAILING }

/**
 * Tracks per-engine rate-limit cooldowns and recent success stats, so
 * [com.frerox.toolz.data.search.WebSearchRepository] can skip engines that
 * just got rate-limited (429/403 or a bot-challenge page) instead of hammering
 * them again on the next search, and so the UI can show live engine status.
 *
 * Thread-safe: read from and written to concurrently by parallel engine fetches.
 */
@Singleton
class EngineHealthTracker @Inject constructor() {

    private data class Cooldown(val until: Long)
    private data class Stats(val lastSuccessAt: Long, val lastResultCount: Int)

    private val cooldowns = ConcurrentHashMap<EngineId, Cooldown>()
    private val stats = ConcurrentHashMap<EngineId, Stats>()

    private val cooldownDurationMs = TimeUnit.SECONDS.toMillis(30)

    fun isAvailable(engine: EngineId): Boolean {
        val cooldown = cooldowns[engine] ?: return true
        return System.currentTimeMillis() >= cooldown.until
    }

    /** Puts [engine] on cooldown after a rate-limit signal (429/403 or bot challenge). */
    fun recordRateLimited(engine: EngineId) {
        cooldowns[engine] = Cooldown(until = System.currentTimeMillis() + cooldownDurationMs)
    }

    fun recordSuccess(engine: EngineId, resultCount: Int) {
        stats[engine] = Stats(System.currentTimeMillis(), resultCount)
    }

    /** Snapshot of every concrete engine's health, for a UI status indicator. */
    fun healthSnapshot(): Map<EngineId, EngineHealth> =
        EngineId.CONCRETE.associateWith { engine ->
            when {
                !isAvailable(engine) -> EngineHealth.COOLDOWN
                stats.containsKey(engine) -> EngineHealth.OK
                else -> EngineHealth.FAILING
            }
        }
}
