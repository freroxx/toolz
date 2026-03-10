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

            // Set the background color of the root layout
            views.setInt(R.id.widget_root, "setBackgroundColor", finalBgColor)
            
            // Calculate contrasting text color
            val luminance = 0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)
            val isDarkBg = (luminance < 128 && opacity > 0.5)
            
            val textColor = if (isDarkBg) Color.WHITE else Color.parseColor("#1D1B20")
            val subTextColor = if (isDarkBg) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F")
            val iconTint = if (isDarkBg) Color.WHITE else accentColor

            // Apply colors to views if they exist in the current layout
            
            // Main Text Colors
            setTextViewColor(views, R.id.widget_music_title, textColor)
            setTextViewColor(views, R.id.widget_step_count, textColor)
            setTextViewColor(views, R.id.widget_coin_text, textColor)
            setTextViewColor(views, R.id.widget_note_title, textColor)

            // Subtext Colors
            setTextViewColor(views, R.id.widget_music_artist, subTextColor)
            setTextViewColor(views, R.id.widget_step_goal, subTextColor)
            setTextViewColor(views, R.id.widget_step_label, subTextColor)
            setTextViewColor(views, R.id.widget_compass_label, subTextColor)
            setTextViewColor(views, R.id.widget_coin_label, subTextColor)

            // Icon Tints
            setIconColor(views, R.id.widget_step_icon, iconTint)
            setIconColor(views, R.id.widget_compass_icon, iconTint)
            setIconColor(views, R.id.widget_music_prev, iconTint)
            setIconColor(views, R.id.widget_music_next, iconTint)
            
            // Progress elements and primary buttons use accent
            try {
                // setProgressTintPixels is used via setInt for dynamic tinting
                views.setInt(R.id.widget_music_progress, "setProgressTintPixels", accentColor)
                views.setInt(R.id.widget_step_progress, "setProgressTintPixels", accentColor)
                
                // For primary buttons
                views.setInt(R.id.widget_music_play_pause, "setBackgroundColor", accentColor)
                views.setInt(R.id.widget_flashlight_button, "setBackgroundColor", accentColor)
            } catch (e: Exception) {}
        }
    }

    private fun setTextViewColor(views: RemoteViews, viewId: Int, color: Int) {
        try {
            // Check if the resource exists to avoid "Unresolved reference" during compilation 
            // if XMLs are not yet fully processed, but since we are using R.id directly, 
            // the build system should find them if they are in the XML files.
            views.setTextColor(viewId, color)
        } catch (e: Exception) {}
    }

    private fun setIconColor(views: RemoteViews, viewId: Int, color: Int) {
        try {
            views.setInt(viewId, "setColorFilter", color)
        } catch (e: Exception) {}
    }
}
