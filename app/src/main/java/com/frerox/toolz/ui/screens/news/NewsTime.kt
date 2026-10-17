package com.frerox.toolz.ui.screens.news

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Shared news time helpers — pure on (epoch, now) so they are unit-testable
 * without Android or locale mocking. Labels for Today/Yesterday are passed
 * in by the UI (stringResource) so the logic stays localization-agnostic.
 */
object NewsTime {

    private val SHORT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val SHORT_YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    private const val MINUTE_MS = 60L * 1000
    private const val HOUR_MS = 60L * 60 * 1000
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Date chip for popup header + history cards. */
    fun newsDate(publishAt: Long, now: Long, todayLabel: String, yesterdayLabel: String): String {
        if (publishAt <= 0L) return ""
        return try {
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(publishAt).atZone(zone).toLocalDate()
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            when {
                day == today || publishAt > now -> todayLabel
                day == today.minusDays(1) -> yesterdayLabel
                day.year == today.year -> SHORT_FORMAT.format(day)
                else -> SHORT_YEAR_FORMAT.format(day)
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** NEW badge: published within the last 7 days and not in the future. */
    fun isRecent(publishAt: Long, now: Long): Boolean =
        publishAt in 1..now && now - publishAt < 7L * DAY_MS

    /** Expired items keep living in history; surface an honest "Expired" chip. */
    fun isExpired(expiresAt: Long?, now: Long): Boolean =
        expiresAt != null && expiresAt <= now

    /**
     * Short relative delta for the "updated X ago" sync stamp:
     * Xm / Xh / Xd, minimum 1m; empty beyond 7 days (caller falls back
     * to a plain date string).
     */
    fun relativeShort(pastMs: Long, now: Long): String {
        if (pastMs <= 0L) return ""
        val delta = now - pastMs
        return when {
            delta < MINUTE_MS -> "1m"
            delta < HOUR_MS -> "${(delta / MINUTE_MS)}m"
            delta < DAY_MS -> "${(delta / HOUR_MS)}h"
            delta < 7L * DAY_MS -> "${(delta / DAY_MS)}d"
            else -> ""
        }
    }
}
