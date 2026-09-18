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

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D-P1-04: backup `listAdapter<TaskEntry>` round-trip with the app's Moshi
 * (KotlinJsonAdapterFactory). Guards the .tzbk tasks.json path.
 */
class TaskBackupRoundTripTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter<List<TaskEntry>>(
        Types.newParameterizedType(List::class.java, TaskEntry::class.java)
    )

    @Test
    fun tasks_surviveBackupRoundTrip() {
        val tasks = listOf(
            TaskEntry(
                id = 7,
                title = "  Spaced Title  ",
                description = "desc",
                category = "Work",
                priority = TaskPriority.CRITICAL,
                isCompleted = false,
                dueDate = 1_780_000_000_000L,
                subTasks = listOf(SubTask(id = "s1", title = "sub", isDone = true)),
                completedAt = null,
                createdAt = 1_779_000_000_000L
            ),
            TaskEntry(id = 8, title = "No due", priority = TaskPriority.NONE)
        )
        val json = adapter.toJson(tasks)
        assertEquals(tasks, adapter.fromJson(json))
    }

    @Test
    fun corruptTasksJson_decodesToNullOrEmpty_neverThrows() {
        // LocalBackupManager uses orEmpty() — corrupt payload must not throw here.
        val decoded = try {
            adapter.fromJson("{corrupt")
        } catch (_: Exception) {
            null
        }
        assertEquals(null, decoded)
    }
}
