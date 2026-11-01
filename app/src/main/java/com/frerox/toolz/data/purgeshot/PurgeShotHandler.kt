package com.frerox.toolz.data.purgeshot

import android.app.NotificationManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class PopupCandidate(
    val uri: Uri,
    val displayName: String,
    val filePath: String?,
    val sizeLabel: String?
)

object PurgeShotHandler {
    private const val TAG = "PurgeShotHandler"
    private const val SCHEDULED_NOTIF_ID = 4150
    /** User-facing prompt notification that backs up the direct popup launch. */
    private const val PROMPT_NOTIF_ID = 4151
    private const val MAX_PENDING_PROMPTS = 16
    /**
     * How long to wait for the popup activity to actually become visible before
     * posting the notification fallback. `startActivity` does not throw when
     * Android silently blocks a background launch, so visibility must be
     * checked instead of relying on an exception.
     */
    private const val PROMPT_FALLBACK_DELAY_MS = 900L

    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serializes the persisted pending-prompt list. Detection can run from the
     * service, the JobScheduler path and the Shizuku watcher at the same time;
     * without this lock two of them could read-modify-write the same DataStore
     * key and drop an entry.
     */
    private val pendingMutex = Mutex()

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

    // ── Persisted prompt store ────────────────────────────────────────────
    //
    // A prompt is only cleared when the user actually resolves it. Previously
    // the handler wrote `purgeshot_last_screenshot_uri` *before* launching the
    // popup, so a blocked background-activity start (or process death right
    // after detection) silently swallowed the prompt forever. Pending prompts
    // are now persisted first, the launch is backed by a notification, and the
    // dedupe set only grows once a decision has been made.

    private fun encodePending(items: List<PopupCandidate>): String {
        val array = JSONArray()
        items.forEach { candidate ->
            array.put(
                JSONObject().apply {
                    put("uri", candidate.uri.toString())
                    put("name", candidate.displayName)
                    candidate.filePath?.let { put("path", it) }
                    candidate.sizeLabel?.let { put("size", it) }
                },
            )
        }
        return array.toString()
    }

