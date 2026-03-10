package com.frerox.toolz.widget

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object WidgetUtils {
    fun applyTheme(context: Context, views: RemoteViews, repository: SettingsRepository) {
        runBlocking {
            val bgColor = repository.widgetBackgroundColor.first()
            val opacity = repository.widgetOpacity.first()
            val accentColor = repository.widgetAccentColor.first()

            val alpha = (opacity * 255).toInt()
            val finalBgColor = Color.argb(
                alpha,
                Color.red(bgColor),
                Color.green(bgColor),
                Color.blue(bgColor)
            )

            views.setInt(R.id.widget_root, "setBackgroundColor", finalBgColor)
            
            // Calculate contrasting text color (Black or White)
            val luminance = 0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)
            val textColor = if (luminance > 128 || opacity < 0.5) Color.parseColor("#1D1B20") else Color.WHITE
            val subTextColor = if (luminance > 128 || opacity < 0.5) Color.parseColor("#49454F") else Color.parseColor("#CAC4D0")

            // Apply text colors to common IDs if they exist
            val textViews = intArrayOf(
                R.id.widget_music_title, R.id.widget_step_count, R.id.widget_coin_text,
                R.id.widget_note_title // Note: Notes widget uses ListView, handled in adapter/factory
            )
            for (id in textViews) {
                try { views.setTextColor(id, textColor) } catch (e: Exception) {}
            }

            val subTextViews = intArrayOf(
                R.id.widget_music_artist, R.id.widget_step_goal
            )
            for (id in subTextViews) {
                try { views.setTextColor(id, subTextColor) } catch (e: Exception) {}
            }

            // Dynamic Tinting for Accent Elements
            // We use setInt with "setColorFilter" or specific M3-like methods if available
            try {
                // For progress bars
                views.setInt(R.id.widget_music_progress, "setProgressTintPixels", accentColor)
                views.setInt(R.id.widget_step_progress, "setProgressTintPixels", accentColor)
                
                // For primary buttons
                views.setInt(R.id.widget_music_play_pause, "setColorFilter", Color.WHITE)
                // Note: Changing background tint of a drawable in RemoteViews is tricky,
                // but we can set the background color of the view itself if it's a simple shape.
            } catch (e: Exception) {}
        }
    }
}
