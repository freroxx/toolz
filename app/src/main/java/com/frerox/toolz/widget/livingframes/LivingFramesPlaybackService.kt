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

package com.frerox.toolz.widget.livingframes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.data.livingframes.LivingFrameEngine
import com.frerox.toolz.data.livingframes.LivingFrameLoopMode
import com.frerox.toolz.data.livingframes.LivingFramesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service driving Living Frames animation.
 *
 * Honest engineering notes (surfaced in the tool UI, not hidden):
 * - Home widgets are RemoteViews: no VideoView, no GIF auto-play, no 60fps.
 *   Each visible frame is a Binder transaction (~1MB shared limit), so frames
 *   are pre-downscaled to <=320px and pushed via partiallyUpdateAppWidget.
 * - 24 fps is experimental: launchers (esp. Samsung / Xiaomi) throttle
 *   background updates and Doze stops us. The loop drops late frames instead
 *   of queueing them, pauses on SCREEN_OFF / power-save, and backs off on
 *   consecutive Binder failures.
 * - In-app preview is full-smooth Compose; the home widget is best-effort.
 */
@AndroidEntryPoint
class LivingFramesPlaybackService : Service() {

    @Inject lateinit var repo: LivingFramesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private val frameCache = LinkedHashMap<String, Bitmap>(48, 0.75f, true)
    private var consecutiveFailures = 0

