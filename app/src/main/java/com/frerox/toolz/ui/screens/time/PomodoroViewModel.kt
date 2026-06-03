package com.frerox.toolz.ui.screens.time

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
)

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val aiSettingsManager: AiSettingsManager,
    private val openAiService: OpenAiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PomodoroState())
    val uiState: StateFlow<PomodoroState> = _uiState.asStateFlow()

    private var toolService: ToolService? = null
    private var isBound = false
    private var mediaPlayer: MediaPlayer? = null
    private var lastFinishCount = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
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
        Intent(context, ToolService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        observeSettings()
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
                settingsRepository.offlineModeEnabled
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
                    offlineMode = values[9] as Boolean
                )
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
        val offlineMode: Boolean
    )

    private fun bindPomodoroFlows(service: ToolService) {
        viewModelScope.launch {
            service.pomodoroRemaining.collect { remaining ->
                _uiState.update { it.copy(remainingTime = remaining.coerceAtLeast(0L)) }
            }
        }
        viewModelScope.launch {
            service.pomodoroTotalMs.collect { total ->
                _uiState.update { it.copy(totalTime = total.coerceAtLeast(1L)) }
            }
        }
        viewModelScope.launch {
            service.pomodoroModeState.collect { mode ->
                _uiState.update {
                    it.copy(
                        mode = mode.toPomodoroMode(),
                        isFinished = if (it.isRunning) false else it.isFinished,
                    )
                }
            }
        }
        viewModelScope.launch {
            service.isPomodoroRunning.collect { running ->
                _uiState.update { it.copy(isRunning = running, isFinished = if (running) false else it.isFinished) }
            }
        }
        viewModelScope.launch {
            service.pomodoroSessionsDone.collect { sessions ->
                _uiState.update { it.copy(sessionsCompleted = sessions.coerceAtLeast(0)) }
            }
        }
        viewModelScope.launch {
            service.pomodoroFinishedCount.collect { count ->
                if (count > lastFinishCount) {
                    lastFinishCount = count
                    _uiState.update { it.copy(isFinished = true) }
                    playRingtone()
                    if (_uiState.value.autoStartNext) {
                        toggleStartStop()
                    }
                } else {
                    lastFinishCount = count
                }
            }
        }
    }

    fun toggleStartStop() {
        val state = _uiState.value
        if (state.isRunning) {
            toolService?.pausePomodoro()
        } else {
            stopRingtone()
            toolService?.startPomodoro(state.remainingTime, state.mode.name)
            _uiState.update { it.copy(isFinished = false) }
        }
    }

    fun selectMode(mode: PomodoroMode) {
        if (_uiState.value.isRunning) return
        stopRingtone()
        toolService?.setPomodoroMode(mode.name)
        val minutes = when(mode) {
            PomodoroMode.WORK -> _uiState.value.workMinutes
            PomodoroMode.SHORT_BREAK -> _uiState.value.shortBreakMinutes
            PomodoroMode.LONG_BREAK -> _uiState.value.longBreakMinutes
        }
        _uiState.update {
            it.copy(
                mode = mode,
                remainingTime = minutes * 60 * 1000L,
                totalTime = minutes * 60 * 1000L,
                isFinished = false,
            )
        }
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

    fun setRingtoneUri(uri: String) {
        viewModelScope.launch { settingsRepository.setPomodoroRingtoneUri(uri) }
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
            _uiState.update { it.copy(isFormattingQuotes = true) }
            val currentQuotes = _uiState.value.quotes
            val groqKey = aiSettingsManager.getApiKey("Groq").ifBlank { aiSettingsManager.getApiKey() }
            
            if (groqKey.isBlank()) {
                _uiState.update { it.copy(isFormattingQuotes = false) }
                return@launch
            }

            val prompt = """
                Format the following list of quotes correctly. 
                Each quote should be in double quotes "", and the source should be in parentheses ().
                One quote per line. Remove any extra numbering or symbols.
                
                List to format:
                $currentQuotes
            """.trimIndent()

            try {
                val resp = withContext(Dispatchers.IO) {
                    openAiService.getChatCompletion(
                        url = "https://api.groq.com/openai/v1/chat/completions",
                        authHeader = "Bearer $groqKey",
                        request = OpenAiRequest(
                            model = "llama-3.1-8b-instant",
                            messages = listOf(
                                OpenAiMessage("system", MessageContent.Text("You are a helpful assistant that formats quotes. Reply with ONLY the formatted quotes, one per line.")),
                                OpenAiMessage("user", MessageContent.Text(prompt)),
                            ),
                            maxTokens = 2000,
                        )
                    )
                }
                val formatted = resp.choices.firstOrNull()?.message?.content?.trim()
                if (!formatted.isNullOrBlank()) {
                    settingsRepository.setPomodoroQuotes(formatted)
                }
            } catch (e: Exception) {
                Log.e("PomodoroVM", "AI format failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isFormattingQuotes = false) }
            }
        }
    }

    private fun playRingtone() {
        viewModelScope.launch {
            val ringtoneUriStr = _uiState.value.ringtoneUri
            val uri = if (!ringtoneUriStr.isNullOrEmpty()) {
                Uri.parse(ringtoneUriStr)
            } else {
                Settings.System.DEFAULT_NOTIFICATION_URI
            }

            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRingtone() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _uiState.update { it.copy(isFinished = false) }
    }

    fun reset() {
        toolService?.resetPomodoro()
        stopRingtone()
        val minutes = when(_uiState.value.mode) {
            PomodoroMode.WORK -> _uiState.value.workMinutes
            PomodoroMode.SHORT_BREAK -> _uiState.value.shortBreakMinutes
            PomodoroMode.LONG_BREAK -> _uiState.value.longBreakMinutes
        }
        _uiState.update {
            it.copy(
                remainingTime = minutes * 60 * 1000L,
                totalTime = minutes * 60 * 1000L,
                isRunning = false,
                isFinished = false,
            )
        }
    }

    fun resetGoal() {
        toolService?.resetPomodoroGoal()
    }

    fun skip() {
        toolService?.skipPomodoro()
        stopRingtone()
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
        }
        mediaPlayer?.release()
    }
}

private fun String.toPomodoroMode(): PomodoroMode = when (this) {
    "SHORT_BREAK" -> PomodoroMode.SHORT_BREAK
    "LONG_BREAK" -> PomodoroMode.LONG_BREAK
    else -> PomodoroMode.WORK
}
