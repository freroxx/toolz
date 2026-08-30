/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.data.purgeshot

/**
 * Shared pure utilities for PurgeShot.
 *
 * Previously [formatDurationLabel] was duplicated identically in three places:
 *  - [PurgeShotHandler] (private)
 *  - [PurgeShotViewModel] (private)
 *  - [PurgeShotPopup] (private, as `presetLabelFor`)
 * A single change (e.g. adding "2 hours") had to be made in 3 files and
 * could drift silently. All three now delegate here.
 */
object PurgeShotUtils {

    fun formatDurationLabel(duration: Long): String = when (duration) {
        30_000L -> "30 sec"
        60_000L -> "1 min"
        5 * 60_000L -> "5 min"
        15 * 60_000L -> "15 min"
        30 * 60_000L -> "30 min"
        60 * 60_000L -> "1 hour"
        2 * 60 * 60_000L -> "2 hours"
        6 * 60 * 60_000L -> "6 hours"
        12 * 60 * 60_000L -> "12 hours"
        24 * 60 * 60_000L -> "1 day"
        2 * 24 * 60 * 60_000L -> "2 days"
        3 * 24 * 60 * 60_000L -> "3 days"
        7 * 24 * 60 * 60_000L -> "1 week"
        14 * 24 * 60 * 60_000L -> "2 weeks"
        30L * 24 * 60 * 60_000L -> "1 month"
        else -> if (duration < 60_000L) "${duration / 1000}s" else "${duration / 60_000} min"
    }
}
