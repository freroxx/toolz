package com.frerox.toolz.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.widget.RemoteViews
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R

class FlashlightWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> toggleFlashlight(context)
            ACTION_STROBE, ACTION_SOS -> {
                // For Strobe and SOS, we'll open the app's flashlight screen as it requires complex timing
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("navigate_to", "flashlight")
                    putExtra("mode", if (intent.action == ACTION_STROBE) "strobe" else "sos")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(appIntent)
            }
        }
        
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, FlashlightWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.flashlight_widget)
        
        views.setOnClickPendingIntent(R.id.widget_flashlight_button, getPendingSelfIntent(context, ACTION_TOGGLE))
        views.setOnClickPendingIntent(R.id.widget_flashlight_strobe, getPendingSelfIntent(context, ACTION_STROBE))
        views.setOnClickPendingIntent(R.id.widget_flashlight_sos, getPendingSelfIntent(context, ACTION_SOS))
        
        // Update color based on state if we could track it reliably
        // For now, just ensure it's responsive
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getPendingSelfIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, FlashlightWidgetProvider::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun toggleFlashlight(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            isFlashlightOn = !isFlashlightOn
            cameraManager.setTorchMode(cameraId, isFlashlightOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.frerox.toolz.action.TOGGLE"
        private const val ACTION_STROBE = "com.frerox.toolz.action.STROBE"
        private const val ACTION_SOS = "com.frerox.toolz.action.SOS"
        private var isFlashlightOn = false
    }
}
