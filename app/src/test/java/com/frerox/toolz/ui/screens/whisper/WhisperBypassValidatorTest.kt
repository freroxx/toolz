/*
 * Copyright (C) 2026 Toolz Contributors
 */
package com.frerox.toolz.ui.screens.whisper

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The bypass verdict itself is now decided SERVER-SIDE by the
 * `whisper-bypass-verify` Edge Function (constant-time compare + per-identity
 * rate limiting), so wrong-password outcomes are integration tests, not unit
 * tests — these cases would otherwise make real network calls in CI.
 *
 * What remains unit-testable is the local contract: blank/oversized input must
 * short-circuit to false without ever touching the network, and any transport
 * failure must fail CLOSED (false).
 */
class WhisperBypassValidatorTest {

    @Test
    fun testEmptyAndBlankInputs_returnFalseWithoutNetwork() = runTest {
        assertFalse(isWhisperBypassPassword(""))
        assertFalse(isWhisperBypassPassword("   "))
        assertFalse(isWhisperBypassPassword("\n\t"))
    }

    @Test
    fun testOversizedInput_shortCircuitsToFalse() = runTest {
        assertFalse(isWhisperBypassPassword("a".repeat(257)))
    }
}
