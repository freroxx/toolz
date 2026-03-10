package com.frerox.toolz.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.MusicPlayerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.music_widget)
            WidgetUtils.applyTheme(context, views, settingsRepository)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        
        // Trigger the service to update the widgets with the latest player state
        val intent = Intent(context, MusicPlayerService::class.java).apply {
            action = "UPDATE_WIDGET"
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {}
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        val action = intent.action
        if (action == "TOGGLE_PLAY" || action == "SKIP_NEXT" || action == "SKIP_PREV") {
            val serviceIntent = Intent(context, MusicPlayerService::class.java).apply {
                this.action = action
            }
            context.startService(serviceIntent)
        }
    }
}
