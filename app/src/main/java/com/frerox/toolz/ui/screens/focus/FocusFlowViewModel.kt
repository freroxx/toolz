package com.frerox.toolz.ui.screens.focus

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.AiSettingsManager
import com.frerox.toolz.data.ai.ApiKeySource
import com.frerox.toolz.data.ai.MessageContent
import com.frerox.toolz.data.ai.OpenAiMessage
import com.frerox.toolz.data.ai.OpenAiRequest
import com.frerox.toolz.data.ai.OpenAiService
import com.frerox.toolz.data.focus.AppCategory
import com.frerox.toolz.data.focus.AppLimit
import com.frerox.toolz.data.focus.AppLimitRepository
import com.frerox.toolz.data.focus.AppUsageInfo
import com.frerox.toolz.data.settings.SettingsRepository
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
) : ViewModel() {

    companion object {
        private const val TAG               = "FocusFlowViewModel"
        private const val AI_MODEL_PRIMARY  = "llama-3.3-70b-versatile"
        private const val AI_MODEL_FALLBACK = "llama-3.1-8b-instant"
        private const val GROQ_URL          = "https://api.groq.com/openai/v1/chat/completions"
        // SharedPreferences file + key for persisting AI-generated categories
        private const val PREFS_AI_CACHE    = "focus_ai_category_cache"
        private const val KEY_CATEGORIES    = "categories_json"
        private const val PREFS_USAGE_CACHE = "focus_daily_usage_cache"

        private val EXCLUDED_PACKAGES = setOf(
            "android", "com.android.systemui",
            "com.android.launcher", "com.android.launcher3", "com.android.launcher2",
            "com.google.android.apps.nexuslauncher", "com.sec.android.app.launcher",
            "com.miui.home", "com.oneplus.launcher", "com.huawei.android.launcher",
            "com.vivo.launcher", "com.oppo.launcher", "com.asus.launcher",
            "com.realme.launcher", "com.nothing.launcher",
            "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard",
        )
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

    // ── Combined stats ─────────────────────────────────────────────────────

    /**
     * Primary data source for the UI. Merges raw stats with limits, user
     * mappings, custom names, and AI categories — in that priority order.
     *
     * [distinctUntilChanged] prevents redundant UI recompositions and is the
     * first line of defence against overlay flicker.
     */
    val combinedUsageStats: Flow<List<AppUsageInfo>> = combine(
        _rawStats, _appLimits, userMappings, appNameMappings, _aiCategoryCache,
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
        }.sortedByDescending { it.usageTimeMillis }

    }.distinctUntilChanged()

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
                    topApps.sortByDescending { it.second }
                } catch(e: Exception) { Log.e(TAG, "Failed pulling daily usage", e) }
            }
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
            val statsManager = context.getSystemService(Context.USAGE_STATS_SERVICE)
                    as? UsageStatsManager ?: return@launch
            val pm  = context.packageManager
            val now = System.currentTimeMillis()

            // ── Timezone-correct interval ──────────────────────────────────
            val tz  = TimeZone.getDefault()
            val cal = Calendar.getInstance(tz)
            
            val todayStr = String.format(Locale.US, "%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

            cal.apply {
                if (_isWeekly.value) {
                    // Normalize to start of week (Monday) regardless of locale
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
                try {
                    val stats = statsManager.queryAndAggregateUsageStats(startTime, now)

                    // Detect the currently active (resumed) app session.
                    // We only add the extra ongoing time if the aggregate's lastTimeUsed predates
                    // the resume event, meaning the system hasn't yet flushed that session.
                    val ongoingSessions = mutableMapOf<String, Long>()
                    val recentEvents = statsManager.queryEvents(now - 600_000L, now) // last 10 min
                    // Track the last RESUMED and last PAUSED timestamps per pkg
                    val lastResumed = mutableMapOf<String, Long>()
                    val lastPaused  = mutableMapOf<String, Long>()

                    while (recentEvents.hasNextEvent()) {
                        val ev = android.app.usage.UsageEvents.Event()
                        recentEvents.getNextEvent(ev)
                        when (ev.eventType) {
                            android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ->
                                lastResumed[ev.packageName] = ev.timeStamp
                            android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                            android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED ->
                                lastPaused[ev.packageName] = ev.timeStamp
                        }
                    }

                    lastResumed.forEach { (pkg, resumeTime) ->
                        val pauseTime = lastPaused[pkg] ?: 0L
                        // App is still in foreground if it was resumed after the last pause
                        if (resumeTime > pauseTime) {
                            val ongoingMs = now - resumeTime
                            if (ongoingMs > 0L) {
                                // Only add if the aggregate stats don't already include this time.
                                // The aggregate lastTimeUsed tells us when the system last wrote stats.
                                val aggregateLastUsed = stats[pkg]?.lastTimeUsed ?: 0L
                                if (aggregateLastUsed < resumeTime) {
                                    // System hasn't flushed this session yet — add it
                                    ongoingSessions[pkg] = ongoingMs
                                }
                                // If aggregateLastUsed >= resumeTime, the system already included
                                // the in-progress session in totalTimeInForeground — don't double-add
                            }
                        }
                    }

                    stats.asSequence()
                        .filter { (pkg, usage) ->
                            val totalTime = usage.totalTimeInForeground + (ongoingSessions[pkg] ?: 0L)
                            val isToolz = pkg == context.packageName
                            (totalTime >= 5_000L || isToolz) &&
                                    pkg !in EXCLUDED_PACKAGES &&
                                    (isToolz || pm.getLaunchIntentForPackage(pkg) != null)
                        }
                        .mapNotNull { (pkg, usage) ->
                            val name = try {
                                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                            } catch (_: Exception) {
                                if (pkg == context.packageName) "Toolz" else return@mapNotNull null
                            }
                            AppUsageInfo(
                                packageName     = pkg,
                                appName         = name,
                                usageTimeMillis = usage.totalTimeInForeground + (ongoingSessions[pkg] ?: 0L),
                            )
                        }
                        .toList()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query usage stats", e)
                    emptyList()
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
        val currentMappings = userMappings.value
        val currentAiCache  = _aiCategoryCache.value

        // Only classify apps not covered by user mappings, AI cache, or heuristics
        val toClassify = apps.filter { info ->
            !currentMappings.containsKey(info.packageName) &&
                    !currentAiCache.containsKey(info.packageName) &&
                    guessCategory(info.packageName) == AppCategory.OTHER
        }

        if (toClassify.isEmpty()) return

        val groqKey = aiSettingsManager.resolveApiKeyWithRemoteSync("Groq").value
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
                val groqKey = aiSettingsManager.resolveApiKeyWithRemoteSync("Groq").value
                if (groqKey.isBlank()) {
                    _screenTips.value = "AI key not configured. Please supply a Groq key in AI Settings."
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
        try {
            return requestBlock(initialKey)
        } catch (e: HttpException) {
            if (e.code() == 401 && !aiSettingsManager.hasUserApiKey("Groq")) {
                val refreshed = aiSettingsManager.refreshRemoteKeyAfterAuthFailure("Groq", initialKey)
                if ((refreshed.source == ApiKeySource.REMOTE || refreshed.source == ApiKeySource.DEFAULT) &&
                    refreshed.value.isNotBlank() &&
                    refreshed.value != initialKey
                ) {
                    return requestBlock(refreshed.value)
                }
                throw IllegalStateException(
                    "The Toolz default key for Groq is invalid or unavailable. Please add your own key in AI settings."
                )
            }
            throw e
        }
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
