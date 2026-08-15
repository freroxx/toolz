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

package com.frerox.toolz.ui.screens.focus

import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.MessageContent
import com.frerox.toolz.data.ai.OpenAiMessage
import com.frerox.toolz.data.ai.OpenAiRequest
import com.frerox.toolz.data.ai.OpenAiService
import com.frerox.toolz.data.focus.AppCategory
import com.frerox.toolz.data.focus.AppLimit
import com.frerox.toolz.data.focus.AppLimitRepository
import com.frerox.toolz.data.focus.AppUsageInfo
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ToolService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.util.*
import javax.inject.Inject
import com.frerox.toolz.data.focus.UsageStatsRepository

// ─────────────────────────────────────────────────────────────
//  Over-limit status — consumed by the accessibility service
// ─────────────────────────────────────────────────────────────

data class OverLimitStatus(
    val isOverLimit : Boolean,
    val usageMillis : Long,
    val limitMillis : Long,
    /** 0..2f — values > 1.0 mean limit is exceeded. */
    val percentUsed : Float,
)

// ─────────────────────────────────────────────────────────────
//  Focus session — UI-local timer state (NEW)
//
//  This is intentionally NOT persisted or backed by a repository — I don't
//  have visibility into AppLimitRepository/SettingsRepository's full surface
//  beyond what's used in the original file, so I'm not inventing a new DB
//  entity or DataStore key. It's a ViewModel-scoped countdown that survives
//  configuration change (survives as long as the ViewModel does, which is
//  the standard Compose/Hilt lifecycle) but not process death. If you want
//  it to survive process death or show up elsewhere (e.g. a notification),
//  that's a deliberate follow-up wired through AppLimitRepository or a new
//  small repository — flagging rather than guessing at that surface.
// ─────────────────────────────────────────────────────────────

enum class FocusSessionState { IDLE, RUNNING, PAUSED, COMPLETED }

data class FocusSessionUiState(
    val state: FocusSessionState = FocusSessionState.IDLE,
    val totalMillis: Long = 25 * 60_000L,
    val remainingMillis: Long = 25 * 60_000L,
)

