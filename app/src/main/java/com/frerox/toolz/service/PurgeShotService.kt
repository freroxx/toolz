/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frerox.toolz.R
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Live-process event listener: watches MediaStore.Images for new screenshots while Toolz's
 * process is alive, for lower-latency detection than the JobScheduler fallback in
 * [PurgeShotObserverJobService] (which is the path that keeps working once this service and
 * its process are gone).
 *
 * All actual "is this a screenshot / is it new" logic lives in [PurgeShotDetector], shared
 * with the JobScheduler fallback so the two paths can't disagree about what they've seen.
 */
@AndroidEntryPoint
class PurgeShotService : Service() {

    companion object {
        private const val TAG = "PurgeShotService"
        const val ACTION_START = "com.frerox.toolz.PURGESHOT_START"
        const val ACTION_STOP = "com.frerox.toolz.PURGESHOT_STOP"
        const val ACTION_HANDLE_FALLBACK = "com.frerox.toolz.PURGESHOT_HANDLE_FALLBACK"
        private const val NOTIF_ID = 4100
        const val CHANNEL_ID = "purgeshot_channel"
        const val CHANNEL_NAME = "PurgeShot"
        private const val OBSERVER_DEBOUNCE_MS = 800L
    }

    /**
     * Public so [PurgeShotDetector] (query/heuristic logic) and
     * [com.frerox.toolz.data.purgeshot.PurgeShotHandler] (routing after detection) can share
     * one candidate type instead of each defining their own and drifting apart.
     */
    data class ScreenshotCandidate(
        val uri: android.net.Uri,
        val displayName: String,
        val dateAddedMs: Long,
        val relativePath: String?,
        val filePath: String?,
        val bucketName: String? = null
    )

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var repository: PurgeShotRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private var observerThread: HandlerThread? = null
    private var lastTriggerMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createChannelIfNeeded()
        registerObserver()
        scope.launch { runCatching { repository.ensureRestoredAndRescheduled() } }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = android.app.NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Monitors screenshots for timed deletion"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun registerObserver() {
        if (observer != null) return
        // Dedicated background thread instead of the main looper: onChange does non-trivial
        // work (dispatch into a coroutine that queries MediaStore), and MediaStore can fire
        // bursts of change notifications (e.g. a gallery sync) — keeping that off the main
        // thread avoids UI jank if the process happens to have UI visible when it fires.
        val thread = HandlerThread("PurgeShotObserver").apply { start() }
        observerThread = thread
        val handler = Handler(thread.looper)
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                val now = System.currentTimeMillis()
                if (now - lastTriggerMs < OBSERVER_DEBOUNCE_MS) return
                lastTriggerMs = now
                scope.launch {
                    handleTrigger()
                }
            }
        }
        try {
            val cr = contentResolver
            // Scoped to Images only. The previous version also observed MediaStore.Files
            // ("outside Toolz fallback" for OEMs that insert via Files first), but that meant
            // every non-image file write anywhere in shared storage — downloads, WhatsApp
            // media, app caches — woke this observer, debounced, and triggered a MediaStore
            // query for no benefit, since screenshots are always indexed under Images too.
            // The JobScheduler fallback in PurgeShotObserverJobService already exists
            // specifically to catch what this live observer misses; duplicating its coverage
            // here just burned battery.
            cr.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer!!)
            Log.d(TAG, "ContentObserver registered (Images)")
        } catch (e: Exception) {
            Log.w(TAG, "register observer failed", e)
        }
    }

    private fun unregisterObserver() {
        observer?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        observer = null
        observerThread?.quitSafely()
        observerThread = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Service killed by swipe — restart if still enabled (outside Toolz resilience)
        scope.launch {
            try {
                if (settingsRepository.purgeShotEnabled.first()) {
                    val restart = Intent(this@PurgeShotService, PurgeShotService::class.java).apply { action = ACTION_START }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(restart) else startService(restart)
                    PurgeShotObserverJobService.schedule(this@PurgeShotService)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun postMediaPermissionNotification() {
        try {
            val intent = Intent(this, com.frerox.toolz.MainActivity::class.java).apply {
                putExtra("navigate_to", "purgeshot")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = android.app.PendingIntent.getActivity(this, 4101, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PurgeShot needs photo access")
                .setContentText("Tap to grant access so screenshots can be detected outside Toolz")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            mgr?.notify(4101, notif)
        } catch (_: Exception) {
        }
    }

    private suspend fun handleTrigger() {
        try {
            if (!PurgeShotDetector.hasMediaPermission(this)) {
                Log.w(TAG, "Missing media permission — cannot query screenshots outside Toolz")
                postMediaPermissionNotification()
                return
            }
            delay(PurgeShotDetector.SETTLE_DELAY_MS)
            PurgeShotDetector.detectAndHandle(
                context = this,
                repository = repository,
                settingsRepository = settingsRepository,
                awaitSettle = false // already settled above
            )
        } catch (e: Exception) {
            Log.w(TAG, "handleTrigger failed", e)
        }
    }

    private fun tryStartForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            // Android 14+ throws ForegroundServiceStartNotAllowedException when starting FGS from background
            // after swipe or boot while app not in foreground. Don't crash — keep the ContentObserver
            // alive as a background observer (works while process is alive) and rely on JobScheduler +
            // WorkManager poll for outside-process detection. The observer will still catch screenshots
            // while the service process lives, just without a sticky notification.
            Log.w(TAG, "startForeground blocked (background), continuing as background observer", e)
            // Still ensure detector + Job are armed so "outside Toolz" path works.
            try { PurgeShotObserverJobService.schedule(this) } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_HANDLE_FALLBACK -> {
                // Invoked when something (e.g. a manual re-check) wants the live service to
                // re-run detection immediately rather than wait for the next MediaStore change.
                scope.launch { handleTrigger() }
                val notif = buildForegroundNotification()
                tryStartForeground(notif)
                scope.launch { runCatching { repository.ensureRestoredAndRescheduled() } }
                return START_STICKY
            }
        }
        val notif = buildForegroundNotification()
        tryStartForeground(notif)
        scope.launch { runCatching { repository.ensureRestoredAndRescheduled() } }
        return START_STICKY
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, com.frerox.toolz.MainActivity::class.java).apply {
            putExtra("navigate_to", "purgeshot")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = android.app.PendingIntent.getActivity(this, 0, launchIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PurgeShot active")
            .setContentText("Watching for screenshots")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        unregisterObserver()
        scope.cancel()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}