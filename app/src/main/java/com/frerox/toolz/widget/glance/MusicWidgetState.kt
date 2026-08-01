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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

// ---------------------------------------------------------------------------
//  Music Widget — shared state keys (written by MusicPlayerService, read by
//  MusicGlanceWidget). Uses PreferencesGlanceStateDefinition backed by
//  DataStore<Preferences> so updates are efficient and reactive.
//
//  Progress is stored as a (positionMs, durationMs, capturedAtElapsedMs)
//  tuple rather than a single 0f-1f snapshot. The widget derives "now" by
//  adding elapsed wall-clock time since capturedAtElapsedMs to positionMs
//  at render time, so the bar reads as live between broadcasts instead of
//  only moving once per second when the service happens to re-broadcast.
//  capturedAtElapsedMs uses SystemClock.elapsedRealtime() (monotonic,
//  immune to clock/timezone changes), not System.currentTimeMillis().
// ---------------------------------------------------------------------------

object MusicWidgetStateDefinition : GlanceStateDefinition<androidx.datastore.preferences.core.Preferences>
by PreferencesGlanceStateDefinition

object MusicWidgetState {
    val KEY_TITLE    = stringPreferencesKey("mw_title")
    val KEY_ARTIST   = stringPreferencesKey("mw_artist")
    val KEY_ALBUM    = stringPreferencesKey("mw_album")
    val KEY_PLAYING  = booleanPreferencesKey("mw_is_playing")
    val KEY_ART_PATH = stringPreferencesKey("mw_art_path")    // absolute file path to processed bitmap
    val KEY_ART_SHAPE= stringPreferencesKey("mw_art_shape")   // "CIRCLE" | "SQUARE"
    val KEY_ACCENT_COLOR = stringPreferencesKey("mw_accent_color") // hex string
    val KEY_HAS_NEXT = booleanPreferencesKey("mw_has_next")
    val KEY_HAS_PREV = booleanPreferencesKey("mw_has_prev")
    val KEY_IS_FAVORITE = booleanPreferencesKey("mw_is_favorite")
    val KEY_NEXT_TITLE = stringPreferencesKey("mw_next_title")

    // Live/drift-corrected progress — see file doc above.
    val KEY_POSITION_MS = longPreferencesKey("mw_position_ms")
    val KEY_DURATION_MS = longPreferencesKey("mw_duration_ms")
    val KEY_CAPTURED_AT_ELAPSED_MS = longPreferencesKey("mw_captured_at_elapsed_ms")

    // Up-next queue, encoded as a compact JSON array of {id,title,artist,index}
    // objects (see QueueTrackInfo). org.json ships with the Android SDK
    // itself, so this avoids pulling in a serialization dependency just to
    // pass a handful of short strings through DataStore.
    val KEY_QUEUE_JSON = stringPreferencesKey("mw_queue_json")

    @Deprecated(
        "Replaced by KEY_POSITION_MS / KEY_DURATION_MS / KEY_CAPTURED_AT_ELAPSED_MS, " +
                "which let the widget interpolate a live position between broadcasts " +
                "instead of only showing whatever fraction was true at the moment of " +
                "the last one."
    )
    val KEY_PROGRESS = floatPreferencesKey("mw_progress")
}

/** One row of the widget's "Up Next" queue. */
data class QueueTrackInfo(
    val mediaId: String,
    val title: String,
    val artist: String,
    val queueIndex: Int
)