    // Per-widget playback cursors.
    private data class Cursor(var index: Int = 0, var dir: Int = 1, var lastPushMs: Long = 0L, var lastDiagMs: Long = 0L)
    private val cursors = mutableMapOf<Int, Cursor>()
    private val nudges = mutableSetOf<Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        beginLoop()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(screenReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NUDGE -> {
                val id = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
                if (id != -1) synchronized(nudges) { nudges.add(id) }
            }
            ACTION_REFRESH -> beginLoop()
        }
        // Ensure we stay alive only while widgets are bound.
        scope.launch {
            try { repo.load() } catch (_: Exception) {}
            if (LivingFramesWidgetHelper.allWidgetIds(this@LivingFramesPlaybackService).isEmpty()) {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        loopJob?.cancel()
        scope.cancel()
        synchronized(frameCache) {
            try { frameCache.values.forEach { if (!it.isRecycled) it.recycle() } } catch (_: Exception) {}
            frameCache.clear()
        }
        super.onDestroy()
    }

    private var screenOn = true
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOn = intent?.action != Intent.ACTION_SCREEN_OFF
        }
    }

    private fun beginLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            try { repo.load() } catch (_: Exception) {}
            var lastBeat = 0L
            while (isActive) {
                val tickStart = android.os.SystemClock.elapsedRealtime()
                try {
                    val tickBudget = driveOnce()
                    if (tickStart - lastBeat > 2000L) {
                        LivingFramesWidgetHelper.writeHeartbeat(
                            this@LivingFramesPlaybackService, screenOn, isPowerSave()
                        )
                        lastBeat = tickStart
                    }
                    // Adaptive rest: never spin faster than the fastest widget needs,
                    // never slower than 250ms idle when nothing animates.
                    delay(tickBudget)
                } catch (_: Exception) {
                    delay(500L)
                }
                // Watchdog: stop when no widgets remain.
                if (LivingFramesWidgetHelper.allWidgetIds(this@LivingFramesPlaybackService).isEmpty()) {
                    // Keep service cheap: idle a while before exiting for launcher races.
                    delay(10_000L)
                    if (LivingFramesWidgetHelper.allWidgetIds(this@LivingFramesPlaybackService).isEmpty()) {
                        stopSelf()
                        return@launch
                    }
                }
                // Watchdog against clock jumps.
                if (android.os.SystemClock.elapsedRealtime() - tickStart > 30_000L) {
                    cursors.clear()
                }
            }
        }
    }

    /**
     * One drive pass over all bound widgets. Returns ms to wait before next pass.
     */
    private suspend fun driveOnce(): Long {
        if (!screenOn) return 500L
        if (isPowerSave()) return 1000L
        val ids = LivingFramesWidgetHelper.allWidgetIds(this)
        if (ids.isEmpty()) return 1000L
        var minInterval = 1000L
        val now = android.os.SystemClock.elapsedRealtime()
        ids.forEach { appWidgetId ->
            try {
                val frameId = repo.boundFrame(appWidgetId) ?: return@forEach
                val frame = repo.get(frameId) ?: return@forEach
                if (frame.type == com.frerox.toolz.data.livingframes.LivingFrameType.SLIDESHOW) {
                    // Slideshows run on AlarmManager, never on the fps loop.
                    return@forEach
                }
                if (frame.frameCount <= 1) {
                    // Static photo: refresh at most every 15 min.
                    val cursor = cursors.getOrPut(appWidgetId) { Cursor() }
                    if (now - cursor.lastPushMs > 15 * 60_000L) {
                        repo.decodeWidgetFrame(frame, 0)?.let { cover ->
                            LivingFramesWidgetHelper.pushFrame(this, appWidgetId, frame, cover)
                            cursor.lastPushMs = now
                            writeDiagThrottled(appWidgetId, cursor, 0, 1, now)
                            try { cover.recycle() } catch (_: Exception) {}
                        }
                    }
                    return@forEach
                }
                val interval = LivingFrameEngine.intervalMs(frame.fps)
                minInterval = minOf(minInterval, interval)
                val cursor = cursors.getOrPut(appWidgetId) { Cursor() }
                val nudged = synchronized(nudges) { nudges.remove(appWidgetId) }
                val due = nudged || (now - cursor.lastPushMs >= interval)
                if (!due) return@forEach
                // Drop-late policy: if we are more than 2 intervals behind, skip
                // ahead instead of bursting catch-up frames (prevents Binder floods).
                if (!nudged && cursor.lastPushMs != 0L) {
                    val behind = (now - cursor.lastPushMs) / interval
                    if (behind >= 3) {
                        var idx = cursor.index
                        var d = cursor.dir
                        repeat(minOf(behind.toInt(), frame.frameCount)) {
                            val (n, nd) = LivingFrameEngine.nextIndex(idx, d, frame.frameCount, frame.loopMode)
                            idx = n; d = nd
                        }
                        cursor.index = idx; cursor.dir = d
                    }
                }
                if (frame.loopMode == LivingFrameLoopMode.ONCE &&
                    LivingFrameEngine.isFinished(cursor.index, frame.frameCount, frame.loopMode) &&
                    !nudged
                ) return@forEach

                val bitmap = cachedFrame(frame, cursor.index)
                if (bitmap == null) {
                    // Advance past corrupt frames so one bad file can't wedge playback.
                    val (sn, sd) = LivingFrameEngine.nextIndex(cursor.index, cursor.dir, frame.frameCount, frame.loopMode)
                    cursor.index = sn
                    cursor.dir = sd
                    return@forEach
                }
                LivingFramesWidgetHelper.pushFrame(this, appWidgetId, frame, bitmap)
                cursor.lastPushMs = now
                writeDiagThrottled(appWidgetId, cursor, cursor.index, frame.frameCount, now)
                consecutiveFailures = 0
                val (n, nd) = LivingFrameEngine.nextIndex(cursor.index, cursor.dir, frame.frameCount, frame.loopMode)
                cursor.index = n; cursor.dir = nd
            } catch (_: Exception) {
                consecutiveFailures++
                if (consecutiveFailures > 20) {
                    // Back off hard — launcher is throttling us.
                    return 2000L
                }
            }
        }
        return minInterval.coerceIn(33L, 1000L)
    }

    private suspend fun cachedFrame(frame: com.frerox.toolz.data.livingframes.LivingFrame, index: Int): Bitmap? {
        // Effects are baked per push — key includes them or stale bitmaps win.
        val key = "${frame.id}:$index:q${(frame.opacity * 20).toInt()}:c${frame.cornerRadiusDp}:${frame.fit}"
        synchronized(frameCache) { frameCache[key]?.let { return it } }
        val bmp = repo.decodeWidgetFrame(frame, index) ?: return null
        synchronized(frameCache) {
            // Evict oldest when over budget.
            while (frameCache.size >= 16) {
                val oldest = frameCache.keys.firstOrNull() ?: break
                try { frameCache.remove(oldest)?.recycle() } catch (_: Exception) {}
            }
            frameCache[key] = bmp
        }
        return bmp
    }

    private fun writeDiagThrottled(appWidgetId: Int, cursor: Cursor, index: Int, total: Int, now: Long) {
        if (now - cursor.lastDiagMs < 5000L) return
        cursor.lastDiagMs = now
        try {
            LivingFramesWidgetHelper.writeDiag(this, appWidgetId, index, total)
        } catch (_: Exception) {}
    }

    private fun isPowerSave(): Boolean = try {
        (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
    } catch (_: Exception) { false }

    private fun startForegroundNotification() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "Living Widgets", NotificationManager.IMPORTANCE_MIN)
                    )
                } catch (_: Exception) {}
            }
            val pending = android.app.PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, com.frerox.toolz.ui.navigation.Screen.LivingWidgets.route)
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )
            val notif: Notification = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, CHANNEL)
                    .setContentTitle("Living Widgets animating")
                    .setContentText("Tap to manage your home-screen widgets")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pending)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle("Living Widgets animating")
                    .setContentText("Tap to manage your home-screen widgets")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pending)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIF_ID, notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val CHANNEL = "living_frames_playback"
        const val NOTIF_ID = 4417
        const val ACTION_NUDGE = "com.frerox.toolz.LIVING_FRAMES_NUDGE"
        const val ACTION_REFRESH = "com.frerox.toolz.LIVING_FRAMES_REFRESH"
        const val EXTRA_WIDGET_ID = "widget_id"

        fun ensureRunning(context: Context) {
            try {
                if (allBound(context)) {
                    val i = Intent(context, LivingFramesPlaybackService::class.java)
                        .setAction(ACTION_REFRESH)
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                    else context.startService(i)
                }
            } catch (_: Exception) {}
        }

        fun nudge(context: Context, appWidgetId: Int) {
            try {
                val i = Intent(context, LivingFramesPlaybackService::class.java)
                    .setAction(ACTION_NUDGE)
                    .putExtra(EXTRA_WIDGET_ID, appWidgetId)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
                else context.startService(i)
            } catch (_: Exception) {}
        }

        private fun allBound(context: Context): Boolean = try {
            LivingFramesWidgetHelper.allWidgetIds(context).isNotEmpty()
        } catch (_: Exception) { false }
    }
}
