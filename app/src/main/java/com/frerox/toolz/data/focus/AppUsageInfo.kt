package com.frerox.toolz.data.focus

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long,
    val todayUsageTimeMillis: Long = 0,
    val limitMillis: Long? = null,
    val category: AppCategory = AppCategory.OTHER,
    val isBlocked: Boolean = false
)

enum class AppCategory {
    TOOLZ, DISTRACTION, OTHER
}
