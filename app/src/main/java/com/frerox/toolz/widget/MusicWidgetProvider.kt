package com.frerox.toolz.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.service.MusicPlayerService

class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Widgets are updated via the MusicPlayerService
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
