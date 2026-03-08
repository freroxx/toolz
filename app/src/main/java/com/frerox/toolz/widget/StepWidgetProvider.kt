package com.frerox.toolz.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.steps.StepRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class StepWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var stepRepository: StepRepository

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val steps = stepRepository.currentSteps.value
            // In a real app, we'd fetch the goal from settings too.
            // For widget, we can't easily collect flows, so we use the current value.
            
            val views = RemoteViews(context.packageName, R.layout.step_widget)
            views.setTextViewText(R.id.widget_step_count, steps.toString())
            
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "step_counter")
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_step_count, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
