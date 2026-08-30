package com.frerox.toolz.data.purgeshot

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.frerox.toolz.MainActivity
import com.frerox.toolz.R
import com.frerox.toolz.ToolzApplication
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.PurgeShotActionReceiver
import com.frerox.toolz.service.PurgeShotPopupActivity
import com.frerox.toolz.service.PurgeShotService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object PurgeShotHandler {
    private const val TAG = "PurgeShotHandler"
    private const val BATCH_DEBOUNCE_MS = 1200L
    private const val SCHEDULED_NOTIF_ID = 4150

    private val batchMutex = Mutex()
    private val pendingBatch = mutableListOf<PurgeShotService.ScreenshotCandidate>()
    private val handlerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batchJob: Job? = null

    suspend fun handleNewScreenshot(
        context: Context,
        repository: PurgeShotRepository,
        settingsRepository: SettingsRepository,
        candidate: PurgeShotService.ScreenshotCandidate
    ) {
        val uriStr = candidate.uri.toString()

        batchMutex.withLock {
            // Dedup within current pending batch
            if (pendingBatch.any { it.uri.toString() == uriStr }) {
                Log.d(TAG, "dedup within batch $uriStr")
                return
            }

            // Dedup against stored last screenshot if recent
            val storedLast = settingsRepository.purgeShotLastScreenshotUri.first()
            if (uriStr == storedLast && pendingBatch.isEmpty()) {
                Log.d(TAG, "dedup storedLast $uriStr")
                return
            }
            settingsRepository.setPurgeShotLastScreenshotUri(uriStr)

            pendingBatch.add(candidate)
            Log.d(TAG, "Added to batch: $uriStr (total: ${pendingBatch.size})")

            // Reset debounce timer to accumulate any additional rapid screenshots
            batchJob?.cancel()
            batchJob = handlerScope.launch {
                delay(BATCH_DEBOUNCE_MS)
                processBatch(context, repository, settingsRepository)
            }
        }
    }

    private suspend fun processBatch(
        context: Context,
        repository: PurgeShotRepository,
        settingsRepository: SettingsRepository
    ) {
        val batch = batchMutex.withLock {
            val list = pendingBatch.toList()
            pendingBatch.clear()
            list
        }

        if (batch.isEmpty()) return
        Log.i(TAG, "Processing batch of ${batch.size} screenshot(s)")

        val smartAuto = settingsRepository.purgeShotSmartAuto.first()
        val autoDuration = settingsRepository.purgeShotAutoDuration.first()
        val label = formatDurationLabel(autoDuration)

        if (smartAuto) {
            // Smart Auto: queue immediately with auto time
            for (item in batch) {
                repository.enqueue(item.uri, item.displayName, autoDuration, label, item.filePath)
            }
            Log.i(TAG, "SmartAuto queued ${batch.size} screenshot(s) for $label")
            showScheduledNotification(context, settingsRepository, batch.size, label)
            return
        }

        val isFocused = ToolzApplication.isFocused.value
        val canOverlay = canDrawOverlays(context)
        val canShowPopup = isFocused || canOverlay || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        val hasNotifPerm = hasNotificationPermission(context)

        val urisList = ArrayList(batch.map { it.uri.toString() })
        val namesList = ArrayList(batch.map { it.displayName })
        val pathsList = ArrayList(batch.map { it.filePath ?: "" })
        val singleCandidate = batch.last()
        val sizeLabel = if (batch.size == 1) querySizeLabel(context, singleCandidate.uri) else null

        // 1. If inside Toolz (focused): launch popup directly
        if (isFocused) {
            if (canShowPopup) {
                try {
                    val intent = Intent(context, PurgeShotPopupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putStringArrayListExtra("uris", urisList)
                        putStringArrayListExtra("displayNames", namesList)
                        putStringArrayListExtra("paths", pathsList)
                        putExtra("uri", singleCandidate.uri.toString())
                        putExtra("displayName", if (batch.size > 1) "${batch.size} Screenshots" else singleCandidate.displayName)
                        putExtra("path", singleCandidate.filePath)
                        putExtra("sizeLabel", sizeLabel)
                    }
                    context.startActivity(intent)
                    Log.i(TAG, "Popup launched (focused) for batch of ${batch.size}")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Popup failed even when focused, fallback to notification", e)
                }
            }
            if (hasNotifPerm) {
                showPopupFallbackNotification(context, batch, sizeLabel)
            }
            return
        }

        // 2. Outside Toolz: Post notification first (guaranteed outside-process delivery)
        if (hasNotifPerm) {
            showPopupFallbackNotification(context, batch, sizeLabel)
            Log.i(TAG, "Fallback notification shown (outside Toolz) for batch of ${batch.size}")
        }

        // 3. Best-effort popup over other apps when overlay permission is granted
        if (canShowPopup) {
            try {
                val intent = Intent(context, PurgeShotPopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putStringArrayListExtra("uris", urisList)
                    putStringArrayListExtra("displayNames", namesList)
                    putStringArrayListExtra("paths", pathsList)
                    putExtra("uri", singleCandidate.uri.toString())
                    putExtra("displayName", if (batch.size > 1) "${batch.size} Screenshots" else singleCandidate.displayName)
                    putExtra("path", singleCandidate.filePath)
                    putExtra("sizeLabel", sizeLabel)
                }
                context.startActivity(intent)
                Log.i(TAG, "Popup attempted outside Toolz for batch of ${batch.size}")
            } catch (e: Exception) {
                Log.w(TAG, "Popup start blocked outside Toolz (notification already active)", e)
            }
        }
    }

    private fun canDrawOverlays(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context)

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun querySizeLabel(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val sz = c.getColumnIndex(MediaStore.MediaColumns.SIZE).let { if (it != -1) c.getLong(it) else 0L }
                val w = c.getColumnIndex(MediaStore.Images.Media.WIDTH).let { if (it != -1) c.getInt(it) else 0 }
                val h = c.getColumnIndex(MediaStore.Images.Media.HEIGHT).let { if (it != -1) c.getInt(it) else 0 }
                buildString {
                    if (sz > 0) append(formatBytes(sz))
                    if (w > 0 && h > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("${w}×${h}")
                    }
                }.takeIf { it.isNotBlank() }
            } else null
        }
    }.getOrNull()

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

    /** @see PurgeShotUtils.formatDurationLabel */
    internal fun formatDurationLabel(duration: Long): String = PurgeShotUtils.formatDurationLabel(duration)

    /**
     * The ONLY user-facing notification posted after screenshot deletion is scheduled.
     * Controlled by the in-app notification toggle setting.
     */
    suspend fun showScheduledNotification(
        context: Context,
        settingsRepository: SettingsRepository,
        count: Int,
        label: String
    ) {
        val enabled = try { settingsRepository.purgeShotNotificationsEnabled.first() } catch (_: Exception) { true }
        if (!enabled) return

        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            ensureChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "purgeshot")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(
                context,
                SCHEDULED_NOTIF_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (count > 1) "Screenshots scheduled ($count)" else "Screenshot scheduled"
            val text = "Will be deleted in $label"

            val notif = NotificationCompat.Builder(context, PurgeShotService.ALERTS_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .build()

            mgr.notify(SCHEDULED_NOTIF_ID, notif)
        } catch (_: Exception) {}
    }

    private fun showPopupFallbackNotification(
        context: Context,
        batch: List<PurgeShotService.ScreenshotCandidate>,
        sizeLabel: String?
    ) {
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            ensureChannel(context)

            val isMultiple = batch.size > 1
            val title = if (isMultiple) "Delete these ${batch.size} screenshots?" else "Delete this screenshot?"
            val bodyText = if (isMultiple) "${batch.size} screenshots • tap to choose" else "${batch[0].displayName}${sizeLabel?.let { " • $it" } ?: ""} • tap to choose"

            val builder = NotificationCompat.Builder(context, PurgeShotService.ALERTS_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(bodyText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            val urisList = ArrayList(batch.map { it.uri.toString() })
            val namesList = ArrayList(batch.map { it.displayName })
            val pathsList = ArrayList(batch.map { it.filePath ?: "" })

            // Full-screen popup intent
            val popupIntent = Intent(context, PurgeShotPopupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putStringArrayListExtra("uris", urisList)
                putStringArrayListExtra("displayNames", namesList)
                putStringArrayListExtra("paths", pathsList)
                putExtra("uri", batch.last().uri.toString())
                putExtra("displayName", if (isMultiple) "${batch.size} Screenshots" else batch.last().displayName)
                putExtra("path", batch.last().filePath)
                putExtra("sizeLabel", sizeLabel)
            }
            val popupPi = PendingIntent.getActivity(
                context,
                batch.hashCode(),
                popupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(popupPi)
            builder.setFullScreenIntent(popupPi, true)

            // Keep action
            val keepIntent = Intent(context, PurgeShotActionReceiver::class.java).apply {
                action = "PURGE_KEEP"
                putStringArrayListExtra("uris", urisList)
                putExtra("uri", batch.last().uri.toString())
            }
            val keepPi = PendingIntent.getBroadcast(
                context,
                batch.hashCode() + 1,
                keepIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_launcher_foreground, "Keep", keepPi)

            // Quick timer actions
            val quick = listOf(
                "1 min" to 60_000L,
                "15 min" to 15 * 60_000L,
                "1 hour" to 60 * 60_000L
            )
            quick.forEachIndexed { idx, (label, millis) ->
                val actIntent = Intent(context, PurgeShotActionReceiver::class.java).apply {
                    action = "PURGE_ENQUEUE"
                    putStringArrayListExtra("uris", urisList)
                    putStringArrayListExtra("displayNames", namesList)
                    putStringArrayListExtra("paths", pathsList)
                    putExtra("uri", batch.last().uri.toString())
                    putExtra("displayName", batch.last().displayName)
                    putExtra("path", batch.last().filePath)
                    putExtra("duration", millis)
                    putExtra("label", label)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    batch.hashCode() + 10 + idx,
                    actIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_launcher_foreground, label, pi)
            }

            mgr.notify(batch.hashCode(), builder.build())
        } catch (_: Exception) {}
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            if (mgr.getNotificationChannel(PurgeShotService.ALERTS_CHANNEL_ID) == null) {
                val ch = android.app.NotificationChannel(
                    PurgeShotService.ALERTS_CHANNEL_ID,
                    PurgeShotService.ALERTS_CHANNEL_NAME,
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shows actions and popup when a new screenshot is taken"
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }
}
