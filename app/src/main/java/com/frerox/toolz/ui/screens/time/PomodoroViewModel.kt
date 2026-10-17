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

package com.frerox.toolz.ui.screens.time

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.MessageContent
import com.frerox.toolz.data.ai.OpenAiMessage
import com.frerox.toolz.data.ai.OpenAiRequest
import com.frerox.toolz.data.ai.OpenAiService
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ToolService
import com.frerox.toolz.service.nextModeAfterWork
import com.frerox.toolz.service.nextPomodoroMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class PomodoroMode(val label: String, val supportingLabel: String) {
    WORK("Focus", "Deep work"),
    SHORT_BREAK("Short break", "Reset"),
    LONG_BREAK("Long break", "Recover")
}

data class PomodoroState(
    val remainingTime: Long = 25 * 60 * 1000L,
    val totalTime: Long = 25 * 60 * 1000L,
    val mode: PomodoroMode = PomodoroMode.WORK,
    val isRunning: Boolean = false,
    val sessionsCompleted: Int = 0,
    val sessionsGoal: Int = 8,
    val isFinished: Boolean = false,
    val autoStartNext: Boolean = false,
    val keepScreenOn: Boolean = true,
    
    // New Settings
    val workMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val ringtoneUri: String? = null,
    val showQuotes: Boolean = true,
    val quotes: String = "",
    val isFormattingQuotes: Boolean = false,
    val offlineMode: Boolean = false,
    val gradualVolume: Boolean = false,
    val quoteError: String? = null,
    val showStatusPill: Boolean = true,
)

