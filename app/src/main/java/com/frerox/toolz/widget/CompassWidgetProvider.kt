package com.frerox.toolz.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.frerox.toolz.service.CompassService

class CompassWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Start service to handle orientation updates
        val intent = Intent(context, CompassService::class.java)
        context.startService(intent)
    }

    override fun onDisabled(context: Context) {
        // Stop service when last widget is removed
        val intent = Intent(context, CompassService::class.java)
        context.stopService(intent)
    }
}
