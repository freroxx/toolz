package com.frerox.toolz.data.focus

import android.graphics.drawable.Drawable

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMillis: Long,
    val limitMillis: Long? = null,
    val category: AppCategory = AppCategory.OTHER,
    val icon: Drawable? = null
)

enum class AppCategory {
    TOOLZ, DISTRACTION, OTHER
}