/** P-P0-01: UI shares the ONE phase-truth function with Service (never a second %4). */
fun nextPhaseIsLongBreak(persistedCompleted: Int): Boolean =
    nextModeAfterWork(persistedCompleted) == "LONG_BREAK"

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val aiSettingsManager: AiSettingsManager,
    private val openAiService: OpenAiService,
    private val gate: com.frerox.toolz.data.developer.SpoofGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PomodoroState())
    val uiState: StateFlow<PomodoroState> = _uiState.asStateFlow()

    private var toolService: ToolService? = null
    private var isBound = false
    // P-P0-04: keep Jobs, cancel before re-collect. No delay-hacks, no stale reads.
    private var pomodoroJobs: List<Job> = emptyList()
    private var lastFinishCount = -1

    // ── DEV-SPOOF: Timers sandbox — pomodoro runs on a RAM ticker, ToolService
    // is never started/bound while the flag is on (no alarm FIFO, no foreground
    // notification, nothing persisted). Rebinds like a fresh open on disarm.
    private var sandboxActiveNow = false
    private var sandboxPomodoroJob: Job? = null

    private val isPomodoroSandboxActive: Boolean
        get() = gate.sandboxed(com.frerox.toolz.data.developer.DevToolId.TIMERS)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // DEV-SPOOF: a bind queued before the sandbox armed can still land
            // here — drop it instead of piping real service flows over fixtures.
            if (sandboxActiveNow) {
                unbindQuietly()
                return
            }
            val binder = service as ToolService.LocalBinder
            toolService = binder.getService()
            isBound = true
            bindPomodoroFlows(binder.getService())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            toolService = null
            isBound = false
        }
    }

    init {
        // P-P0-03: startService() before bind so the service survives UI death
        // (bind-only dies with the activity; started service survives Doze/kill).
        // DEV-SPOOF: skipped entirely while the Timers sandbox is on — the fake
        // pomodoro needs neither the service nor its foreground state.
        if (isPomodoroSandboxActive) {
            enterSandboxImmediately()
        } else {
            sandboxActiveNow = false
            bindToolService()
        }
        viewModelScope.launch {
            gate.active(com.frerox.toolz.data.developer.DevToolId.TIMERS).collect { spoofed ->
                if (spoofed) {
                    if (!sandboxActiveNow) enterSandboxImmediately()
                } else {
                    leaveSandboxToReal()
                }
            }
        }
        observeSettings()
    }

    /** Quiet unbind reused by the guarded connect + sandbox teardown paths. */
    private fun unbindQuietly() {
        try { context.unbindService(connection) } catch (_: Exception) {}
    }

    private fun bindToolService() {
        try {
            val startIntent = Intent(context, ToolService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        } catch (_: Exception) {}
        Intent(context, ToolService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    /** DEV-SPOOF: unbind/stop the service and stage a paused RAM fixture. */
    private fun enterSandboxImmediately() {
        sandboxActiveNow = true
        try { context.unbindService(connection) } catch (_: Exception) {}
        try { context.stopService(Intent(context, ToolService::class.java)) } catch (_: Exception) {}
        isBound = false
        toolService = null
        pomodoroJobs.forEach { try { it.cancel() } catch (_: Exception) {} }
        pomodoroJobs = emptyList()
        // Fixture: staged at the user's current work length, paused at zero sessions.
        val duration = fixtureDurationMs(_uiState.value)
        _uiState.update {
            it.copy(
                remainingTime = duration,
                totalTime = duration,
                mode = PomodoroMode.WORK,
                isRunning = false,
                isFinished = false,
                sessionsCompleted = 0,
            )
        }
        android.util.Log.w(
            "DevSpoof",
            "Timers sandbox active — pomodoro on RAM ticker, ToolService detached",
        )
    }

    /** DEV-SPOOF: drop the fixture and rebind the service like a fresh open. */
    private fun leaveSandboxToReal() {
        if (!sandboxActiveNow) return
        sandboxActiveNow = false
        sandboxPomodoroJob?.cancel()
        sandboxPomodoroJob = null
        // Run state zeroes; settings-driven fields are kept for a flicker-free dial
        // (settings collectors are still live; service flows re-seed on connect).
        _uiState.update {
            it.copy(
                isRunning = false,
                isFinished = false,
                remainingTime = 0L,
                totalTime = 0L,
                sessionsCompleted = 0,
            )
        }
        bindToolService()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine<Any?, PomodoroSettings>(
                settingsRepository.pomodoroWorkMinutes,
                settingsRepository.pomodoroShortBreakMinutes,
                settingsRepository.pomodoroLongBreakMinutes,
                settingsRepository.pomodoroAutoStart,
                settingsRepository.pomodoroKeepScreenOn,
                settingsRepository.pomodoroSessionsGoal,
                settingsRepository.pomodoroRingtoneUri,
                settingsRepository.pomodoroShowQuotes,
                settingsRepository.pomodoroQuotes,
                settingsRepository.offlineModeEnabled,
                settingsRepository.pomodoroGradualVolume,
                settingsRepository.pomodoroShowStatusPill
            ) { values ->
                PomodoroSettings(
                    workMinutes = values[0] as Int,
                    shortBreakMinutes = values[1] as Int,
                    longBreakMinutes = values[2] as Int,
                    autoStart = values[3] as Boolean,
                    keepScreenOn = values[4] as Boolean,
                    sessionsGoal = values[5] as Int,
                    ringtoneUri = values[6] as String?,
                    showQuotes = values[7] as Boolean,
                    quotes = values[8] as String,
                    offlineMode = values[9] as Boolean,
                    gradualVolume = values[10] as Boolean,
                    showStatusPill = values[11] as Boolean
                )
            // FIX (user report settings "stuck"): one throwing flow must never kill
            // this 11-flow combine permanently (frozen goal/durations with no error).
            // Retry with backoff so transient DataStore/AI-settings failures recover.
            }.retryWhen { cause, attempt ->
                android.util.Log.e("PomodoroVM", "observeSettings failed (attempt $attempt)", cause as? Throwable)
                kotlinx.coroutines.delay((1000L * (attempt + 1)).coerceAtMost(10_000L))
                true
            }.collect { settings ->
                _uiState.update { it.copy(
                    workMinutes = settings.workMinutes,
                    shortBreakMinutes = settings.shortBreakMinutes,
                    longBreakMinutes = settings.longBreakMinutes,
                    autoStartNext = settings.autoStart,
                    keepScreenOn = settings.keepScreenOn,
                    sessionsGoal = settings.sessionsGoal,
                    ringtoneUri = settings.ringtoneUri,
                    showQuotes = settings.showQuotes,
                    quotes = settings.quotes,
                    offlineMode = settings.offlineMode,
                    gradualVolume = settings.gradualVolume,
                    showStatusPill = settings.showStatusPill,
                ) }
            }
        }
    }

    private data class PomodoroSettings(
        val workMinutes: Int,
        val shortBreakMinutes: Int,
        val longBreakMinutes: Int,
        val autoStart: Boolean,
        val keepScreenOn: Boolean,
        val sessionsGoal: Int,
        val ringtoneUri: String?,
        val showQuotes: Boolean,
        val quotes: String,
        val offlineMode: Boolean,
        val gradualVolume: Boolean,
        val showStatusPill: Boolean
    )

    private fun bindPomodoroFlows(service: ToolService) {
        // P-P0-04: cancel previous collectors before re-collect (reconnect must not
        // double-fire). P-P2-01: flows are the sole truth — VM never computes durations.
        pomodoroJobs.forEach { try { it.cancel() } catch (_: Exception) {} }
        // Seed lastFinishCount from current service value so a reconnect does NOT
        // re-fire isFinished + sound (no double sound on reconnect).
        lastFinishCount = try { service.pomodoroFinishedCount.value } catch (_: Exception) { 0 }
        if (lastFinishCount < 0) lastFinishCount = 0
        pomodoroJobs = listOf(
            viewModelScope.launch {
                service.pomodoroRemaining
                    .map { it.coerceAtLeast(0L) }
                    .distinctUntilChanged()
                    .collect { remaining ->
                        _uiState.update { it.copy(remainingTime = remaining) }
                    }
            },
            viewModelScope.launch {
                service.pomodoroTotalMs
                    .map { it.coerceAtLeast(1L) }
                    .distinctUntilChanged()
                    .collect { total ->
                        _uiState.update { it.copy(totalTime = total) }
                    }
            },
            viewModelScope.launch {
                service.pomodoroModeState
                    .collect { mode ->
                        _uiState.update {
                            it.copy(
                                mode = mode.toPomodoroMode(),
                                // Clear stale finished flag only when mode actually settles;
                                // running collector below also clears on start.
                                isFinished = if (it.isRunning) false else it.isFinished,
                            )
                        }
                    }
            },
            viewModelScope.launch {
                service.isPomodoroRunning
                    .collect { running ->
                        _uiState.update { it.copy(isRunning = running, isFinished = if (running) false else it.isFinished) }
                    }
            },
            viewModelScope.launch {
                service.pomodoroSessionsDone
                    .map { it.coerceAtLeast(0) }
                    .distinctUntilChanged()
                    .collect { sessions ->
                        _uiState.update { it.copy(sessionsCompleted = sessions) }
                    }
            },
            viewModelScope.launch {
                // P-P0-04: debounced finished event with explicit values. Service owns
                // auto-start + sound — VM only raises the banner flag. NEVER call
                // toggleStartStop() / playRingtone() from stale state here.
                // (StateFlow already conflates; init seed above prevents reconnect re-fire.)
                service.pomodoroFinishedCount
                    .collect { count ->
                        if (lastFinishCount >= 0 && count > lastFinishCount) {
                            _uiState.update { it.copy(isFinished = true) }
                        }
                        lastFinishCount = count
                    }
            },
        )
    }

    fun toggleStartStop() {
        // DEV-SPOOF: sandboxed → start/pause the RAM ticker; no alarm scheduling.
        if (isPomodoroSandboxActive) { toggleSandboxPomodoro(); return }
        val state = _uiState.value
        if (state.isRunning) {
            toolService?.pausePomodoro()
        } else {
            // P-P1-03: stopAlarm() cancels both alarm IDs — safe even when not ringing.
            toolService?.stopAlarm()
            // Service is the sole duration truth; pass remaining as-is (service falls
            // back to durationForMode when <= 0). Never compute here.
            toolService?.startPomodoro(state.remainingTime, state.mode.name)
            _uiState.update { it.copy(isFinished = false) }
        }
    }

    fun selectMode(mode: PomodoroMode) {
        // DEV-SPOOF: sandboxed → restage the fixture for the picked phase.
        if (isPomodoroSandboxActive) {
            val cur = _uiState.value
            if (cur.isRunning) return // as real mode
            val duration = fixtureDurationMs(cur.copy(mode = mode))
            _uiState.value = cur.copy(
                mode = mode,
                remainingTime = duration,
                totalTime = duration,
                isFinished = false,
            )
            return
        }
        // P-P2-01: VM calls service only — flows echo the truth. No optimistic
        // duration math from laggy settings cache (flicker source).
        if (_uiState.value.isRunning) return
        toolService?.stopAlarm()
        toolService?.setPomodoroMode(mode.name)
        _uiState.update { it.copy(isFinished = false) }
    }

    fun setWorkMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setPomodoroWorkMinutes(minutes) }
    }

    fun setShortBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setPomodoroShortBreakMinutes(minutes) }
    }

    fun setLongBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setPomodoroLongBreakMinutes(minutes) }
    }

    fun setSessionsGoal(goal: Int) {
        viewModelScope.launch { settingsRepository.setPomodoroSessionsGoal(goal.coerceIn(1, 12)) }
    }

    fun setAutoStartNext(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPomodoroAutoStart(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPomodoroKeepScreenOn(enabled) }
    }

    fun setGradualVolume(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPomodoroGradualVolume(enabled) }
    }

    fun setShowStatusPill(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPomodoroShowStatusPill(enabled) }
    }

    fun setRingtoneUri(uri: String) {
        // P-P1-02: validate grant before persisting. Revoked/blank URIs fall back
        // to the system default (service also falls back — never crashes).
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmed = uri.trim()
                if (trimmed.isBlank()) {
                    settingsRepository.setPomodoroRingtoneUri("")
                    return@launch
                }
                val parsed = runCatching { Uri.parse(trimmed) }.getOrNull()
                if (parsed == null) {
                    _uiState.update { it.copy(quoteError = null) }
                    return@launch
                }
                val readable = try {
                    context.contentResolver.openFileDescriptor(parsed, "r")?.close()
                    true
                } catch (_: SecurityException) {
                    false
                } catch (_: Exception) {
                    // Non-content schemes (file/default) — let service try + fall back.
                    true
                }
                if (readable) {
                    try {
                        // Persist grant for reboot survival when possible.
                        if (parsed.scheme == "content") {
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    parsed,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                    settingsRepository.setPomodoroRingtoneUri(trimmed)
                } else {
                    // No grant — store blank so service uses the default alarm sound.
                    settingsRepository.setPomodoroRingtoneUri("")
                }
            } catch (_: Exception) {}
        }
    }

    fun setShowQuotes(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPomodoroShowQuotes(enabled) }
    }

    fun setQuotes(quotes: String) {
        viewModelScope.launch { settingsRepository.setPomodoroQuotes(quotes) }
    }

    fun resetQuotes() {
        viewModelScope.launch { settingsRepository.setPomodoroQuotes(SettingsRepository.DEFAULT_POMODORO_QUOTES) }
    }

    fun formatQuotesWithAi() {
        if (_uiState.value.offlineMode) return
        viewModelScope.launch {
            _uiState.update { it.copy(isFormattingQuotes = true, quoteError = null) }
            // P-P2-06: truncate unbounded input before the prompt (blowup guard).
            val currentQuotes = _uiState.value.quotes.take(4000)
            val groqKey = aiSettingsManager.getApiKey("Groq").ifBlank { aiSettingsManager.getApiKey() }

            if (groqKey.isBlank()) {
                _uiState.update { it.copy(isFormattingQuotes = false, quoteError = "No API key configured") }
                return@launch
            }

            val prompt = """
                Format the following list of quotes correctly. 
                Each quote should be in double quotes "", and the source should be in parentheses ().
                One quote per line. Remove any extra numbering or symbols.
                
                List to format:
                $currentQuotes
            """.trimIndent()

            val models = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b")
            var lastError: String? = null
            for (modelName in models) {
                try {
                    // P-P2-06: bounded timeout + IO dispatcher so offline hangs surface.
                    val resp = withTimeoutOrNull(30_000L) {
                        withContext(Dispatchers.IO) {
                            openAiService.getChatCompletion(
                                url = "https://api.groq.com/openai/v1/chat/completions",
                                authHeader = "Bearer $groqKey",
                                request = OpenAiRequest(
                                    model = modelName,
                                    messages = listOf(
                                        OpenAiMessage("system", MessageContent.Text("You are a helpful assistant that formats quotes. Reply with ONLY the formatted quotes, one per line.")),
                                        OpenAiMessage("user", MessageContent.Text(prompt)),
                                    ),
                                    maxTokens = 2000,
                                )
                            )
                        }
                    }
                    if (resp == null) {
                        lastError = "Request timed out ($modelName)"
                        continue
                    }
                    val formatted = resp.choices.firstOrNull()?.message?.content?.trim()
                    if (!formatted.isNullOrBlank()) {
                        settingsRepository.setPomodoroQuotes(formatted.take(8000))
                        lastError = null
                        break
                    } else {
                        lastError = "Empty response ($modelName)"
                    }
                } catch (e: Exception) {
                    Log.e("PomodoroVM", "AI format failed with $modelName: ${e.message}")
                    lastError = e.message ?: "Request failed ($modelName)"
                }
            }

            _uiState.update { it.copy(isFormattingQuotes = false, quoteError = lastError) }
        }
    }

    fun clearQuoteError() {
        _uiState.update { it.copy(quoteError = null) }
    }

    fun playRingtone() {
        // P-P2-01: dead stub removed from call sites — Service owns sound. Kept for
        // binary compat; does nothing by design.
    }

    fun stopRingtone() {
        // DEV-SPOOF: sandboxed → nothing ever rang; just clear the fake banner.
        if (isPomodoroSandboxActive) {
            _uiState.update { it.copy(isFinished = false) }
            return
        }
        toolService?.stopAlarm()
        _uiState.update { it.copy(isFinished = false) }
    }

    fun reset() {
        // DEV-SPOOF: sandboxed → restage the current phase on the fixture.
        if (isPomodoroSandboxActive) {
            val cur = _uiState.value
            val duration = fixtureDurationMs(cur)
            _uiState.update {
                it.copy(isRunning = false, isFinished = false, remainingTime = duration, totalTime = duration)
            }
            return
        }
        // P-P2-01: service is truth — no optimistic duration math. Flows echo.
        toolService?.stopAlarm()
        toolService?.resetPomodoro()
        _uiState.update {
            it.copy(isRunning = false, isFinished = false)
        }
    }

    fun resetGoal() {
        // DEV-SPOOF: sandboxed → RAM-only count, the real persisted progress
        // must never be wiped by the fake tool.
        if (isPomodoroSandboxActive) {
            _uiState.update { it.copy(sessionsCompleted = 0) }
            return
        }
        // Reset session progress back to 0/goal. Optimistic UI update first so
        // the settings row + stats clear instantly, then service (truth) or
        // direct repo fallback when unbound (service null = silent no-op before).
        _uiState.update { it.copy(sessionsCompleted = 0) }
        val svc = toolService
        if (svc != null) {
            svc.resetPomodoroGoal()
        } else {
            viewModelScope.launch {
                try { settingsRepository.setPomodoroSessionsCompleted(0) } catch (_: Exception) {}
            }
        }
    }

    fun skip() {
        // DEV-SPOOF: sandboxed → silent RAM phase cycle; skipping WORK still
        // counts as completed, exactly like the real service rule (P-P2-02).
        if (isPomodoroSandboxActive) { skipSandboxPomodoro(); return }
        // Skipping WORK counts as completed (service increments); skipping a
        // break never counts. Service cycles silently, no alarm.
        toolService?.stopAlarm()
        toolService?.skipPomodoro()
        _uiState.update { it.copy(isFinished = false) }
    }

    // ── DEV-SPOOF: pomodoro fixture internals ───────────────────────────────

    /** Fixture length for a phase, straight from the user's live settings. */
    private fun fixtureDurationMs(s: PomodoroState): Long {
        val minutes = when (s.mode) {
            PomodoroMode.WORK -> s.workMinutes
            PomodoroMode.SHORT_BREAK -> s.shortBreakMinutes
            PomodoroMode.LONG_BREAK -> s.longBreakMinutes
        }.coerceAtLeast(1)
        return minutes * 60_000L
    }

    /** DEV-SPOOF: start/pause the RAM ticker; never touches the service. */
    private fun toggleSandboxPomodoro() {
        val s = _uiState.value
        if (s.isRunning) {
            _uiState.update { it.copy(isRunning = false) }
            return
        }
        // Service parity: a zero remaining falls back to the phase duration.
        val duration = if (s.remainingTime > 0L) s.remainingTime else fixtureDurationMs(s)
        _uiState.update {
            it.copy(
                isRunning = true,
                isFinished = false,
                remainingTime = duration,
                totalTime = if (it.remainingTime > 0L) it.totalTime else duration,
            )
        }
        startSandboxPomodoroTicker()
    }

    /** DEV-SPOOF: silent RAM cycle — WORK counts, breaks never increment. */
    private fun skipSandboxPomodoro() {
        val s = _uiState.value
        val nextMode = PomodoroMode.valueOf(nextPomodoroMode(s.mode.name, s.sessionsCompleted))
        val completed = if (s.mode == PomodoroMode.WORK) s.sessionsCompleted + 1 else s.sessionsCompleted
        val duration = fixtureDurationMs(s.copy(mode = nextMode))
        _uiState.update {
            it.copy(
                mode = nextMode,
                sessionsCompleted = completed,
                remainingTime = duration,
                totalTime = duration,
                isRunning = it.isRunning, // silent continue, as the real skip
                isFinished = false,
            )
        }
    }

    /** One-tick-at-a-time RAM pomodoro; finish → quiet phase cycle (auto-start aware). */
    private fun startSandboxPomodoroTicker() {
        if (sandboxPomodoroJob?.isActive == true) return
        sandboxPomodoroJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val base = _uiState.value
                if (!base.isRunning || base.remainingTime <= 0L) continue
                val next = base.remainingTime - 1000L
                if (next > 0L) {
                    _uiState.update { it.copy(remainingTime = next) }
                    continue
                }
                // Fake finish — visual only: no alarm FIFO, no sound, no
                // notification, nothing persisted.
                _uiState.update {
                    it.copy(remainingTime = 0L, isRunning = false, isFinished = true)
                }
                if (base.mode == PomodoroMode.WORK) {
                    _uiState.update { it.copy(sessionsCompleted = it.sessionsCompleted + 1) }
                }
                kotlinx.coroutines.delay(1500)
                val after = _uiState.value
                // The user may have reset/skipped/started during the quiet beat —
                // then this finish is already handled and no chain fires.
                if (!after.isFinished || after.isRunning) continue
                if (!after.autoStartNext) continue
                val nextPhaseName = if (after.mode == PomodoroMode.WORK) {
                    // One shared phase-truth fn, pre-increment count — service rule.
                    nextModeAfterWork(after.sessionsCompleted - 1)
                } else {
                    "WORK"
                }
                val nextMode = PomodoroMode.valueOf(nextPhaseName)
                val duration = fixtureDurationMs(after.copy(mode = nextMode))
                _uiState.update {
                    it.copy(
                        mode = nextMode,
                        remainingTime = duration,
                        totalTime = duration,
                        isRunning = true,
                        isFinished = false,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pomodoroJobs.forEach { try { it.cancel() } catch (_: Exception) {} }
        pomodoroJobs = emptyList()
        // DEV-SPOOF: drop the RAM ticker with the VM.
        sandboxPomodoroJob?.cancel()
        sandboxPomodoroJob = null
        if (isBound) {
            try { context.unbindService(connection) } catch (_: Exception) {}
            isBound = false
        }
    }
}

private fun String.toPomodoroMode(): PomodoroMode = when (this) {
    "SHORT_BREAK" -> PomodoroMode.SHORT_BREAK
    "LONG_BREAK" -> PomodoroMode.LONG_BREAK
    else -> PomodoroMode.WORK
}
