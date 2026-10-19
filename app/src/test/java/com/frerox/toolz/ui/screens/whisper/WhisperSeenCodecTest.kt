/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import com.frerox.toolz.data.whisper.WhisperSeenCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seen codec is the only non-Android part of the local Daily Ups seen
 * store. These cases pin its survival rules: live Longs only, malformed values
 * and keys ignored, the expiry boundary exclusive, and a deterministic 512-row
 * cap so the preference file can never grow without bound.
 */
class WhisperSeenCodecTest {

    @Test
    fun testDecode_keepsOnlyLivePrefixedLongs() {
        val decoded = WhisperSeenCodec.decode(
            mapOf(
                "seen_live" to 5_000L,
                "seen_expired" to 999L,
                "seen_boundary" to 1_000L,
                "seen_string" to "5000",
                "seen_int" to 5_000,
                "other_key" to 5_000L,
            ),
            nowMs = 1_000L,
        )
        assertEquals(mapOf("live" to 5_000L), decoded)
    }

    @Test
    fun testDecode_dropsBlankAndUnprefixedKeys() {
        val decoded = WhisperSeenCodec.decode(
            mapOf(
                "seen_" to 5_000L,
                "seen" to 5_000L,
                "seen_ok" to 5_000L,
                "seen_ " to 5_000L,
            ),
            nowMs = 0L,
        )
        assertEquals(setOf("ok"), decoded.keys)
        assertTrue(WhisperSeenCodec.decode(emptyMap(), nowMs = 0L).isEmpty())
    }

    @Test
    fun testDecode_capsAt512_keepingLatestExpiries() {
        val raw = (1..600).associate { id -> "seen_$id" to id.toLong() }
        val decoded = WhisperSeenCodec.decode(raw, nowMs = 0L)

        assertEquals(WhisperSeenCodec.MAX_ENTRIES, decoded.size)
        assertEquals(512, decoded.size)
        assertTrue(decoded.containsKey("600"))
        assertTrue(decoded.containsKey("89"))
        assertTrue(!decoded.containsKey("88"))
        assertTrue(!decoded.containsKey("1"))
    }

    @Test
    fun testDecode_equalExpiries_sortByStoryId() {
        val decoded = WhisperSeenCodec.decode(
            mapOf("seen_b" to 5_000L, "seen_a" to 5_000L, "seen_c" to 6_000L),
            nowMs = 0L,
        )
        assertEquals(listOf("c", "a", "b"), decoded.keys.toList())
    }

    @Test
    fun testKeyPrefix_andCapConstant() {
        assertEquals("seen_abc", WhisperSeenCodec.key("abc"))
        assertEquals(512, WhisperSeenCodec.MAX_ENTRIES)
        assertEquals("seen_story-1", WhisperSeenCodec.key("story-1"))
    }
}
