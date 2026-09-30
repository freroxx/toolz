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
import android.location.LocationListener
import android.location.LocationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class StepCounterService : Service(), SensorEventListener {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var stepRepository: StepRepository

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
    private var locationManager: LocationManager? = null
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
    private var isBackgroundNotificationsEnabled = true
    private var isGpsEnabled = false
    private var hasActivityPermission = false
    private var isBatterySaveActive = true
    private var isCounterEnabled = true

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val logTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val todayStr: String get() = LocalDate.now().format(dateFormatter)

    private var settingsJob: Job? = null

    private val binder = LocalBinder()

    // Engine Debug Logging — buffer only when observed to avoid hot-path churn.
    private var debugCallback: com.frerox.toolz.IEngineDebugCallback? = null
    private val engineLogBuffer = Collections.synchronizedList(mutableListOf<String>())

    fun setDebugCallback(callback: com.frerox.toolz.IEngineDebugCallback?) {
        debugCallback = callback
    }

    private fun logToDebug(message: String) {
        if (debugCallback == null) return
        val entry = "[${LocalTime.now().format(logTimeFormatter)}] $message"
        synchronized(engineLogBuffer) {
            engineLogBuffer.add(entry)
            if (engineLogBuffer.size > 100) engineLogBuffer.removeAt(0)
        }
        try {
            debugCallback?.onLogReceived(entry)
            
            // Update motion status
            val status = if (!isCounterEnabled) {
                "PAUSED"
            } else if (currentEngineMode == "STRICT") {
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
    // Persisted so reboot / process death / midnight don't stall counting.
    // ------------------------------------------------------------------
    private var lastRawStepCount = -1L
    private var sessionBaseSteps = -1L
    private var osStepsForwardedThisSession = 0L
    private var sessionDateStr: String = ""

    // ------------------------------------------------------------------
    // Location listener
    // ------------------------------------------------------------------
    private val locationListener = LocationListener { location ->
        if (!isCounterEnabled) return@LocationListener
        
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

    // ------------------------------------------------------------------
    // Foreground
    // ------------------------------------------------------------------
    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        sensorThread = android.os.HandlerThread("SensorThread").apply { start() }
        sensorHandler = sensorThread?.looper?.let { android.os.Handler(it) }

        sessionDateStr = LocalDate.now().format(dateFormatter)
        // Restore persisted OS-counter session (reboot / process death safe).
        serviceScope.launch {
            try {
                val savedBase = settingsRepository.stepSessionBase.first()
                val savedForwarded = settingsRepository.stepSessionForwarded.first()
                val savedDate = settingsRepository.stepSessionDate.first()
                val savedRaw = settingsRepository.lastOsStepCount.first()
                sensorMutex.withLock {
                    if (savedDate == sessionDateStr && savedBase >= 0) {
                        sessionBaseSteps = savedBase
                        osStepsForwardedThisSession = savedForwarded.coerceAtLeast(0)
                    }
                    if (savedRaw >= 0) lastRawStepCount = savedRaw
                }
            } catch (_: Exception) { }
        }

        initializeEngines()
    }

    private fun persistSession() {
        val base = sessionBaseSteps
        val forwarded = osStepsForwardedThisSession
        val date = sessionDateStr
        val raw = lastRawStepCount
        serviceScope.launch {
            try {
                settingsRepository.setStepSession(base, forwarded, date)
                if (raw >= 0) settingsRepository.setLastOsStepCount(raw)
            } catch (_: Exception) { }
        }
    }

    private fun initializeEngines() {
        logToDebug("Initializing engine: $currentEngineMode")
        if (currentEngineMode == "STRICT") {
            dspEngine = StrictEngine(
                onStepEmitted = { delta ->
                    if (!isCounterEnabled) return@StrictEngine
                    logToDebug("STRICT: Emitted $delta steps")
                    serviceScope.launch {
                        val today = todayStr
                        stepRepository.addStepsDelta(today, delta, delta)
                        markStepActivity(delta)
                        updateNotification()
                    }
                },
                onLog = { msg -> logToDebug(msg) }
            )
            simpleEngine = null
        } else {
            simpleEngine = SimpleStepEngine(
                onStepDetected = { countDelta, _ ->
                    if (!isCounterEnabled) return@SimpleStepEngine
                    logToDebug("SIMPLE: Emitted $countDelta steps")
                    serviceScope.launch {
                        val today = todayStr
                        stepRepository.addStepsDelta(today, countDelta, countDelta)
                        markStepActivity(countDelta)
                        updateNotification()
                    }
                },
                onLog = { msg -> logToDebug(msg) },
                useHardwareStepCounter = (stepSensor != null)
            )
            dspEngine = null
        }
    }

    private fun markStepActivity(delta: Int) {
        if (delta <= 0) return
        lastStepActivityTimeMs = SystemClock.elapsedRealtime()
        lastStepCountForGpsGate += delta
        // Wake GPS when movement resumes after deep sleep.
        if (isGpsEnabled && !gpsCurrentlyActive && isCounterEnabled) {
            startGpsTracking(highAccuracy = !isBatterySaveActive)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        hasActivityPermission = checkActivityPermission()

        if (!hasActivityPermission) {
            startForeground(NotificationHelper.ID_STEP_COUNTER, createPermissionNudgeNotification())
            return START_STICKY
        }

        // Start foreground with correct type — Android 14+ requires ACTIVITY_RECOGNITION
        startForegroundWithCorrectType(steps = 0)

        // Observe settings changes and update engine/location accordingly.
        // Single collector — cancelled and restarted on re-entry to avoid leaks.
        settingsJob?.cancel()
        settingsJob = serviceScope.launch {
            combine(
                combine(settingsRepository.stepGoal, settingsRepository.notificationsEnabled, settingsRepository.stepNotifications) { g: Int, n: Boolean, s: Boolean -> Triple(g, n, s) },
                combine(settingsRepository.stepCounterEnabled, settingsRepository.stepUseGps, settingsRepository.stepBatterySave) { c: Boolean, u: Boolean, b: Boolean -> Triple(c, u, b) },
                settingsRepository.stepSensitivity,
                settingsRepository.stepEngineMode,
                settingsRepository.backgroundNotificationsEnabled
            ) { t1: Triple<Int, Boolean, Boolean>, t2: Triple<Boolean, Boolean, Boolean>, sensitivity: Int, engineMode: String, backgroundEnabled: Boolean ->
                val goal = t1.first
                val globalEnabled = t1.second
                val stepEnabled = t1.third
                val counterEnabled = t2.first
                val useGps = t2.second
                val batterySave = t2.third

                isCounterEnabled = counterEnabled
                isBackgroundNotificationsEnabled = backgroundEnabled

                if (!counterEnabled) {
                    sensorManager?.unregisterListener(this@StepCounterService)
                    stopGpsTracking()
                    dspEngine?.resetEngine()
                    simpleEngine?.reset()
                    logToDebug("Engine PAUSED by user settings — stopped all sensors")
                    stopSelf()
                    return@combine
                }

                currentGoal = goal
                isNotificationEnabled = globalEnabled && stepEnabled
                // Refresh ongoing notification visibility when background toggle flips.
                updateNotificationVisibility()

                val batterySaveChanged = batterySave != isBatterySaveActive
                isBatterySaveActive = batterySave

                // GPS is strictly opt-in. STRICT mode no longer forces it on —
                // the engine works without GPS, GPS only adds distance validation.
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
        if (!isCounterEnabled) {
            sensorManager?.unregisterListener(this)
            return
        }

        // Battery tiers: saver = NORMAL (~5Hz), full = GAME (~50Hz).
        val accelDelay = if (isBatterySaveActive) SensorManager.SENSOR_DELAY_NORMAL
            else SensorManager.SENSOR_DELAY_GAME
        logToDebug("Registering sensors ($currentEngineMode): accelDelay=$accelDelay batterySave=$isBatterySaveActive")
        sensorManager?.unregisterListener(this)

        accelSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, accelDelay, sensorHandler)
        }

        // OS hardware counter is the source of truth in BOTH modes (when present).
        // STRICT previously ignored it entirely, wasting battery on accel DSP.
        stepSensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
        }

        if (currentEngineMode == "SIMPLE") {
            gyroSensor?.let { sensor ->
                sensorManager?.registerListener(this, sensor, accelDelay, sensorHandler)
            }
        }
    }

    // ------------------------------------------------------------------
    // Sensor Events
    // ------------------------------------------------------------------
    private fun sensorEventTimeMs(event: SensorEvent): Long {
        // event.timestamp = nanos since boot (same base as elapsedRealtimeNanos).
        // Wall-clock currentTimeMillis jumps with NTP/timezone — never use it here.
        return try {
            (event.timestamp / 1_000_000L).coerceAtLeast(0L)
        } catch (_: Exception) {
            SystemClock.elapsedRealtime()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isCounterEnabled) return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                onStepCounterEvent(event.values[0].toLong())
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val timeMs = sensorEventTimeMs(event)
                if (currentEngineMode == "STRICT") {
                    dspEngine?.processAccelerometer(event.values, timeMs)
                } else {
                    simpleEngine?.processAccelerometer(event.values, timeMs)
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val timeMs = sensorEventTimeMs(event)
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
     * Session deltas + midnight + reboot safe. Emits via atomic DB deltas.
     */
    private fun onStepCounterEvent(rawStepCount: Long) {
        serviceScope.launch {
            // Capture engine callbacks outside the mutex (they do DB I/O).
            var toForward = 0
            var isReboot = false
            var isFreshSession = false
            sensorMutex.withLock {
                val today = LocalDate.now().format(dateFormatter)
                // Midnight rollover: new day = new session base.
                if (sessionDateStr != today) {
                    sessionDateStr = today
                    sessionBaseSteps = rawStepCount
                    osStepsForwardedThisSession = 0L
                    lastRawStepCount = rawStepCount
                    persistSession()
                    return@withLock
                }

                if (sessionBaseSteps < 0) {
                    sessionBaseSteps = rawStepCount
                    isFreshSession = true
                    // Offline recovery: steps taken while service was dead.
                    // DataStore read on fresh session only (not hot path).
                    val lastSavedOsCount = try {
                        settingsRepository.lastOsStepCount.first()
                    } catch (_: Exception) { -1L }
                    if (lastSavedOsCount in 0 until rawStepCount) {
                        val offline = (rawStepCount - lastSavedOsCount).coerceAtMost(50_000L).toInt()
                        if (offline > 0) {
                            logToDebug("OFFLINE RECOVERY: +$offline steps while dead")
                            toForward = offline
                        }
                    }
                    lastRawStepCount = rawStepCount
                    persistSession()
                } else {
                    // Reboot / sensor reset: OS counter went backwards.
                    // Old code kept forwarded count -> permanent stall (delta <= 0 forever).
                    // Correct: start a new boot session, forwarded resets to 0.
                    if (lastRawStepCount >= 0 && rawStepCount < lastRawStepCount) {
                        isReboot = true
                        sessionBaseSteps = rawStepCount
                        osStepsForwardedThisSession = 0L
                        lastRawStepCount = rawStepCount
                        logToDebug("OS COUNTER RESET (reboot): base=$rawStepCount")
                        persistSession()
                        return@withLock
                    }

                    val sessionSteps = (rawStepCount - sessionBaseSteps).coerceAtLeast(0L)
                    val delta = (sessionSteps - osStepsForwardedThisSession).coerceAtLeast(0L)
                        .coerceAtMost(50_000L).toInt()
                    if (delta > 0) {
                        osStepsForwardedThisSession += delta
                        toForward = delta
                    }
                    if (rawStepCount != lastRawStepCount) {
                        lastRawStepCount = rawStepCount
                        persistSession()
                    }

                    // Adaptive GPS management
                    if (isGpsEnabled && toForward == 0) {
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

            if (toForward > 0 && !isReboot && isCounterEnabled) {
                if (currentEngineMode == "STRICT") {
                    dspEngine?.onOsStepDetected(toForward)
                } else {
                    simpleEngine?.onOsStepDetected(toForward)
                }
                // Engines emit via onStepEmitted -> addStepsDelta + markStepActivity.
                // If no hardware-gated engine path exists (shouldn't happen now),
                // fall back is handled by engine callbacks themselves.
                if (isFreshSession) logToDebug("Fresh session base=$rawStepCount")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGpsTracking(highAccuracy: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        if (gpsCurrentlyActive && gpsInLowPowerMode == !highAccuracy) return  // Already in this mode

        val intervalMs = if (highAccuracy) 10_000L else 30_000L
        val minDistanceM = if (highAccuracy) 2.0f else 5.0f

        locationManager?.removeUpdates(locationListener)
        try {
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervalMs,
                    minDistanceM,
                    locationListener,
                    mainLooper
                )
            } else if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    intervalMs,
                    minDistanceM,
                    locationListener,
                    mainLooper
                )
            }
            gpsCurrentlyActive = true
            gpsInLowPowerMode = !highAccuracy
        } catch (_: Exception) {}
    }

    private fun stopGpsTracking() {
        locationManager?.removeUpdates(locationListener)
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
            .setSmallIcon(R.drawable.ic_stat_toolz)
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
            .setSmallIcon(R.drawable.ic_stat_toolz)
            .setContentTitle("Step Tracker — Permission Required")
            .setContentText("Tap to grant Activity Recognition for accurate step counting.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        // Background toggle OFF → hide step-counter ongoing notification entirely.
        if (!isBackgroundNotificationsEnabled) {
            try {
                getSystemService(NotificationManager::class.java)?.cancel(NotificationHelper.ID_STEP_COUNTER)
            } catch (_: Exception) {}
            return
        }
        serviceScope.launch {
            val steps = stepRepository.currentSteps.first()
            withContext(Dispatchers.Main) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NotificationHelper.ID_STEP_COUNTER, createNotification(steps))
            }
        }
    }

    private fun updateNotificationVisibility() {
        if (!isBackgroundNotificationsEnabled) {
            try {
                getSystemService(NotificationManager::class.java)?.cancel(NotificationHelper.ID_STEP_COUNTER)
            } catch (_: Exception) {}
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        } else {
            updateNotification()
        }
    }

    private fun startForegroundWithCorrectType(steps: Int) {
        val notification = createNotification(steps)
        // Background toggle OFF → satisfy FGS start, then hide immediately.
        if (!isBackgroundNotificationsEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NotificationHelper.ID_STEP_COUNTER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
                } else {
                    startForeground(NotificationHelper.ID_STEP_COUNTER, notification)
                }
            } catch (_: Exception) {}
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            try { getSystemService(NotificationManager::class.java)?.cancel(NotificationHelper.ID_STEP_COUNTER) } catch (_: Exception) {}
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NotificationHelper.ID_STEP_COUNTER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NotificationHelper.ID_STEP_COUNTER, notification)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        settingsJob?.cancel()
        settingsJob = null
        debugCallback = null
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        sensorThread?.quitSafely()
        sensorHandler = null
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
