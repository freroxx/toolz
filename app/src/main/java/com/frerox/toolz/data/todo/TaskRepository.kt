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
