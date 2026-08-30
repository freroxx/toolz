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

    // Thread-safe set of recently handled URIs/paths to prevent duplicate popups / duplicate enqueues
    private val handledUris = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    fun isUriHandled(uriStr: String): Boolean {
        return handledUris.contains(uriStr)
    }

    /** Returns true if uri was newly marked, false if already handled (atomic check-and-add). */
    fun tryMarkHandled(uriStr: String): Boolean {
        synchronized(handledUris) {
            if (handledUris.contains(uriStr)) return false
            handledUris.add(uriStr)
            if (handledUris.size > 200) {
                val it = handledUris.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
            return true
        }
    }

    fun isAnyHandled(uriStr: String, filePath: String?): Boolean {
        synchronized(handledUris) {
            if (handledUris.contains(uriStr)) return true
            if (filePath != null && handledUris.contains(filePath)) return true
            return false
        }
    }

    fun markUriHandled(uriStr: String) {
        synchronized(handledUris) {
            handledUris.add(uriStr)
            if (handledUris.size > 200) {
                val it = handledUris.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }

    fun markAllHandled(uris: Collection<String>) {
        for (u in uris) markUriHandled(u)
    }

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
            if (current.any { it.uri == candidate.uri || (candidate.filePath != null && it.filePath == candidate.filePath) }) current
            else current + candidate
        }
    }

    /** Atomically adds candidate if not already present by uri or filePath. Returns true if added. */
    fun tryAddCandidate(candidate: PopupCandidate): Boolean {
        var added = false
        _activeBatchFlow.update { current ->
            if (current.any { it.uri == candidate.uri || (candidate.filePath != null && it.filePath == candidate.filePath) }) {
                current
            } else {
                added = true
                current + candidate
            }
        }
        return added
    }

    suspend fun handleNewScreenshot(
        context: Context,
        repository: PurgeShotRepository,
        settingsRepository: SettingsRepository,
        candidate: PurgeShotService.ScreenshotCandidate
    ) {
        val uriStr = candidate.uri.toString()
        val path = candidate.filePath

        // 1. Memory check: already handled recently? (check both uri and filePath)
        if (isAnyHandled(uriStr, path)) {
            Log.d(TAG, "skip already handled uri $uriStr path=$path")
            return
        }

        // 2. Database check: is this URI or filePath already in the database (any status)?
        val existingByUri = repository.getEntryByUri(uriStr)
        if (existingByUri != null) {
            Log.d(TAG, "skip uri already in database (status=${existingByUri.status}) $uriStr")
            markUriHandled(uriStr)
            if (path != null) markUriHandled(path)
            return
        }
        if (path != null) {
            val existingByPath = repository.getEntryByPath(path)
            if (existingByPath != null) {
                Log.d(TAG, "skip path already in database (status=${existingByPath.status}) path=$path uri=$uriStr")
                markUriHandled(uriStr)
                markUriHandled(path)
                return
            }
        }

        // 3. Stored last screenshot dedup (quick DataStore check)
        val storedLast = try { settingsRepository.purgeShotLastScreenshotUri.first() } catch (_: Exception) { null }
        if ((uriStr == storedLast || (path != null && path == storedLast)) && _activeBatchFlow.value.isEmpty()) {
            Log.d(TAG, "dedup storedLast $uriStr path=$path")
            markUriHandled(uriStr)
            if (path != null) markUriHandled(path)
            return
        }
        settingsRepository.setPurgeShotLastScreenshotUri(uriStr)

        val smartAuto = try { settingsRepository.purgeShotSmartAuto.first() } catch (_: Exception) { false }
        val autoDuration = try { settingsRepository.purgeShotAutoDuration.first() } catch (_: Exception) { 15 * 60_000L }
        val label = formatDurationLabel(autoDuration)

        if (smartAuto) {
            // Atomic claim: if another coroutine already claimed this uri/path, skip
            val claimed = tryMarkHandled(uriStr)
            if (!claimed) {
                Log.d(TAG, "smartAuto race skip $uriStr")
                return
            }
            if (path != null) markUriHandled(path)
            repository.enqueue(candidate.uri, candidate.displayName, autoDuration, label, path)
            Log.i(TAG, "SmartAuto queued $uriStr for $label")
            showScheduledNotification(context, settingsRepository, 1, label)
            return
        }

        val sizeLabel = querySizeLabel(context, candidate.uri)
        val popupCandidate = PopupCandidate(
            uri = candidate.uri,
            displayName = candidate.displayName,
            filePath = path,
            sizeLabel = sizeLabel
        )

        // 3. Append to active batch atomically (dedup by uri or filePath)
        val added = tryAddCandidate(popupCandidate)
        if (!added) {
            Log.d(TAG, "dedup within activeBatch race $uriStr path=$path")
            return
        }
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

            val title = if (count > 1) {
                context.getString(R.string.st_PurgeShot_Notif_Scheduled_Multiple, count)
            } else {
                context.getString(R.string.st_PurgeShot_Notif_Scheduled_Single)
            }
            val text = context.getString(R.string.st_PurgeShot_Notif_Scheduled_Desc, label)

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
