/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.developer

import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofGridTest {

    private val allFlags = DevToolId.entries.associate { it.prefKey to true }

    @Test
    fun `master off overrides every persisted flag`() {
        val grid = SpoofGrid(master = false, flags = allFlags)

        assertEquals(DevToolId.entries.toSet(), grid.keys)
        assertTrue(grid.values.none { it })
    }

    @Test
    fun `master on maps persisted flags per tool`() {
        val flags = allFlags - DevToolId.BATTERY.prefKey - DevToolId.WHISPER.prefKey
        val grid = SpoofGrid(master = true, flags = flags)

        assertFalse(grid.getValue(DevToolId.BATTERY))
        assertFalse(grid.getValue(DevToolId.WHISPER))
        assertTrue(grid.getValue(DevToolId.DEVICE))
        assertTrue(grid.getValue(DevToolId.SYSTEM))
    }

    @Test
    fun `unknown or missing flags stay off`() {
        val grid = SpoofGrid(
            master = true,
            flags = mapOf(DevToolId.STEPS.prefKey to true, "spoof_unknown" to true),
        )

        assertTrue(grid.getValue(DevToolId.STEPS))
        assertEquals(1, grid.values.count { it })
    }
}
