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
        // Forward control actions to MusicPlayerService
        when (intent.action) {
            MUSIC_ACTION_TOGGLE -> context.startService(Intent(context, MusicPlayerService::class.java).apply { action = "TOGGLE_PLAY" })
            MUSIC_ACTION_NEXT   -> context.startService(Intent(context, MusicPlayerService::class.java).apply { action = "SKIP_NEXT" })
            MUSIC_ACTION_PREV   -> context.startService(Intent(context, MusicPlayerService::class.java).apply { action = "SKIP_PREV" })
        }
    }
}
