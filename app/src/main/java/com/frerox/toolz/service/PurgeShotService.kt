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

    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
        } catch (_: Exception) {}
    }

    private suspend fun handlePossibleScreenshot(triggerUri: Uri?) {
        try {
            if (!settingsRepository.purgeShotEnabled.first()) {
                Log.d(TAG, "PurgeShot disabled, ignoring")
                return
            }
            if (!hasMediaPermission()) {
                Log.w(TAG, "Missing media permission — cannot query screenshots outside Toolz")
                postMediaPermissionNotification()
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
            // Age filter: allow 15s window (JobScheduler batch ~1.3s + MediaStore settle)
            val age = System.currentTimeMillis() - candidate.dateAddedMs
            if (age > 15_000 || age < -2000) {
                Log.d(TAG, "Candidate too old/new age=$age ms uri=$uriStr")
                return
            }
            // Robust screenshot detection — works outside Toolz on all OEMs
            val rel = candidate.relativePath ?: ""
            val buck = candidate.bucketName ?: ""
            val path = candidate.filePath ?: ""
            val name = candidate.displayName
            val isScreenshot = name.contains("screenshot", ignoreCase = true) ||
                rel.contains("screenshot", ignoreCase = true) ||
                buck.contains("screenshot", ignoreCase = true) ||
                path.contains("screenshot", ignoreCase = true) ||
                name.contains("screen", ignoreCase = true) && (rel.contains("screenshots", ignoreCase = true) || buck.contains("screenshots", ignoreCase = true)) ||
                buck.equals("Screenshots", ignoreCase = true) ||
                rel.equals("DCIM/Screenshots", ignoreCase = true) ||
                rel.equals("Pictures/Screenshots", ignoreCase = true)
            if (!isScreenshot) {
                val combined = "$rel/$buck/$path/$name"
                val inScreenshotDir = combined.contains("Screenshots", ignoreCase = true) ||
                    combined.contains("Screenshot", ignoreCase = true)
                if (!inScreenshotDir) {
                    Log.d(TAG, "Not a screenshot (name=$name rel=$rel buck=$buck path=$path) — skipping but still logging for outside-Toolz debugging")
                    return
                }
            }
            lastSeenUri = uriStr
            settingsRepository.setPurgeShotLastScreenshotUri(uriStr)

            val smartAuto = settingsRepository.purgeShotSmartAuto.first()
            val autoDuration = settingsRepository.purgeShotAutoDuration.first()

            // Compute size label for popup metadata (optional, not blocking)
            val sizeLabel = runCatching {
                contentResolver.query(candidate.uri, arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val szIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                        val wIdx = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                        val hIdx = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                        val bytes = if (szIdx != -1) c.getLong(szIdx) else 0L
                        val w = if (wIdx != -1) c.getInt(wIdx) else 0
                        val h = if (hIdx != -1) c.getInt(hIdx) else 0
                        buildString {
                            if (bytes > 0) append(formatBytes(bytes))
                            if (w > 0 && h > 0) {
                                if (isNotEmpty()) append(" • ")
                                append("${w}×${h}")
                            }
                        }.takeIf { it.isNotBlank() }
                    } else null
                }
            }.getOrNull()

            if (smartAuto) {
                // Auto-bypass: directly queue without popup
                val presetLabel = formatDurationLabel(autoDuration)
                repository.enqueue(candidate.uri, candidate.displayName, autoDuration, presetLabel, candidate.filePath)
                Log.i(TAG, "SmartAuto queued $uriStr for $presetLabel")
                // Show silent notification as feedback (with undo)
                showAutoQueuedNotification(candidate.displayName, presetLabel, candidate.uri, sizeLabel)
            } else {
                // Trigger expressive popup (Activity overlay) — blur respects performanceMode inside popup
                val intent = Intent(this@PurgeShotService, PurgeShotPopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("uri", uriStr)
                    putExtra("displayName", candidate.displayName)
                    putExtra("path", candidate.filePath)
                    putExtra("sizeLabel", sizeLabel)
                }
                startActivity(intent)
                Log.i(TAG, "Launched PurgeShot popup for $uriStr size=$sizeLabel")
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
        val filePath: String?,
        val bucketName: String? = null
    )

    private fun queryLatestScreenshot(): ScreenshotCandidate? {
        val resolver: ContentResolver = contentResolver
        // Robust projection — DATE_TAKEN is most accurate for camera/screenshot time, BUCKET for OEM folder name
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA // deprecated but useful fallback
        )
        // Order by DATE_TAKEN first (screenshot time) then DATE_ADDED
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
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
                    val takenIdx = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val relIdx = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    val bucketIdx = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: "screenshot.jpg"
                    val addedSec = c.getLong(addedIdx)
                    val modSec = c.getLong(modIdx)
                    val takenMs = if (takenIdx != -1) c.getLong(takenIdx) else 0L
                    // Best timestamp: DATE_TAKEN if valid, else DATE_ADDED, else DATE_MODIFIED
                    val dateAddedMs = when {
                        takenMs > 0 -> takenMs
                        addedSec > 0 -> addedSec * 1000
                        modSec > 0 -> modSec * 1000
                        else -> System.currentTimeMillis()
                    }
                    val rel = if (relIdx != -1) c.getString(relIdx) else null
                    val buck = if (bucketIdx != -1) c.getString(bucketIdx) else null
                    val path = if (dataIdx != -1) c.getString(dataIdx) else null
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    ScreenshotCandidate(uri, name, dateAddedMs, rel, path, buck)
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

    private fun showAutoQueuedNotification(displayName: String, label: String, uri: Uri? = null, sizeLabel: String? = null) {
        try {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            // Undo action — opens PurgeShot screen
            val undoIntent = Intent(this, com.frerox.toolz.MainActivity::class.java).apply {
                putExtra("navigate_to", "purgeshot")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val undoPi = android.app.PendingIntent.getActivity(this, (System.currentTimeMillis() % 10000).toInt(), undoIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PurgeShot: auto-queued")
                .setContentText("$displayName → deletes in $label${sizeLabel?.let { " • $it" } ?: ""}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$displayName will be permanently deleted in $label${sizeLabel?.let { " ($it)" } ?: ""}. Tap Undo to keep it."))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_launcher_foreground, "Undo", undoPi)
                .addAction(R.drawable.ic_launcher_foreground, "Open queue", undoPi)
                .build()
            mgr.notify((System.currentTimeMillis() % 10000).toInt() + 5000, notif)
        } catch (_: Exception) {}
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> "${(mb * 10).toInt() / 10.0} MB"
            kb >= 1 -> "${kb.toInt()} KB"
            else -> "$bytes B"
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            "com.frerox.toolz.PURGESHOT_HANDLE_FALLBACK" -> {
                // JobScheduler fallback — handle screenshot even if observer missed
                scope.launch {
                    delay(500)
                    handlePossibleScreenshot(null)
                }
                // Also ensure foreground for next triggers
                val notif = buildForegroundNotification()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else startForeground(NOTIF_ID, notif)
                } catch (_: Exception) {}
                scope.launch { runCatching { repository.ensureRestoredAndRescheduled() } }
                return START_STICKY
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
