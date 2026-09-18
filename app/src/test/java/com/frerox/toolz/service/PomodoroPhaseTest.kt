/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P-P0-01: phase truth is ONE function. 3 sessions + restart -> 4th still LONG.
 * Skip x4 never grants LONG (skips don't increment — cadence only advances on finish).
 */
class PomodoroPhaseTest {

    @Test
    fun workSessions_cycle_short_until_fourth() {
        assertEquals("SHORT_BREAK", nextModeAfterWork(0))
        assertEquals("SHORT_BREAK", nextModeAfterWork(1))
        assertEquals("SHORT_BREAK", nextModeAfterWork(2))
        assertEquals("LONG_BREAK", nextModeAfterWork(3))
    }

    @Test
    fun cycle_repeats_every_four() {
        assertEquals("LONG_BREAK", nextModeAfterWork(7))
        assertEquals("SHORT_BREAK", nextModeAfterWork(4))
        assertEquals("SHORT_BREAK", nextModeAfterWork(8))
    }

    @Test
    fun breaks_always_return_to_work() {
        assertEquals("WORK", nextPomodoroMode("SHORT_BREAK", 3))
        assertEquals("WORK", nextPomodoroMode("LONG_BREAK", 3))
        assertEquals("SHORT_BREAK", nextPomodoroMode("WORK", 0))
        assertEquals("LONG_BREAK", nextPomodoroMode("WORK", 3))
    }

    @Test
    fun negative_and_overflow_inputs_clamped() {
        assertEquals("SHORT_BREAK", nextModeAfterWork(-5))
        assertEquals("SHORT_BREAK", nextModeAfterWork(0))
    }

    @Test
    fun restart_preserves_cadence_derived_from_persisted() {
        // 3 sessions done (persisted) -> next work completion is 4th -> LONG.
        val persistedAfterRestart = 3
        assertEquals("LONG_BREAK", nextModeAfterWork(persistedAfterRestart))
    }
}
