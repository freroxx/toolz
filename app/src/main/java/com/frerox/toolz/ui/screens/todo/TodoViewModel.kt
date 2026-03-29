package com.frerox.toolz.ui.screens.todo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.calendar.EventEntry
import com.frerox.toolz.data.calendar.EventRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.todo.SubTask
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.data.todo.TaskRepository
import com.frerox.toolz.service.ToolService
import com.frerox.toolz.util.CalendarAlarmScheduler
import com.frerox.toolz.util.TaskAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class TodoUiState(
    val tasks: List<TaskEntry> = emptyList(),
    val completedToday: List<TaskEntry> = emptyList(),
    val categories: List<String> = listOf("Personal", "Dev", "Science", "Shopping", "Fitness", "Work"),
    val isSessionActive: Boolean = false,
    val sessionTaskId: Int? = null,
    val sessionTimeMillis: Long = 0L,
    val sortOrder: TaskSortOrder = TaskSortOrder.PRIORITY,
    val performanceMode: Boolean = false
)

enum class TaskSortOrder {
    URGENCY, PRIORITY, DATE_ADDED, DUE_DATE
}

@HiltViewModel
class TodoViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: TaskRepository,
    private val eventRepository: EventRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmScheduler: TaskAlarmScheduler,
    private val calendarAlarmScheduler: CalendarAlarmScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState = _uiState.asStateFlow()

    private var toolService: ToolService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ToolService.LocalBinder
            toolService = binder.getService()
            isBound = true
            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            toolService = null
        }
    }

    init {
        Intent(context, ToolService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        combine(
            repository.activeTasks, 
            repository.getCompletedToday(),
            _uiState.map { it.sortOrder }.distinctUntilChanged()
        ) { active, completed, sortOrder ->
            _uiState.update { it.copy(
                tasks = sortTasks(active, sortOrder),
                completedToday = completed
            ) }
        }.launchIn(viewModelScope)

        settingsRepository.performanceMode.onEach { perf ->
            _uiState.update { it.copy(performanceMode = perf) }
        }.launchIn(viewModelScope)
    }

    private fun observeService() {
        toolService?.let { service ->
            service.isTodoSessionActive.combine(service.todoSessionTime) { active, time ->
                active to time
            }.combine(service.todoTaskId) { (active, time), id ->
                Triple(active, time, id)
            }.onEach { (active, time, id) ->
                _uiState.update { it.copy(
                    isSessionActive = active,
                    sessionTimeMillis = time,
                    sessionTaskId = id
                ) }
            }.launchIn(viewModelScope)
        }
    }

    private fun sortTasks(tasks: List<TaskEntry>, order: TaskSortOrder): List<TaskEntry> {
        val now = System.currentTimeMillis()
        return when (order) {
            TaskSortOrder.URGENCY -> tasks.sortedWith(
                compareByDescending<TaskEntry> { 
                    val hoursUntilDue = it.dueDate?.let { due ->
                        TimeUnit.MILLISECONDS.toHours(due - now)
                    } ?: 100L
                    val invertedPriority = (6 - it.priority)
                    (invertedPriority * 10) - hoursUntilDue
                }.thenByDescending { it.createdAt }
            )
            TaskSortOrder.PRIORITY -> tasks.sortedBy { it.priority }
            TaskSortOrder.DATE_ADDED -> tasks.sortedByDescending { it.createdAt }
            TaskSortOrder.DUE_DATE -> tasks.sortedBy { it.dueDate ?: Long.MAX_VALUE }
        }
    }

    fun setSortOrder(order: TaskSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun addTask(
        title: String, 
        description: String? = null, 
        category: String, 
        priority: Int, 
        dueDate: Long? = null,
        subTasks: List<SubTask> = emptyList()
    ) {
        viewModelScope.launch {
            val task = TaskEntry(
                title = title,
                description = description,
                category = category,
                priority = priority,
                dueDate = dueDate,
                subTasks = subTasks
            )
            val id = repository.addTask(task)
            val savedTask = repository.getTaskById(id.toInt())
            savedTask?.let { alarmScheduler.scheduleReminder(it) }
        }
    }

    fun updateTask(task: TaskEntry) {
        viewModelScope.launch {
            repository.updateTask(task)
            if (task.dueDate != null && !task.isCompleted) {
                alarmScheduler.scheduleReminder(task)
            } else {
                alarmScheduler.cancelReminder(task)
            }
        }
    }

    fun toggleTaskCompletion(task: TaskEntry) {
        viewModelScope.launch {
            val updated = task.copy(
                isCompleted = !task.isCompleted,
                completedAt = if (!task.isCompleted) System.currentTimeMillis() else null
            )
            repository.updateTask(updated)
            if (updated.isCompleted) {
                alarmScheduler.cancelReminder(updated)
                if (_uiState.value.sessionTaskId == task.id) {
                    stopSession()
                }
            } else {
                alarmScheduler.scheduleReminder(updated)
            }
        }
    }

    fun toggleSubTask(task: TaskEntry, subTaskId: String) {
        viewModelScope.launch {
            val updatedSubTasks = task.subTasks.map {
                if (it.id == subTaskId) it.copy(isDone = !it.isDone) else it
            }
            repository.updateTask(task.copy(subTasks = updatedSubTasks))
        }
    }

    fun deleteTask(task: TaskEntry) {
        viewModelScope.launch {
            repository.deleteTask(task)
            alarmScheduler.cancelReminder(task)
            if (_uiState.value.sessionTaskId == task.id) {
                stopSession()
            }
        }
    }

    fun startSession(taskId: Int) {
        val task = (_uiState.value.tasks + _uiState.value.completedToday).find { it.id == taskId } ?: return
        toolService?.startTodoSession(taskId, task.title)
    }

    fun stopSession() {
        toolService?.stopTodoSession()
    }

    fun addCategory(name: String) {
        if (name.isNotBlank() && !_uiState.value.categories.contains(name)) {
            _uiState.update { it.copy(categories = it.categories + name) }
        }
    }

    fun addToCalendar(task: TaskEntry) {
        viewModelScope.launch {
            val event = EventEntry(
                title = task.title,
                description = task.description,
                timestamp = task.dueDate ?: System.currentTimeMillis(),
                eventType = "DEADLINE",
                subjectColor = "#6200EE",
                remindersEnabled = true
            )
            val id = eventRepository.insertEvent(event)
            calendarAlarmScheduler.scheduleEventReminders(event.copy(id = id.toInt()))
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (isBound) {
                context.unbindService(connection)
                isBound = false
            }
        } catch (e: Exception) {
            // Service might have already been unbound
        }
    }
}
