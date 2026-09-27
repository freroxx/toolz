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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

// ---------------------------------------------------------------------------
//  Stopwatch widget state — written by ToolService, read by
//  StopwatchGlanceWidget. Replaces the old Timer widget state (tw_* keys)
//  in place: same provider component, fresh sw_* keys, stale timer state
//  falls back to the idle defaults below.
//  Live protocol: BASE_ELAPSED is the elapsedRealtime anchor the run
//  started from (base = now - accumulated at start). While running the
//  widget derives now = nowElapsed - base; while paused it shows
//  ACCUMULATED verbatim. CAPTURED_AT stamps the push for drift math.
// ---------------------------------------------------------------------------

object StopwatchWidgetStateDefinition : GlanceStateDefinition<androidx.datastore.preferences.core.Preferences>
by PreferencesGlanceStateDefinition

object StopwatchWidgetState {
    val KEY_BASE_ELAPSED_MS = longPreferencesKey("sw_base_elapsed_ms")
    val KEY_ACCUMULATED_MS = longPreferencesKey("sw_accumulated_ms")
    val KEY_IS_RUNNING = booleanPreferencesKey("sw_is_running")
    val KEY_CAPTURED_AT_ELAPSED_MS = longPreferencesKey("sw_captured_at_elapsed_ms")
    val KEY_LAP_COUNT = intPreferencesKey("sw_lap_count")
    val KEY_LAST_LAP_MS = longPreferencesKey("sw_last_lap_ms")
}
