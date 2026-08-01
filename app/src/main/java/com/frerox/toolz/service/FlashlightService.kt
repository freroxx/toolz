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

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.repository.FlashlightRepository
import com.frerox.toolz.ui.screens.light.FlashlightMode
import android.content.ComponentName
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

private const val TAG        = "FlashlightService"
private const val CHANNEL_ID = "flashlight_v2"
private const val NOTIF_ID   = 1001

// ─────────────────────────────────────────────────────────────────────────────
// FlashlightService — redesigned foreground service
//
//  Key improvements over previous version:
//  1. Single consolidated ACTION_CYCLE_MODE replaces hidden mode management.
//  2. Dual RemoteViews: compact (single-line) + expanded (full control row).
//  3. FOREGROUND_SERVICE_TYPE_CAMERA declared on Android 14+.
//  4. Broadcast ACTION_STATE_CHANGED so tiles/widgets stay in sync without
//     polling the singleton — singleton kept only as a lightweight optimisation
//     for in-process access (ViewModel, TileService).
//  5. nudgeBrightness replaces discrete step logic; clamps to [0.10, 1.00].
//  6. Notification channel upgraded: setSilent + FOREGROUND_SERVICE_IMMEDIATE.
//  7. modeColor / modeLabel / modeIcon helpers centralize mode-specific theming
//     and are reused by both compact and expanded RemoteViews.
//
// I stopped at 7 so y'all can see the 67, yes this is childish, I know...
// ─────────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class FlashlightService : Service() {

    @Inject lateinit var dataStore:  DataStore<Preferences>
    @Inject lateinit var repository: FlashlightRepository

    private val scope          = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var cam:  CameraManager
    private var cameraId:      String?  = null
    private var brightSupport: Boolean  = false
    private var maxBright:     Int      = 1
    private var modeJob:       Job?     = null
    private var timerJob:      Job?     = null

    private val _mode           = MutableStateFlow(FlashlightMode.STEADY)
    private val _brightness     = MutableStateFlow(1.0f)
    private val _strobeMs       = MutableStateFlow(80L)
    private val _discoRange     = MutableStateFlow(40L to 300L)
    private val _timerMinutes   = MutableStateFlow(0)

    val isOn: StateFlow<Boolean> get() = repository.isOn

    companion object {
        const val ACTION_TOGGLE        = "com.frerox.toolz.FLASHLIGHT_TOGGLE"
        const val ACTION_STOP          = "com.frerox.toolz.FLASHLIGHT_STOP"
        const val ACTION_BRIGHTNESS_UP = "com.frerox.toolz.FLASHLIGHT_BRIGHT_UP"
        const val ACTION_BRIGHTNESS_DN = "com.frerox.toolz.FLASHLIGHT_BRIGHT_DN"
        const val ACTION_CYCLE_MODE    = "com.frerox.toolz.FLASHLIGHT_CYCLE_MODE"
        const val ACTION_STATE_CHANGED = "com.frerox.toolz.FLASHLIGHT_STATE_CHANGED"

        @Volatile private var _inst: FlashlightService? = null
        fun getInstance(): FlashlightService? = _inst
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private var persistentNotif = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) {
                repository.setOn(enabled)
                if (enabled) {
                    runAsForeground()
                } else {
                    // Stop foreground but keep notification if persistent
                    if (persistentNotif) {
                        runAsForeground()
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                broadcastStateChange()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        _inst = this
        try {
            cam = getSystemService(CAMERA_SERVICE) as CameraManager
            findFlashCamera()
            cam.registerTorchCallback(torchCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CameraManager", e)
        }
        createNotificationChannel()
        scope.launch { loadSettings() }

        scope.launch {
            dataStore.data.map { it[booleanPreferencesKey("flashlight_notifications")] ?: true }
                .collect { enabled ->
                    persistentNotif = enabled
                    if (enabled || isOn.value) {
                        runAsForeground()
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        // Only stopSelf if we're not in the middle of an operation
                        if (!isOn.value) stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        // Handle incoming data if any
        when (action) {
            ACTION_BRIGHTNESS_UP, ACTION_BRIGHTNESS_DN, ACTION_CYCLE_MODE -> {}
            else -> intent?.let { handleExtras(it) }
        }

        // CRITICAL: If started via startForegroundService, we MUST call startForeground 
        // within 5 seconds. We do it immediately unless it's an explicit stop action.
        if (action != ACTION_STOP) {
            runAsForeground()
        }

        scope.launch {
            when (action) {
                ACTION_TOGGLE        -> if (isOn.value) doStop(hard = false) else doStart()
                ACTION_STOP          -> doStop(hard = true)
                ACTION_BRIGHTNESS_UP -> nudge(+0.15f)
                ACTION_BRIGHTNESS_DN -> nudge(-0.15f)
                ACTION_CYCLE_MODE    -> cycleMode()
                else                 -> {
                    if (!isOn.value && action != null) doStart() 
                    else if (isOn.value || persistentNotif) runAsForeground()
                    else {
                        // Not on, not persistent, no specific action -> don't stay alive
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun handleExtras(intent: Intent) {
        if (intent.hasExtra("brightness")) {
            val b = intent.getFloatExtra("brightness", _brightness.value)
            _brightness.value = b.coerceIn(0.1f, 1.0f)
            repository.setBrightness(_brightness.value)
        }
        if (intent.hasExtra("mode")) {
            val mName = intent.getStringExtra("mode")
            try {
                mName?.let { 
                    _mode.value = FlashlightMode.valueOf(it)
                    repository.setMode(_mode.value)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _inst = null
        try {
            cam.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
        scope.cancel()
        rawTorch(false)
        super.onDestroy()
    }

    // ── Public API (called in-process by ViewModel / QuickControlActivity) ────

    fun toggle() {
        scope.launch {
            if (isOn.value) doStop() else doStart()
        }
    }

    fun setBrightness(v: Float)       { 
        _brightness.value = v.coerceIn(0.1f, 1.0f)
        repository.setBrightness(_brightness.value)
        if (isOn.value && _mode.value == FlashlightMode.STEADY) applyBrightness()
        updateNotification()
        broadcastStateChange() 
    }
    fun setMode(m: FlashlightMode)    { 
        _mode.value = m
        repository.setMode(m)
        if (isOn.value) restartMode()
        updateNotification()
        broadcastStateChange() 
    }
    fun setStrobeMs(ms: Long)         { _strobeMs.value = ms.coerceIn(40L, 500L); if (isOn.value && _mode.value == FlashlightMode.STROBE) restartMode(); broadcastStateChange() }
    fun setDiscoRange(min: Long, max: Long) { _discoRange.value = min.coerceAtMost(max) to max.coerceAtLeast(min); broadcastStateChange() }
    fun setTimer(minutes: Int) {
        _timerMinutes.value = minutes
        timerJob?.cancel()
        if (minutes > 0 && isOn.value) scheduleTimer(minutes)
        broadcastStateChange()
    }

    // Expose current state for UI / TileService
    fun currentMode()       = _mode.value
    fun currentBrightness() = _brightness.value
    fun brightSupported()   = brightSupport

    // ── Core on / off ─────────────────────────────────────────────────────────

    private fun doStart() {
        repository.setOn(true)
        repository.setMode(_mode.value)
        repository.setBrightness(_brightness.value)
        
        runAsForeground()
        restartMode()
        
        if (_timerMinutes.value > 0) scheduleTimer(_timerMinutes.value)
        broadcastStateChange()
    }

    private fun doStop(hard: Boolean = false) {
        repository.setOn(false)
        modeJob?.cancel()
        timerJob?.cancel()
        rawTorch(false)
        
        val stopFlag = if (hard) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
        
        if (hard) {
            stopForeground(stopFlag)
            stopSelf()
        } else {
            if (persistentNotif) {
                // "Soft" stop: keep notification but make it dismissible
                stopForeground(stopFlag)
                updateNotification()
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        broadcastStateChange()
    }

    // ── Mode engine ───────────────────────────────────────────────────────────

    private fun restartMode() {
        modeJob?.cancel()
        modeJob = scope.launch {
            when (_mode.value) {
                FlashlightMode.STEADY -> applyBrightness()
                FlashlightMode.STROBE -> loopStrobe()
                FlashlightMode.SOS    -> loopSos()
                FlashlightMode.DISCO  -> loopDisco()
            }
        }
    }

    private suspend fun loopStrobe() {
        while (true) {
            val t = _strobeMs.value
            applyBrightness(); delay(t)
            rawTorch(false);   delay(t)
        }
    }

    private suspend fun loopSos() {
        val dot = 200L; val dash = 600L; val gap = 200L
        while (true) {
            repeat(3) { applyBrightness(); delay(dot);  rawTorch(false); delay(gap) }; delay(gap * 2)
            repeat(3) { applyBrightness(); delay(dash); rawTorch(false); delay(gap) }; delay(gap * 2)
            repeat(3) { applyBrightness(); delay(dot);  rawTorch(false); delay(gap) }; delay(2_000L)
        }
    }

    private suspend fun loopDisco() {
        while (true) {
            val r  = _discoRange.value
            val ms = (r.first..r.second).random()
            applyBrightness(); delay(ms)
            rawTorch(false);   delay(ms)
        }
    }

    private fun cycleMode() {
        val all = FlashlightMode.entries
        _mode.value = all[(all.indexOf(_mode.value) + 1) % all.size]
        if (isOn.value) restartMode()
        updateNotification()
        broadcastStateChange()
    }

    private fun nudge(delta: Float) {
        _brightness.value = (_brightness.value + delta).coerceIn(0.1f, 1.0f)
        repository.setBrightness(_brightness.value)
        if (isOn.value && _mode.value == FlashlightMode.STEADY) applyBrightness()
        updateNotification()
    }

    private fun scheduleTimer(minutes: Int) {
        timerJob?.cancel()
        timerJob = scope.launch { delay(minutes * 60_000L); doStop() }
    }

    // ── Camera hardware ───────────────────────────────────────────────────────

    private fun applyBrightness() {
        val id = cameraId ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && brightSupport) {
            try {
                val lvl = (_brightness.value * maxBright).toInt().coerceIn(1, maxBright)
                cam.turnOnTorchWithStrengthLevel(id, lvl)
                return
            } catch (e: Exception) { Log.w(TAG, "Strength-level failed: ${e.message}") }
        }
        rawTorch(true)
    }

    private fun rawTorch(on: Boolean) {
        try { cameraId?.let { cam.setTorchMode(it, on) } } catch (_: Exception) {}
    }

    private fun findFlashCamera() {
        try {
            for (id in cam.cameraIdList) {
                val c = cam.getCameraCharacteristics(id)
                if (c[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true) {
                    cameraId = id; detectBrightSupport(c); break
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Camera probe failed", e) }
    }

    private fun detectBrightSupport(c: CameraCharacteristics) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        try {
            val max = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: return
            if (max >= 2) { brightSupport = true; maxBright = max }
        } catch (_: Exception) {}
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private suspend fun loadSettings() {
        try {
            val p = dataStore.data.first()
            val modeName = p[stringPreferencesKey("flashlight_mode")]
            if (modeName != null) {
                try {
                    _mode.value = FlashlightMode.valueOf(modeName)
                } catch (e: Exception) {
                    _mode.value = FlashlightMode.STEADY
                }
            }
            _brightness.value   = p[floatPreferencesKey("flashlight_brightness")] ?: 1.0f
            _strobeMs.value     = p[longPreferencesKey("flashlight_strobe_interval")] ?: 80L
            val dMin = p[longPreferencesKey("flashlight_disco_min")] ?: 40L
            val dMax = p[longPreferencesKey("flashlight_disco_max")] ?: 300L
            _discoRange.value   = minOf(dMin, dMax) to maxOf(dMin, dMax)
            _timerMinutes.value = p[intPreferencesKey("flashlight_timer")] ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load settings", e)
        }
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private fun broadcastStateChange() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).apply { `package` = packageName })
        TileService.requestListeningState(this, ComponentName(this, FlashlightTileService::class.java))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NOTIFICATION SYSTEM
    // ─────────────────────────────────────────────────────────────────────────

    // Mode-specific theming helpers
    private fun modeColor() = when (_mode.value) {
        FlashlightMode.STEADY -> 0xFFFBC02D.toInt()   // warm amber
        FlashlightMode.STROBE -> 0xFF42A5F5.toInt()   // electric blue
        FlashlightMode.SOS    -> 0xFFEF5350.toInt()   // urgent red
        FlashlightMode.DISCO  -> 0xFFBA68C8.toInt()   // vivid purple
    }
    private fun modeLabel() = when (_mode.value) {
        FlashlightMode.STEADY -> "Steady Beam"
        FlashlightMode.STROBE -> "Strobe Flash"
        FlashlightMode.SOS    -> "SOS Signal"
        FlashlightMode.DISCO  -> "Disco Mode"
    }
    private fun modeIconRes() = when (_mode.value) {
        FlashlightMode.STEADY -> R.drawable.ic_flashlight_on
        FlashlightMode.STROBE -> R.drawable.ic_notif_strobe
        FlashlightMode.SOS    -> R.drawable.ic_notif_sos
        FlashlightMode.DISCO  -> R.drawable.ic_notif_disco
    }

    private fun pi(action: String, reqCode: Int = action.hashCode()): PendingIntent {
        val i = Intent(this, FlashlightService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, reqCode, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun buildNotification(): Notification {
        val on          = isOn.value
        val brightPct   = (_brightness.value * 100).toInt()

        // ── Compact view ─────────────────────────────────────────────────────
        val compact = RemoteViews(packageName, R.layout.notification_flashlight)
        compact.setInt(R.id.notif_mode_strip, "setBackgroundColor", modeColor())
        compact.setImageViewResource(R.id.notif_icon,
            if (on) modeIconRes() else R.drawable.ic_flashlight_off)
        // Use the proper RemoteViews method for color filter
        compact.setInt(R.id.notif_icon, "setColorFilter", modeColor())
        compact.setTextViewText(R.id.notif_mode_label, modeLabel())
        compact.setTextViewText(R.id.notif_bright_value, "$brightPct%")
        compact.setProgressBar(R.id.notif_brightness_bar, 100, brightPct, false)
        // Use white for progress bar to ensure visibility on colorized notification backgrounds
        compact.setColorStateList(R.id.notif_brightness_bar, "setProgressTintList", ColorStateList.valueOf(0xFFFFFFFF.toInt()))
        compact.setImageViewResource(R.id.notif_btn_toggle,
            if (on) R.drawable.ic_notif_power_off else R.drawable.ic_notif_power_on)
        compact.setOnClickPendingIntent(R.id.notif_btn_toggle, pi(ACTION_TOGGLE, 10))

        // ── Expanded view ────────────────────────────────────────────────────
        val expanded = RemoteViews(packageName, R.layout.notification_flashlight_expanded)
        expanded.setInt(R.id.notif_exp_accent, "setBackgroundColor", modeColor())
        expanded.setTextViewText(R.id.notif_exp_mode, modeLabel())
        expanded.setTextViewText(R.id.notif_exp_bright_label, "Intensity  $brightPct%")
        expanded.setProgressBar(R.id.notif_exp_bright_bar, 100, brightPct, false)
        // Use white for progress bar to ensure visibility on colorized notification backgrounds
        expanded.setColorStateList(R.id.notif_exp_bright_bar, "setProgressTintList", ColorStateList.valueOf(0xFFFFFFFF.toInt()))

        expanded.setImageViewResource(R.id.notif_exp_btn_bright_dn, R.drawable.ic_notif_brightness_dn)
        expanded.setImageViewResource(R.id.notif_exp_btn_toggle,
            if (on) R.drawable.ic_notif_power_off else R.drawable.ic_notif_power_on)
        expanded.setImageViewResource(R.id.notif_exp_btn_bright_up, R.drawable.ic_notif_brightness_up)
        expanded.setImageViewResource(R.id.notif_exp_btn_mode,      R.drawable.ic_notif_cycle_mode)
        expanded.setImageViewResource(R.id.notif_exp_btn_stop,      R.drawable.ic_notif_close)

        expanded.setOnClickPendingIntent(R.id.notif_exp_btn_bright_dn, pi(ACTION_BRIGHTNESS_DN, 21))
        expanded.setOnClickPendingIntent(R.id.notif_exp_btn_toggle,    pi(ACTION_TOGGLE, 22))
        expanded.setOnClickPendingIntent(R.id.notif_exp_btn_bright_up, pi(ACTION_BRIGHTNESS_UP, 23))
        expanded.setOnClickPendingIntent(R.id.notif_exp_btn_mode,      pi(ACTION_CYCLE_MODE, 24))
        expanded.setOnClickPendingIntent(R.id.notif_exp_btn_stop,      pi(ACTION_STOP, 25))

        val tapIntent = PendingIntent.getActivity(
            this, NOTIF_ID,
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to", "flashlight")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (on) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off)
            .setContentTitle(modeLabel())
            .setContentText("$brightPct% intensity \u00B7 tap to open")
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(tapIntent)
            .setDeleteIntent(pi(ACTION_STOP, 99))
            .setOngoing(on || persistentNotif)
            .setSilent(true)
            .setColorized(true)
            .setColor(modeColor())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        if (!isOn.value && !persistentNotif) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun runAsForeground() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires a specific type. 
            // We use CAMERA if the torch is active, otherwise SPECIAL_USE for the persistent notification.
            val type = if (isOn.value) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                startForeground(NOTIF_ID, notif, type)
            } catch (e: Exception) {
                Log.e(TAG, "startForeground failed with type $type, falling back", e)
                try {
                    startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } catch (e2: Exception) {
                    startForeground(NOTIF_ID, notif)
                }
            }
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL_ID, "Flashlight Controls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Live flashlight mode, brightness, and toggle controls"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}