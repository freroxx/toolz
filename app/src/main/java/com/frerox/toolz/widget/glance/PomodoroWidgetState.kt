/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.widget.glance

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

// ---------------------------------------------------------------------------
//  Pomodoro Widget — shared state keys (written by ToolService, read by
//  PomodoroGlanceWidget). P-P1-03: Long millis (no Float rounding).
// ---------------------------------------------------------------------------

object PomodoroWidgetStateDefinition : GlanceStateDefinition<androidx.datastore.preferences.core.Preferences>
by PreferencesGlanceStateDefinition

object PomodoroWidgetState {
    // "WORK" | "SHORT_BREAK" | "LONG_BREAK"
    val KEY_MODE          = stringPreferencesKey("pw_mode")
    val KEY_REMAINING_MS  = longPreferencesKey("pw_remaining_ms")
    val KEY_TOTAL_MS      = longPreferencesKey("pw_total_ms")
    // Live interpolation anchor: elapsedRealtime at push time. Widget derives
    // now = remaining - (nowElapsed - capturedAt) while running.
    val KEY_CAPTURED_AT_ELAPSED_MS = longPreferencesKey("pw_captured_at_elapsed_ms")
    // Legacy Float keys (pre-P1-03) — read as fallback for widgets written
    // before the Long migration, then overwritten with Long on next push.
    val KEY_REMAINING_MS_LEGACY  = androidx.datastore.preferences.core.floatPreferencesKey("pw_remaining_ms")
    val KEY_TOTAL_MS_LEGACY      = androidx.datastore.preferences.core.floatPreferencesKey("pw_total_ms")
    val KEY_IS_RUNNING    = booleanPreferencesKey("pw_is_running")
    // Sessions completed today
    val KEY_SESSIONS_DONE = intPreferencesKey("pw_sessions_done")
    val KEY_SESSIONS_GOAL = intPreferencesKey("pw_sessions_goal")
}
