package com.frerox.toolz.widget.glance

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition

// ---------------------------------------------------------------------------
//  Music Widget — shared state keys (written by MusicPlayerService, read by
//  MusicGlanceWidget). Uses PreferencesGlanceStateDefinition backed by
//  DataStore<Preferences> so updates are efficient and reactive.
// ---------------------------------------------------------------------------

object MusicWidgetStateDefinition : GlanceStateDefinition<androidx.datastore.preferences.core.Preferences>
by PreferencesGlanceStateDefinition

object MusicWidgetState {
    val KEY_TITLE    = stringPreferencesKey("mw_title")
    val KEY_ARTIST   = stringPreferencesKey("mw_artist")
    val KEY_PROGRESS = floatPreferencesKey("mw_progress")     // 0f–1f
    val KEY_PLAYING  = booleanPreferencesKey("mw_is_playing")
    val KEY_ART_PATH = stringPreferencesKey("mw_art_path")    // absolute file path to processed bitmap
    val KEY_ART_SHAPE= stringPreferencesKey("mw_art_shape")   // "CIRCLE" | "SQUARE"
}
