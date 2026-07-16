package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.frerox.toolz.service.MusicPlayerService
import com.frerox.toolz.util.VibrationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * High-performance callback for music widget controls.
 *
 * This class handles optimistic UI updates (toggling the play/pause or favorite
 * icon immediately in DataStore) and triggers haptic feedback via
 * [VibrationManager] before forwarding the intent to [MusicPlayerService].
 * This makes the widget feel instant, even if the service takes a moment
 * to process the playback command.
 */
class MusicActionCallback : ActionCallback {

    companion object {
        val PARAM_ACTION = ActionParameters.Key<String>("music_action")
        val PARAM_INDEX = ActionParameters.Key<Int>("music_index")

        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
        const val ACTION_FAVORITE = "favorite"
        const val ACTION_SEEK = "seek"

        // Extra key carrying which queue row was tapped
        const val EXTRA_QUEUE_INDEX = "com.frerox.toolz.EXTRA_QUEUE_INDEX"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MusicWidgetEntryPoint {
        fun vibrationManager(): VibrationManager
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[PARAM_ACTION] ?: return
        val haptics = EntryPointAccessors.fromApplication(
            context.applicationContext,
            MusicWidgetEntryPoint::class.java
        ).vibrationManager()

        // 1. Immediate Haptic Feedback
        haptics.vibrateClick()

        // 2. Optimistic UI Updates
        // Toggling these keys locally in Preferences makes Glance re-render
        // the icons instantly while the service works in the background.
        if (action == ACTION_TOGGLE || action == ACTION_FAVORITE) {
            updateAppWidgetState(context, glanceId) { prefs ->
                if (action == ACTION_TOGGLE) {
                    val current = prefs[MusicWidgetState.KEY_PLAYING] ?: false
                    prefs[MusicWidgetState.KEY_PLAYING] = !current
                } else {
                    val current = prefs[MusicWidgetState.KEY_IS_FAVORITE] ?: false
                    prefs[MusicWidgetState.KEY_IS_FAVORITE] = !current
                }
            }
            MusicGlanceWidget().update(context, glanceId)
        }

        // 3. Forward to Service
        val serviceIntent = Intent(context, MusicPlayerService::class.java).apply {
            this.action = when (action) {
                ACTION_TOGGLE -> MusicPlayerService.ACTION_TOGGLE_PLAY
                ACTION_NEXT -> MusicPlayerService.ACTION_SKIP_NEXT
                ACTION_PREV -> MusicPlayerService.ACTION_SKIP_PREV
                ACTION_FAVORITE -> MusicPlayerService.ACTION_TOGGLE_FAVORITE
                ACTION_SEEK -> MusicPlayerService.ACTION_SEEK_TO_QUEUE_INDEX
                else -> null
            }
            if (action == ACTION_SEEK) {
                putExtra(EXTRA_QUEUE_INDEX, parameters[PARAM_INDEX] ?: -1)
            }
        }

        if (serviceIntent.action != null) {
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            } catch (_: Exception) {
                // Fallback to regular start if foreground fails (e.g. background restriction)
                context.startService(serviceIntent)
            }
        }
    }
}
