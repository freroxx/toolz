/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

/**
 * Reconnect pacing shared by Whisper's app-scoped realtime lanes.
 *
 * The previous monitor retried a dropped socket every 3 seconds forever. A
 * revoked/expired session or a backend incident therefore caused a constant
 * 20-requests-per-minute retry per lane, drained battery and flooded logcat.
 * Exponential backoff with bounded jitter keeps recovery fast for the first
 * drop while backing off to one attempt per minute under a sustained outage.
 *
 * Pure and total so it can be unit-tested without Android or a coroutine clock.
 */
internal object WhisperRealtimeBackoff {

    const val BASE_DELAY_MS = 3_000L
    const val MAX_DELAY_MS = 60_000L
    private const val BASE_CAP_MS = 48_000L
    private const val MAX_ATTEMPT_EXPONENT = 5

    /**
     * Delay before retry number [attempt] (0-based). [jitterMs] is clamped to a
     * quarter of the current base so callers can pass a random value without
     * exceeding [MAX_DELAY_MS].
     */
    fun delayMs(attempt: Int, jitterMs: Long = 0L): Long {
        val exponent = attempt.coerceIn(0, MAX_ATTEMPT_EXPONENT)
        val base = (BASE_DELAY_MS shl exponent).coerceAtMost(BASE_CAP_MS)
        val jitterCap = (base / 4).coerceAtLeast(0L)
        val jitter = jitterMs.coerceIn(0L, jitterCap)
        return (base + jitter).coerceAtMost(MAX_DELAY_MS)
    }
}
