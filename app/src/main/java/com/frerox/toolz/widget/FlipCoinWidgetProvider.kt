package com.frerox.toolz.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.frerox.toolz.R
import kotlin.random.Random

class FlipCoinWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_FLIP) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FlipCoinWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.flip_coin_widget)
                val isHeads = Random.nextBoolean()
                
                views.setTextViewText(R.id.widget_coin_text, if (isHeads) "HEADS" else "TAILS")
                views.setImageViewResource(R.id.widget_coin_image, 
                    if (isHeads) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.flip_coin_widget)
        
        val intent = Intent(context, FlipCoinWidgetProvider::class.java).apply {
            action = ACTION_FLIP
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_coin_image, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        private const val ACTION_FLIP = "com.frerox.toolz.action.FLIP_COIN"
    }
}
