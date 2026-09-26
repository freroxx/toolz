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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

// ---------------------------------------------------------------------------
//  Timer widget state — written by ToolService, read by TimerGlanceWidget.
//  Long millis + elapsedRealtime anchor so the widget interpolates live
//  between pushes (same protocol as Pomodoro).
// ---------------------------------------------------------------------------

object TimerWidgetStateDefinition : GlanceStateDefinition<androidx.datastore.preferences.core.Preferences>
by PreferencesGlanceStateDefinition

object TimerWidgetState {
    val KEY_REMAINING_MS = longPreferencesKey("tw_remaining_ms")
    val KEY_TOTAL_MS = longPreferencesKey("tw_total_ms")
    val KEY_IS_RUNNING = booleanPreferencesKey("tw_is_running")
    val KEY_IS_RINGING = booleanPreferencesKey("tw_is_ringing")
    val KEY_CAPTURED_AT_ELAPSED_MS = longPreferencesKey("tw_captured_at_elapsed_ms")
}
