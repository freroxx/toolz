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

        private val EXCLUDED_PACKAGES = setOf(
            "android", "com.android.systemui",
            "com.android.launcher", "com.android.launcher3", "com.android.launcher2",
            "com.google.android.apps.nexuslauncher", "com.sec.android.app.launcher",
            "com.miui.home", "com.oneplus.launcher", "com.huawei.android.launcher",
            "com.vivo.launcher", "com.oppo.launcher", "com.asus.launcher",
            "com.realme.launcher", "com.nothing.launcher",
        )
    }

    // ── Persistent AI category cache ───────────────────────────────────────
    private val aiPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_AI_CACHE, Context.MODE_PRIVATE)

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
     *
     * **Why 2.5 s debounce fixes the flicker:**
     * When the overlay appears, the target app briefly goes to background,
     * triggering a usage re-check. Without debounce, the re-check sees the
     * app still over-limit → new overlay → old one destroyed → new one appears
     * → loop. The 2.5 s debounce ensures only stable state that hasn't changed
     * for 2.5 seconds reaches the service, so the brief backgrounding caused
     * by the overlay itself never triggers a redundant recreation.
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

    // ── Public API ─────────────────────────────────────────────────────────

    fun toggleWeekly(weekly: Boolean) {
        _isWeekly.value = weekly
        refreshStats()
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
            // Always pass the device's default timezone to Calendar so that
            // midnight boundaries match the user's local clock, not UTC.
            // The week rollback guard prevents DAY_OF_WEEK assignment from
            // jumping forward when today is before the locale's firstDayOfWeek.
            val tz  = TimeZone.getDefault()
            val cal = Calendar.getInstance(tz).apply {
                if (_isWeekly.value) {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    // If setting firstDayOfWeek moved us into the future,
                    // step back one full week
                    if (timeInMillis > now) add(Calendar.WEEK_OF_YEAR, -1)
                }
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE,      0)
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = cal.timeInMillis

            val usageList = withContext(Dispatchers.IO) {
                try {
                    statsManager.queryAndAggregateUsageStats(startTime, now)
                        .asSequence()
                        .filter { (pkg, stats) ->
                            stats.totalTimeInForeground >= 5_000L &&
                                    pkg !in EXCLUDED_PACKAGES &&
                                    pm.getLaunchIntentForPackage(pkg) != null
                        }
                        .mapNotNull { (pkg, stats) ->
                            val name = try {
                                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                            } catch (_: Exception) { return@mapNotNull null }
                            AppUsageInfo(
                                packageName     = pkg,
                                appName         = name,
                                usageTimeMillis = stats.totalTimeInForeground,
                            )
                        }
                        .toList()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query usage stats", e)
                    emptyList()
                }
            }

            _rawStats.value = usageList
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

    private suspend fun <T> runGroqRequest(
        initialKey: String,
        requestBlock: suspend (String) -> T,
    ): T {
        try {
            return requestBlock(initialKey)
        } catch (e: HttpException) {
            if (e.code() == 401 && !aiSettingsManager.hasUserApiKey("Groq")) {
                val refreshed = aiSettingsManager.refreshRemoteKeyAfterAuthFailure("Groq", initialKey)
                if (refreshed.source == com.frerox.toolz.data.ai.ApiKeySource.REMOTE &&
                    refreshed.value.isNotBlank() &&
                    refreshed.value != initialKey
                ) {
                    return requestBlock(refreshed.value)
                }
                throw IllegalStateException(
                    "The Toolz default key for Groq is unavailable. Refresh keys or add your own key in AI settings."
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
