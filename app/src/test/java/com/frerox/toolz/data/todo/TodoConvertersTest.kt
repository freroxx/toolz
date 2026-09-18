/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-P0-04: corrupt / null subtask payloads must NEVER crash the list
 * (was: nullable+throwing converter -> NPE at count/isNotEmpty).
 */
class TodoConvertersTest {

    private val converters = TodoConverters()

    @Test
    fun null_returnsEmpty_noThrow() {
        assertEquals(emptyList<SubTask>(), converters.toSubTaskList(null))
    }

    @Test
    fun blank_returnsEmpty_noThrow() {
        assertEquals(emptyList<SubTask>(), converters.toSubTaskList(""))
        assertEquals(emptyList<SubTask>(), converters.toSubTaskList("   "))
    }

    @Test
    fun corruptJson_returnsEmpty_noThrow() {
        assertEquals(emptyList<SubTask>(), converters.toSubTaskList("{not json"))
        assertEquals(emptyList<SubTask>(), converters.toSubTaskList("[{broken"))
        assertEquals(emptyList<SubTask>(), converters.toSubTaskList("null"))
    }

    @Test
    fun roundTrip_preservesSubTasks() {
        val list = listOf(
            SubTask(id = "a", title = "one", isDone = false),
            SubTask(id = "b", title = "two", isDone = true)
        )
        val json = converters.fromSubTaskList(list)
        assertEquals(list, converters.toSubTaskList(json))
    }

    @Test
    fun emptyList_roundTrips() {
        val json = converters.fromSubTaskList(emptyList())
        assertTrue(converters.toSubTaskList(json).isEmpty())
    }
}
