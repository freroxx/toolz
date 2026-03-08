package com.frerox.toolz.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.frerox.toolz.R

class CompassWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.compass_widget)
            // Logic to update rotation based on sensor would typically require a service
            // for real-time updates in a widget, which is battery intensive.
            // For now, we provide a stable static view that can be updated on tap or periodically.
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
