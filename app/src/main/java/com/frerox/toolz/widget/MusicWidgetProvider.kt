package com.frerox.toolz.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.frerox.toolz.service.MusicPlayerService

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Trigger the service to update the widgets with the latest player state
        val intent = Intent(context, MusicPlayerService::class.java).apply {
            action = "UPDATE_WIDGET"
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // Service might not be startable in background on some OS versions without foreground
        }
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
