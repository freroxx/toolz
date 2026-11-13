/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The viewer's receipt/reaction maps are populated per open and were never
 * trimmed, so they grew for the ViewModel's lifetime. On close they are pruned
 * to the stories the hub still lists.
 */
class WhisperDailyUpsCachePruneTest {

    @Test
    fun `stale stories are dropped and live ones kept`() {
        val map = mapOf("a" to 1, "b" to 2, "c" to 3)
        assertEquals(mapOf("a" to 1, "c" to 3), pruneToLiveStories(map, setOf("a", "c", "d")))
    }

    @Test
    fun `nothing stale keeps the same instance`() {
        val map = mapOf("a" to "x")
        assertSame(map, pruneToLiveStories(map, setOf("a", "b")))
    }

    @Test
    fun `an empty hub clears every entry`() {
        val map = mapOf("a" to 1, "b" to 2)
        assertTrue(pruneToLiveStories(map, emptySet()).isEmpty())
    }

    @Test
    fun `an empty map stays empty`() {
        assertTrue(pruneToLiveStories(emptyMap<String, Int>(), setOf("a")).isEmpty())
    }
}
