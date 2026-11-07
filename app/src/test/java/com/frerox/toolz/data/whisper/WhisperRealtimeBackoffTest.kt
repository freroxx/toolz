/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperRealtimeBackoffTest {

    @Test
    fun `first drop retries quickly`() {
        assertEquals(3_000L, WhisperRealtimeBackoff.delayMs(0))
        assertEquals(6_000L, WhisperRealtimeBackoff.delayMs(1))
        assertEquals(12_000L, WhisperRealtimeBackoff.delayMs(2))
    }

    @Test
    fun `sustained outage backs off but never exceeds the cap`() {
        for (attempt in 0..100) {
            val delay = WhisperRealtimeBackoff.delayMs(attempt, jitterMs = 60_000L)
            assertTrue("attempt $attempt -> $delay", delay in 0..WhisperRealtimeBackoff.MAX_DELAY_MS)
        }
        assertEquals(WhisperRealtimeBackoff.MAX_DELAY_MS, WhisperRealtimeBackoff.delayMs(10, jitterMs = 60_000L))
    }

    @Test
    fun `jitter is clamped to a quarter of the base`() {
        // Base at attempt 0 is 3 s -> at most 750 ms of jitter.
        assertEquals(3_000L, WhisperRealtimeBackoff.delayMs(0, jitterMs = -5L))
        assertEquals(3_750L, WhisperRealtimeBackoff.delayMs(0, jitterMs = 999_999L))
    }

    @Test
    fun `negative attempt is treated as the first retry`() {
        assertEquals(WhisperRealtimeBackoff.delayMs(0), WhisperRealtimeBackoff.delayMs(-3))
    }
}
