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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao
) {
    val activeTasks: Flow<List<TaskEntry>> = taskDao.getActiveTasks()

    fun startOfDayNow(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun getCompletedToday(): Flow<List<TaskEntry>> = taskDao.getCompletedToday(startOfDayNow())

    /**
     * D-P1-02 midnight fix: the old `getCompletedToday()` computed startOfDay
     * ONCE per collection, so after midnight the list went stale until restart
     * (and progress denominators collapsed). This re-emits at every local
     * midnight and re-queries, so rollover is correct without restart.
     */
    fun getCompletedTodayAuto(): Flow<List<TaskEntry>> = midnightTicks().flatMapLatest { startOfDay ->
        taskDao.getCompletedToday(startOfDay)
    }

    /** D-P1-02 orphans: completed-before-today (invisible to both active and today queries). */
    fun getCompletedHistory(limit: Int = 30): Flow<List<TaskEntry>> =
        taskDao.getCompletedHistory(limit)

    private fun midnightTicks(): Flow<Long> = flow {
        while (true) {
            emit(startOfDayNow())
            val now = System.currentTimeMillis()
            val nextMidnight = startOfDayNow() + 24 * 60 * 60 * 1000L
            delay((nextMidnight - now).coerceAtLeast(60_000L))
        }
    }

    suspend fun addTask(task: TaskEntry) = taskDao.insertTask(task)
    suspend fun updateTask(task: TaskEntry) = taskDao.updateTask(task)
    suspend fun deleteTask(task: TaskEntry) = taskDao.deleteTask(task)
    suspend fun getTaskById(id: Int) = taskDao.getTaskById(id)
}
