package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperBypassValidatorTest {

    @Test
    fun testEmptyAndBlankInputs_returnFalse() {
        assertFalse(isWhisperBypassPassword(""))
        assertFalse(isWhisperBypassPassword("   "))
        assertFalse(isWhisperBypassPassword("\n\t"))
    }

    @Test
    fun testIncorrectPasswords_returnFalse() {
        assertFalse(isWhisperBypassPassword("password"))
        assertFalse(isWhisperBypassPassword("admin123"))
        assertFalse(isWhisperBypassPassword("1234"))
        assertFalse(isWhisperBypassPassword("random_wrong_code"))
    }
}