// ─────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class)
@HiltViewModel
class FocusFlowViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLimitRepository : AppLimitRepository,
    private val settingsRepository : SettingsRepository,
    private val openAiService      : OpenAiService,
    private val aiSettingsManager  : AiSettingsManager,
    private val usageRepository    : UsageStatsRepository,
) : ViewModel() {

    companion object {
        private const val TAG               = "FocusFlowViewModel"
        private const val AI_MODEL_PRIMARY  = "openai/gpt-oss-120b"
        private const val AI_MODEL_FALLBACK = "openai/gpt-oss-20b"
        private const val GROQ_URL          = "https://api.groq.com/openai/v1/chat/completions"
        // SharedPreferences file + key for persisting AI-generated categories
        private const val PREFS_AI_CACHE    = "focus_ai_category_cache"
        private const val KEY_CATEGORIES    = "categories_json"
        private const val PREFS_USAGE_CACHE = "focus_daily_usage_cache"

        private val EXCLUDED_PACKAGES = emptySet<String>() // Moved to UsageStatsRepository
    }

    // ── Persistent AI category cache ───────────────────────────────────────
    private val aiPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_AI_CACHE, Context.MODE_PRIVATE)

    // ── Local daily usage history cache ──────────────────────────────────
    private val usagePrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_USAGE_CACHE, Context.MODE_PRIVATE)

    // ── Internal state ─────────────────────────────────────────────────────

    private val _rawStats          = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    private val _isWeekly          = MutableStateFlow(false)
    val isWeekly                   = _isWeekly.asStateFlow()

    private val _productivityScore = MutableStateFlow(0)
    val productivityScore: StateFlow<Int> = _productivityScore.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(false)
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    /**
     * AI-determined categories. Pre-loaded from SharedPreferences so results
     * from previous sessions are immediately available without re-calling Groq.
     * User-set mappings always take priority over this cache.
     */
    private val _aiCategoryCache = MutableStateFlow(loadAiCacheFromPrefs())

    private val _isAiClassifying = MutableStateFlow(false)
    val isAiClassifying: StateFlow<Boolean> = _isAiClassifying.asStateFlow()

    private val _screenTips = MutableStateFlow<String?>(null)
    val screenTips = _screenTips.asStateFlow()

    private val _isLoadingTips = MutableStateFlow(false)
    val isLoadingTips = _isLoadingTips.asStateFlow()

    val aiClassifiedPackages: StateFlow<Set<String>> = _aiCategoryCache
        .map { it.keys.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // ── Focus session state (NEW) ───────────────────────────────────────────

    private val _focusSession = MutableStateFlow(FocusSessionUiState())
    val focusSession: StateFlow<FocusSessionUiState> = _focusSession.asStateFlow()

    private var toolService: ToolService? = null
    private var isBound = false

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

    private fun bindPomodoroFlows(service: ToolService) {
        viewModelScope.launch {
            combine(
                service.pomodoroRemaining,
                service.pomodoroTotalMs,
                service.isPomodoroRunning,
                service.pomodoroModeState
            ) { remaining, total, running, mode ->
                val state = when {
                    remaining <= 0 && !running -> FocusSessionState.COMPLETED
                    running -> FocusSessionState.RUNNING
                    remaining < total && !running -> FocusSessionState.PAUSED
                    else -> FocusSessionState.IDLE
                }
                FocusSessionUiState(
                    state = state,
                    totalMillis = total,
                    remainingMillis = remaining
                )
            }.collect { uiState ->
                _focusSession.value = uiState
                if (uiState.state == FocusSessionState.IDLE || uiState.state == FocusSessionState.COMPLETED) {
                    settingsRepository.setFocusFlowSessionActive(false)
                }
            }
        }
    }

    fun startFocusSession(minutes: Int) {
        val totalMs = minutes * 60_000L
        viewModelScope.launch {
            settingsRepository.setFocusFlowSessionActive(true)
            toolService?.startPomodoro(totalMs, "WORK")
        }
    }

    fun togglePauseFocusSession() {
        toolService?.let { service ->
            if (service.isPomodoroRunning.value) {
                service.pausePomodoro()
            } else {
                service.startPomodoro(_focusSession.value.remainingMillis, "WORK")
            }
        }
    }

    fun cancelFocusSession() {
        viewModelScope.launch {
            settingsRepository.setFocusFlowSessionActive(false)
            toolService?.resetPomodoro()
        }
    }

    fun dismissCompletedSession() {
        viewModelScope.launch {
            settingsRepository.setFocusFlowSessionActive(false)
            toolService?.resetPomodoro()
        }
    }

    fun resetAllFocusData() {
        viewModelScope.launch {
            appLimitRepository.deleteAllLimits()
            settingsRepository.clearFocusMappings()
            _aiCategoryCache.value = emptyMap()
            saveAiCacheToPrefs(emptyMap())
            refreshStats()
        }
    }

    // ── Settings / DB flows ────────────────────────────────────────────────

    private val _performanceMode = settingsRepository.performanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _appLimits = appLimitRepository.allLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val userMappings = settingsRepository.appCategoryMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val appNameMappings = settingsRepository.appNameMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val customInstructions = settingsRepository.focusAiCustomInstructions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val offlineModeEnabled: Flow<Boolean> = settingsRepository.offlineModeEnabled

    // ── Combined stats ─────────────────────────────────────────────────────

    /**
     * Primary data source for the UI. Merges raw stats with limits, user
     * mappings, custom names, and AI categories — in that priority order.
     *
     * [distinctUntilChanged] prevents redundant UI recompositions and is the
     * first line of defence against overlay flicker.
     */
    val combinedUsageStats: Flow<List<AppUsageInfo>> = combine(
        _rawStats, _appLimits, userMappings, appNameMappings, _aiCategoryCache
    ) { stats, limits, mappings, nameMap, aiCache ->

        val pm       = context.packageManager
        val statsMap = stats.associateBy { it.packageName }.toMutableMap()

        // Ensure apps with active limits are always visible even with 0 usage
        limits.forEach { limit ->
            if (!statsMap.containsKey(limit.packageName)) {
                val displayName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(limit.packageName, 0)).toString()
                } catch (_: Exception) { limit.packageName }
                statsMap[limit.packageName] = AppUsageInfo(
                    packageName     = limit.packageName,
                    appName         = nameMap[limit.packageName] ?: displayName,
                    usageTimeMillis = 0L,
                )
            }
        }

        statsMap.values.map { stat ->
            val limit = limits.find { it.packageName == stat.packageName }
            // Priority: user-set → AI cache → heuristic
            val category = when {
                mappings.containsKey(stat.packageName) ->
                    if (mappings[stat.packageName] == "Productive") AppCategory.TOOLZ
                    else AppCategory.DISTRACTION
                aiCache.containsKey(stat.packageName) -> aiCache.getValue(stat.packageName)
                else -> guessCategory(stat.packageName)
            }

            stat.copy(
                appName     = nameMap[stat.packageName] ?: stat.appName,
                limitMillis = limit?.limitMillis,
                category    = category,
            )
        }
    }.combine(_focusSession) { stats, session ->
        stats.map { stat ->
            val isOverLimit = stat.limitMillis != null && stat.limitMillis > 0 && stat.todayUsageTimeMillis >= stat.limitMillis
            val isSessionBlocked = session.state == FocusSessionState.RUNNING && stat.category == AppCategory.DISTRACTION

            stat.copy(isBlocked = isOverLimit || isSessionBlocked)
        }.sortedByDescending { it.usageTimeMillis }
    }.distinctUntilChanged()

    val top5Apps: StateFlow<List<AppUsageInfo>> = combinedUsageStats
        .map { it.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val blockedApps: StateFlow<List<AppUsageInfo>> = combinedUsageStats
        .map { stats -> stats.filter { it.isBlocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Debounced over-limit map for the accessibility service.
     */
    val appsOverLimit: StateFlow<Map<String, OverLimitStatus>> = combinedUsageStats
        .map { stats ->
            stats
                .filter { it.limitMillis != null && it.limitMillis > 0 }
                .associate { info ->
                    val pct = (info.usageTimeMillis.toFloat() / info.limitMillis!!).coerceIn(0f, 2f)
                    info.packageName to OverLimitStatus(
                        isOverLimit = info.usageTimeMillis >= info.limitMillis,
                        usageMillis = info.usageTimeMillis,
                        limitMillis = info.limitMillis,
                        percentUsed = pct,
                    )
                }
        }
        .distinctUntilChanged()
        .debounce(2_500L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ── Init ───────────────────────────────────────────────────────────────

    init {
        Intent(context, ToolService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        refreshStats()

        // Auto-refresh adapts interval based on performance mode
        viewModelScope.launch {
            _performanceMode.collectLatest { perfMode ->
                while (true) {
                    kotlinx.coroutines.delay(if (perfMode) 60_000L else 30_000L)
                    refreshStats()
                }
            }
        }

        viewModelScope.launch {
            combinedUsageStats.collect { calculateProductivityScore(it) }
        }

        // Only trigger AI if there are unclassified apps and WiFi is available
        viewModelScope.launch {
            _rawStats.collectLatest { stats ->
                if (stats.isNotEmpty() && isWifiConnected()) {
                    categorizeAppsWithAi(stats)
                }
            }
        }
    }

    fun toggleWeekly(weekly: Boolean) {
        _isWeekly.value = weekly
        refreshStats()
    }

    data class DailyLocalStat(
        val date: String,
        val totalMillis: Long,
        val topApps: List<Pair<String, Long>>
    )

    fun getWeeklyLocalStats(): List<DailyLocalStat> {
        val result = mutableListOf<DailyLocalStat>()
        val tz = TimeZone.getDefault()
        val cal = Calendar.getInstance(tz)
        
        // Ensure we are working with midnight boundaries
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        for (i in 6 downTo 0) {
            val dCal = cal.clone() as Calendar
            dCal.add(Calendar.DAY_OF_YEAR, -i)
            
            val y = dCal.get(Calendar.YEAR)
            val m = dCal.get(Calendar.MONTH) + 1
            val d = dCal.get(Calendar.DAY_OF_MONTH)
            val key = String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
            val shortDate = android.text.format.DateFormat.format("EE dd", dCal).toString()

            val jsonStr = usagePrefs.getString(key, null)
            var total = 0L
            val topApps = mutableListOf<Pair<String, Long>>()
            
            if (jsonStr != null) {
                try {
                    val array = org.json.JSONArray(jsonStr)
                    for (j in 0 until array.length()) {
                        val obj = array.getJSONObject(j)
                        val time = obj.getLong("time")
                        val name = obj.getString("name")
                        total += time
                        topApps.add(Pair(name, time))
                    }
                } catch(e: Exception) { Log.e(TAG, "Failed pulling daily usage", e) }
            }

            // Fallback: If cache is empty or it's TODAY, fetch live from system
            // Today's cache might be stale since it only updates every refreshStats()
            if (total <= 0L || i == 0) {
                val dayStart = dCal.timeInMillis
                val dayEnd = dayStart + 24 * 3600_000L
                total = usageRepository.queryTotalUsageInRange(dayStart, dayEnd)
                // Note: We don't fill topApps for fallback to keep it fast, 
                // chart only needs the total.
            }

            topApps.sortByDescending { it.second }
            result.add(DailyLocalStat(shortDate, total, topApps.take(10)))
        }
        return result
    }

    fun resetAppSettings(packageName: String) {
        viewModelScope.launch {
            removeAppLimit(packageName)
            settingsRepository.removeAppCategoryMapping(packageName)
            refreshStats()
        }
    }

    private fun saveDailyUsageLocally(dateKey: String, usageList: List<AppUsageInfo>) {
        try {
            val jsonArray = org.json.JSONArray()
            usageList.forEach { info ->
                val obj = JSONObject()
                obj.put("pkg", info.packageName)
                obj.put("name", info.appName)
                obj.put("time", info.usageTimeMillis)
                jsonArray.put(obj)
            }
            usagePrefs.edit().putString(dateKey, jsonArray.toString()).apply()
        } catch(e: Exception) {
            Log.e(TAG, "Failed to save daily usage locally", e)
        }
    }

    /**
     * Clears all AI-generated categories (both in-memory and persisted) and
     * immediately re-triggers classification for the current app list.
     * Called when the user taps the refresh AI button.
     */
    fun refreshAiCategories() {
        _aiCategoryCache.value = emptyMap()
        saveAiCacheToPrefs(emptyMap())
        viewModelScope.launch {
            if (_rawStats.value.isNotEmpty() && isWifiConnected()) {
                categorizeAppsWithAi(_rawStats.value)
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _hasUsagePermission.value = usageRepository.hasUsageStatsPermission()
            if (!_hasUsagePermission.value) {
                _rawStats.value = emptyList()
                return@launch
            }

            val now = System.currentTimeMillis()
            val tz  = TimeZone.getDefault()
            val cal = Calendar.getInstance(tz)

            val todayStr = String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

            cal.apply {
                if (_isWeekly.value) {
                    val dow = get(Calendar.DAY_OF_WEEK)
                    val daysFromMonday = (dow - Calendar.MONDAY + 7) % 7
                    add(Calendar.DAY_OF_YEAR, -daysFromMonday)
                }
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE,      0)
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = cal.timeInMillis

            val usageList = withContext(Dispatchers.IO) {
                if (_isWeekly.value) {
                    val weekly = usageRepository.queryWeeklyByAggregate(startTime, now)
                    weekly.map { info ->
                        info.copy(todayUsageTimeMillis = usageRepository.queryPackageUsageToday(info.packageName))
                    }
                } else {
                    val daily = usageRepository.queryDailyByEvents(startTime, now)
                    daily.map { it.copy(todayUsageTimeMillis = it.usageTimeMillis) }
                }
            }

            _rawStats.value = usageList
            if (!_isWeekly.value && usageList.isNotEmpty()) {
                saveDailyUsageLocally(todayStr, usageList)
            }
            if (usageList.isEmpty()) {
                Log.d(TAG, "No usage stats found. Check permissions or app usage today.")
            }
        }
    }

    fun setAppLimit(packageName: String, limitMinutes: Long) {
        if (packageName == context.packageName) return // Security: Toolz is immune to limits
        viewModelScope.launch {
            appLimitRepository.setLimit(AppLimit(packageName, limitMinutes * 60_000L))
        }
    }

    fun removeAppLimit(packageName: String) {
        viewModelScope.launch {
            appLimitRepository.getLimitForApp(packageName)?.let {
                appLimitRepository.removeLimit(it)
            }
        }
    }

    fun updateAppCategory(packageName: String, isProductive: Boolean) {
        if (packageName == context.packageName && !isProductive) return // Security: Toolz must be productive
        viewModelScope.launch {
            settingsRepository.setAppCategoryMapping(
                packageName,
                if (isProductive) "Productive" else "Distraction",
            )
        }
    }

    fun renameApp(packageName: String, customName: String) {
        viewModelScope.launch {
            settingsRepository.setAppNameMapping(packageName, customName)
        }
    }

    fun setCustomInstructions(instructions: String) {
        viewModelScope.launch {
            settingsRepository.setFocusAiCustomInstructions(instructions)
        }
    }

    // ── AI categorization ──────────────────────────────────────────────────

    private suspend fun categorizeAppsWithAi(apps: List<AppUsageInfo>) {
        if (settingsRepository.offlineModeEnabled.first()) return
        val currentMappings = userMappings.value
        val currentAiCache  = _aiCategoryCache.value

        // Only classify apps not covered by user mappings, AI cache, or heuristics
        val toClassify = apps.filter { info ->
            !currentMappings.containsKey(info.packageName) &&
                    !currentAiCache.containsKey(info.packageName) &&
                    guessCategory(info.packageName) == AppCategory.OTHER
        }

        if (toClassify.isEmpty()) return

        val groqKey = aiSettingsManager.getApiKey("Groq")
        if (groqKey.isBlank()) {
            Log.d(TAG, "Groq key not configured; skipping AI categorization")
            return
        }

        withContext(Dispatchers.IO) {
            _isAiClassifying.value = true

            val prompt    = buildClassificationPrompt(toClassify)
            val validPkgs = toClassify.map { it.packageName }

            for (model in listOf(AI_MODEL_PRIMARY, AI_MODEL_FALLBACK)) {
                try {
                    val request = OpenAiRequest(
                        model    = model,
                        messages = listOf(
                            OpenAiMessage("system", MessageContent.Text(
                                "You are an Android app classifier. " +
                                        "Classify apps as productive or distraction. " +
                                        "Reply ONLY with a valid JSON object. No prose."
                            )),
                            OpenAiMessage("user", MessageContent.Text(prompt)),
                        ),
                        maxTokens = 512,
                    )

                    val raw = runGroqRequest(groqKey) { requestKey ->
                        openAiService.getChatCompletion(
                            url        = GROQ_URL,
                            authHeader = "Bearer $requestKey",
                            request    = request,
                        ).choices.firstOrNull()?.message?.content
                    } ?: continue

                    val parsed = parseClassificationResponse(raw, validPkgs)
                    if (parsed.isNotEmpty()) {
                        _aiCategoryCache.update { it + parsed }
                        saveAiCacheToPrefs(_aiCategoryCache.value)
                        Log.d(TAG, "AI classified ${parsed.size} apps via $model")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AI classification failed ($model): ${e.message}")
                }
            }

            _isAiClassifying.value = false
        }
    }

    private fun buildClassificationPrompt(apps: List<AppUsageInfo>): String = buildString {
        appendLine("Classify each Android app as 'productive' or 'distraction'.")
        appendLine("Productive: tools, utilities, work, education, finance, health, maps, email, calendar, notes.")
        appendLine("Distraction: social media, short-form video, games, entertainment streaming.")
        appendLine()
        appendLine("""Reply ONLY with JSON: {"pkg.name":"productive","pkg2":"distraction"}""")
        appendLine()
        apps.forEach { appendLine(""""${it.packageName}": "${it.appName}"""") }
    }

    fun generateScreenTips(forceRefresh: Boolean = false) {
        if (!forceRefresh && (_screenTips.value != null || _isLoadingTips.value)) return
        _isLoadingTips.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (settingsRepository.offlineModeEnabled.first()) {
                    _screenTips.value = "AI Tips are disabled in Offline Mode."
                    _isLoadingTips.value = false
                    return@launch
                }
                val groqKey = aiSettingsManager.getApiKey("Groq")
                if (groqKey.isBlank()) {
                    _screenTips.value = "AI key not configured. Please supply a Groq key in AI settings."
                    return@launch
                }

                val isWeeklyTab = _isWeekly.value
                val stats = combinedUsageStats.first().sortedByDescending { it.usageTimeMillis }
                val top10Apps = stats.take(10)

                val heavyDistractions = stats.filter {
                    it.category == AppCategory.DISTRACTION && it.usageTimeMillis > 3_600_000L
                }

                val customInstr = customInstructions.value
                val appUsageContext = top10Apps.joinToString("\n") {
                    "- ${it.appName}: ${it.usageTimeMillis / 3_600_000}h ${(it.usageTimeMillis % 3_600_000) / 60_000}m (${it.category})"
                }

                val userContext = buildString {
                    appendLine("User's ${if (isWeeklyTab) "Weekly" else "Daily"} Top App Usage:")
                    appendLine(appUsageContext)
                    if (customInstr.isNotBlank()) {
                        appendLine("\nUser Custom Instructions: $customInstr")
                    }
                    if (heavyDistractions.isNotEmpty()) {
                        val distractionList = heavyDistractions.joinToString { it.appName }
                        appendLine("\nHeavy distractions detected: $distractionList. Help them reduce this.")
                    }
                }

                val request = OpenAiRequest(
                    model = AI_MODEL_PRIMARY,
                    messages = listOf(
                        OpenAiMessage("system", MessageContent.Text("You are an expert productivity coach. Analyze the user's screen usage and provide personalized advice. Do NOT use emojis. Use Markdown formatting like **bold**, *italic*, and bullet points.")),
                        OpenAiMessage("user", MessageContent.Text("${userContext}\n\nGive me 3 short, actionable, and creative tips to improve focus based on this specific data. DO NOT USE ANY EMOJIS. Format your response with headers and bullet points. Output ONLY the tips."))
                    ),
                    maxTokens = 800,
                )

                val response = runGroqRequest(groqKey) { requestKey ->
                    openAiService.getChatCompletion(
                        url = GROQ_URL,
                        authHeader = "Bearer $requestKey",
                        request = request
                    ).choices.firstOrNull()?.message?.content
                }
                _screenTips.value = response ?: "Failed to generate tips."
            } catch (e: Exception) {
                _screenTips.value = "Error generating tips: ${e.message}"
            } finally {
                _isLoadingTips.value = false
            }
        }
    }

    private suspend fun <T> runGroqRequest(
        initialKey: String,
        requestBlock: suspend (String) -> T,
    ): T {
        return requestBlock(initialKey)
    }

    private fun parseClassificationResponse(
        json: String,
        validPackages: List<String>,
    ): Map<String, AppCategory> {
        val result = mutableMapOf<String, AppCategory>()
        Regex(""""([^"]+)"\s*:\s*"(productive|distraction)"""", RegexOption.IGNORE_CASE)
            .findAll(json)
            .forEach { match ->
                val pkg = match.groupValues[1]
                if (pkg in validPackages) {
                    result[pkg] = if (match.groupValues[2].lowercase() == "productive")
                        AppCategory.TOOLZ else AppCategory.DISTRACTION
                }
            }
        return result
    }

    // ── AI cache persistence ───────────────────────────────────────────────

    private fun saveAiCacheToPrefs(cache: Map<String, AppCategory>) {
        try {
            val json = JSONObject().apply { cache.forEach { (k, v) -> put(k, v.name) } }
            aiPrefs.edit().putString(KEY_CATEGORIES, json.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist AI cache: ${e.message}")
        }
    }

    private fun loadAiCacheFromPrefs(): Map<String, AppCategory> {
        val raw = aiPrefs.getString(KEY_CATEGORIES, null) ?: return emptyMap()
        return try {
            val json   = JSONObject(raw)
            val result = mutableMapOf<String, AppCategory>()
            json.keys().forEach { pkg ->
                val cat = AppCategory.entries.firstOrNull { it.name == json.getString(pkg) }
                if (cat != null) result[pkg] = cat
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load AI cache: ${e.message}")
            emptyMap()
        }
    }

    // ── Heuristics ─────────────────────────────────────────────────────────

    private fun guessCategory(packageName: String): AppCategory {
        val lower = packageName.lowercase()
        return when {
            lower == context.packageName.lowercase()         -> AppCategory.TOOLZ
            PRODUCTIVE_KEYWORDS.any  { lower.contains(it) } -> AppCategory.TOOLZ
            DISTRACTION_KEYWORDS.any { lower.contains(it) } -> AppCategory.DISTRACTION
            else                                             -> AppCategory.OTHER
        }
    }

    // ── Score ──────────────────────────────────────────────────────────────

    private fun calculateProductivityScore(list: List<AppUsageInfo>) {
        val toolz   = list.filter { it.category == AppCategory.TOOLZ       }.sumOf { it.usageTimeMillis }
        val distr   = list.filter { it.category == AppCategory.DISTRACTION }.sumOf { it.usageTimeMillis }
        val total   = toolz + distr
        _productivityScore.value =
            if (total == 0L) 50
            else ((toolz.toDouble() / total * 100).toInt()).coerceIn(5, 98)
    }

    // ── Network ────────────────────────────────────────────────────────────

    private fun isWifiConnected(): Boolean {
        val cm   = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Keyword sets
// ─────────────────────────────────────────────────────────────

private val PRODUCTIVE_KEYWORDS = setOf(
    "calculator", "notes", "note", "pdf", "office", "docs", "studio",
    "calendar", "chrome", "browser", "learn", "translate", "dictionary",
    "finance", "bank", "maps", "navigation", "email", "gmail", "drive",
    "sheets", "slides", "keep", "tasks", "clock", "weather", "camera",
    "gallery", "health", "fitness", "workout", "meditation", "reading",
    "epub", "kindle", "library", "code", "git", "editor",
)

private val DISTRACTION_KEYWORDS = setOf(
    "facebook", "instagram", "tiktok", "youtube", "twitter", "x.android",
    "snapchat", "netflix", "disney", "game", "pubg", "freefire", "reels",
    "shorts", "twitch", "reddit", "pinterest", "tumblr", "spotify",
    "soundcloud", "clash", "candy", "minecraft", "roblox", "brawl",
    "among", "fortnite", "garena", "mlbb", "mobilelegends", "likee",
    "kwai", "vigo", "helo", "moj", "roposo", "josh", "ludo", "carrom",
)