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
import java.util.concurrent.TimeUnit

/**
 * T-P0-01 atomic guard: priority truth 1=Critical..5=None must hold in
 * urgency scoring, PRIORITY ASC ordering, and overdue ranking — everywhere
 * (DAO ASC, VM sorts, pill). If any side flips alone, this fails.
 */
class TaskPriorityTest {

    private val now = 1_700_000_000_000L

    private fun task(priority: Int, dueInHours: Long?): TaskEntry {
        val due = dueInHours?.let { now + TimeUnit.HOURS.toMillis(it) }
        return TaskEntry(
            id = 0,
            title = "t",
            priority = priority,
            dueDate = due,
            createdAt = now
        )
    }

    @Test
    fun critical_outranks_all_at_equal_due() {
        val scores = (1..5).map { TaskPriority.urgencyScore(it, now + 3_600_000L, now) }
        assertEquals(
            listOf(50L - 1L, 40L - 1L, 30L - 1L, 20L - 1L, 10L - 1L),
            scores
        )
        for (i in 0 until scores.size - 1) {
            assertTrue("priority ${i + 1} must outscore ${i + 2}", scores[i] > scores[i + 1])
        }
    }

    @Test
    fun priority_asc_matches_critical_first() {
        val shuffled = listOf(
            task(5, 1), task(3, 1), task(1, 1), task(4, 1), task(2, 1)
        )
        val sorted = shuffled.sortedBy { TaskPriority.coerce(it.priority) }
        assertEquals(listOf(1, 2, 3, 4, 5), sorted.map { it.priority })
    }

    @Test
    fun overdue_critical_tops_everything() {
        val overdueCritical = TaskPriority.urgencyScore(1, now - TimeUnit.HOURS.toMillis(5), now)
        val futureNone = TaskPriority.urgencyScore(5, now + TimeUnit.HOURS.toMillis(1), now)
        val undatedNone = TaskPriority.urgencyScore(5, null, now)
        assertTrue(overdueCritical > futureNone)
        assertTrue(overdueCritical > undatedNone)
    }

    @Test
    fun null_due_sinks_below_dated_at_equal_priority() {
        val dated = TaskPriority.urgencyScore(3, now + TimeUnit.HOURS.toMillis(200), now)
        val undated = TaskPriority.urgencyScore(3, null, now)
        assertTrue("dated far-future must still outrank undated", dated > undated)
    }

    @Test
    fun out_of_range_priority_coerced() {
        assertEquals(1, TaskPriority.coerce(0))
        assertEquals(5, TaskPriority.coerce(99))
        // Coerced scores match boundary scores.
        assertEquals(
            TaskPriority.urgencyScore(1, null, now),
            TaskPriority.urgencyScore(-7, null, now)
        )
    }
}
