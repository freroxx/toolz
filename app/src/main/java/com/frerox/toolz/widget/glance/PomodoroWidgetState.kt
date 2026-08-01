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
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

// ---------------------------------------------------------------------------
//  Pomodoro Widget — shared state keys (written by ToolService, read by
//  PomodoroGlanceWidget).
// ---------------------------------------------------------------------------

object PomodoroWidgetStateDefinition : GlanceStateDefinition<androidx.datastore.preferences.core.Preferences>
by PreferencesGlanceStateDefinition

object PomodoroWidgetState {
    // "WORK" | "SHORT_BREAK" | "LONG_BREAK"
    val KEY_MODE          = stringPreferencesKey("pw_mode")
    val KEY_REMAINING_MS  = floatPreferencesKey("pw_remaining_ms")
    val KEY_TOTAL_MS      = floatPreferencesKey("pw_total_ms")
    val KEY_IS_RUNNING    = booleanPreferencesKey("pw_is_running")
    // Sessions completed today
    val KEY_SESSIONS_DONE = intPreferencesKey("pw_sessions_done")
    val KEY_SESSIONS_GOAL = intPreferencesKey("pw_sessions_goal")
}
