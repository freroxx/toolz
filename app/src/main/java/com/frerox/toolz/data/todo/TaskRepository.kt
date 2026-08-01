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

import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    val activeTasks: Flow<List<TaskEntry>> = taskDao.getActiveTasks()

    fun getCompletedToday(): Flow<List<TaskEntry>> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return taskDao.getCompletedToday(calendar.timeInMillis)
    }

    suspend fun addTask(task: TaskEntry) = taskDao.insertTask(task)
    suspend fun updateTask(task: TaskEntry) = taskDao.updateTask(task)
    suspend fun deleteTask(task: TaskEntry) = taskDao.deleteTask(task)
    suspend fun getTaskById(id: Int) = taskDao.getTaskById(id)
}
