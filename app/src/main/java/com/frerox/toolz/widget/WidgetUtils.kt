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
        val bgColor = runBlocking { repository.widgetBackgroundColor.first() }
        val accentColor = runBlocking { repository.widgetAccentColor.first() }
        val opacity = runBlocking { repository.widgetOpacity.first() }

        val alpha = (opacity * 255).toInt()
        val finalBgColor = Color.argb(
            alpha,
            Color.red(bgColor),
            Color.green(bgColor),
            Color.blue(bgColor)
        )

        views.setInt(R.id.widget_root, "setBackgroundColor", finalBgColor)
        // Some widgets might have specific accent elements
    }
}
