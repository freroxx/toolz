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

package com.frerox.toolz.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.frerox.toolz.widget.glance.MusicGlanceWidget
import com.frerox.toolz.widget.glance.MusicWidgetState
import com.frerox.toolz.widget.glance.QueueTrackInfo
import com.frerox.toolz.widget.glance.encodeQueueJson
import com.frerox.toolz.widget.glance.PomodoroGlanceWidget
import com.frerox.toolz.widget.glance.PomodoroWidgetState
import com.frerox.toolz.widget.glance.PomodoroWidgetStateDefinition
import com.frerox.toolz.widget.glance.SearchBarGlanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun updateMusicWidget(
        title: String,
        artist: String,
        album: String?,
        positionMs: Long,
        durationMs: Long,
        capturedAtElapsedMs: Long,
        isPlaying: Boolean,
        hasNext: Boolean,
        hasPrev: Boolean,
        accentColor: String?,
        artShape: String,
        artFilePath: String?,
        isFavorite: Boolean,
        nextTitle: String?,
        queue: List<QueueTrackInfo>
    ) {
        val glanceIds = GlanceAppWidgetManager(context)
            .getGlanceIds(MusicGlanceWidget::class.java)

        if (glanceIds.isEmpty()) return // no widget instances on the home screen — nothing to push

        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[MusicWidgetState.KEY_TITLE] = title
                prefs[MusicWidgetState.KEY_ARTIST] = artist
                if (album != null) prefs[MusicWidgetState.KEY_ALBUM] = album
                prefs[MusicWidgetState.KEY_POSITION_MS] = positionMs
                prefs[MusicWidgetState.KEY_DURATION_MS] = durationMs
                prefs[MusicWidgetState.KEY_CAPTURED_AT_ELAPSED_MS] = capturedAtElapsedMs
                prefs[MusicWidgetState.KEY_PLAYING] = isPlaying
                prefs[MusicWidgetState.KEY_HAS_NEXT] = hasNext
                prefs[MusicWidgetState.KEY_HAS_PREV] = hasPrev
                if (accentColor != null) prefs[MusicWidgetState.KEY_ACCENT_COLOR] = accentColor
                prefs[MusicWidgetState.KEY_ART_SHAPE] = artShape
                if (artFilePath != null) prefs[MusicWidgetState.KEY_ART_PATH] = artFilePath
                prefs[MusicWidgetState.KEY_IS_FAVORITE] = isFavorite
                if (nextTitle != null) {
                    prefs[MusicWidgetState.KEY_NEXT_TITLE] = nextTitle
                } else {
                    prefs.remove(MusicWidgetState.KEY_NEXT_TITLE)
                }
                prefs[MusicWidgetState.KEY_QUEUE_JSON] = encodeQueueJson(queue)
            }
            // Explicitly trigger a refresh for this instance
            MusicGlanceWidget().update(context, glanceId)
        }
    }

    suspend fun updatePomodoroWidget(
        mode: String,
        remainingMs: Float,
        totalMs: Float,
        isRunning: Boolean,
        sessionsDone: Int? = null,
        sessionsGoal: Int? = null
    ) {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(PomodoroGlanceWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, PomodoroWidgetStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[PomodoroWidgetState.KEY_MODE] = mode
                    this[PomodoroWidgetState.KEY_REMAINING_MS] = remainingMs
                    this[PomodoroWidgetState.KEY_TOTAL_MS] = totalMs
                    this[PomodoroWidgetState.KEY_IS_RUNNING] = isRunning
                    sessionsDone?.let { this[PomodoroWidgetState.KEY_SESSIONS_DONE] = it }
                    sessionsGoal?.let { this[PomodoroWidgetState.KEY_SESSIONS_GOAL] = it }
                }
            }
        }
        PomodoroGlanceWidget().updateAll(context)
    }

    suspend fun updateSearchBarWidget() {
        // SearchBar doesn't have dynamic state yet, but we provide this for consistency
        SearchBarGlanceWidget().updateAll(context)
    }
}
