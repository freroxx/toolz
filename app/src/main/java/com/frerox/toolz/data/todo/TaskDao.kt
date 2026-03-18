package com.frerox.toolz.data.todo

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority ASC, dueDate ASC")
    fun getActiveTasks(): Flow<List<TaskEntry>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 AND completedAt >= :startOfDay ORDER BY completedAt DESC")
    fun getCompletedToday(startOfDay: Long): Flow<List<TaskEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntry): Long

    @Update
    suspend fun updateTask(task: TaskEntry)

    @Delete
    suspend fun deleteTask(task: TaskEntry)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): TaskEntry?

    @Query("SELECT * FROM tasks WHERE dueDate IS NOT NULL")
    fun getTasksWithDueDate(): Flow<List<TaskEntry>>
}
