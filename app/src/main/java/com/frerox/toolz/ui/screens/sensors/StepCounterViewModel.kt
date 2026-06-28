package com.frerox.toolz.ui.screens.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.data.steps.StepRepository
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.ai.AiMessage
import com.frerox.toolz.data.ai.AiSettingsHelper
import com.frerox.toolz.util.OfflineManager
import com.frerox.toolz.util.OfflineState
import com.frerox.toolz.util.StepTrackerUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class StepState(
    val steps: Int = 0,
    val goal: Int = 10000,
    val isSensorPresent: Boolean = true,
    val isEnabledInSettings: Boolean = true,
    val weeklyHistory: List<StepEntry> = emptyList(),
    val fullHistory: List<StepEntry> = emptyList(),
    val distanceDisplay: Double = 0.0,
    val distanceUnit: String = "km",
    val calories: Int = 0,
    val moveMinutes: Int = 0,
    val retention: String = "30d",
    val aiEnabled: Boolean = false,
    val aiProvider: String = "Gemini",
    val aiModel: String = "gemini-3.0-flash",
    val aiTone: String = "Professional",
    val aiMood: String = "Encouraging",
    val aiStyle: String = "Concise",
    val stepLength: Int = 75,
    val caloriesPer1k: Int = 40,
    val measurementSystem: String = "Metric",
    val useGps: Boolean = false,
    val isOffline: Boolean = false,
    val aiAdvice: String? = null,
    val isAiLoading: Boolean = false,
    val aiChatHistory: List<AiMessage> = emptyList(),
    val availableProviders: List<String> = emptyList(),
    val bestDaySteps: Int = 0,
    val allTimeTotal: Long = 0,
    val averageSteps: Int = 0,
    val batterySave: Boolean = true,
    val stepNotifications: Boolean = true,
    val stepSensitivity: Int = 50,
    val stepEngineMode: String = "STRICT",
    val streak: Int = 0,
    val activeDaysCount: Int = 0,
    val rawHistoryForRange: List<StepEntry> = emptyList(),
    val debugLogs: List<String> = emptyList(),
    val motionStatus: String = "IDLE"
)

