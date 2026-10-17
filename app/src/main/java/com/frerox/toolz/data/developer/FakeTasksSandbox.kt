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

package com.frerox.toolz.data.developer

import com.frerox.toolz.data.todo.SubTask
import com.frerox.toolz.data.todo.TaskEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DEV-SPOOF: RAM-only task fixture (the Todo third of the Tasks tool).
 * Mirrors the real priority/sort/complete semantics exactly: adding, editing,
 * completing (with completedAt), subtask toggling, deleting and undo restore
 * all mutate this list — the Room table, alarm scheduler and ToolService
 * session stay untouched.
 */
object FakeTasksSandbox {

    /** Fixture ids start at 1000 so they can never collide with Room rows. */
    private val active = MutableStateFlow<List<TaskEntry>>(emptyList())
    val activeTasks: StateFlow<List<TaskEntry>> = active.asStateFlow()

    private val completedToday = MutableStateFlow<List<TaskEntry>>(emptyList())
    val completedTasks: StateFlow<List<TaskEntry>> = completedToday.asStateFlow()

    private var nextId = 1000

    /** DEV-SPOOF: seeds a believable mixed-backlog board on every demo arm. */
    fun arm() {
        nextId = 1000
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        val hour = 3_600_000L

        completedToday.value = listOf(
            TaskEntry(
                id = nextId++, title = "Empty inbox to zero", category = "Work",
                priority = 2, isCompleted = true, completedAt = now - 2 * hour,
                createdAt = now - day,
            ),
        )
        active.value = listOf(
            TaskEntry(
                id = nextId++, title = "Ship v2.6.0 release notes",
                description = "Feature list + demo mode blurb. Screenshot pass before upload.",
                category = "Dev", priority = 1,
                dueDate = now + 5 * hour, createdAt = now - 2 * day,
                subTasks = listOf(
                    SubTask("${nextId}a", "Draft feature list", isDone = true),
                    SubTask("${nextId}b", "Screenshots", isDone = false),
                    SubTask("${nextId}c", "Proofread", isDone = false),
                ),
            ),
            TaskEntry(
                id = nextId++, title = "Call Dentist — book annual cleaning",
                category = "Personal", priority = 2,
                dueDate = now + day, createdAt = now - 4 * day,
            ),
            TaskEntry(
                id = nextId++, title = "Restock coffee beans",
                description = "The dark Ethiopian preferred.",
                category = "Shopping", priority = 3,
                createdAt = now - day,
            ),
            TaskEntry(
                id = nextId++, title = "Hill run 10km",
                category = "Fitness", priority = 4,
                dueDate = now + 2 * day, createdAt = now - 6 * hour,
            ),
            TaskEntry(
                id = nextId++, title = "Backup laptop + phone",
                category = "Personal", priority = 5,
                createdAt = now - 3 * day,
            ),
        )
    }

    /** DEV-SPOOF: RAM add — no Room, no reminder alarm. */
    fun addTask(task: TaskEntry) {
        active.value = active.value + task.copy(id = nextId++)
    }

    fun updateTask(task: TaskEntry) {
        active.value = active.value.map { if (it.id == task.id) task else it }
    }

    /** DEV-SPOOF: toggle on the fresh fixture row, completedAt mirrors real. */
    fun toggleCompletion(id: Int): Boolean {
        if (find(id) == null) return false
        val hit = find(id)!!
        if (hit.isCompleted) {
            val restored = hit.copy(isCompleted = false, completedAt = null)
            completedToday.value = completedToday.value.filterNot { it.id == id }
            active.value = active.value + restored
        } else {
            val completed = hit.copy(isCompleted = true, completedAt = System.currentTimeMillis())
            active.value = active.value.filterNot { it.id == id }
            completedToday.value = completedToday.value + completed
        }
        return true
    }

    fun toggleSubTask(id: Int, subTaskId: String) {
        active.value = active.value.map {
            if (it.id == id) {
                it.copy(subTasks = it.subTasks.map { s ->
                    if (s.id == subTaskId) s.copy(isDone = !s.isDone) else s
                })
            } else it
        }
    }

    fun delete(id: Int) {
        active.value = active.value.filterNot { it.id == id }
        completedToday.value = completedToday.value.filterNot { it.id == id }
    }

    /** DEV-SPOOF: undo re-inserts with the ORIGINAL id (mirrors real). */
    fun restore(task: TaskEntry) {
        val list = if (task.isCompleted) completedToday.value else active.value
        val present = list.any { it.id == task.id }
        if (!present) {
            if (task.isCompleted) completedToday.value = completedToday.value + task
            else active.value = active.value + task
        }
        updateSeed(task)
    }

    private fun updateSeed(task: TaskEntry) {
        if (task.id >= nextId) nextId = task.id + 1
    }

    private fun find(id: Int): TaskEntry? =
        active.value.find { it.id == id } ?: completedToday.value.find { it.id == id }

    fun reset() {
        active.value = emptyList()
        completedToday.value = emptyList()
        nextId = 1000
    }
}
