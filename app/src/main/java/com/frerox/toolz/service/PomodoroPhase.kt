/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.service

/**
 * P-P0-01: Single source of truth for Pomodoro phase.
 *
 * ONE function shared by Service / ViewModel / UI. Never duplicate `%4` formulas.
 * [persistedCompleted] is the DataStore-backed completed work-session count
 * (sessions finished, NOT skips — skips never increment, P-P2-02).
 *
 * Rule: after completing work session number (persistedCompleted+1),
 * every 4th work session is followed by LONG, otherwise SHORT.
 * Breaks always return to WORK.
 */
fun nextModeAfterWork(persistedCompleted: Int): String {
    val safe = persistedCompleted.coerceAtLeast(0)
    return if ((safe + 1) % 4 == 0) "LONG_BREAK" else "SHORT_BREAK"
}

/** Full transition: WORK -> nextModeAfterWork(completed); any break -> WORK. */
fun nextPomodoroMode(currentMode: String, persistedCompleted: Int): String {
    return if (currentMode == "WORK") nextModeAfterWork(persistedCompleted) else "WORK"
}
