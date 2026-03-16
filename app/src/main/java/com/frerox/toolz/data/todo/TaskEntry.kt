package com.frerox.toolz.data.todo

import androidx.room.Entity
import androidx.room.PrimaryKey

data class SubTask(
    val id: String,
    val title: String,
    val isDone: Boolean = false
)

@Entity(tableName = "tasks")
data class TaskEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String? = null,
    val category: String = "Personal",
    val priority: Int = 3, // 1 (High) to 5 (Low)
    val isCompleted: Boolean = false,
    val dueDate: Long? = null,
    val subTasks: List<SubTask> = emptyList(),
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
