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
        _uiState,
        _configFlow,
    ) { events, tasks, internal, (configs, matchedName, _) ->

        val filteredEvents = if (internal.isAcademicMode) {
            events.filter { it.eventType == "EXAM" || it.eventType == "EVALUATION" }
        } else {
            events
        }

        internal.copy(
            events           = filteredEvents,
            tasks            = tasks,
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
            val cal = Calendar.getInstance().apply {
                timeInMillis = it.selectedDate
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            it.copy(selectedDate = cal.timeInMillis)
        }
    }

    fun nextMonth() {
        _uiState.update {
            val cal = Calendar.getInstance().apply { timeInMillis = it.selectedDate }
            cal.add(Calendar.MONTH, 1)
            it.copy(selectedDate = cal.timeInMillis)
        }
    }

    fun previousMonth() {
        _uiState.update {
            val cal = Calendar.getInstance().apply { timeInMillis = it.selectedDate }
            cal.add(Calendar.MONTH, -1)
            it.copy(selectedDate = cal.timeInMillis)
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
                    // Preserve the original prompt across retries
                    lastUserPrompt       = prompt.ifBlank { it.lastUserPrompt },
                )
            }

            val systemPrompt = buildSystemPrompt()
            val userContent  = buildUserContent(
                prompt      = prompt.ifBlank { _uiState.value.lastUserPrompt },
                retryRawJson = retryRawJson,
            )
            val fullPrompt   = "$systemPrompt\n\n$userContent"

            // Use .first() to consume exactly one response regardless of
            // whether the Flow is cold single-shot or could emit multiple items.
            try {
                val result = chatRepository
                    .getChatResponse(
                        prompt  = fullPrompt,
                        history = emptyList(),
                        image   = _uiState.value.attachedImage,
                    )
                    .first()

                result
                    .onSuccess { raw -> handleAiSuccess(raw) }
                    .onFailure { e   -> handleAiFailure(e.message) }

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
        viewModelScope.launch {
            val event = EventEntry(
                title            = title.trim(),
                description      = description?.trim(),
                timestamp        = timeMillis,  // ← use directly; date+time already combined
                eventType        = type,
                subjectColor     = color,
                remindersEnabled = reminders,
            )
            val id = repository.insertEvent(event)
            if (reminders) {
                alarmScheduler.scheduleEventReminders(event.copy(id = id.toInt()))
            }
        }
    }

    fun updateEvent(event: EventEntry) {
        viewModelScope.launch {
            repository.updateEvent(event)
            if (event.remindersEnabled && !event.isCompleted) {
                alarmScheduler.scheduleEventReminders(event)
            } else {
                alarmScheduler.cancelEventReminders(event)
            }
        }
    }

    fun toggleEventCompletion(event: EventEntry) {
        viewModelScope.launch {
            val updated = event.copy(isCompleted = !event.isCompleted)
            repository.updateEvent(updated)
            when {
                updated.isCompleted      -> alarmScheduler.cancelEventReminders(updated)
                updated.remindersEnabled -> alarmScheduler.scheduleEventReminders(updated)
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
            repository.deleteEvent(event.id)
            alarmScheduler.cancelEventReminders(event)
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
     */
    private fun buildSystemPrompt(): String {
        val tz       = TimeZone.getDefault()
        val tzId     = tz.id
        val tzOffset = formatUtcOffset(tz)

        val now      = Calendar.getInstance(tz)
        val isoFmt   = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = tz }
        val humanFmt = SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' HH:mm", Locale.US).apply { timeZone = tz }
        val dateFmt  = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US).apply { timeZone = tz }
        val shortFmt = SimpleDateFormat("EEE MMM dd", Locale.US).apply { timeZone = tz }

        val nowHuman = humanFmt.format(now.time)
        val nowIso   = isoFmt.format(now.time)
        val selDate  = dateFmt.format(Date(_uiState.value.selectedDate))

        // Week containing the selected date (Mon → Sun).
        val weekCal = Calendar.getInstance(tz).apply {
            timeInMillis = _uiState.value.selectedDate
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val weekDays = (0..6).map { offset ->
            val d = Calendar.getInstance(tz).apply {
                timeInMillis = weekCal.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            "${shortFmt.format(d.time)} → ${isoFmt.format(d.time)}"
        }

        // Next 14 days reference grid.
        val next14 = (0..13).map { offset ->
            val d = Calendar.getInstance(tz).apply {
                timeInMillis = now.timeInMillis
                add(Calendar.DAY_OF_YEAR, offset)
            }
            "${shortFmt.format(d.time)} → ${isoFmt.format(d.time)}"
        }

        // Existing events for conflict awareness.
        val existingEventsSnippet = _uiState.value.events
            .take(30)
            .joinToString("\n") { ev ->
                "  - \"${ev.title}\" on ${dateFmt.format(Date(ev.timestamp))}"
            }
            .ifBlank { "  (none)" }

        return """
SYSTEM — Calendar Event Parser
You are a high-precision calendar event parser for the "Toolz" productivity app.
Your ONLY output must be a raw JSON array.  No markdown.  No prose.  No code fences.

═══════════════════════════════
DEVICE TIME CONTEXT
═══════════════════════════════
Current moment (ISO-8601) : $nowIso
Current moment (human)    : $nowHuman
Timezone ID               : $tzId
UTC offset                : $tzOffset
Calendar view date        : $selDate

Week containing "$selDate" (Mon–Sun):
${weekDays.joinToString("\n")}

Next 14 days reference grid (use these ISO timestamps to anchor relative terms):
${next14.joinToString("\n")}

Already-existing events (do NOT duplicate these unless the user explicitly asks):
$existingEventsSnippet

═══════════════════════════════
DATE & TIME RESOLUTION RULES
═══════════════════════════════
1. TIMEZONE: All output timestamps MUST represent times in timezone "$tzId".
   Never default to UTC.  Never shift the hour.

2. ANCHOR: Relative terms ("next Monday", "this Friday", "the 15th") are
   anchored to "$selDate" UNLESS the user says "today", "right now", or names
   a different month/year explicitly.

3. "THIS [DAY]"  → the occurrence of that weekday within the SAME calendar
   week as "$selDate" shown in the week grid above.

4. "NEXT [DAY]"  → the FIRST occurrence of that weekday STRICTLY AFTER
   "$selDate".  If "$selDate" is already that weekday, go to the following week.

5. BARE DATE ("the 15th", "Jan 5"):  choose the nearest future occurrence
   relative to "$selDate".  Prefer the current month; if already past, use
   the next month.

6. YEAR RESOLUTION: When no year is given, pick the year that puts the date
   closest to (and after) "$selDate".

7. MISSING TIME: Default to 09:00 in timezone "$tzId".

8. TIMESTAMP FORMAT: Output MUST be a 13-digit Unix milliseconds Long.
   Correct: 1741257000000   Wrong: 1741257000 (10-digit seconds).

9. SANITY CHECK: If a resolved timestamp falls more than $MAX_YEARS_IN_PAST year in
   the past or more than $MAX_YEARS_IN_FUTURE years in the future, re-evaluate
   before outputting.

═══════════════════════════════
OUTPUT CONTRACT
═══════════════════════════════
• Output ONLY a raw JSON array — no ```json, no preamble, no explanations.
• If no events are found, output exactly: []
• Each element must match this schema exactly:

[
  {
    "title"       : "string — clear, concise event name",
    "timestamp"   : 1234567890000,
    "eventType"   : "EXAM | EVALUATION | DEADLINE | BIRTHDAY | MEETING | HOLIDAY | GENERAL",
    "subjectColor": "#RRGGBB — pick from palette below",
    "description" : "string | null — location, notes, or any extra detail"
  }
]

Color palette:
  EXAM / EVALUATION  →  #FF5252
  DEADLINE           →  #FF6D00
  BIRTHDAY           →  #E040FB
  MEETING            →  #448AFF
  HOLIDAY            →  #00BFA5
  GENERAL            →  #9E9E9E

═══════════════════════════════
EXAMPLES OF CORRECT OUTPUT
═══════════════════════════════
Input: "Math exam next Monday at 10am"
(Assuming $selDate is Wednesday March 19 2025, timezone Africa/Algiers UTC+01:00)
→ [{"title":"Math Exam","timestamp":1742464800000,"eventType":"EXAM","subjectColor":"#FF5252","description":null}]

Input: "Team standup every day this week at 9am" (same anchor)
→ Emit one entry per day (Mon–Fri) with the correct per-day timestamp.

Input: "birthday party"  (no date at all)
→ []

═══════════════════════════════
CRITICAL RULES (never break)
═══════════════════════════════
• NEVER invent a date if none is inferable from the text.
• NEVER output seconds-based (10-digit) timestamps.
• NEVER wrap output in markdown code fences.
• NEVER emit duplicate events that already exist in the "existing events" list
  unless the user explicitly requests a new one with the same name.
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
            val events = withContext(Dispatchers.Default) { parseAiJson(rawResponse) }

            if (events.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading   = false,
                        isScanning  = false,
                        syncResults = emptyList(),
                        errorMessage = "No events were found. Try rephrasing or adding more detail.",
                    )
                }
                return
            }

            val validated   = withContext(Dispatchers.Default) { events.map { validateAndFixTimestamp(it) } }
            val syncResults = syncUseCase.processAiEvents(validated)

            _uiState.update {
                it.copy(
                    isLoading   = false,
                    isScanning  = false,
                    syncResults = syncResults,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing error: ${e.message} | Raw: $rawResponse")
            _uiState.update {
                it.copy(
                    isLoading            = false,
                    isScanning           = false,
                    errorMessage         = "Couldn't parse the AI response. Tap Retry to try again.",
                    rawAiFailureResponse = rawResponse,
                )
            }
        }
    }

    private fun handleAiFailure(message: String?) {
        _uiState.update {
            it.copy(
                isLoading    = false,
                isScanning   = false,
                errorMessage = message
                    ?.takeIf { it.isNotBlank() }
                    ?: "AI request failed. Please check your connection and API key.",
            )
        }
    }

    /**
     * Robustly parses the AI's JSON output.
     *
     * Handles: leading/trailing whitespace, markdown fences, BOM characters,
     * single-object responses (not wrapped in an array), mixed 10/13-digit timestamps.
     */
    private fun parseAiJson(raw: String): List<AiEventResult> {
        val cleaned = raw
            .trimStart('\uFEFF')                 // strip BOM
            .replace(Regex("```[a-z]*"), "")     // strip ```json
            .replace("```", "")
            .trim()

        if (cleaned == "[]" || cleaned.isBlank()) return emptyList()

        // Extract outermost JSON array.
        val arrayStart = cleaned.indexOf('[')
        val arrayEnd   = cleaned.lastIndexOf(']')
        if (arrayStart != -1 && arrayEnd > arrayStart) {
            return parseJsonArray(cleaned.substring(arrayStart, arrayEnd + 1))
        }

        // Fallback: single object not wrapped in an array.
        val objStart = cleaned.indexOf('{')
        val objEnd   = cleaned.lastIndexOf('}')
        if (objStart != -1 && objEnd > objStart) {
            Log.w(TAG, "AI returned single object; wrapping in array.")
            return parseJsonArray("[${cleaned.substring(objStart, objEnd + 1)}]")
        }

        throw IllegalArgumentException("No JSON structure found in AI response.")
    }

    private fun parseJsonArray(json: String): List<AiEventResult> {
        val type    = Types.newParameterizedType(List::class.java, AiEventResult::class.java)
        val adapter = moshi.adapter<List<AiEventResult>>(type)
        return adapter.fromJson(json) ?: emptyList()
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
