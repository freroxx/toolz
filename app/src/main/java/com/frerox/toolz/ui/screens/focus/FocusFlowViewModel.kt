package com.frerox.toolz.ui.screens.focus

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.focus.AppCategory
import com.frerox.toolz.data.focus.AppLimit
import com.frerox.toolz.data.focus.AppLimitRepository
import com.frerox.toolz.data.focus.AppUsageInfo
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

@HiltViewModel
class FocusFlowViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLimitRepository: AppLimitRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _usageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    
    private val _isWeekly = MutableStateFlow(false)
    val isWeekly = _isWeekly.asStateFlow()

    private val _productivityScore = MutableStateFlow(0)
    val productivityScore: StateFlow<Int> = _productivityScore.asStateFlow()

    private val _performanceMode = settingsRepository.performanceMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val performanceMode = _performanceMode

    private val _appLimits = appLimitRepository.allLimits.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val userMappings = settingsRepository.appCategoryMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val appNameMappings = settingsRepository.appNameMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val combinedUsageStats: Flow<List<AppUsageInfo>> = combine(_usageStats, _appLimits, userMappings, appNameMappings) { stats, limits, mappings, nameMap ->
        stats.map { stat ->
            val limit = limits.find { it.packageName == stat.packageName }
            val mappedCategory = mappings[stat.packageName]?.let { 
                if (it == "Productive") AppCategory.TOOLZ else AppCategory.DISTRACTION 
            }
            val customName = nameMap[stat.packageName]
            stat.copy(
                appName = customName ?: stat.appName,
                limitMillis = limit?.limitMillis,
                category = mappedCategory ?: guessCategory(stat.packageName)
            )
        }.sortedByDescending { it.usageTimeMillis }
    }

    init {
        refreshStats()
        // Auto-refresh every minute
        viewModelScope.launch {
            while (true) {
                delay(if (_performanceMode.value) 120000 else 60000)
                refreshStats()
            }
        }
        
        // Recalculate score when mappings change
        viewModelScope.launch {
            combinedUsageStats.collect { 
                calculateProductivityScore(it)
            }
        }
    }

    fun toggleWeekly(weekly: Boolean) {
        _isWeekly.value = weekly
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val statsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val calendar = Calendar.getInstance()
            
            val startTime: Long
            val endTime = System.currentTimeMillis()
            
            if (_isWeekly.value) {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                startTime = calendar.timeInMillis
            } else {
                // Today: from 00:00 to now (12PM colloquially means end of day or noon, 
                // but "day's screen time" usually means midnight to midnight)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                startTime = calendar.timeInMillis
            }

            // Using queryAndAggregateUsageStats for better performance and "Settings-like" accuracy
            val aggregatedStats = withContext(Dispatchers.IO) {
                statsManager.queryAndAggregateUsageStats(startTime, endTime)
            }
            
            val pm = context.packageManager

            val usageList = withContext(Dispatchers.Default) {
                aggregatedStats.filter { it.value.totalTimeInForeground >= 60000 }.mapNotNull { (packageName, stats) ->
                    val appInfo = try {
                        pm.getApplicationInfo(packageName, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                    
                    if (appInfo == null) return@mapNotNull null

                    val appName = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        packageName
                    }
                    
                    AppUsageInfo(
                        packageName = packageName,
                        appName = appName,
                        usageTimeMillis = stats.totalTimeInForeground,
                        icon = null // We'll load icons lazily in the UI or use a placeholder to save memory
                    )
                }
            }

            _usageStats.value = usageList
        }
    }

    fun setAppLimit(packageName: String, limitMinutes: Long) {
        viewModelScope.launch {
            appLimitRepository.setLimit(
                AppLimit(packageName, limitMinutes * 60 * 1000)
            )
        }
    }

    fun removeAppLimit(packageName: String) {
        viewModelScope.launch {
            val limit = appLimitRepository.getLimitForApp(packageName)
            if (limit != null) {
                appLimitRepository.removeLimit(limit)
            }
        }
    }

    fun updateAppCategory(packageName: String, isProductive: Boolean) {
        viewModelScope.launch {
            val category = if (isProductive) "Productive" else "Distraction"
            settingsRepository.setAppCategoryMapping(packageName, category)
        }
    }

    fun renameApp(packageName: String, customName: String) {
        viewModelScope.launch {
            settingsRepository.setAppNameMapping(packageName, customName)
        }
    }

    private fun guessCategory(packageName: String): AppCategory {
        val lower = packageName.lowercase()
        return when {
            lower == context.packageName.lowercase() -> AppCategory.TOOLZ
            lower.contains("calculator") || lower.contains("note") || lower.contains("pdf") || 
            lower.contains("office") || lower.contains("studio") || lower.contains("calendar") ||
            lower.contains("browser") || lower.contains("chrome") || lower.contains("learn") -> AppCategory.TOOLZ
            
            lower.contains("facebook") || lower.contains("instagram") || lower.contains("tiktok") || 
            lower.contains("youtube") || lower.contains("twitter") || lower.contains("x.android") || 
            lower.contains("snapchat") || lower.contains("netflix") || lower.contains("disney") || 
            lower.contains("game") || lower.contains("pubg") || lower.contains("freefire") ||
            lower.contains("reels") || lower.contains("shorts") -> AppCategory.DISTRACTION
            
            else -> AppCategory.OTHER
        }
    }

    private fun calculateProductivityScore(list: List<AppUsageInfo>) {
        val toolzTime = list.filter { it.category == AppCategory.TOOLZ }.sumOf { it.usageTimeMillis }
        val distractionTime = list.filter { it.category == AppCategory.DISTRACTION }.sumOf { it.usageTimeMillis }
        
        if (toolzTime + distractionTime == 0L) {
            _productivityScore.value = 50
            return
        }

        val score = (toolzTime.toDouble() / (toolzTime + distractionTime).toDouble() * 100).toInt()
        _productivityScore.value = score.coerceIn(5, 98)
    }
}
