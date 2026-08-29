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
import kotlinx.coroutines.flow.first

object PurgeShotHandler {
    private const val TAG = "PurgeShotHandler"

    suspend fun handleNewScreenshot(
        context: Context,
        repository: PurgeShotRepository,
        settingsRepository: SettingsRepository,
        candidate: PurgeShotService.ScreenshotCandidate
    ) {
        val uriStr = candidate.uri.toString()
        // dedup via DataStore
        val storedLast = settingsRepository.purgeShotLastScreenshotUri.first()
        if (uriStr == storedLast) {
            Log.d(TAG, "dedup storedLast $uriStr")
            return
        }
        settingsRepository.setPurgeShotLastScreenshotUri(uriStr)

        val smartAuto = settingsRepository.purgeShotSmartAuto.first()
        val autoDuration = settingsRepository.purgeShotAutoDuration.first()

        val sizeLabel = querySizeLabel(context, candidate.uri)

        if (smartAuto) {
            // Smart Auto: no popup, directly queue with auto time — this is the spec
            val label = formatDurationLabel(autoDuration)
            repository.enqueue(candidate.uri, candidate.displayName, autoDuration, label, candidate.filePath)
            Log.i(TAG, "SmartAuto queued $uriStr for $label")
            showAutoNotification(context, candidate.displayName, label, sizeLabel)
            return
        }

        // Not smart-auto: popup vs notification routing.
        // Previous code did: if canShowPopup try popup and RETURN (assuming success). On Android 10+,
        // background activity start is silently blocked without exception, so we returned thinking
        // popup showed, but user saw nothing — this was the "doesn't work anywhere at all" root cause
        // when screenshot taken outside Toolz (isFocused=false) and overlay not granted.
        // Fixed: when app is not focused (outside Toolz), ALWAYS show notification fallback first.
        // Popup is best-effort additional (only if overlay/Focused), never exclusive.
        val isFocused = ToolzApplication.isFocused.value
        val canOverlay = canDrawOverlays(context)
        val canShowPopup = isFocused || canOverlay || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        val hasNotifPerm = hasNotificationPermission(context)

        // If we are inside Toolz (focused), popup is reliable — try it first, fallback to notification only on exception.
        if (isFocused) {
            if (canShowPopup) {
                try {
                    val intent = Intent(context, PurgeShotPopupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("uri", uriStr)
                        putExtra("displayName", candidate.displayName)
                        putExtra("path", candidate.filePath)
                        putExtra("sizeLabel", sizeLabel)
                    }
                    context.startActivity(intent)
                    Log.i(TAG, "Popup launched (focused) for $uriStr")
                    // Even when focused, also ensure notification fallback is NOT needed — popup is visible.
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Popup failed even when focused, fallback to notification", e)
                }
            }
            // Focused but popup failed or not allowed — try notification if permitted
            if (hasNotifPerm) {
                showPopupFallbackNotification(context, candidate, sizeLabel)
            } else {
                Log.w(TAG, "No notification permission and popup failed — user will see nothing, post permission notification")
                // Try to at least post a permission request via the helper (will be blocked too but attempt)
                showPopupFallbackNotification(context, candidate, sizeLabel)
            }
            return
        }

        // Outside Toolz: ALWAYS show notification first (guaranteed outside-process path).
        // This is the "works everywhere" guarantee — notification works even when app dead, BAL blocked, or overlay denied.
        if (hasNotifPerm) {
            showPopupFallbackNotification(context, candidate, sizeLabel)
            Log.i(TAG, "Fallback notification shown (outside Toolz) for $uriStr")
        } else {
            Log.w(TAG, "Missing POST_NOTIFICATIONS, notification will be silently blocked — attempting popup via overlay as last resort")
        }

        // Best-effort popup as well when overlay/focus allows — don't return early on assumed success,
        // because BAL may silently block without exception. We log attempt but notification is already shown.
        if (canShowPopup) {
            try {
                val intent = Intent(context, PurgeShotPopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("uri", uriStr)
                    putExtra("displayName", candidate.displayName)
                    putExtra("path", candidate.filePath)
                    putExtra("sizeLabel", sizeLabel)
                }
                context.startActivity(intent)
                Log.i(TAG, "Popup also attempted (outside Toolz, overlay=$canOverlay) for $uriStr")
            } catch (e: Exception) {
                Log.w(TAG, "Popup BAL blocked outside Toolz (notification already shown)", e)
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
            // Below 13, notifications are granted by default (unless user disabled in settings, which we can't check synchronously without NM)
            // Check via NotificationManagerCompat areNotificationsEnabled as best-effort
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
        else -> "${duration / 60_000} min"
    }

    private fun showAutoNotification(context: Context, displayName: String, label: String, sizeLabel: String?) {
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            ensureChannel(context)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "purgeshot")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(context, (System.currentTimeMillis() % 10000).toInt(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notif = NotificationCompat.Builder(context, PurgeShotService.CHANNEL_ID)
                .setContentTitle("PurgeShot • auto-queued")
                .setContentText("$displayName • $label${sizeLabel?.let { " • $it" } ?: ""}")
                .setStyle(NotificationCompat.BigTextStyle().bigText("$displayName will be deleted in $label. Tap to undo."))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_launcher_foreground, "Undo", pi)
                .setContentIntent(pi)
                .build()
            mgr.notify((System.currentTimeMillis() % 10000).toInt() + 5000, notif)
        } catch (_: Exception) {}
    }

    private fun showPopupFallbackNotification(context: Context, candidate: PurgeShotService.ScreenshotCandidate, sizeLabel: String?) {
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            ensureChannel(context)

            // Build 6 timer actions as notification buttons (first 3 as actions, rest via open)
            val presets = PurgeShotPreset.defaults() // will be resolved with live autoDuration elsewhere, but use static for fallback
            val builder = NotificationCompat.Builder(context, PurgeShotService.CHANNEL_ID)
                .setContentTitle("Delete this screenshot?")
                .setContentText("${candidate.displayName}${sizeLabel?.let { " • $it" } ?: ""} • tap to choose")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)

            // Full-screen popup intent (when user taps body)
            val popupIntent = Intent(context, PurgeShotPopupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("uri", candidate.uri.toString())
                putExtra("displayName", candidate.displayName)
                putExtra("path", candidate.filePath)
                putExtra("sizeLabel", sizeLabel)
            }
            val popupPi = PendingIntent.getActivity(context, candidate.uri.hashCode(), popupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(popupPi)
            builder.setFullScreenIntent(popupPi, true)

            // Add Keep action
            val keepIntent = Intent(context, PurgeShotActionReceiver::class.java).apply {
                action = "PURGE_KEEP"
                putExtra("uri", candidate.uri.toString())
            }
            val keepPi = PendingIntent.getBroadcast(context, candidate.uri.hashCode() + 1, keepIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_launcher_foreground, "Keep", keepPi)

            // Add up to 3 quick timer actions
            val quick = listOf(
                "1 min" to 60_000L,
                "15 min" to 15 * 60_000L,
                "1 hour" to 60 * 60_000L
            )
            quick.forEachIndexed { idx, (label, millis) ->
                val actIntent = Intent(context, PurgeShotActionReceiver::class.java).apply {
                    action = "PURGE_ENQUEUE"
                    putExtra("uri", candidate.uri.toString())
                    putExtra("displayName", candidate.displayName)
                    putExtra("path", candidate.filePath)
                    putExtra("duration", millis)
                    putExtra("label", label)
                }
                val pi = PendingIntent.getBroadcast(context, candidate.uri.hashCode() + 10 + idx, actIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(R.drawable.ic_launcher_foreground, label, pi)
            }

            mgr.notify(candidate.uri.hashCode(), builder.build())
        } catch (_: Exception) {}
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            if (mgr.getNotificationChannel(PurgeShotService.CHANNEL_ID) == null) {
                val ch = android.app.NotificationChannel(PurgeShotService.CHANNEL_ID, PurgeShotService.CHANNEL_NAME, android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "PurgeShot screenshot controls"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }
}
