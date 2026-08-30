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
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.PurgeShotPopupActivity
import com.frerox.toolz.service.PurgeShotService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

data class PopupCandidate(
    val uri: Uri,
    val displayName: String,
    val filePath: String?,
    val sizeLabel: String?
)

object PurgeShotHandler {
    private const val TAG = "PurgeShotHandler"
    private const val SCHEDULED_NOTIF_ID = 4150

    // Shared state holding all screenshots accumulated in the current popup session
    private val _activeBatchFlow = MutableStateFlow<List<PopupCandidate>>(emptyList())
    val activeBatchFlow: StateFlow<List<PopupCandidate>> = _activeBatchFlow.asStateFlow()

    private val _isPopupActive = MutableStateFlow(false)
    val isPopupActive: StateFlow<Boolean> = _isPopupActive.asStateFlow()

    fun setPopupActive(active: Boolean) {
        _isPopupActive.value = active
        if (!active) {
            _activeBatchFlow.value = emptyList()
        }
    }

    fun clearActiveBatch() {
        _activeBatchFlow.value = emptyList()
    }

    fun addCandidateDirectly(candidate: PopupCandidate) {
        _activeBatchFlow.update { current ->
            if (current.any { it.uri == candidate.uri }) current
            else current + candidate
        }
    }

    suspend fun handleNewScreenshot(
        context: Context,
        repository: PurgeShotRepository,
        settingsRepository: SettingsRepository,
        candidate: PurgeShotService.ScreenshotCandidate
    ) {
        val uriStr = candidate.uri.toString()

        // 1. Dedup check within currently active batch
        val currentBatch = _activeBatchFlow.value
        if (currentBatch.any { it.uri.toString() == uriStr }) {
            Log.d(TAG, "dedup within activeBatch $uriStr")
            return
        }

        // 2. Dedup against stored last screenshot if batch is empty
        val storedLast = try { settingsRepository.purgeShotLastScreenshotUri.first() } catch (_: Exception) { null }
        if (uriStr == storedLast && currentBatch.isEmpty()) {
            Log.d(TAG, "dedup storedLast $uriStr")
            return
        }
        settingsRepository.setPurgeShotLastScreenshotUri(uriStr)

        val smartAuto = try { settingsRepository.purgeShotSmartAuto.first() } catch (_: Exception) { false }
        val autoDuration = try { settingsRepository.purgeShotAutoDuration.first() } catch (_: Exception) { 15 * 60_000L }
        val label = formatDurationLabel(autoDuration)

        if (smartAuto) {
            // Smart Auto: queue immediately with auto time and show the single scheduled notification
            repository.enqueue(candidate.uri, candidate.displayName, autoDuration, label, candidate.filePath)
            Log.i(TAG, "SmartAuto queued $uriStr for $label")
            showScheduledNotification(context, settingsRepository, 1, label)
            return
        }

        val sizeLabel = querySizeLabel(context, candidate.uri)
        val popupCandidate = PopupCandidate(
            uri = candidate.uri,
            displayName = candidate.displayName,
            filePath = candidate.filePath,
            sizeLabel = sizeLabel
        )

        // 3. Append to active batch
        _activeBatchFlow.update { it + popupCandidate }
        val updatedBatch = _activeBatchFlow.value
        Log.i(TAG, "Appended screenshot to active batch (now ${updatedBatch.size} items, popupActive=${_isPopupActive.value})")

        // 4. If the popup is ALREADY active on screen (e.g. user screenshotting the popup or taking another screenshot),
        // we do NOT restart or create duplicate activities! The open popup's Compose state automatically collects
        // activeBatchFlow and instantly updates to show both/all screenshots.
        if (_isPopupActive.value) {
            Log.i(TAG, "Popup is already active on screen, live-updating Compose view with ${updatedBatch.size} screenshots")
            return
        }

        // 5. If popup is NOT active, launch PurgeShotPopupActivity
        val popupIntent = Intent(context, PurgeShotPopupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("uri", candidate.uri.toString())
            putExtra("displayName", candidate.displayName)
            putExtra("path", candidate.filePath)
            putExtra("sizeLabel", sizeLabel)
        }

        try {
            context.startActivity(popupIntent)
            Log.i(TAG, "Popup launched for $uriStr")
        } catch (e: Exception) {
            Log.w(TAG, "Popup launch failed for $uriStr", e)
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

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            if (mgr.getNotificationChannel(PurgeShotService.ALERTS_CHANNEL_ID) == null) {
                val ch = android.app.NotificationChannel(
                    PurgeShotService.ALERTS_CHANNEL_ID,
                    PurgeShotService.ALERTS_CHANNEL_NAME,
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Shows confirmation when screenshot deletion is scheduled"
                    setShowBadge(true)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }
}
