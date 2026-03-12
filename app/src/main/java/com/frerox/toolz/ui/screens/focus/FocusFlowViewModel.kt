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
    private val appLimitRepository: AppLimitRepository
) : ViewModel() {

    private val _usageStats = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val usageStats: StateFlow<List<AppUsageInfo>> = _usageStats.asStateFlow()

    private val _isWeekly = MutableStateFlow(false)
    val isWeekly = _isWeekly.asStateFlow()

    private val _productivityScore = MutableStateFlow(0)
    val productivityScore: StateFlow<Int> = _productivityScore.asStateFlow()

    private val _appLimits = appLimitRepository.allLimits.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val combinedUsageStats: Flow<List<AppUsageInfo>> = combine(_usageStats, _appLimits) { stats, limits ->
        stats.map { stat ->
            val limit = limits.find { it.packageName == stat.packageName }
            stat.copy(limitMillis = limit?.limitMillis)
        }
    }

    init {
        refreshStats()
        // Auto-refresh every minute
        viewModelScope.launch {
            while (true) {
                delay(60000)
                refreshStats()
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
            
            val startTime = if (_isWeekly.value) {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            
            val endTime = System.currentTimeMillis()

            val stats = withContext(Dispatchers.IO) {
                statsManager.queryUsageStats(
                    if (_isWeekly.value) UsageStatsManager.INTERVAL_WEEKLY else UsageStatsManager.INTERVAL_DAILY,
                    startTime, 
                    endTime
                )
            }
            
            val pm = context.packageManager

            val aggregatedStats = stats.groupBy { it.packageName }.mapValues { entry ->
                entry.value.sumOf { it.totalTimeInForeground }
            }

            val usageList = withContext(Dispatchers.Default) {
                aggregatedStats.filter { it.value >= 60000 }.map { (packageName, time) ->
                    val appInfo = try {
                        pm.getApplicationInfo(packageName, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }

                    val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: packageName
                    val icon = appInfo?.loadIcon(pm)
                    val category = guessCategory(packageName)
                    
                    AppUsageInfo(
                        packageName = packageName,
                        appName = appName,
                        usageTimeMillis = time,
                        category = category,
                        icon = icon
                    )
                }.sortedByDescending { it.usageTimeMillis }
            }

            _usageStats.value = usageList
            calculateProductivityScore(usageList)
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
            lower.contains("game") || lower.contains("pubg") || lower.contains("freefire") -> AppCategory.DISTRACTION
            
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
