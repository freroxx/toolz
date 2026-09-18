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

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // T-P0-01 truth: priority 1=Critical..5=None, so ASC puts Critical first.
    // Matches TaskPriority + TodoScreen colors/labels + VM URGENCY + pill.
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority ASC, dueDate ASC")
    fun getActiveTasks(): Flow<List<TaskEntry>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 AND completedAt >= :startOfDay ORDER BY completedAt DESC")
    fun getCompletedToday(startOfDay: Long): Flow<List<TaskEntry>>

    // D-P1-02 midnight orphans: yesterday-completed satisfies NEITHER the active
    // query NOR getCompletedToday — without this they vanish forever (still
    // backed up). History surfaces them instead of auto-purging.
    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAt DESC LIMIT :limit")
    fun getCompletedHistory(limit: Int = 30): Flow<List<TaskEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntry): Long

    @Update
    suspend fun updateTask(task: TaskEntry)

    @Delete
    suspend fun deleteTask(task: TaskEntry)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntry?

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksSync(): List<TaskEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntry>)

    @Query("SELECT * FROM tasks WHERE dueDate IS NOT NULL")
    fun getTasksWithDueDate(): Flow<List<TaskEntry>>

    // T-P0-03 boot/restore reschedule needs a one-shot read (Flow never
    // completes inside a BootReceiver goAsync block). Wires the dead flow above.
    @Query("SELECT * FROM tasks WHERE dueDate IS NOT NULL AND isCompleted = 0")
    suspend fun getTasksWithDueDateSync(): List<TaskEntry>
}
