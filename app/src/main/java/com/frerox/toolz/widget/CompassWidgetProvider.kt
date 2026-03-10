package com.frerox.toolz.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.CompassService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CompassWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.compass_widget)
            WidgetUtils.applyTheme(context, views, settingsRepository)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        // Ensure service is running when widgets are updated
        try {
            context.startService(Intent(context, CompassService::class.java))
        } catch (e: Exception) {}
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        try {
            context.startService(Intent(context, CompassService::class.java))
        } catch (e: Exception) {}
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        try {
            context.stopService(Intent(context, CompassService::class.java))
        } catch (e: Exception) {}
    }
}
