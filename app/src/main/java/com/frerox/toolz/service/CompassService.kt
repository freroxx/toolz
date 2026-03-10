package com.frerox.toolz.service

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.widget.RemoteViews
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.widget.CompassWidgetProvider
import com.frerox.toolz.widget.WidgetUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class CompassService : Service(), SensorEventListener {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var sensorManager: SensorManager? = null
    private var rotationVector: Sensor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastUpdate = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        rotationVector?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdate < 100) return // Limit updates to 10fps for battery
        lastUpdate = currentTime

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val normalizedAzimuth = (azimuth + 360) % 360

            updateCompassWidget(normalizedAzimuth)
        }
    }

    private fun updateCompassWidget(azimuth: Float) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, CompassWidgetProvider::class.java)
        val views = RemoteViews(packageName, R.layout.compass_widget)

        // Apply theme settings
        WidgetUtils.applyTheme(this, views, settingsRepository)

        // Rotate the dial
        views.setFloat(R.id.widget_compass_dial, "setRotation", -azimuth)
        
        appWidgetManager.updateAppWidget(componentName, views)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }
}
