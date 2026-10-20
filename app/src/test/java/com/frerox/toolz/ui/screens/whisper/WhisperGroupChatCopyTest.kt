/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the text-only clipboard gate; poll/image containers are excluded by the caller. */
class WhisperGroupChatCopyTest {

    @Test
    fun textBodyIsCopyable() {
        assertTrue(groupMessageCopyable("hello there"))
        assertTrue(groupMessageCopyable("  padded  "))
    }

    @Test
    fun blankBodyIsNotCopyable() {
        assertFalse(groupMessageCopyable(""))
        assertFalse(groupMessageCopyable("   "))
    }
}
