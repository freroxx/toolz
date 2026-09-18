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

package com.frerox.toolz.ui.screens.calendar

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.AiConfig
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.calendar.*
import com.frerox.toolz.data.todo.TaskEntry
import com.frerox.toolz.util.CalendarAlarmScheduler
import com.frerox.toolz.util.TaskAlarmScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────
//  Constants
// ─────────────────────────────────────────────────────────────

private const val TAG = "CalendarViewModel"

private const val MAX_YEARS_IN_PAST   = 1
private const val MAX_YEARS_IN_FUTURE = 5

object ReminderPref {
    const val ALWAYS = "ALWAYS"
    const val YES    = "YES"
    const val NOPE   = "NOPE"
    const val ASK    = "ASK"
}

// ─────────────────────────────────────────────────────────────
//  UI State
// ─────────────────────────────────────────────────────────────

data class CalendarUiState(
    val events: List<EventEntry>         = emptyList(),
    val tasks: List<TaskEntry>           = emptyList(),
    val selectedDate: Long               = System.currentTimeMillis(),
    val isAcademicMode: Boolean          = false,
    val isLoading: Boolean               = false,
    val isScanning: Boolean              = false,
    val offlineModeEnabled: Boolean      = false,
    val syncResults: List<SyncResult>    = emptyList(),
    val errorMessage: String?            = null,
    val availableConfigs: List<AiConfig> = emptyList(),
    val currentConfig: String            = "",
    val attachedImage: Bitmap?           = null,
    val aiReminderPreference: String     = ReminderPref.ASK,
    /**
     * Preserved when the AI parse fails so [retryWithRawJson] can
     * include the bad response in a self-correction follow-up.
     */
    val rawAiFailureResponse: String?    = null,
    /**
     * The original user prompt, kept so a retry can re-include it
     * alongside the bad raw JSON for better self-correction context.
     */
    val lastUserPrompt: String           = "",
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: EventRepository,
    private val chatRepository: ChatRepository,
    private val aiSettingsManager: AiSettingsManager,
    private val syncUseCase: SyncImageToCalendarUseCase,
    private val alarmScheduler: CalendarAlarmScheduler,
    private val taskAlarmScheduler: TaskAlarmScheduler,
    private val settingsRepository: com.frerox.toolz.data.settings.SettingsRepository,
    private val moshi: Moshi,
) : ViewModel() {

    // ── Internal mutable state (pure VM-owned fields) ─────────────────────
    private val _uiState = MutableStateFlow(CalendarUiState())

    // ── AI config flow — loaded once on IO, not re-read on every DB event ─
    /**
     * Emits the current AI config label whenever [_uiState] changes in a way
     * that could affect the selected config (e.g. after [switchAiConfig]).
     *
     * Runs on [Dispatchers.IO] so SharedPreferences reads never block the
     * main thread.
     */
    private val _configFlow: Flow<Triple<List<AiConfig>, String, String>> = _uiState
        .map { it.currentConfig }           // only recompute when the config name changes
        .distinctUntilChanged()
        .map {
            withContext(Dispatchers.IO) {
                val configs         = aiSettingsManager.getSavedConfigs()
                val currentProvider = aiSettingsManager.getAiProvider() ?: "Gemini"
                val currentModel    = aiSettingsManager.getSelectedModel()
                val currentKey      = aiSettingsManager.getRawApiKey(currentProvider)
                val matchedName     = configs.find {
                    it.provider == currentProvider &&
                            it.model == currentModel &&
                            it.apiKey == currentKey
                }?.name ?: currentProvider
                Triple(configs, matchedName, currentProvider)
            }
        }

    /** Public immutable state combining DB streams + internal state + config. */
    val uiState: StateFlow<CalendarUiState> = combine(
        repository.getAllEvents(),
        repository.getTasksWithDueDate(),
        settingsRepository.offlineModeEnabled,
        _uiState,
        _configFlow,
    ) { events, tasks, offline, internal, (configs, matchedName, _) ->

        val filteredEvents = if (internal.isAcademicMode) {
            events.filter { it.eventType == "EXAM" || it.eventType == "EVALUATION" }
        } else {
            events
        }

        internal.copy(
            events           = filteredEvents,
            tasks            = tasks,
            offlineModeEnabled = offline,
            availableConfigs = configs,
            currentConfig    = matchedName,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    // ─────────────────────────────────────────────────────────
    //  Date navigation
    // ─────────────────────────────────────────────────────────

    fun onDateSelected(timestamp: Long) {
        _uiState.update { it.copy(selectedDate = timestamp) }
    }

    fun setDate(year: Int, month: Int) {
        _uiState.update {
            // CAL-P0-02: DAY=1 first, then set, then clamp (delegated for testability).
            it.copy(
                selectedDate = com.frerox.toolz.util.CalendarUtils.setYearMonthClamped(
                    it.selectedDate, year, month
                )
            )
        }
    }

    fun nextMonth() {
        _uiState.update {
            // CAL-P0-02: Jan-31 +1mo -> Feb-28, never Mar-03.
            it.copy(selectedDate = com.frerox.toolz.util.CalendarUtils.addMonthsClamped(it.selectedDate, 1))
        }
    }

    fun previousMonth() {
        _uiState.update {
            it.copy(selectedDate = com.frerox.toolz.util.CalendarUtils.addMonthsClamped(it.selectedDate, -1))
        }
    }

    fun goToToday() {
        _uiState.update { it.copy(selectedDate = System.currentTimeMillis()) }
    }

    // ─────────────────────────────────────────────────────────
    //  UI toggles
    // ─────────────────────────────────────────────────────────

    fun toggleAcademicMode() {
        _uiState.update { it.copy(isAcademicMode = !it.isAcademicMode) }
    }

    fun setAttachedImage(bitmap: Bitmap?) {
        _uiState.update { it.copy(attachedImage = bitmap) }
    }

    fun setAiReminderPreference(pref: String) {
        _uiState.update { it.copy(aiReminderPreference = pref) }
    }

    fun switchAiConfig(config: AiConfig) {
        aiSettingsManager.applyConfig(config)
        // Trigger _configFlow by updating currentConfig; the flow will read the
        // new settings from disk and update availableConfigs + currentConfig.
        _uiState.update { it.copy(currentConfig = config.name) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null, rawAiFailureResponse = null) }
    }

    // ─────────────────────────────────────────────────────────
    //  AI prompt processing
    // ─────────────────────────────────────────────────────────

    /**
     * Send [prompt] (and optionally an attached image) to the AI and parse
     * the returned JSON into a list of [SyncResult]s for the user to confirm.
     *
     * @param prompt       What the user typed in the AI sheet.
     * @param retryRawJson Non-null when this is an automatic retry after a
     *                     parse failure; the bad JSON is embedded in the
     *                     follow-up so the model can self-correct.
     */
    fun processAiPrompt(prompt: String, retryRawJson: String? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading            = true,
                    isScanning           = true,
                    errorMessage         = null,
                    rawAiFailureResponse = null,
                    lastUserPrompt       = prompt.ifBlank { it.lastUserPrompt },
                )
            }

            // CAL-P1-02.6: offline gate — never surface raw network errors in offline mode.
            if (_uiState.value.offlineModeEnabled) {
                handleAiFailure("Offline mode is on — AI parsing isn't available. Add the event manually.")
                return@launch
            }

            // CAL-P1-02: prompt needs existing events; fetch once on IO (not per emission).
            val existing: List<EventEntry> = try {
                withContext(Dispatchers.IO) {
                    repository.getAllEventsSync()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Existing-events fetch failed, continuing without", e)
                emptyList()
            }
            val systemPrompt = buildSystemPrompt(existing)
            val userContent  = buildUserContent(
                prompt      = prompt.ifBlank { _uiState.value.lastUserPrompt },
                retryRawJson = retryRawJson,
            )
            val fullPrompt   = "$systemPrompt\n\n$userContent"

            try {
                // CAL-P1-01: pass through the USER-selected model (never hardcoded).
                // CAL-P1-02.4: collect the FULL response — .first() truncates streaming chats.
                val selectedModel = withContext(Dispatchers.IO) {
                    runCatching { aiSettingsManager.getSelectedModel() }.getOrNull()
                }
                val sb = StringBuilder()
                var lastError: String? = null
                chatRepository
                    .getChatResponse(
                        prompt        = fullPrompt,
                        history       = emptyList(),
                        image         = _uiState.value.attachedImage,
                        modelOverride = selectedModel?.takeIf { it.isNotBlank() }
                    )
                    .collect { result ->
                        result
                            .onSuccess { chunk -> sb.append(chunk.text) }
                            .onFailure { e -> lastError = e.message }
                    }

                val fullText = sb.toString()
                if (fullText.isNotBlank()) {
                    handleAiSuccess(fullText)
                } else {
                    handleAiFailure(lastError)
                }

            } catch (e: Exception) {
                handleAiFailure(e.message)
            }
        }
    }

    /**
     * Retry the last AI request, embedding the previously-bad raw JSON in the
     * follow-up prompt so the model can identify and correct its mistake.
     */
    fun retryWithRawJson() {
        val raw = _uiState.value.rawAiFailureResponse ?: return
        processAiPrompt(
            prompt       = _uiState.value.lastUserPrompt,
            retryRawJson = raw,
        )
    }

    // ─────────────────────────────────────────────────────────
    //  Sync result management
    // ─────────────────────────────────────────────────────────

    fun updateSyncResult(index: Int, updatedEvent: EventEntry) {
        _uiState.update { state ->
            val list = state.syncResults.toMutableList()
            if (index in list.indices) {
                list[index] = when (val r = list[index]) {
                    is SyncResult.New        -> SyncResult.New(updatedEvent)
                    is SyncResult.Reschedule -> r.copy(updated = updatedEvent)
                }
            }
            state.copy(syncResults = list)
        }
    }

    fun removeSyncResult(index: Int) {
        _uiState.update { state ->
            state.copy(
                syncResults = state.syncResults.toMutableList().apply {
                    if (index in indices) removeAt(index)
                }
            )
        }
    }

    fun confirmSync() {
        viewModelScope.launch {
            val pref = _uiState.value.aiReminderPreference

            _uiState.value.syncResults.forEach { result ->
                val raw = when (result) {
                    is SyncResult.New        -> result.event
                    is SyncResult.Reschedule -> result.updated
                }

                val event = when (pref) {
                    ReminderPref.ALWAYS,
                    ReminderPref.YES  -> raw.copy(remindersEnabled = true)
                    ReminderPref.NOPE -> raw.copy(remindersEnabled = false)
                    // ReminderPref.ASK — respect whatever value was set on the event
                    else              -> raw
                }

                if (result is SyncResult.New) {
                    val insertedId = repository.insertEvent(event)
                    if (event.remindersEnabled) {
                        alarmScheduler.scheduleEventReminders(event.copy(id = insertedId.toInt()))
                    }
                } else {
                    repository.updateEvent(event)
                    if (event.remindersEnabled) alarmScheduler.scheduleEventReminders(event)
                    else                        alarmScheduler.cancelEventReminders(event)
                }
            }

            _uiState.update { it.copy(syncResults = emptyList(), attachedImage = null) }
        }
    }

    fun cancelSync() {
        _uiState.update { it.copy(syncResults = emptyList(), attachedImage = null) }
    }

    // ─────────────────────────────────────────────────────────
    //  CRUD
    // ─────────────────────────────────────────────────────────

    /**
     * Inserts a new event.
     *
     * [timeMillis] is the **fully-combined** date+time timestamp produced by
     * [AddEventDialog] — the dialog is now responsible for merging the
     * chosen date and time into one value before calling this function.
     * The ViewModel must NOT re-merge with [CalendarUiState.selectedDate],
     * which would silently overwrite the user's chosen date.
     */
    fun addManualEvent(
        title: String,
        description: String?,
        timeMillis: Long,
        type: String,
        color: String,
        reminders: Boolean,
    ) {
        // CAL-P1-04: past + reminders=true must warn, never silently skip.
        if (reminders && timeMillis <= System.currentTimeMillis() + 60_000L) {
            _uiState.update {
                it.copy(errorMessage = "That time is in the past — reminders won't fire. Pick a future time.")
            }
        }
        viewModelScope.launch {
            val event = EventEntry(
                title            = title.trim(),
                description      = description?.trim(),
                timestamp        = timeMillis,  // ← use directly; date+time already combined
                eventType        = type,
                subjectColor     = color,
                remindersEnabled = reminders,
            )
            try {
                val id = repository.insertEvent(event)
                if (reminders) {
                    alarmScheduler.scheduleEventReminders(event.copy(id = id.toInt()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "addManualEvent failed", e)
                _uiState.update { it.copy(errorMessage = "Couldn't save the event. Please try again.") }
            }
        }
    }

    fun updateEvent(event: EventEntry) {
        viewModelScope.launch {
            try {
                repository.updateEvent(event)
                // CAL-P0-04: cancel-first is inside scheduleEventReminders, but an
                // update to past/disabled must actively cancel (no stale alarm).
                if (event.remindersEnabled && !event.isCompleted) {
                    alarmScheduler.scheduleEventReminders(event)
                } else {
                    alarmScheduler.cancelEventReminders(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateEvent failed for ${event.id}", e)
                _uiState.update { it.copy(errorMessage = "Couldn't update the event. Please try again.") }
            }
        }
    }

    fun toggleEventCompletion(event: EventEntry) {
        viewModelScope.launch {
            try {
                val updated = event.copy(isCompleted = !event.isCompleted)
                repository.updateEvent(updated)
                when {
                    updated.isCompleted      -> alarmScheduler.cancelEventReminders(updated)
                    updated.remindersEnabled -> alarmScheduler.scheduleEventReminders(updated)
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleEventCompletion failed for ${event.id}", e)
                _uiState.update { it.copy(errorMessage = "Couldn't update the event. Please try again.") }
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
                taskAlarmScheduler.cancelReminder(updated)
            } else {
                taskAlarmScheduler.scheduleReminder(updated)
            }
        }
    }

    fun deleteEvent(event: EventEntry) {
        viewModelScope.launch {
            try {
                repository.deleteEvent(event.id)
                alarmScheduler.cancelEventReminders(event)
            } catch (e: Exception) {
                Log.e(TAG, "deleteEvent failed for ${event.id}", e)
                _uiState.update { it.copy(errorMessage = "Couldn't delete the event. Please try again.") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────

    /**
     * Builds a rich, timezone-aware system prompt.
     *
     * - Passes the device's **timezone ID** so the AI never guesses offset.
     * - Passes an **ISO-8601 timestamp** alongside the human-readable string.
     * - Provides the **full current week** (Mon–Sun) for unambiguous relative days.
     * - Provides the **next 14 days** as explicit reference dates.
     * - Passes **existing event titles + dates** for duplicate/conflict awareness.
     * - Includes worked examples for the most common failure modes.
     *
     * CAL-P1-02: implements what this doc promises (tz/ISO/week/existing).
     */
    private fun buildSystemPrompt(existing: List<EventEntry> = emptyList()): String {
        val tz = TimeZone.getDefault()
        val now = Calendar.getInstance(tz)
        val humanFmt = SimpleDateFormat("EEEE, MMMM dd, yyyy, h:mm a", Locale.US)
        val nowHuman = humanFmt.format(now.time)
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = tz
        }
        val nowIso = isoFmt.format(now.time)
        val utcOffset = formatUtcOffset(tz)

        // Full current week Mon–Sun.
        val weekStart = (now.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayFmt = SimpleDateFormat("EEEE yyyy-MM-dd", Locale.US)
        val weekLines = (0..6).joinToString("\n") { i ->
            val d = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            "- ${dayFmt.format(d.time)}"
        }
        // Next 14 days explicit reference.
        val refFmt = SimpleDateFormat("EEEE yyyy-MM-dd", Locale.US)
        val next14 = (0..13).joinToString("\n") { i ->
            val d = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            "- ${refFmt.format(d.time)}"
        }
        val existingLines = existing.take(50).joinToString("\n") { e ->
            val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = tz }
            "- ${e.title} @ ${try { f.format(Date(e.timestamp)) } catch (_: Exception) { "?" }}"
        }.ifBlank { "(none)" }

        return """
You are the core Calendar Parsing Engine for the "Toolz" utility ecosystem. Your sole purpose is to analyze natural language user requests, extract event metrics, and format them into a strict JSON payload for system execution.

### CRITICAL CONTEXT
- Current Reference Date/Time: $nowHuman
- Current ISO-8601: $nowIso
- Device Timezone ID: ${tz.id} ($utcOffset) — resolve ALL relative times in this zone.
- "Today" means ${dayFmt.format(now.time)} in ${tz.id}.

### CURRENT WEEK (Monday-first):
$weekLines

### NEXT 14 DAYS REFERENCE:
$next14

### EXISTING EVENTS (avoid duplicates, detect conflicts):
$existingLines

### STRICT OPERATIONAL RULES
1. OUTPUT ONLY JSON. Do not include introductory text, conversational pleasantries, markdown blocks (other than the raw JSON), or concluding remarks.
2. DATE MATHEMATICS: Use the Current Reference Date to resolve all relative time statements ("tomorrow", "next Friday", "in 3 hours", "at 2pm"). "next Friday" = the Friday of NEXT week, not this week if today is past Friday.
3. TIMEZONE: Emit "start_time_iso" in LOCAL wall time (YYYY-MM-DDTHH:MM:SS, no Z suffix) as it appears in ${tz.id}.
4. IMPLICIT DURATION: If the user does not specify an end time or duration, default "duration_minutes" to 60.
5. UNRESOLVABLE INPUT: If the input contains absolutely no date or event intent, return an empty JSON object: {}.

### EXPECTED OUTPUT SCHEMA
{
  "title": "String (Clear, concise title of the event)",
  "start_time_iso": "String (ISO 8601 format: YYYY-MM-DDTHH:MM:SS)",
  "duration_minutes": Integer,
  "description": "String (Any extra context, location, or notes extracted, or empty string)"
}

### FEW-SHOT GOLDEN EXAMPLES

User: "i have a job interview tomorrow at 2pm"
AI:
{
  "title": "Job Interview",
  "start_time_iso": "2026-05-18T14:00:00",
  "duration_minutes": 60,
  "description": "Added via Toolz AI"
}

User: "Remind me to study physics with Omar this Friday from 4 to 6 PM at the café"
AI:
{
  "title": "Study Physics with Omar",
  "start_time_iso": "2026-05-22T16:00:00",
  "duration_minutes": 120,
  "description": "Location: Café"
}

User: "Gym in 30 minutes"
AI:
{
  "title": "Gym",
  "start_time_iso": "2026-05-17T22:12:00",
  "duration_minutes": 60,
  "description": ""
}
        """.trimIndent()
    }

    /**
     * Constructs the user-facing content block.
     *
     * When [retryRawJson] is non-null, the original [prompt] **and** the bad
     * JSON are both embedded so the model has full context for self-correction.
     */
    private fun buildUserContent(prompt: String, retryRawJson: String?): String {
        val base = if (prompt.isBlank()) {
            "Extract all events and schedules from the attached image."
        } else {
            "INPUT TO PROCESS:\n$prompt"
        }

        return if (retryRawJson != null) {
            """
$base

RETRY NOTE: Your previous response could not be parsed as valid JSON.
Previous bad response (correct this):
$retryRawJson

Please output ONLY the corrected raw JSON array now. No markdown, no prose.
            """.trimIndent()
        } else {
            base
        }
    }

    private suspend fun handleAiSuccess(rawResponse: String) {
        try {
            val rawEvents = withContext(Dispatchers.Default) { parseAiJson(rawResponse) }

            if (rawEvents.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading   = false,
                        isScanning  = false,
                        syncResults = emptyList(),
                        // CAL-P1-02.5: keep preview only until confirm/cancel — failed parse clears image.
                        attachedImage = null,
                        errorMessage = "No events were found. Try rephrasing or adding more detail.",
                    )
                }
                return
            }

            // CAL-P1-02: validate every event on the unified AiCalendarEvent model.
            val warnings = mutableListOf<String>()
            val events = rawEvents.map { validateAiCalendarEvent(it, warnings) }

            val (syncResults, syncWarnings) = syncUseCase.processAiCalendarEventsWithWarnings(events)
            warnings.addAll(syncWarnings)

            _uiState.update {
                it.copy(
                    isLoading   = false,
                    isScanning  = false,
                    syncResults = syncResults,
                    errorMessage = warnings.takeIf { w -> w.isNotEmpty() }?.joinToString("\n"),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing error: ${e.message} | Raw: $rawResponse")
            _uiState.update {
                it.copy(
                    isLoading            = false,
                    isScanning           = false,
                    // CAL-P1-02.5: failed parse clears the preview image.
                    attachedImage        = null,
                    errorMessage         = "Couldn't parse the AI response. Tap Retry to try again.",
                    rawAiFailureResponse = rawResponse,
                )
            }
        }
    }

    private fun handleAiFailure(message: String?) {
        // CAL-P1-02.6: map raw network errors to user strings — never leak e.message verbatim.
        val userMessage = when {
            message.isNullOrBlank() -> "AI request failed. Please check your connection and API key."
            message.contains("timeout", ignoreCase = true) ->
                "AI request timed out. Check your connection and try again."
            message.contains("offline", ignoreCase = true) ->
                "You're offline — AI parsing isn't available. Add the event manually."
            message.contains("401", ignoreCase = true) || message.contains("unauthorized", ignoreCase = true) ||
                message.contains("api key", ignoreCase = true) || message.contains("apikey", ignoreCase = true) ->
                "AI request rejected — check your API key in AI settings."
            message.contains("429", ignoreCase = true) || message.contains("rate", ignoreCase = true) ->
                "AI is rate-limited right now. Wait a minute and tap Retry."
            else -> "AI request failed. Please check your connection and API key."
        }
        Log.w(TAG, "AI failure (mapped): $message -> $userMessage")
        _uiState.update {
            it.copy(
                isLoading    = false,
                isScanning   = false,
                errorMessage = userMessage,
            )
        }
    }

    /**
     * Robustly parses the AI's JSON output.
     *
     * Handles: leading/trailing whitespace, markdown fences, BOM characters,
     * single-object responses (not wrapped in an array), NDJSON (`}{` sequences),
     * mixed 10/13-digit timestamps. Unknown fields ignored, null rows filtered.
     *
     * CAL-P1-02: balanced-brace scan so trailing prose can't corrupt the payload.
     */
    private fun parseAiJson(raw: String): List<AiCalendarEvent> {
        val cleaned = raw
            .trimStart('\uFEFF')                 // strip BOM
            .replace(Regex("```[a-z]*"), "")     // strip ```json
            .replace("```", "")
            .trim()

        if (cleaned == "{}" || cleaned.isBlank()) return emptyList()

        // CAL-P1-02: NDJSON (`}\s*{`) -> wrap as array elements.
        val ndjsonFixed = Regex("""\}\s*\{""").replace(cleaned) { "},{ " }

        // Extract outermost JSON array if present
        val arrayStart = ndjsonFixed.indexOf('[')
        val arrayEnd   = ndjsonFixed.lastIndexOf(']')
        if (arrayStart != -1 && arrayEnd > arrayStart) {
            return parseJsonArray(ndjsonFixed.substring(arrayStart, arrayEnd + 1))
        }

        // Balanced-brace scan: collect every top-level {...} object.
        val objects = extractTopLevelObjects(ndjsonFixed)
        if (objects.isNotEmpty()) {
            return parseJsonArray("[${objects.joinToString(",")}]")
        }

        // Handle single object {} as per instructions
        val objStart = ndjsonFixed.indexOf('{')
        val objEnd   = ndjsonFixed.lastIndexOf('}')
        if (objStart != -1 && objEnd > objStart) {
            return parseJsonArray("[${ndjsonFixed.substring(objStart, objEnd + 1)}]")
        }

        throw IllegalArgumentException("No JSON structure found in AI response.")
    }

    /** Extracts top-level balanced `{...}` objects, respecting strings/escapes. */
    private fun extractTopLevelObjects(s: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inStr = false
        var esc = false
        for (i in s.indices) {
            val c = s[i]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') inStr = false
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start != -1) {
                        out.add(s.substring(start, i + 1))
                        start = -1
                    }
                    if (depth < 0) depth = 0
                }
            }
        }
        return out
    }

    // Nullable DTO: filters rows missing title/time instead of failing the batch.
    private data class AiCalendarEventDto(
        val title: String? = null,
        val start_time_iso: String? = null,
        val duration_minutes: Int? = null,
        val description: String? = null
    )

    private fun parseJsonArray(json: String): List<AiCalendarEvent> {
        return try {
            val type    = Types.newParameterizedType(List::class.java, AiCalendarEventDto::class.java)
            val adapter = moshi.adapter<List<AiCalendarEventDto>>(type)
            (adapter.fromJson(json) ?: emptyList())
                .filter { !it.title.isNullOrBlank() && !it.start_time_iso.isNullOrBlank() }
                .map {
                    AiCalendarEvent(
                        title = it.title!!.trim(),
                        start_time_iso = it.start_time_iso!!,
                        duration_minutes = it.duration_minutes ?: 60,
                        description = it.description ?: ""
                    )
                }
        } catch (_: Exception) {
            // Legacy strict path for payloads the DTO can't read.
            val type    = Types.newParameterizedType(List::class.java, AiCalendarEvent::class.java)
            val adapter = moshi.adapter<List<AiCalendarEvent>>(type)
            adapter.fromJson(json) ?: emptyList()
        }
    }

    /**
     * CAL-P1-02: validates the unified [AiCalendarEvent] model.
     * - Parses ISO wall time in device tz (lenient=false, never fallback to `now`).
     * - Numeric guard is magnitude-based (`ts < 1e12` s->ms), never string-length.
     * - Out-of-range clamps with a WARNING surfaced to the user (never silent tomorrow-09:00).
     * - Sanitizes duration (1..1439, default 60).
     */
    private fun validateAiCalendarEvent(event: AiCalendarEvent, warnings: MutableList<String>): AiCalendarEvent {
        val tz = TimeZone.getDefault()
        val now = System.currentTimeMillis()
        var ts = parseIsoToMillis(event.start_time_iso, tz)
            ?: run {
                // Unparseable -> skip-neutral: place at next rounded hour + warn (never `now`).
                val fallback = Calendar.getInstance(tz).apply {
                    timeInMillis = now
                    add(Calendar.HOUR_OF_DAY, 1)
                    set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                warnings.add("“${event.title}”: date was unclear — placed at next hour. Please verify.")
                Log.w(TAG, "Unparseable start_time_iso='${event.start_time_iso}', fallback=$fallback")
                fallback
            }

        // Magnitude guard (epoch seconds vs millis) — not string length.
        if (ts in 1..999_999_999_99L) {
            ts *= 1_000L
            Log.d(TAG, "Converted epoch-seconds → ms: $ts")
        }

        val minTs = Calendar.getInstance(tz).apply { add(Calendar.YEAR, -MAX_YEARS_IN_PAST) }.timeInMillis
        val maxTs = Calendar.getInstance(tz).apply { add(Calendar.YEAR, MAX_YEARS_IN_FUTURE) }.timeInMillis
        var description = event.description
        if (ts < minTs || ts > maxTs) {
            val clamped = Calendar.getInstance(tz).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val fmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
            warnings.add("“${event.title}”: date out of range — moved to ${fmt.format(Date(clamped))}. Please verify.")
            Log.w(TAG, "Timestamp $ts out of [$minTs..$maxTs]; clamped to $clamped with warning.")
            ts = clamped
            description = "[Date adjusted to valid range] $description".trim()
        }

        val duration = event.duration_minutes.coerceIn(1, 1439).let {
            if (event.duration_minutes !in 1..1439) {
                Log.w(TAG, "Duration ${event.duration_minutes} coerced to $it")
                it
            } else it
        }
        val title = event.title.trim().take(200)
        return event.copy(title = title, start_time_iso = event.start_time_iso, duration_minutes = duration, description = description)
            .let { validated ->
                // Re-emit with corrected timestamp encoded back to ISO for the use case.
                validated.copy(start_time_iso = millisToIso(ts, tz))
            }
    }

    private fun parseIsoToMillis(iso: String, tz: TimeZone): Long? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        )
        for (f in formats) {
            try {
                f.isLenient = false
                f.timeZone = tz
                val t = f.parse(iso.trim())?.time ?: continue
                // Magnitude guard for pure-epoch strings that slipped in.
                if (iso.trim().matches(Regex("""\d{9,13}"""))) {
                    var num = iso.trim().toLong()
                    if (num < 1_000_000_000_000L) num *= 1_000L
                    return num
                }
                return t
            } catch (_: Exception) { }
        }
        // Bare epoch digits.
        iso.trim().toLongOrNull()?.let { num ->
            return if (num < 1_000_000_000_000L) num * 1_000L else num
        }
        return null
    }

    private fun millisToIso(millis: Long, tz: TimeZone): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = tz
            isLenient = false
        }.format(Date(millis))
    }

    /**
     * Normalises a parsed [AiEventResult]:
     * - Converts 10-digit (seconds) timestamps to 13-digit milliseconds.
     * - Clamps timestamps that are wildly out of range.
     * - Ensures [AiEventResult.eventType] and [AiEventResult.subjectColor] are valid.
     */
    private fun validateAndFixTimestamp(event: AiEventResult): AiEventResult {
        val tz  = TimeZone.getDefault()
        val now = System.currentTimeMillis()

        var ts = event.timestamp

        if (ts.toString().length <= 10) {
            ts *= 1_000L
            Log.d(TAG, "Converted 10-digit timestamp → ms: $ts")
        }

        val minTs = Calendar.getInstance(tz).apply { add(Calendar.YEAR, -MAX_YEARS_IN_PAST)   }.timeInMillis
        val maxTs = Calendar.getInstance(tz).apply { add(Calendar.YEAR,  MAX_YEARS_IN_FUTURE) }.timeInMillis

        if (ts < minTs || ts > maxTs) {
            Log.w(TAG, "Timestamp $ts out of [$minTs..$maxTs]; defaulting to tomorrow 09:00.")
            ts = Calendar.getInstance(tz).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE,      0)
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val knownTypes = setOf("EXAM", "EVALUATION", "DEADLINE", "BIRTHDAY", "MEETING", "HOLIDAY", "GENERAL")
        val fixedType  = event.eventType?.uppercase()?.takeIf { it in knownTypes } ?: "GENERAL"

        val colorRegex = Regex("^#[0-9A-Fa-f]{6}$")
        val fixedColor = if (event.subjectColor?.matches(colorRegex) == true) event.subjectColor else "#9E9E9E"

        return event.copy(
            timestamp    = ts,
            eventType    = fixedType,
            subjectColor = fixedColor,
        )
    }

    // ─────────────────────────────────────────────────────────
    //  Utilities
    // ─────────────────────────────────────────────────────────

    private fun formatUtcOffset(tz: TimeZone): String {
        val totalMinutes = tz.rawOffset / 60_000
        val sign  = if (totalMinutes >= 0) "+" else "-"
        val abs   = Math.abs(totalMinutes)
        val hours = abs / 60
        val mins  = abs % 60
        return "UTC%s%02d:%02d".format(sign, hours, mins)
    }
}
