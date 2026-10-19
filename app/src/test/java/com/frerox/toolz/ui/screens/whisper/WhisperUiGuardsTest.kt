/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigation gate is the single shared contract behind every Whisper
 * double-push guard. These cases pin the window boundaries, including the
 * exact millisecond where a new push becomes legal again.
 */
class WhisperUiGuardsTest {

    @Test
    fun testFirstTap_isAccepted() {
        val gate = ElapsedTapGate()
        assertTrue(gate.tryAccept(1_000L))
    }

    @Test
    fun testSameFrameSecondTap_isRejected() {
        val gate = ElapsedTapGate()
        assertTrue(gate.tryAccept(5_000L))
        assertFalse(gate.tryAccept(5_000L))
    }

    @Test
    fun testExactWindowBoundary_isAccepted() {
        val gate = ElapsedTapGate()
        assertTrue(gate.tryAccept(10_000L))
        assertTrue(gate.tryAccept(10_000L + NAV_TAP_WINDOW_MS))
    }

    @Test
    fun testOneMillisecondBeforeWindow_isRejected() {
        val gate = ElapsedTapGate()
        assertTrue(gate.tryAccept(10_000L))
        assertFalse(gate.tryAccept(10_000L + NAV_TAP_WINDOW_MS - 1L))
    }

    @Test
    fun testReset_rearmsImmediately() {
        val gate = ElapsedTapGate()
        assertTrue(gate.tryAccept(20_000L))
        assertFalse(gate.tryAccept(20_001L))
        gate.reset()
        assertTrue(gate.tryAccept(20_001L))
    }
}
