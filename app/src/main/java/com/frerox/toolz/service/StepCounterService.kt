package com.frerox.toolz.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.steps.StepDao
import com.frerox.toolz.data.steps.StepEntry
import com.frerox.toolz.data.steps.StepRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {

    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var stepRepository: StepRepository

    @Inject
    lateinit var stepDao: StepDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    private var currentGoal = 10000
    private var isNotificationEnabled = true
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val todayStr: String get() = dateFormat.format(Date())

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): StepCounterService = this@StepCounterService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        serviceScope.launch {
            combine(
                settingsRepository.stepGoal,
                settingsRepository.notificationsEnabled,
                settingsRepository.stepNotifications,
                settingsRepository.stepCounterEnabled
            ) { goal, globalEnabled, stepEnabled, counterEnabled ->
                if (!counterEnabled) {
                    stopSelf()
                }
                Triple(goal, globalEnabled, stepEnabled)
            }.collect { (goal, global, step) ->
                currentGoal = goal
                isNotificationEnabled = global && step
                updateNotification()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.ID_STEP_COUNTER, 
                createNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                } else {
                    0
                }
            )
        } else {
            startForeground(NotificationHelper.ID_STEP_COUNTER, createNotification())
        }
        
        registerSensor()
    }

    private fun registerSensor() {
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()
            
            serviceScope.launch {
                val currentEntry = stepDao.getStepsForDate(todayStr).first()
                
                if (currentEntry == null) {
                    // New day, initialize
                    stepDao.insertOrUpdateSteps(StepEntry(todayStr, 0, totalStepsSinceBoot))
                } else {
                    val lastSensorValue = currentEntry.lastSensorValue
                    
                    if (totalStepsSinceBoot != lastSensorValue) {
                        val stepsSinceLastUpdate = if (totalStepsSinceBoot < lastSensorValue) {
                            // Reboot happened
                            totalStepsSinceBoot
                        } else {
                            totalStepsSinceBoot - lastSensorValue
                        }
                        
                        val newSteps = currentEntry.steps + stepsSinceLastUpdate
                        stepDao.insertOrUpdateSteps(StepEntry(todayStr, newSteps, totalStepsSinceBoot))
                        
                        if (isNotificationEnabled) {
                            updateNotification()
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.StepCounter.route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val steps = try {
            runBlocking { stepRepository.currentSteps.first() }
        } catch (e: Exception) { 0 }
        
        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_STEP_COUNTER)
            .setContentTitle("Step Counter")
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)

        if (isNotificationEnabled) {
            builder.setContentText("$steps / $currentGoal steps")
                .setProgress(currentGoal, steps, false)
        } else {
            builder.setContentText("Step tracking active")
        }
        
        return builder.build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NotificationHelper.ID_STEP_COUNTER, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        serviceScope.cancel()
    }
}
