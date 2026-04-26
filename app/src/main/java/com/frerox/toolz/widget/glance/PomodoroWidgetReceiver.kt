package com.frerox.toolz.widget.glance

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.frerox.toolz.service.ToolService

// ---------------------------------------------------------------------------
//  Pomodoro Widget Receiver — forwards control broadcasts to ToolService
// ---------------------------------------------------------------------------

class PomodoroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PomodoroGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = when (intent.action) {
            POMODORO_ACTION_TOGGLE -> ToolService.ACTION_POMODORO_TOGGLE
            POMODORO_ACTION_RESET  -> ToolService.ACTION_POMODORO_RESET
            POMODORO_ACTION_SKIP   -> ToolService.ACTION_POMODORO_SKIP
            else                   -> return
        }
        val serviceIntent = Intent(context, ToolService::class.java).apply { this.action = action }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
