/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.service

import android.app.Notification
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frerox.toolz.R
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
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
 * System Event Listener: watches MediaStore.Images for new screenshots.
 * Pipeline: ContentObserver -> MediaStore query -> debounce -> Smart Auto check -> Popup or Auto-queue.
 *
 * Lifecycle: runs as foreground service when PurgeShot is enabled (so observer survives in background).
 * Also does lightweight polling fallback for manufacturers where observer is unreliable.
 */
@AndroidEntryPoint
class PurgeShotService : Service() {

    companion object {
        private const val TAG = "PurgeShotService"
        const val ACTION_START = "com.frerox.toolz.PURGESHOT_START"
        const val ACTION_STOP = "com.frerox.toolz.PURGESHOT_STOP"
        private const val NOTIF_ID = 4100
        const val CHANNEL_ID = "purgeshot_channel"
        const val CHANNEL_NAME = "PurgeShot"
    }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var repository: PurgeShotRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private var lastSeenUri: String? = null
    private var lastTriggerMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createChannelIfNeeded()
        registerObserver()
        // Durability: ensure any pending queue is re-hydrated + rescheduled (handles clear-data restore)
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
        val handler = Handler(Looper.getMainLooper())
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                // Debounce 600ms — MediaStore fires multiple times per insert
                val now = System.currentTimeMillis()
                if (now - lastTriggerMs < 800) return
                lastTriggerMs = now
                scope.launch {
                    delay(700) // wait for MediaStore to settle (IS_PENDING -> 0)
                    handlePossibleScreenshot(uri)
                }
            }
        }
        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer!!
            )
            Log.d(TAG, "ContentObserver registered")
        } catch (e: Exception) {
            Log.w(TAG, "register observer failed", e)
        }
    }

    private fun unregisterObserver() {
        observer?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    private suspend fun handlePossibleScreenshot(triggerUri: Uri?) {
        try {
            if (!settingsRepository.purgeShotEnabled.first()) {
                Log.d(TAG, "PurgeShot disabled, ignoring")
                return
            }
            val candidate = queryLatestScreenshot() ?: run {
                Log.d(TAG, "No candidate screenshot found for uri=$triggerUri")
                return
            }
            val uriStr = candidate.uri.toString()
            if (uriStr == lastSeenUri) {
                Log.d(TAG, "Duplicate uri, ignore: $uriStr")
                return
            }
            // Dedup via DataStore lastUri + in-memory
            val storedLast = settingsRepository.purgeShotLastScreenshotUri.first()
            if (uriStr == storedLast) {
                Log.d(TAG, "Stored last uri duplicate: $uriStr")
                return
            }
            // Age filter: only screenshots taken within last 10 seconds are considered fresh
            val age = System.currentTimeMillis() - candidate.dateAddedMs
            if (age > 10_000) {
                Log.d(TAG, "Candidate too old age=$age ms uri=$uriStr")
                return
            }
            // Ensure it's actually a screenshot (path contains /screenshots/ or display name contains screenshot)
            val isScreenshot = candidate.displayName.contains("screenshot", ignoreCase = true) ||
                (candidate.relativePath?.contains("screenshot", ignoreCase = true) == true) ||
                (candidate.filePath?.contains("screenshot", ignoreCase = true) == true) ||
                candidate.displayName.contains("screen", ignoreCase = true)
            // Some OEMs use "Screenshot" prefix; if not, still consider if very recent image in Screenshots folder
            if (!isScreenshot) {
                // Check if file is in DCIM/Screenshots or Pictures/Screenshots
                val path = candidate.relativePath ?: candidate.filePath ?: ""
                val inScreenshotDir = path.contains("Screenshots", ignoreCase = true) ||
                    path.contains("ScreenShots", ignoreCase = true)
                if (!inScreenshotDir) {
                    Log.d(TAG, "Not a screenshot (name=${candidate.displayName} path=$path), skipping")
                    return
                }
            }
            lastSeenUri = uriStr
            settingsRepository.setPurgeShotLastScreenshotUri(uriStr)

            val smartAuto = settingsRepository.purgeShotSmartAuto.first()
            val autoDuration = settingsRepository.purgeShotAutoDuration.first()

            if (smartAuto) {
                // Auto-bypass: directly queue without popup
                val presetLabel = formatDurationLabel(autoDuration)
                repository.enqueue(candidate.uri, candidate.displayName, autoDuration, presetLabel, candidate.filePath)
                Log.i(TAG, "SmartAuto queued $uriStr for $presetLabel")
                // Show silent notification as feedback (optional)
                showAutoQueuedNotification(candidate.displayName, presetLabel)
            } else {
                // Trigger expressive popup (Activity overlay)
                val intent = Intent(this@PurgeShotService, PurgeShotPopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("uri", uriStr)
                    putExtra("displayName", candidate.displayName)
                    putExtra("path", candidate.filePath)
                }
                startActivity(intent)
                Log.i(TAG, "Launched PurgeShot popup for $uriStr")
            }
        } catch (e: Exception) {
            Log.w(TAG, "handlePossibleScreenshot failed", e)
        }
    }

    private data class ScreenshotCandidate(
        val uri: Uri,
        val displayName: String,
        val dateAddedMs: Long,
        val relativePath: String?,
        val filePath: String?
    )

    private fun queryLatestScreenshot(): ScreenshotCandidate? {
        val resolver: ContentResolver = contentResolver
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA // deprecated but useful for path
        )
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        return try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                sort
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val addedIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val modIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val relIdx = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: "screenshot.jpg"
                    val addedSec = c.getLong(addedIdx)
                    val modSec = c.getLong(modIdx)
                    // Prefer DATE_ADDED, fallback to DATE_MODIFIED
                    val epochSec = if (addedSec > 0) addedSec else modSec
                    val dateAddedMs = epochSec * 1000
                    val rel = if (relIdx != -1) c.getString(relIdx) else null
                    val path = if (dataIdx != -1) c.getString(dataIdx) else null
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    ScreenshotCandidate(uri, name, dateAddedMs, rel, path)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryLatestScreenshot error", e)
            null
        }
    }

    private fun formatDurationLabel(duration: Long): String = when (duration) {
        30_000L -> "30 sec"
        60_000L -> "1 min"
        5 * 60_000L -> "5 min"
        15 * 60_000L -> "15 min"
        30 * 60_000L -> "30 min"
        60 * 60_000L -> "1 hour"
        6 * 60 * 60_000L -> "6 hours"
        12 * 60 * 60_000L -> "12 hours"
        24 * 60 * 60_000L -> "1 day"
        3 * 24 * 60 * 60_000L -> "3 days"
        7 * 24 * 60 * 60_000L -> "1 week"
        14 * 24 * 60 * 60_000L -> "2 weeks"
        30L * 24 * 60 * 60_000L -> "1 month"
        else -> {
            val mins = duration / 60_000
            if (mins < 60) "${mins} min" else "${duration / (60*60_000)} hr"
        }
    }

    private fun showAutoQueuedNotification(displayName: String, label: String) {
        try {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PurgeShot: auto-queued")
                .setContentText("$displayName → deletes in $label")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            mgr.notify((System.currentTimeMillis() % 10000).toInt() + 5000, notif)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // Start foreground with low-priority notification (required for background observer on Android 14+)
        val notif = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        // Ensure queue restored after every start (covers BOOT / update)
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
