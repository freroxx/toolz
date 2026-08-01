/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.steps.StepRepository
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.util.NotificationHelper
import com.frerox.toolz.util.StepTrackerUtils
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var stepRepository: StepRepository
    @Inject lateinit var stepDao: com.frerox.toolz.data.steps.StepDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ------------------------------------------------------------------
    // Sensor
    // ------------------------------------------------------------------
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    /**
     * Mutex that serialises sensor-event processing coroutines.
     * Prevents the race condition where two rapid sensor bursts both read
     * `lastSensorValue`, compute overlapping deltas, and both write — losing steps.
     */
    private val sensorMutex = Mutex()
    
    var dspEngine: StrictEngine? = null
    var simpleEngine: SimpleStepEngine? = null
    var currentEngineMode = "SIMPLE"

    // kept for future use, not used as a gate
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private var isAccelerometerActive = true  // kept for future use, not used as a gate
    private var lastAccelUpdate = 0L

    private var sensorThread: android.os.HandlerThread? = null
    private var sensorHandler: android.os.Handler? = null

    // ------------------------------------------------------------------
    // GPS
    // ------------------------------------------------------------------
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private val kalmanFilter = StepTrackerUtils.KalmanFilter()
    private var lastLocation: Location? = null
    private var gpsDistanceMeters: Double = 0.0
    private var gpsCurrentlyActive = false
    private var gpsInLowPowerMode = false
    
    // GPS adaptive polling — adapts based on activity level
    private var lastStepCountForGpsGate = 0
    private var lastStepActivityTimeMs = SystemClock.elapsedRealtime()
    private val GPS_SLEEP_AFTER_STATIC_MS = 90_000L   // 90s static → low-power mode
    private val GPS_DEEP_SLEEP_AFTER_MS = 120_000L    // 120s static → GPS off

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------
    private var currentGoal = 10000
    private var isNotificationEnabled = true
    private var isGpsEnabled = false
    private var hasActivityPermission = false
    private var isBatterySaveActive = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val todayStr: String get() = dateFormat.format(Date())

    private val binder = LocalBinder()

    // --- Engine Debug Logging ---
    private var debugCallback: com.frerox.toolz.IEngineDebugCallback? = null
    private val engineLogBuffer = Collections.synchronizedList(mutableListOf<String>())

    fun setDebugCallback(callback: com.frerox.toolz.IEngineDebugCallback?) {
        debugCallback = callback
    }

    private fun logToDebug(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = "[$time] $message"
        synchronized(engineLogBuffer) {
            engineLogBuffer.add(entry)
            if (engineLogBuffer.size > 100) engineLogBuffer.removeAt(0)
        }
        try {
            debugCallback?.onLogReceived(entry)
            
            // Update motion status
            val status = if (currentEngineMode == "STRICT") {
                dspEngine?.state?.name ?: "IDLE"
            } else {
                if (simpleEngine?.isSuspended == true) "SUSPENDED" else "ACTIVE"
            }
            debugCallback?.onMotionStatusChanged(status)
        } catch (e: Exception) {
            // Callback died
            debugCallback = null
        }
    }

    // ------------------------------------------------------------------
    // SENSOR RESET DETECTION & OFFLINE RECOVERY
    // ------------------------------------------------------------------
    private var lastRawStepCount = -1L
    private var sessionBaseSteps = -1L
    private var osStepsForwardedThisSession = 0L

    // ------------------------------------------------------------------
    // Location callback
    // ------------------------------------------------------------------
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            
            // Speedometer Anti-Cheat — if speed > 10 m/s, user is likely driving
            val driving = location.hasSpeed() && location.speed > 10.0f
            dspEngine?.forceSuspend(driving)
            simpleEngine?.setGpsSuspended(driving)
            
            lastLocation?.let { prev ->
                val dist = prev.distanceTo(location).toDouble()
                // Ignore noise < 2m and implausible GPS jumps > 50m in one update
                val isSuspended = if (currentEngineMode == "STRICT") {
                    dspEngine?.state == EngineState.SUSPENDED
                } else {
                    simpleEngine?.isSuspended ?: false
                }
                if (dist in 2.0..50.0 && !isSuspended) {
                    val smoothed = kalmanFilter.filter(dist)
                    gpsDistanceMeters += smoothed
                    serviceScope.launch(Dispatchers.Main) { updateNotification() }
                }
            }
            lastLocation = location
        }
    }

    // ------------------------------------------------------------------
    // Foreground
    // ------------------------------------------------------------------
    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        sensorThread = android.os.HandlerThread("SensorThread").apply { start() }
        sensorHandler = android.os.Handler(sensorThread!!.looper)

        initializeEngines()
    }

    private fun initializeEngines() {
        logToDebug("Initializing engine: $currentEngineMode")
        if (currentEngineMode == "STRICT") {
            dspEngine = StrictEngine(
                onStepEmitted = { delta ->
                    logToDebug("STRICT: Emitted $delta steps")
                    serviceScope.launch {
                        val today = todayStr
                        val existing = stepDao.getStepsForDateSync(today)?.steps ?: 0
                        val newTotal = (existing + delta).coerceAtLeast(0)
                        stepRepository.updateSteps(newTotal)
                        updateNotification()
                    }
                },
                onLog = { msg -> logToDebug(msg) }
            )
            simpleEngine = null
        } else {
            simpleEngine = SimpleStepEngine(
                onStepDetected = { countDelta, _ ->
                    logToDebug("SIMPLE: Emitted $countDelta steps")
                    serviceScope.launch {
                        val today = todayStr
                        val existing = stepDao.getStepsForDateSync(today)?.steps ?: 0
                        val newTotal = (existing + countDelta).coerceAtLeast(0)
                        stepRepository.updateSteps(newTotal)
                        updateNotification()
                    }
                },
                onLog = { msg -> logToDebug(msg) },
                useHardwareStepCounter = (stepSensor != null)
            )
            dspEngine = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        hasActivityPermission = checkActivityPermission()

        if (!hasActivityPermission) {
            startForeground(NotificationHelper.ID_STEP_COUNTER, createPermissionNudgeNotification())
            return START_STICKY
        }

        // (Initialization of today's baseline is no longer needed here as the delta logic handles it)

        // Start foreground with correct type — Android 14+ requires ACTIVITY_RECOGNITION
        startForegroundWithCorrectType(steps = 0)

        // Observe settings changes and update engine/location accordingly
        serviceScope.launch {
            combine(
                combine(settingsRepository.stepGoal, settingsRepository.notificationsEnabled, settingsRepository.stepNotifications) { g, n, s -> Triple(g, n, s) },
                combine(settingsRepository.stepCounterEnabled, settingsRepository.stepUseGps, settingsRepository.stepBatterySave) { c, u, b -> Triple(c, u, b) },
                settingsRepository.stepSensitivity,
                settingsRepository.stepEngineMode
            ) { t1, t2, sensitivity, engineMode ->
                val goal = t1.first
                val globalEnabled = t1.second
                val stepEnabled = t1.third
                val counterEnabled = t2.first
                val useGps = t2.second
                val batterySave = t2.third

                if (!counterEnabled) stopSelf()

                currentGoal = goal
                isNotificationEnabled = globalEnabled && stepEnabled

                val batterySaveChanged = batterySave != isBatterySaveActive
                isBatterySaveActive = batterySave

                val wantsGps = useGps
                if (wantsGps != isGpsEnabled || (batterySaveChanged && isGpsEnabled)) {
                    isGpsEnabled = wantsGps
                    if (isGpsEnabled) startGpsTracking(highAccuracy = !isBatterySaveActive) else stopGpsTracking()
                }

                val engineModeChanged = engineMode != currentEngineMode
                if (engineModeChanged) {
                    sensorManager?.unregisterListener(this@StepCounterService)
                    currentEngineMode = engineMode
                    initializeEngines()
                    registerSensor()
                    
                    // Enforce mandatory GPS for STRICT mode
                    if (currentEngineMode == "STRICT") {
                        isGpsEnabled = true
                        startGpsTracking(highAccuracy = true)
                    }
                }

                dspEngine?.setSensitivity(sensitivity)
                simpleEngine?.setSensitivity(sensitivity)

                if (batterySaveChanged && !engineModeChanged) {
                    registerSensor()
                }
            }.collect {}
        }

        registerSensor()
        return START_STICKY
    }

    private fun registerSensor() {
        // ALWAYS use GAME delay for engines to ensure we don't miss peaks.
        // SENSOR_DELAY_NORMAL (200ms) is too slow for DSP.
        val delay = SensorManager.SENSOR_DELAY_GAME
        
        logToDebug("Registering sensors: GAME delay (~20ms/50Hz)")
        sensorManager?.unregisterListener(this)

        if (currentEngineMode == "STRICT") {
            accelSensor?.let { sensor ->
                sensorManager?.registerListener(this, sensor, delay, sensorHandler)
            }
            // StrictEngine ONLY uses accelerometer. Gyro and Step Counter are NOT registered.
        } else {
            if (stepSensor != null) {
                sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
            } else {
                accelSensor?.let { sensor ->
                    sensorManager?.registerListener(this, sensor, delay, sensorHandler)
                }
                gyroSensor?.let { sensor ->
                    sensorManager?.registerListener(this, sensor, delay, sensorHandler)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Sensor Events
    // ------------------------------------------------------------------
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                onStepCounterEvent(event.values[0].toLong())
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val timeMs = System.currentTimeMillis()
                if (currentEngineMode == "STRICT") {
                    dspEngine?.processAccelerometer(event.values, timeMs)
                } else {
                    simpleEngine?.processAccelerometer(event.values, timeMs)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val timeMs = System.currentTimeMillis()
                val wx = event.values[0]
                val wy = event.values[1]
                val wz = event.values[2]
                val angularVelocity = kotlin.math.sqrt(wx * wx + wy * wy + wz * wz)
                if (currentEngineMode == "STRICT") {
                    // StrictEngine does not use gyroscope
                } else {
                    simpleEngine?.processGyroscope(angularVelocity, timeMs)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Don't stop tracking on low accuracy — the DSP engine handles noise internally
    }

    /**
     * Handles raw step-counter values from the OS.
     * Uses session-based deltas and offline recovery to avoid DB amplification.
     */
    private fun onStepCounterEvent(rawStepCount: Long) {
        serviceScope.launch {
            sensorMutex.withLock {
                if (sessionBaseSteps < 0) {
                    sessionBaseSteps = rawStepCount
                    
                    // Offline Recovery: Recover steps taken while service was dead
                    val lastSavedOsCount = settingsRepository.lastOsStepCount.first()
                    if (lastSavedOsCount in 0 until rawStepCount) {
                        val offlineSteps = (rawStepCount - lastSavedOsCount).toInt()
                        logToDebug("OFFLINE RECOVERY: Recovered $offlineSteps steps taken while service was dead.")
                        
                        if (currentEngineMode == "STRICT") {
                            dspEngine?.onOsStepDetected(offlineSteps)
                        } else {
                            simpleEngine?.onOsStepDetected(offlineSteps)
                        }
                    }
                    
                    settingsRepository.setLastOsStepCount(rawStepCount)
                    lastRawStepCount = rawStepCount
                    return@withLock
                }

                // Detect OS Counter reset (device reboot or sensor restart)
                if (rawStepCount < lastRawStepCount) {
                    sessionBaseSteps = rawStepCount
                    lastRawStepCount = rawStepCount
                    settingsRepository.setLastOsStepCount(rawStepCount)
                    
                    // Re-calculate sessionSteps based on new base
                    // osStepsForwardedThisSession is NOT reset to prevent double-counting
                }

                val sessionSteps = rawStepCount - sessionBaseSteps
                val deltaToForward = (sessionSteps - osStepsForwardedThisSession).toInt().coerceAtLeast(0)

                if (deltaToForward > 0) {
                    osStepsForwardedThisSession += deltaToForward
                    
                    if (currentEngineMode == "STRICT") {
                        dspEngine?.onOsStepDetected(deltaToForward)
                    } else {
                        simpleEngine?.onOsStepDetected(deltaToForward)
                    }
                    
                    lastStepActivityTimeMs = SystemClock.elapsedRealtime()
                    lastStepCountForGpsGate += deltaToForward
                }

                if (rawStepCount != lastRawStepCount) {
                    settingsRepository.setLastOsStepCount(rawStepCount)
                    lastRawStepCount = rawStepCount
                }

                // Adaptive GPS management
                if (isGpsEnabled) {
                    val idleTime = SystemClock.elapsedRealtime() - lastStepActivityTimeMs
                    if (idleTime > GPS_SLEEP_AFTER_STATIC_MS && gpsCurrentlyActive && !gpsInLowPowerMode) {
                        startGpsTracking(highAccuracy = false)
                    }
                    if (idleTime > GPS_DEEP_SLEEP_AFTER_MS && gpsCurrentlyActive) {
                        stopGpsTracking()
                    }
                }
            }
        }
    }

    private fun startGpsTracking(highAccuracy: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (gpsCurrentlyActive && gpsInLowPowerMode == !highAccuracy) return  // Already in this mode

        val request = if (highAccuracy) {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                .setMinUpdateIntervalMillis(5_000L)
                .build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 30_000L)
                .setMinUpdateIntervalMillis(20_000L)
                .setMaxUpdateDelayMillis(60_000L)
                .build()
        }

        fusedLocationClient?.requestLocationUpdates(request, locationCallback, mainLooper)
        gpsCurrentlyActive = true
        gpsInLowPowerMode = !highAccuracy
    }

    private fun stopGpsTracking() {
        fusedLocationClient?.removeLocationUpdates(locationCallback)
        lastLocation = null
        kalmanFilter.reset()
        gpsCurrentlyActive = false
        gpsInLowPowerMode = false
    }

    // ------------------------------------------------------------------
    // Notification
    // ------------------------------------------------------------------

    private fun createNotification(steps: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, Screen.StepCounter.route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 5001, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_STEP_COUNTER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (isNotificationEnabled) {
            val percent = if (currentGoal > 0) (steps * 100 / currentGoal) else 0
            val moveMin = StepTrackerUtils.calculateMoveMinutes(steps)
            builder.setContentTitle("Step Tracker — $percent% complete")
                .setContentText("$steps / $currentGoal steps  •  ${moveMin}m active")
                .setProgress(currentGoal, steps.coerceAtMost(currentGoal), false)

            if (isGpsEnabled && gpsDistanceMeters > 0) {
                val km = gpsDistanceMeters / 1_000.0
                builder.setSubText(String.format(Locale.US, "GPS distance: %.2f km", km))
            }
        } else {
            builder.setContentTitle("Step Tracker Active")
                .setContentText("Tracking your daily movement silently")
        }

        return builder.build()
    }

    private fun createPermissionNudgeNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 5002, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationHelper.baseBuilder(this, NotificationHelper.CHANNEL_STEP_COUNTER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Step Tracker — Permission Required")
            .setContentText("Tap to grant Activity Recognition for accurate step counting.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        serviceScope.launch {
            val steps = stepRepository.currentSteps.first()
            withContext(Dispatchers.Main) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NotificationHelper.ID_STEP_COUNTER, createNotification(steps))
            }
        }
    }

    private fun startForegroundWithCorrectType(steps: Int) {
        val notification = createNotification(steps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NotificationHelper.ID_STEP_COUNTER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NotificationHelper.ID_STEP_COUNTER, notification)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        sensorThread?.quitSafely()
        stopGpsTracking()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep service alive — step tracking should persist
    }

    private fun checkActivityPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): StepCounterService = this@StepCounterService
    }
}
