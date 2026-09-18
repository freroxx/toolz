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

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

data class SubTask(
    val id: String,
    val title: String,
    val isDone: Boolean = false
)

/**
 * T-P0-01 — Priority single truth (atomic with [TaskDao] ASC, [TodoScreen]
 * colors/labels, ViewModel URGENCY + pill ordering + unit test).
 *
 * Truth: **1=Critical .. 5=None** (lower number = more important).
 * - `TaskDao.getActiveTasks` uses `ORDER BY priority ASC` so Critical sorts first.
 * - `TodoViewModel` PRIORITY sort is `sortedBy { priority }` (same direction).
 * - URGENCY weight is `(6 - priority) * 10 - hoursUntilDue` so Critical scores
 *   highest; overdue-Critical always tops. Null dueDate sinks (documented
 *   `NULL_DUE_HOURS` penalty in [TaskPriority.urgencyScore]).
 * - Pill shows the same ordering (min due / Critical-first), never `first()`.
 *
 * DO NOT change one side only (DAO alone, colors alone, etc.) — the inversion
 * returns. The unit test `TaskPriorityTest` guards this ordering.
 */
object TaskPriority {
    const val CRITICAL = 1
    const val HIGH = 2
    const val MEDIUM = 3
    const val LOW = 4
    const val NONE = 5

    /** Hours penalty applied when [dueDate] is null so undated tasks sink below
     * dated ones (nullsLast: 10_000h ≈ 416 days — below every realistic due). */
    const val NULL_DUE_HOURS = 10_000L

    fun isValid(p: Int): Boolean = p in CRITICAL..NONE

    fun coerce(p: Int): Int = p.coerceIn(CRITICAL, NONE)

    /**
     * Higher = more urgent. Overdue grows without bound (negative hours subtract
     * negatively → larger score), Critical outweighs lower priorities at equal
     * due dates, null dueDates sink via [NULL_DUE_HOURS].
     */
    fun urgencyScore(priority: Int, dueDate: Long?, now: Long): Long {
        val p = coerce(priority)
        val hoursUntilDue = if (dueDate == null) {
            NULL_DUE_HOURS
        } else {
            java.util.concurrent.TimeUnit.MILLISECONDS.toHours(dueDate - now)
        }
        return ((6 - p) * 10L) - hoursUntilDue
    }
}

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["isCompleted"]),
        Index(value = ["dueDate"]),
        Index(value = ["priority"]),
        Index(value = ["completedAt"])
    ]
)
data class TaskEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String? = null,
    val category: String = "Personal",
    val priority: Int = 3, // T-P0-01 truth: 1=Critical .. 5=None (ASC = most important first)
    val isCompleted: Boolean = false,
    val dueDate: Long? = null,
    val subTasks: List<SubTask> = emptyList(),
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
