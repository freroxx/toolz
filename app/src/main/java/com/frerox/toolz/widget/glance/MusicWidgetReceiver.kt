package com.frerox.toolz.widget.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.frerox.toolz.service.MusicPlayerService

// ---------------------------------------------------------------------------
//  Music Widget Receiver — handles APPWIDGET_UPDATE + control broadcasts
// ---------------------------------------------------------------------------

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val serviceIntent = Intent(context, MusicPlayerService::class.java)
        when (intent.action) {
            MUSIC_ACTION_TOGGLE -> serviceIntent.action = "TOGGLE_PLAY"
            MUSIC_ACTION_NEXT   -> serviceIntent.action = "SKIP_NEXT"
            MUSIC_ACTION_PREV   -> serviceIntent.action = "SKIP_PREV"
            else -> return
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
