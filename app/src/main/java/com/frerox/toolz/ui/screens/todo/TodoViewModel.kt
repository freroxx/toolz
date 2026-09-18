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
import com.frerox.toolz.data.todo.TaskPriority
import com.frerox.toolz.data.todo.TaskRepository
import com.frerox.toolz.service.ToolService
import com.frerox.toolz.util.CalendarAlarmScheduler
import com.frerox.toolz.util.ScheduleOutcome
import com.frerox.toolz.util.TaskAlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class TodoUiState(
    val tasks: List<TaskEntry> = emptyList(),
    val completedToday: List<TaskEntry> = emptyList(),
    // D-P1-02: yesterday-and-older completed (were invisible orphans).
    val completedHistory: List<TaskEntry> = emptyList(),
    val categories: List<String> = listOf("Personal", "Dev", "Science", "Shopping", "Fitness", "Work"),
    val lastCategory: String = "Personal",
    val isSessionActive: Boolean = false,
    val sessionTaskId: Int? = null,
    // NOTE: session tick deliberately NOT here (was 1Hz whole-Scaffold
    // recompose D-P1-03). Collect [TodoViewModel.sessionTick] in the header.
    val sortOrder: TaskSortOrder = TaskSortOrder.PRIORITY,
    // D-P2-02: search query (rememberSaveable in UI mirrors this).
    val searchQuery: String = "",
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

    // D-P1-03: sortOrder lives OUTSIDE uiState (was `_uiState.map{sortOrder}`
    // fed back into the same combine = feedback loop). Mirrored into uiState
    // for the sort menu only.
    private val sortOrder = MutableStateFlow(TaskSortOrder.PRIORITY)

    // D-P1-03: 1Hz session tick as a SEPARATE flow collected only by the
    // header/banner — never triggers whole-screen recompose.
    private val _sessionTick = MutableStateFlow(0L)
    val sessionTick: StateFlow<Long> = _sessionTick.asStateFlow()

    // T-P0-03: at-due fallback / disabled / past outcomes surfaced as UX
    // (never silent). Screen snackbars AT_DUE_FALLBACK ("due too soon,
    // reminds at due time").
    private val _scheduleNotes = MutableSharedFlow<ScheduleOutcome>(extraBufferCapacity = 8)
    val scheduleNotes: SharedFlow<ScheduleOutcome> = _scheduleNotes.asSharedFlow()

    private val _calendarAdded = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val calendarAdded: SharedFlow<Unit> = _calendarAdded.asSharedFlow()

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
            // D-P1-02: midnight-ticker flow (no stale-until-restart) + history.
            repository.getCompletedTodayAuto(),
            repository.getCompletedHistory(),
            sortOrder,
            settingsRepository.taskCategories,
            settingsRepository.taskLastCategory
        ) { flows ->
            @Suppress("UNCHECKED_CAST")
            val active = flows[0] as List<TaskEntry>
            @Suppress("UNCHECKED_CAST")
            val completed = flows[1] as List<TaskEntry>
            @Suppress("UNCHECKED_CAST")
            val history = flows[2] as List<TaskEntry>
            val order = flows[3] as TaskSortOrder
            @Suppress("UNCHECKED_CAST")
            val cats = (flows[4] as Set<String>).sorted()
            val lastCat = flows[5] as String
            _uiState.update {
                it.copy(
                    tasks = sortTasks(active, order),
                    completedToday = completed,
                    completedHistory = history.filter { h -> completed.none { c -> c.id == h.id } },
                    categories = cats,
                    lastCategory = if (cats.contains(lastCat)) lastCat else cats.firstOrNull() ?: "Personal",
                    sortOrder = order
                )
            }
        }.launchIn(viewModelScope)

        settingsRepository.performanceMode.onEach { perf ->
            _uiState.update { it.copy(performanceMode = perf) }
        }.launchIn(viewModelScope)

        // T-P0-03: dead-toggle wiring — off cancels ALL alarms, on re-arms.
        // drop(1): launch-time value reflects the persisted toggle; alarms are
        // already armed, so only act on real transitions.
        settingsRepository.taskReminderNotifications
            .distinctUntilChanged()
            .drop(1)
            .onEach { enabled ->
                if (enabled) alarmScheduler.rescheduleAllFuture()
                else alarmScheduler.cancelAll()
            }
            .launchIn(viewModelScope)
    }

    private fun observeService() {
        toolService?.let { service ->
            // D-P1-03: active/id drive uiState; the 1Hz time goes to _sessionTick only.
            service.isTodoSessionActive.combine(service.todoTaskId) { active, id ->
                active to id
            }.distinctUntilChanged().onEach { (active, id) ->
                _uiState.update { it.copy(isSessionActive = active, sessionTaskId = id) }
            }.launchIn(viewModelScope)
            service.todoSessionTime.onEach { _sessionTick.value = it }.launchIn(viewModelScope)
        }
    }

    // T-P0-01 truth: 1=Critical..5=None. PRIORITY ASC + URGENCY
    // (6-p)*10-hoursUntilDue both put Critical/overdue on top (atomic with
    // TaskEntry comment, DAO ASC, Screen colors/labels, pill; see TaskPriority).
    // Null dueDate sinks via NULL_DUE_HOURS (undated below dated at equal
    // priority); DUE_DATE uses nullsLast (Long.MAX_VALUE). Tiebreak newest-first
    // matches DATE_ADDED (createdAt desc).
    private fun sortTasks(tasks: List<TaskEntry>, order: TaskSortOrder): List<TaskEntry> {
        val now = System.currentTimeMillis()
        return when (order) {
            TaskSortOrder.URGENCY -> tasks.sortedWith(
                compareByDescending<TaskEntry> {
                    TaskPriority.urgencyScore(it.priority, it.dueDate, now)
                }.thenByDescending { it.createdAt }
            )
            TaskSortOrder.PRIORITY -> tasks.sortedBy { TaskPriority.coerce(it.priority) }
            TaskSortOrder.DATE_ADDED -> tasks.sortedByDescending { it.createdAt }
            TaskSortOrder.DUE_DATE -> tasks.sortedBy { it.dueDate ?: Long.MAX_VALUE }
        }
    }

    fun setSortOrder(order: TaskSortOrder) {
        sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun addTask(
        title: String,
        description: String? = null,
        category: String,
        priority: Int,
        dueDate: Long? = null,
        subTasks: List<SubTask> = emptyList()
    ) {
        // D-P1-03: never trust the UI trim (QuickAdd also trims) — blank guard here.
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = TaskEntry(
                title = title.trim(),
                description = description?.trim().ifNullOrBlank(),
                category = category,
                priority = TaskPriority.coerce(priority),
                dueDate = dueDate,
                subTasks = subTasks
            )
            val rowId = repository.addTask(task)
            // D-P1-03 Long->Int: rowIds fit Int for this table; guard the absurd case.
            if (rowId > Int.MAX_VALUE) return@launch
            val savedTask = repository.getTaskById(rowId.toInt())
            savedTask?.let {
                _scheduleNotes.emit(alarmScheduler.scheduleReminder(it))
            }
            settingsRepository.setTaskLastCategory(category)
        }
    }

    /**
     * Write-through for user-edited fields (the sheet IS the source of truth
     * for title/desc/category/priority/due/subTasks). Toggle paths below MUST
     * NOT use this pattern — they reload by id first (guard 4: never
     * `task.copy()` from a UI snapshot then update under concurrency).
     */
    fun updateTask(task: TaskEntry) {
        if (task.title.isBlank()) return
        viewModelScope.launch {
            val fresh = repository.getTaskById(task.id)
            val toWrite = if (fresh == null) {
                task.copy(title = task.title.trim(), priority = TaskPriority.coerce(task.priority))
            } else {
                fresh.copy(
                    title = task.title.trim(),
                    description = task.description?.trim().ifNullOrBlank(),
                    category = task.category,
                    priority = TaskPriority.coerce(task.priority),
                    dueDate = task.dueDate,
                    subTasks = task.subTasks
                )
            }
            repository.updateTask(toWrite)
            if (toWrite.dueDate != null && !toWrite.isCompleted) {
                _scheduleNotes.emit(alarmScheduler.scheduleReminder(toWrite))
            } else {
                alarmScheduler.cancelReminder(toWrite)
            }
        }
    }

    /** D-P1-01 Undo: re-insert with the ORIGINAL id (REPLACE keeps identity) + reschedule. */
    fun restoreTask(task: TaskEntry) {
        viewModelScope.launch {
            repository.addTask(task)
            val saved = repository.getTaskById(task.id) ?: task
            if (!saved.isCompleted && saved.dueDate != null) {
                _scheduleNotes.emit(alarmScheduler.scheduleReminder(saved))
            }
        }
    }

    fun toggleTaskCompletion(task: TaskEntry) {
        viewModelScope.launch {
            // Guard 4: invert the FRESH row, never the possibly-stale snapshot.
            val fresh = repository.getTaskById(task.id) ?: return@launch
            val updated = fresh.copy(
                isCompleted = !fresh.isCompleted,
                completedAt = if (!fresh.isCompleted) System.currentTimeMillis() else null
            )
            repository.updateTask(updated)
            if (updated.isCompleted) {
                alarmScheduler.cancelReminder(updated)
                if (_uiState.value.sessionTaskId == task.id) {
                    stopSession()
                }
            } else {
                _scheduleNotes.emit(alarmScheduler.scheduleReminder(updated))
            }
        }
    }

    fun toggleSubTask(task: TaskEntry, subTaskId: String) {
        viewModelScope.launch {
            // Guard 4: map subtasks on the FRESH row.
            val fresh = repository.getTaskById(task.id) ?: return@launch
            val updatedSubTasks = fresh.subTasks.map {
                if (it.id == subTaskId) it.copy(isDone = !it.isDone) else it
            }
            repository.updateTask(fresh.copy(subTasks = updatedSubTasks))
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
        // D-P1-03: sessions only for ACTIVE tasks (done-task sessions + elapsed
        // carry-over were both broken). Time reset on switch lives in ToolService.
        val task = _uiState.value.tasks.find { it.id == taskId } ?: return
        toolService?.startTodoSession(taskId, task.title)
    }

    fun stopSession() {
        toolService?.stopTodoSession()
    }

    fun addCategory(name: String) {
        viewModelScope.launch { settingsRepository.addTaskCategory(name) }
    }

    fun addToCalendar(task: TaskEntry) {
        viewModelScope.launch {
            // D-P1-03: never fabricate `now` (instant-past 3 no-op alarms).
            // Undated -> next 09:00; priority -> matching deadline color.
            val timestamp = task.dueDate ?: nextNineAm()
            val event = EventEntry(
                title = task.title,
                description = task.description,
                timestamp = timestamp,
                eventType = "DEADLINE",
                subjectColor = deadlineColorFor(TaskPriority.coerce(task.priority)),
                remindersEnabled = true
            )
            val id = eventRepository.insertEvent(event)
            if (id > Int.MAX_VALUE) return@launch
            calendarAlarmScheduler.scheduleEventReminders(event.copy(id = id.toInt()))
            _calendarAdded.emit(Unit)
        }
    }

    private fun nextNineAm(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun deadlineColorFor(priority: Int): String = when (priority) {
        TaskPriority.CRITICAL -> "#F44336"
        TaskPriority.HIGH -> "#FF9800"
        TaskPriority.MEDIUM -> "#2196F3"
        TaskPriority.LOW -> "#4CAF50"
        else -> "#9E9E9E"
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

private fun String?.ifNullOrBlank(): String? {
    val t = this?.trim()
    return if (t.isNullOrEmpty()) null else t
}
