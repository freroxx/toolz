/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the hub search predicate; pure so it runs without Compose or a device. */
class WhisperGroupSearchTest {

    @Test
    fun blankOrWhitespaceQueryPasses() {
        assertTrue(groupMatches("", "Weekend plans", "See you at six"))
        assertTrue(groupMatches("   ", "Weekend plans", null))
    }

    @Test
    fun nameMatchIgnoresCaseAndSurroundingSpace() {
        assertTrue(groupMatches("  weekend ", "Weekend plans", null))
        assertTrue(groupMatches("PLANS", "Weekend plans", null))
    }

    @Test
    fun previewMatchIgnoresCase() {
        assertTrue(groupMatches("SIX", "Weekend plans", "See you at six"))
    }

    @Test
    fun unrelatedQueryDoesNotMatch() {
        assertFalse(groupMatches("birthday", "Weekend plans", "See you at six"))
        assertFalse(groupMatches("birthday", "Weekend plans", null))
    }
}
