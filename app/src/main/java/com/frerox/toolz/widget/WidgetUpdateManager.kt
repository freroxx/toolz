package com.frerox.toolz.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.frerox.toolz.widget.glance.MusicGlanceWidget
import com.frerox.toolz.widget.glance.MusicWidgetState
import com.frerox.toolz.widget.glance.PomodoroGlanceWidget
import com.frerox.toolz.widget.glance.PomodoroWidgetState
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
        progress: Float,
        isPlaying: Boolean,
        artShape: String,
        artFilePath: String?
    ) {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(MusicGlanceWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[MusicWidgetState.KEY_TITLE] = title
                    this[MusicWidgetState.KEY_ARTIST] = artist
                    this[MusicWidgetState.KEY_PROGRESS] = progress
                    this[MusicWidgetState.KEY_PLAYING] = isPlaying
                    this[MusicWidgetState.KEY_ART_SHAPE] = artShape
                    if (artFilePath != null) {
                        this[MusicWidgetState.KEY_ART_PATH] = artFilePath
                    } else {
                        remove(MusicWidgetState.KEY_ART_PATH)
                    }
                }
            }
        }
        MusicGlanceWidget().updateAll(context)
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
            updateAppWidgetState(context, glanceId) { prefs ->
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
