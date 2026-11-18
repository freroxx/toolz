/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Chats-tab rail's server reconcile is throttled per process. These cases
 * pin the window boundaries, including the first pass after a fresh boot,
 * where `lastAtMs == 0` must always pass.
 */
class WhisperDailyUpsSyncThrottleTest {

    private val window = 90_000L

    @Test
    fun `first reconcile in a process is due`() {
        assertTrue(dailyUpsSyncDue(lastAtMs = 0L, nowMs = 1_000L, windowMs = window))
    }

    @Test
    fun `inside the window is not due`() {
        assertFalse(dailyUpsSyncDue(lastAtMs = 10_000L, nowMs = 99_999L, windowMs = window))
    }

    @Test
    fun `exact window boundary is due`() {
        assertTrue(dailyUpsSyncDue(lastAtMs = 10_000L, nowMs = 10_000L + window, windowMs = window))
    }

    @Test
    fun `one millisecond before the window is not due`() {
        assertFalse(
            dailyUpsSyncDue(lastAtMs = 10_000L, nowMs = 10_000L + window - 1L, windowMs = window),
        )
    }

    @Test
    fun `after the window is due`() {
        assertTrue(dailyUpsSyncDue(lastAtMs = 10_000L, nowMs = 200_000L, windowMs = window))
    }

    @Test
    fun `clock reading before the anchor does not wedge the throttle open`() {
        assertFalse(dailyUpsSyncDue(lastAtMs = 50_000L, nowMs = 10_000L, windowMs = window))
    }
}