@HiltViewModel
class StepCounterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stepRepository: StepRepository,
    private val chatRepository: ChatRepository,
    private val aiSettingsManager: AiSettingsManager,
    private val offlineManager: OfflineManager,
    private val aiDao: com.frerox.toolz.data.ai.AiDao
) : ViewModel() {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    private val _motionStatus = MutableStateFlow("IDLE")

    private var serviceBinder: com.frerox.toolz.service.StepCounterService.LocalBinder? = null
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            serviceBinder = service as? com.frerox.toolz.service.StepCounterService.LocalBinder
            serviceBinder?.getService()?.setDebugCallback(object : com.frerox.toolz.IEngineDebugCallback.Stub() {
                override fun onLogReceived(log: String) {
                    _debugLogs.update { (it + log).takeLast(50) }
                }
                override fun onMotionStatusChanged(status: String) {
                    _motionStatus.value = status
                }
            })
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            serviceBinder = null
        }
    }

    init {
        val intent = android.content.Intent(context, com.frerox.toolz.service.StepCounterService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private val _aiAdvice = MutableStateFlow<String?>(null)
    private val _isAiLoading = MutableStateFlow(false)
    private var coachChatId: Int? = null
    private val _coachChatIdState = MutableStateFlow<Int?>(null)

    private val _trendRange = MutableStateFlow("Week")
    private val _selectedChartEntry = MutableStateFlow<StepEntry?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _aiChatHistory = _coachChatIdState.filterNotNull().flatMapLatest { id ->
        aiDao.getMessagesForChat(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _fullHistory = _trendRange.flatMapLatest { range ->
        val days = when (range) {
            "Week" -> 7
            "Month" -> 35
            "Year" -> 365
            else -> 7
        }
        stepRepository.getStepsForLastNDays(days).map { entries ->
            when (range) {
                "Week" -> StepTrackerUtils.aggregateWeekly(entries)
                "Month" -> StepTrackerUtils.aggregateMonthly(entries)
                "Year" -> StepTrackerUtils.aggregateYearly(entries)
                else -> entries
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _allTimeStats = stepRepository.getStepsForLastNDays(3650).map { history ->
        val bestDay = history.maxOfOrNull { it.steps } ?: 0
        val total = history.sumOf { it.steps.toLong() }
        val avg = if (history.isNotEmpty()) (total / history.size).toInt() else 0
        Triple(bestDay, total, avg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0, 0L, 0))
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _streak = combine(
        stepRepository.getStepsForLastNDays(365),
        settingsRepository.stepGoal
    ) { history, goal ->
        calculateStreak(history, goal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _rawHistoryForRange = _trendRange.flatMapLatest { range ->
        val days = when (range) {
            "Week" -> 7
            "Month" -> 30
            "Year" -> 365
            else -> 7
        }
        stepRepository.getStepsForLastNDays(days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<StepState> = combine(
        stepRepository.currentSteps,
        settingsRepository.stepGoal,
        settingsRepository.stepCounterEnabled,
        stepRepository.weeklySteps,
        settingsRepository.stepHistoryRetention,
        settingsRepository.aiFitnessAgentEnabled,
        settingsRepository.aiFitnessAgentProvider,
        settingsRepository.aiFitnessAgentModel,
        settingsRepository.aiFitnessAgentTone,
        settingsRepository.aiFitnessAgentMood,
        settingsRepository.aiFitnessAgentStyle,
        settingsRepository.stepLengthCm,
        settingsRepository.caloriesPer1000Steps,
        settingsRepository.measurementSystem,
        settingsRepository.stepUseGps,
        offlineManager.offlineState,
        _aiAdvice,
        _isAiLoading,
        _aiChatHistory,
        _fullHistory,
        settingsRepository.stepBatterySave,
        settingsRepository.stepNotifications,
        _allTimeStats,
        settingsRepository.stepSensitivity,
        settingsRepository.stepEngineMode,
        _streak,
        _rawHistoryForRange,
        _debugLogs,
        _motionStatus
    ) { args ->
        val currentSteps = args[0] as? Int ?: 0
        val goal = args[1] as? Int ?: 10000
        val enabled = args[2] as? Boolean ?: false
        val history = (args[3] as? List<*>)?.filterIsInstance<StepEntry>() ?: emptyList()
        val retention = args[4] as? String ?: "30d"
        val aiEnabled = args[5] as? Boolean ?: false
        val provider = args[6] as? String ?: "Gemini"
        val model = args[7] as? String ?: "gemini-3.0-flash"
        val tone = args[8] as? String ?: "Professional"
        val mood = args[9] as? String ?: "Encouraging"
        val style = args[10] as? String ?: "Concise"
        val length = args[11] as? Int ?: 75
        val calPer1k = args[12] as? Int ?: 40
        val system = args[13] as? String ?: "Metric"
        val useGps = args[14] as? Boolean ?: false
        val offline = args[15] as? OfflineState ?: OfflineState.ONLINE
        val advice = args[16] as? String
        val loading = args[17] as? Boolean ?: false
        val chatHistory = (args[18] as? List<*>)?.filterIsInstance<AiMessage>() ?: emptyList()
        val fullHistory = (args[19] as? List<*>)?.filterIsInstance<StepEntry>() ?: emptyList()
        val batterySave = args[20] as? Boolean ?: true
        val stepNotif = args[21] as? Boolean ?: true

        val distKm = StepTrackerUtils.calculateDistanceKm(currentSteps, length)
        val isMetric = system == "Metric"
        val distanceDisplay = if (isMetric) distKm else StepTrackerUtils.kmToMiles(distKm)
        val distanceUnit = if (isMetric) "km" else "mi"

        val calories = StepTrackerUtils.calculateCalories(currentSteps, calPer1k)
        val moveMin = StepTrackerUtils.calculateMoveMinutes(currentSteps)

        val availableProviders = AiSettingsHelper.providers.filter { 
            aiSettingsManager.hasUserApiKey(it) || !AiSettingsHelper.isPlaceholder(AiSettingsHelper.getDefaultKey(it))
        }

        // Analytics
        val allTimeStats = args[22] as? Triple<*, *, *> ?: Triple(0, 0L, 0)
        val bestDay = allTimeStats.first as? Int ?: 0
        val total = allTimeStats.second as? Long ?: 0L
        val avg = allTimeStats.third as? Int ?: 0
        
        val sensitivity = args[23] as? Int ?: 50
        val engineMode = args[24] as? String ?: "STRICT"
        val streak = args[25] as? Int ?: 0
        val rawHistory = (args[26] as? List<*>)?.filterIsInstance<StepEntry>() ?: emptyList()
        val activeDaysCount = rawHistory.count { it.steps >= 250 }
        val debugLogs = args[27] as? List<String> ?: emptyList()
        val motionStatus = args[28] as? String ?: "IDLE"

        StepState(
            steps = currentSteps,
            goal = goal,
            isSensorPresent = stepSensor != null,
            isEnabledInSettings = enabled,
            weeklyHistory = history,
            fullHistory = fullHistory,
            distanceDisplay = distanceDisplay,
            distanceUnit = distanceUnit,
            calories = calories,
            moveMinutes = moveMin,
            retention = retention,
            aiEnabled = aiEnabled,
            aiProvider = provider,
            aiModel = model,
            aiTone = tone,
            aiMood = mood,
            aiStyle = style,
            stepLength = length,
            caloriesPer1k = calPer1k,
            measurementSystem = system,
            useGps = useGps,
            isOffline = offline == OfflineState.OFFLINE,
            aiAdvice = advice,
            isAiLoading = loading,
            aiChatHistory = chatHistory,
            availableProviders = availableProviders,
            bestDaySteps = bestDay,
            allTimeTotal = total,
            averageSteps = avg,
            batterySave = batterySave,
            stepNotifications = stepNotif,
            stepSensitivity = sensitivity,
            stepEngineMode = engineMode,
            streak = streak,
            activeDaysCount = activeDaysCount,
            rawHistoryForRange = rawHistory,
            debugLogs = debugLogs,
            motionStatus = motionStatus
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StepState())

    val trendRange: StateFlow<String> = _trendRange

    /** Selected chart bar — drives the tooltip. */
    val selectedChartEntry: StateFlow<StepEntry?> = _selectedChartEntry

    init {
        // Warm up the coach chat ID
        viewModelScope.launch {
            _coachChatIdState.value = getCoachChatId()
        }
        
        // Auto-fetch AI advice once on first load if conditions are met.
        // This makes the pill feel alive without requiring a manual tap.
        viewModelScope.launch {
            // Small delay so the StateFlow has time to emit its first real value.
            kotlinx.coroutines.delay(800)
            val s = uiState.value
            if (s.aiEnabled && !s.isOffline && s.aiAdvice == null && !s.isAiLoading) {
                fetchAiAdvice()
            }
        }
    }

    private var aiAdviceJob: Job? = null

    private fun getSystemPrompt(): String {
        val state = uiState.value
        val pct = if (state.goal > 0) (state.steps * 100 / state.goal) else 0
        val remaining = (state.goal - state.steps).coerceAtLeast(0)
        val toneMap = mapOf(
            "Professional" to "professional and precise",
            "Casual" to "friendly and casual",
            "Strict" to "strict and no-nonsense",
            "Funny" to "witty and humorous"
        )
        val moodMap = mapOf(
            "Encouraging" to "encouraging",
            "Competitive" to "competitive and driven",
            "Calm" to "calm and measured",
            "Energetic" to "high-energy and enthusiastic"
        )
        return """You are an elite AI Fitness Coach. Be ${toneMap[state.aiTone] ?: "professional"} and ${moodMap[state.aiMood] ?: "encouraging"} in tone.

USER'S DATA:
- Today: ${state.steps} / ${state.goal} steps ($pct% of goal)
- Remaining: $remaining steps
- Distance: ${String.format(Locale.US, "%.2f", state.distanceDisplay)} ${state.distanceUnit}
- Calories: ${state.calories} kcal
- Best Day: ${state.bestDaySteps} steps
- Daily Average: ${state.averageSteps} steps

RULES:
1. Answer the user's SPECIFIC question directly.
2. Keep replies VERY SHORT and PUNCHY (1-4 sentences).
3. Always use markdown: **bold** for emphasis, - bullet points for lists.
4. No filler phrases. No "I understand" or "As an AI".
5. If they hit their goal, celebrate. If behind, give ONE specific action now."""
    }

    fun setTrendRange(range: String) {
        _trendRange.value = range
    }

    /** Called when the user taps a chart bar. Pass null to deselect. */
    fun onChartBarSelected(entry: StepEntry?) {
        _selectedChartEntry.value = entry
    }

    suspend fun getCoachChatId(): Int {
        _coachChatIdState.value?.let { return it }
        val chats = aiDao.getAllChatsSync()
        val coachChat = chats.find { it.title == "AI Fitness Coach" }
        val id = coachChat?.id ?: aiDao.insertChat(com.frerox.toolz.data.ai.AiChat(title = "AI Fitness Coach")).toInt()
        _coachChatIdState.value = id
        return id
    }

    fun fetchAiAdvice() {
        if (uiState.value.isOffline || !uiState.value.aiEnabled) return
        
        aiAdviceJob?.cancel()
        aiAdviceJob = viewModelScope.launch {
            _isAiLoading.value = true
            val systemPrompt = getSystemPrompt()
            
            val provider = if (uiState.value.availableProviders.contains(uiState.value.aiProvider)) uiState.value.aiProvider else uiState.value.availableProviders.firstOrNull() ?: "Gemini"
            val model = uiState.value.aiModel
            
            val userPrompt = "Please analyze my progress and give me a short, punchy motivational summary using Markdown."
            var fullText = ""
            chatRepository.getChatResponse(
                prompt = userPrompt,
                history = emptyList(),
                modelOverride = model,
                providerOverride = provider,
                systemPromptOverride = systemPrompt
            )
                .collect { result ->
                    result.onSuccess { chunk ->
                        fullText += chunk.text
                        _aiAdvice.value = fullText
                    }
                    result.onFailure {
                        _aiAdvice.value = "Coach is offline. Check connection."
                    }
                }
            _isAiLoading.value = false
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || uiState.value.isOffline || !uiState.value.aiEnabled) return

        aiAdviceJob?.cancel()
        aiAdviceJob = viewModelScope.launch {
            val chatId = getCoachChatId()
            
            val userMessage = AiMessage(
                chatId = chatId,
                text = text,
                isUser = true,
                timestamp = System.currentTimeMillis()
            )
            aiDao.insertMessage(userMessage)
            
            _isAiLoading.value = true
            val systemPrompt = getSystemPrompt()
            
            val provider = if (uiState.value.availableProviders.contains(uiState.value.aiProvider)) uiState.value.aiProvider else uiState.value.availableProviders.firstOrNull() ?: "Gemini"
            val model = uiState.value.aiModel

            var fullText = ""
            chatRepository.getChatResponse(
                prompt = text,
                history = uiState.value.aiChatHistory,
                modelOverride = model,
                providerOverride = provider,
                systemPromptOverride = systemPrompt
            )
                .collect { result ->
                    result.onSuccess { chunk ->
                        fullText += chunk.text
                    }
                    result.onFailure {
                        fullText = "Connection lost. Try again later."
                    }
                }
            
            aiDao.insertMessage(AiMessage(
                chatId = chatId,
                text = fullText,
                isUser = false,
                timestamp = System.currentTimeMillis()
            ))
            _isAiLoading.value = false
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            val id = getCoachChatId()
            aiDao.deleteMessagesForChat(id)
            _aiAdvice.value = null
        }
    }

    fun updateGoal(newGoal: Int) {
        viewModelScope.launch {
            settingsRepository.setStepGoal(newGoal)
        }
    }

    fun toggleStepCounter(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStepCounterEnabled(enabled)
        }
    }

    fun updateRetention(retention: String) {
        viewModelScope.launch {
            settingsRepository.setStepHistoryRetention(retention)
            // Cleanup is scheduled by the repository itself on its background coroutine.
            // The ViewModel no longer calls cleanupOldSteps directly to avoid
            // blocking the main coroutine context.
            val days = when (retention) {
                "7d"  -> 7
                "30d" -> 30
                "1y"  -> 365
                else  -> 0
            }
            if (days > 0) {
                stepRepository.cleanupOldSteps(days)
            }
        }
    }

    fun updateAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiFitnessAgentEnabled(enabled) }
    }

    fun updateAiProvider(provider: String) {
        viewModelScope.launch { settingsRepository.setAiFitnessAgentProvider(provider) }
    }

    fun updateAiModel(model: String) {
        viewModelScope.launch { settingsRepository.setAiFitnessAgentModel(model) }
    }

    fun updateAiTone(tone: String) {
        viewModelScope.launch { settingsRepository.setAiFitnessAgentTone(tone) }
    }

    fun updateAiMood(mood: String) {
        viewModelScope.launch { settingsRepository.setAiFitnessAgentMood(mood) }
    }

    fun updateAiStyle(style: String) {
        viewModelScope.launch { settingsRepository.setAiFitnessAgentStyle(style) }
    }

    fun updateStepLength(length: Int) {
        viewModelScope.launch { settingsRepository.setStepLengthCm(length) }
    }

    fun updateCaloriesPer1k(calories: Int) {
        viewModelScope.launch { settingsRepository.setCaloriesPer1000Steps(calories) }
    }

    fun updateMeasurementSystem(system: String) {
        viewModelScope.launch { settingsRepository.setMeasurementSystem(system) }
    }

    fun toggleUseGps(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStepUseGps(enabled) }
    }

    fun toggleBatterySave(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStepBatterySave(enabled) }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStepNotifications(enabled) }
    }

    fun updateStepSensitivity(sensitivity: Int) {
        viewModelScope.launch { settingsRepository.setStepSensitivity(sensitivity) }
    }

    fun updateStepEngineMode(mode: String) {
        viewModelScope.launch { settingsRepository.setStepEngineMode(mode) }
    }

    fun hardResetEngine() {
        serviceBinder?.getService()?.apply {
            if (currentEngineMode == "STRICT") {
                dspEngine?.resetEngine()
            } else {
                simpleEngine?.reset()
            }
        }
    }

    private fun calculateStreak(history: List<StepEntry>, goal: Int): Int {
        if (history.isEmpty()) return 0
        
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val todayStr = dateFormat.format(java.util.Date())
        
        val todayEntry = history.find { it.date == todayStr }
        val hasMetGoalToday = todayEntry != null && todayEntry.steps >= goal
        
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = dateFormat.format(cal.time)
        
        var streak = 0
        val calendar = java.util.Calendar.getInstance()
        if (hasMetGoalToday) {
            streak = 1
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        } else {
            val yesterdayEntry = history.find { it.date == yesterdayStr }
            if (yesterdayEntry == null || yesterdayEntry.steps < goal) {
                return 0
            }
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        
        while (true) {
            val dateStr = dateFormat.format(calendar.time)
            val entry = history.find { it.date == dateStr }
            if (entry != null && entry.steps >= goal) {
                if (dateStr != todayStr) {
                    streak++
                }
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    override fun onCleared() {
        super.onCleared()
    }
}
