package com.frerox.toolz.widget.glance

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// ---------------------------------------------------------------------------
//  Music Widget Receiver — handles APPWIDGET_UPDATE.
//  The actual control logic is handled by MusicActionCallback for better
//  performance and instant UI updates.
// ---------------------------------------------------------------------------

class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicGlanceWidget()
}
