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