    private fun decodePending(raw: String?): List<PopupCandidate> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val uri = obj.optString("uri").takeIf { it.isNotBlank() }
                        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                        ?: continue
                    add(
                        PopupCandidate(
                            uri = uri,
                            displayName = obj.optString("name").ifBlank { uri.lastPathSegment.orEmpty() },
                            filePath = obj.optString("path").takeIf { it.isNotBlank() },
                            sizeLabel = obj.optString("size").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun readPendingLocked(settingsRepository: SettingsRepository): List<PopupCandidate> =
        decodePending(
            runCatching { settingsRepository.purgeShotPendingPrompts.first() }.getOrNull(),
        )

    private fun samePrompt(candidate: PopupCandidate, uriStr: String, path: String?): Boolean =
        candidate.uri.toString() == uriStr ||
            (path != null && candidate.filePath == path)

    /** Adds a prompt to the persisted queue. Returns false if it was already there. */
    suspend fun rememberPending(
        settingsRepository: SettingsRepository,
        candidate: PopupCandidate,
    ): Boolean = pendingMutex.withLock {
        val current = readPendingLocked(settingsRepository)
        if (current.any { samePrompt(it, candidate.uri.toString(), candidate.filePath) }) {
            false
        } else {
            val next = (current + candidate).takeLast(MAX_PENDING_PROMPTS)
            settingsRepository.setPurgeShotPendingPrompts(encodePending(next))
            true
        }
    }

    /**
     * Clears prompts after the user picked a timer / deleted / kept / dismissed.
     * Marks them handled in memory and remembers them as resolved so the same
     * screenshot is never prompted again.
     */
    suspend fun resolvePending(
        settingsRepository: SettingsRepository,
        candidates: List<PopupCandidate>,
    ) {
        if (candidates.isEmpty()) return
        markAllHandled(candidates.map { it.uri.toString() })
        candidates.forEach { it.filePath?.let(::markUriHandled) }
        pendingMutex.withLock {
            val remaining = readPendingLocked(settingsRepository).filterNot { existing ->
                candidates.any { samePrompt(existing, it.uri.toString(), it.filePath) }
            }
            settingsRepository.setPurgeShotPendingPrompts(
                if (remaining.isEmpty()) null else encodePending(remaining),
            )
            settingsRepository.addPurgeShotResolvedUris(
                candidates.flatMap { listOfNotNull(it.uri.toString(), it.filePath) },
            )
            settingsRepository.setPurgeShotLastScreenshotUri(candidates.lastOrNull()?.uri?.toString())
        }
    }

    /**
     * Fire-and-forget variant used by the popup activity. The activity may be
     * finishing, so resolving on its lifecycle scope would sometimes cancel the
     * write and cause the same prompt to reappear.
     */
    fun resolvePendingAsync(
        settingsRepository: SettingsRepository,
        candidates: List<PopupCandidate>,
    ) {
        if (candidates.isEmpty()) return
        fallbackScope.launch { resolvePending(settingsRepository, candidates) }
    }

    /**
     * Re-arms pending prompts after a process restart / service restart. Safe
     * to call repeatedly: the in-memory batch and notification are idempotent.
     */
    suspend fun restorePendingPrompts(
        context: Context,
        settingsRepository: SettingsRepository,
    ) {
        // Prompts belong to PurgeShot: while the feature is off, a stale queue
        // must not surface a deletion popup or notification out of nowhere.
        // A failed read is treated as "unknown" so a transient DataStore error
        // cannot silently discard an unresolved prompt.
        val enabled = runCatching { settingsRepository.purgeShotEnabled.first() }.getOrNull()
        if (enabled == false) {
            pendingMutex.withLock {
                settingsRepository.setPurgeShotPendingPrompts(null)
            }
            cancelPromptNotification(context)
            return
        }
        val pending = pendingMutex.withLock { readPendingLocked(settingsRepository) }
        if (pending.isEmpty()) {
            cancelPromptNotification(context)
            return
        }
        pending.forEach(::addCandidateDirectly)
        if (!_isPopupActive.value) {
            showPromptNotification(context, settingsRepository, pending)
        }
    }

    private suspend fun isResolved(
        settingsRepository: SettingsRepository,
        uriStr: String,
        path: String?,
    ): Boolean {
        val resolved = runCatching { settingsRepository.purgeShotResolvedUris.first() }
            .getOrDefault(emptySet())
        return uriStr in resolved || (path != null && path in resolved)
    }

    private suspend fun rememberResolved(
        settingsRepository: SettingsRepository,
        candidate: PurgeShotService.ScreenshotCandidate,
    ) {
        settingsRepository.addPurgeShotResolvedUris(
            listOfNotNull(candidate.uri.toString(), candidate.filePath),
        )
        settingsRepository.setPurgeShotLastScreenshotUri(candidate.uri.toString())
    }

    private fun launchPopup(context: Context, candidate: PopupCandidate): Boolean = try {
        val popupIntent = Intent(context, PurgeShotPopupActivity::class.java).apply {
            action = PurgeShotPopupActivity.ACTION_SHOW_PROMPT
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("uri", candidate.uri.toString())
            putExtra("displayName", candidate.displayName)
            putExtra("path", candidate.filePath)
            putExtra("sizeLabel", candidate.sizeLabel)
        }
        context.startActivity(popupIntent)
        Log.i(TAG, "Popup launch requested for ${candidate.uri}")
        true
    } catch (e: Exception) {
        Log.w(TAG, "Popup launch failed for ${candidate.uri}", e)
        false
    }

    private fun schedulePromptFallback(
        context: Context,
        settingsRepository: SettingsRepository,
    ) {
        fallbackScope.launch {
            delay(PROMPT_FALLBACK_DELAY_MS)
            if (_isPopupActive.value) return@launch
            val pending = pendingMutex.withLock { readPendingLocked(settingsRepository) }
            if (pending.isNotEmpty()) {
                Log.i(TAG, "Popup did not become visible; posting prompt notification")
                showPromptNotification(context, settingsRepository, pending)
            }
        }
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

        // 3. Already waiting for the user? Make sure the prompt is actually
        //    visible instead of silently returning. This is the recovery path
        //    for a popup launch that Android blocked in the background.
        val alreadyPending = pendingMutex.withLock { readPendingLocked(settingsRepository) }
            .firstOrNull { samePrompt(it, uriStr, path) }
        if (alreadyPending != null) {
            Log.d(TAG, "prompt already pending for $uriStr; ensuring it is visible")
            addCandidateDirectly(alreadyPending)
            markUriHandled(uriStr)
            if (path != null) markUriHandled(path)
            if (!_isPopupActive.value) {
                if (launchPopup(context, alreadyPending)) {
                    schedulePromptFallback(context, settingsRepository)
                } else {
                    showPromptNotification(context, settingsRepository, _activeBatchFlow.value)
                }
            }
            return
        }

        // 4. The user already resolved this screenshot (kept/dismissed) in an
        //    earlier process. Never prompt for it again.
        if (isResolved(settingsRepository, uriStr, path)) {
            Log.d(TAG, "skip already resolved uri $uriStr path=$path")
            markUriHandled(uriStr)
            if (path != null) markUriHandled(path)
            return
        }

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
            rememberResolved(settingsRepository, candidate)
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

        // 5. Append to active batch atomically (dedup by uri or filePath)
        val added = tryAddCandidate(popupCandidate)
        if (!added) {
            Log.d(TAG, "dedup within activeBatch race $uriStr path=$path")
            return
        }
        val updatedBatch = _activeBatchFlow.value
        Log.i(TAG, "Appended screenshot to active batch (now ${updatedBatch.size} items, popupActive=${_isPopupActive.value})")

        // Persist before launching: if the process dies here the prompt is
        // restored by the next service/app start instead of being lost.
        try {
            rememberPending(settingsRepository, popupCandidate)
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist pending prompt", e)
        }

        // 6. If the popup is ALREADY active on screen (e.g. user screenshotting the popup or taking another screenshot),
        // we do NOT restart or create duplicate activities! The open popup's Compose state automatically collects
        // activeBatchFlow and instantly updates to show both/all screenshots.
        if (_isPopupActive.value) {
            Log.i(TAG, "Popup is already active on screen, live-updating Compose view with ${updatedBatch.size} screenshots")
            return
        }

        // 7. Direct launch, with a notification fallback if background-activity
        //    restrictions prevent it from becoming visible.
        if (launchPopup(context, popupCandidate)) {
            schedulePromptFallback(context, settingsRepository)
        } else {
            showPromptNotification(context, settingsRepository, _activeBatchFlow.value)
        }
    }

    // ── Prompt notification ───────────────────────────────────────────────

    /**
     * High-priority, optionally full-screen notification that opens the same
     * popup. This is the reliable path when the OS blocks a direct
     * `startActivity` from a background service.
     */
    private suspend fun showPromptNotification(
        context: Context,
        settingsRepository: SettingsRepository,
        candidates: List<PopupCandidate>,
    ) {
        if (candidates.isEmpty()) return
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            ensureChannel(context)

            val popupIntent = Intent(context, PurgeShotPopupActivity::class.java).apply {
                action = PurgeShotPopupActivity.ACTION_SHOW_PROMPT
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pi = PendingIntent.getActivity(
                context,
                PROMPT_NOTIF_ID,
                popupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val title = if (candidates.size > 1) {
                context.getString(R.string.st_PurgeShot_Notif_Prompt_Multiple, candidates.size)
            } else {
                context.getString(R.string.st_PurgeShot_Notif_Prompt_Single)
            }
            val text = context.getString(R.string.st_PurgeShot_ChooseWhenToDelete)

            val builder = NotificationCompat.Builder(context, PurgeShotService.ALERTS_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_stat_toolz)
                .setLargeIcon(com.frerox.toolz.util.NotificationHelper.toolzLargeIcon(context))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(R.drawable.ic_stat_toolz, text, pi)

            if (canUseFullScreenIntent(context)) {
                builder.setFullScreenIntent(pi, true)
            }

            mgr.notify(PROMPT_NOTIF_ID, builder.build())
            Log.i(TAG, "Prompt notification posted (${candidates.size} screenshot(s))")
        } catch (e: Exception) {
            Log.w(TAG, "Prompt notification failed", e)
        }
    }

    fun cancelPromptNotification(context: Context) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.cancel(PROMPT_NOTIF_ID)
        } catch (_: Exception) {
        }
    }

    private fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            mgr?.canUseFullScreenIntent() ?: false
        } catch (_: Exception) {
            // Unknown capability must not remove the notification fallback.
            false
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
        // Background master switch hides every background-activity notification,
        // including PurgeShot scheduling confirmations.
        val backgroundEnabled = try { settingsRepository.backgroundNotificationsEnabled.first() } catch (_: Exception) { true }
        if (!backgroundEnabled) return

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
                .setSmallIcon(R.drawable.ic_stat_toolz)
                .setLargeIcon(com.frerox.toolz.util.NotificationHelper.toolzLargeIcon(context))
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
