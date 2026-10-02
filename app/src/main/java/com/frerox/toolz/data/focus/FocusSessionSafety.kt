/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Pure, Android-free helpers backing the Focus Flow safe-revamp (Steps 0-5).
 * No Context, no IPC — safe to unit-test on the JVM.
 */

package com.frerox.toolz.data.focus

/** Accepted focus-session length in minutes. Picker offers 15/25/45/60. */
const val FOCUS_MIN_MINUTES = 1
const val FOCUS_MAX_MINUTES = 480

/** Returns the minutes when in range, null otherwise. No clamping — caller shows an error. */
fun validateFocusMinutes(minutes: Int): Int? =
    if (minutes in FOCUS_MIN_MINUTES..FOCUS_MAX_MINUTES) minutes else null

/**
 * Current production productivity-score formula, pinned by tests.
 * toolzMs = productive millis, distrMs = distraction millis (OTHER excluded, historical behavior).
 * Empty -> 50, otherwise percentage clamped to 5..98 (historical behavior).
 */
fun productivityScore(toolzMs: Long, distrMs: Long): Int {
    val total = toolzMs + distrMs
    if (total <= 0L) return 50
    return ((toolzMs.toDouble() / total * 100).toInt()).coerceIn(5, 98)
}

/**
 * Legacy suggestion formula (pinned): next 15-min step above current use, 15..120.
 * Kept for regression tests — do NOT use for new UI (it suggests < used when used > 105m).
 */
fun legacySuggestLimitMinutes(usedMs: Long): Long {
    val usedMinutes = usedMs / 60_000L
    return (((usedMinutes / 15) + 1) * 15).coerceIn(15L, 120L)
}

/**
 * Fixed suggestion formula: at least 15m above current use, rounded UP to 15m, 15..240.
 * used=0 -> 15, used=10 -> 30, used=120 -> 135.
 */
fun suggestLimitMinutes(usedMs: Long): Long {
    val usedMinutes = (usedMs / 60_000L).coerceAtLeast(0L)
    val target = usedMinutes + 15L
    val roundedUp = ((target + 14L) / 15L) * 15L
    return roundedUp.coerceIn(15L, 240L)
}

/** Sums all buckets for a package (queryUsageStats can return several rows per pkg). */
fun sumPackageUsage(entries: List<Pair<String, Long>>, packageName: String): Long =
    entries.filter { it.first == packageName }.sumOf { it.second }

/** Global locks for SharedPrefs files shared between VM and workers (same process). */
object FocusPrefsLocks {
    val usageCacheLock = Any()
    val aiCacheLock = Any()
}
