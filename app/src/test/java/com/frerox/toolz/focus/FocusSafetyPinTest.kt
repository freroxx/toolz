/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Step 0 — safety pins. Pure-JVM, no Android framework.
 * Failing here means a refactor drifted from the contracted behavior.
 */

package com.frerox.toolz.focus

import com.frerox.toolz.data.focus.legacySuggestLimitMinutes
import com.frerox.toolz.data.focus.productivityScore
import com.frerox.toolz.data.focus.suggestLimitMinutes
import com.frerox.toolz.data.focus.sumPackageUsage
import com.frerox.toolz.data.focus.validateFocusMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusSafetyPinTest {

    @Test
    fun score_empty_is_50() {
        assertEquals(50, productivityScore(0L, 0L))
    }

    @Test
    fun score_all_productive_clamped_98() {
        assertEquals(98, productivityScore(60_000L, 0L))
    }

    @Test
    fun score_all_distraction_clamped_5() {
        assertEquals(5, productivityScore(0L, 60_000L))
    }

    @Test
    fun score_half_is_50() {
        assertEquals(50, productivityScore(30_000L, 30_000L))
    }

    @Test
    fun legacy_suggestion_pinned() {
        // 0m used -> 15m; 10m used -> 15m; 120m used -> 120m (capped, instantly over-limit — known bug).
        assertEquals(15L, legacySuggestLimitMinutes(0L))
        assertEquals(15L, legacySuggestLimitMinutes(10 * 60_000L))
        assertEquals(120L, legacySuggestLimitMinutes(120 * 60_000L))
    }

    @Test
    fun fixed_suggestion_always_above_used() {
        assertEquals(15L, suggestLimitMinutes(0L))
        assertEquals(30L, suggestLimitMinutes(10 * 60_000L))
        assertEquals(135L, suggestLimitMinutes(120 * 60_000L))
        // Never suggests <= used.
        listOf(0L, 5L, 25L, 60L, 200L).forEach { mins ->
            val suggested = suggestLimitMinutes(mins * 60_000L)
            assert(suggested * 60_000L > mins * 60_000L) { "suggested $suggested <= used $mins" }
        }
    }

    @Test
    fun minutes_validation() {
        assertEquals(25, validateFocusMinutes(25))
        assertEquals(1, validateFocusMinutes(1))
        assertEquals(480, validateFocusMinutes(480))
        assertNull(validateFocusMinutes(0))
        assertNull(validateFocusMinutes(-5))
        assertNull(validateFocusMinutes(481))
    }

    @Test
    fun package_usage_sums_buckets_not_first() {
        val entries = listOf("a" to 10L, "a" to 20L, "b" to 5L)
        assertEquals(30L, sumPackageUsage(entries, "a"))
        assertEquals(5L, sumPackageUsage(entries, "b"))
        assertEquals(0L, sumPackageUsage(entries, "missing"))
    }
}
