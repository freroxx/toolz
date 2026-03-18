package com.frerox.toolz.data.focus

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long,
    val limitMillis: Long? = null,
    val category: AppCategory = AppCategory.OTHER
)

enum class AppCategory {
    TOOLZ, DISTRACTION, OTHER
}
