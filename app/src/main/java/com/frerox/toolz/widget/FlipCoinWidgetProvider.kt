package com.frerox.toolz.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class FlipCoinWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_FLIP) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                performFlipAnimation(context, appWidgetId)
            }
        }
    }

    private fun performFlipAnimation(context: Context, appWidgetId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.flip_coin_widget)
        WidgetUtils.applyTheme(context, views, settingsRepository)

        scope.launch {
            // "Animation" steps
            views.setTextViewText(R.id.widget_coin_text, "FLIPPING...")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            repeat(4) {
                delay(150)
                views.setImageViewResource(R.id.widget_coin_image, android.R.drawable.btn_star_big_off)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
                delay(150)
                views.setImageViewResource(R.id.widget_coin_image, android.R.drawable.btn_star_big_on)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }

            val isHeads = Random.nextBoolean()
            views.setTextViewText(R.id.widget_coin_text, if (isHeads) "HEADS" else "TAILS")
            views.setImageViewResource(R.id.widget_coin_image, 
                if (isHeads) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.flip_coin_widget)
        WidgetUtils.applyTheme(context, views, settingsRepository)
        
        val intent = Intent(context, FlipCoinWidgetProvider::class.java).apply {
            action = ACTION_FLIP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        private const val ACTION_FLIP = "com.frerox.toolz.action.FLIP_COIN"
    }
}
