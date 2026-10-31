/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.frerox.toolz.ui.screens.clipboard

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.ToolzForegroundTracker
import com.frerox.toolz.data.clipboard.ClipAiState
import com.frerox.toolz.data.clipboard.ClipboardAiProcessor
import com.frerox.toolz.data.clipboard.ClipboardAttachment
import com.frerox.toolz.data.clipboard.ClipboardBlobStore
import com.frerox.toolz.data.clipboard.ClipboardCaptureProcessor
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.clipboard.ClipboardFilter
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.data.clipboard.ClipboardPayload
import com.frerox.toolz.data.clipboard.ClipboardPayloadItem
import com.frerox.toolz.data.clipboard.ClipboardRetentionManager
import com.frerox.toolz.data.clipboard.ClipboardScreenshotIngestor
import com.frerox.toolz.data.clipboard.ClipboardSource
import com.frerox.toolz.data.clipboard.ClipboardType
import com.frerox.toolz.data.clipboard.ClipSensitivityReason
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.FakeClipboardSandbox
import com.frerox.toolz.data.developer.SpoofGate
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ClipboardService
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.util.shizuku.ShizukuShellExecutor
import com.frerox.toolz.worker.ClipboardCleanupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject

/** Date buckets used by the history list. Labels are resolved in the UI. */
enum class ClipboardDateGroup { PINNED, TODAY, YESTERDAY, OLDER }

data class ClipboardGroup(
    val group: ClipboardDateGroup,
    val entries: List<ClipboardEntry>,
)

data class ClipboardSettings(
    val monitoring: Boolean = true,
    val aiEnabled: Boolean = true,
    val autoAi: Boolean = false,
    val retentionDays: Int = 7,
    val excludeSensitive: Boolean = false,
    val onboardingSeen: Boolean = false,
    val biometricLock: Boolean = true,
    val itemQuotaMb: Int = 25,
    val totalQuotaMb: Int = 250,
    val maxEntries: Int = 500,
    val backupWarningSeen: Boolean = false,
    val offline: Boolean = false,
    val captureScreenshots: Boolean = true,
)

data class ClipboardGateInfo(
    val status: ClipboardGate.Status = ClipboardGate.Status.SETUP_REQUIRED,
    val capabilities: ClipboardGate.Capabilities = ClipboardGate.Capabilities(),
)

data class ClipboardUiState(
    val entries: List<ClipboardEntry> = emptyList(),
    val trashed: List<ClipboardEntry> = emptyList(),
    val visibleEntries: List<ClipboardEntry> = emptyList(),
    val attachmentsByClip: Map<Int, List<ClipboardAttachment>> = emptyMap(),
    val filter: ClipboardFilter = ClipboardFilter.ALL,
    val query: String = "",
    val showTrash: Boolean = false,
    val selection: Set<Int> = emptySet(),
    val revealedIds: Set<Int> = emptySet(),
    val aiRunningIds: Set<Int> = emptySet(),
    val busy: Boolean = false,
    val aiConfigured: Boolean = false,
    val gate: ClipboardGateInfo = ClipboardGateInfo(),
    val settings: ClipboardSettings = ClipboardSettings(),
    val stats: ClipboardRetentionManager.Stats = ClipboardRetentionManager.Stats(),
    val sandboxActive: Boolean = false,
    /** False until the first real settings/history snapshot has arrived. */
    val loaded: Boolean = false,
) {
    val isSelectionMode: Boolean get() = selection.isNotEmpty()
    val canCaptureInBackground: Boolean get() = gate.capabilities.backgroundCapable
    val showSetupBanner: Boolean
        get() = loaded && settings.monitoring && !gate.capabilities.backgroundCapable &&
            gate.status != ClipboardGate.Status.PAUSED
}

enum class ClipboardMessage {
    SAVED,
    BUMPED,
    EMPTY,
    NOT_FOREGROUND,
    SENSITIVE_SKIPPED,
    TOO_LARGE,
    QUOTA,
    FAILED,
    COPIED,
    SELECTION_COPIED,
    SHARED,
    PINNED,
    UNPINNED,
    LOCKED,
    UNLOCKED,
    MARKED_SENSITIVE,
    MARKED_NOT_SENSITIVE,
    DELETED,
    DELETED_FOREVER,
    RESTORED,
    TRASH_EMPTY,
    CLEARED,
    AI_DONE,
    AI_SENSITIVE,
    AI_TEXT_ONLY,
    AI_DISABLED,
    AI_NOT_CONFIGURED,
    AI_FAILED,
    ONBOARDING_DONE,
    APP_OP_GRANTED,
    APP_OP_FAILED,
    APP_OP_REVOKED,
    SHIZUKU_GRANTED,
    SHIZUKU_DENIED,
    SHIZUKU_NOT_RUNNING,
    ALREADY_AUTHORIZED,
    SANDBOX,
    ATTACHMENT_FAILED,
    NOTHING_TO_COPY,
}

sealed interface ClipboardEvent {
    data class Message(
        val type: ClipboardMessage,
        val count: Int = 0,
        val detail: String? = null,
    ) : ClipboardEvent

    data class Share(val intent: Intent) : ClipboardEvent
}

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val application: Application,
    private val clipboardDao: ClipboardDao,
    private val settingsRepository: SettingsRepository,
    private val shizukuExecutor: ShizukuShellExecutor,
    private val captureProcessor: ClipboardCaptureProcessor,
    private val aiProcessor: ClipboardAiProcessor,
    private val retentionManager: ClipboardRetentionManager,
    private val blobStore: ClipboardBlobStore,
    private val screenshotIngestor: ClipboardScreenshotIngestor,
    private val gate: SpoofGate,
    val classifier: ClipboardClassifier,
) : AndroidViewModel(application) {

    val clipboardSandboxActive: Boolean
        get() = gate.sandboxed(DevToolId.CLIPBOARD)

    // ── History sources ───────────────────────────────────────────────────

    private val realEntries = clipboardDao.getAllEntries()
    private val realTrashed = clipboardDao.getTrashedEntries()
    private val realAttachments = clipboardDao.getAllAttachments()
    private val sandboxFlow = gate.active(DevToolId.CLIPBOARD)

    private val activeEntries: Flow<List<ClipboardEntry>> = combine(
        realEntries,
        sandboxFlow,
        FakeClipboardSandbox.entries(),
    ) { real, spoofed, fake ->
        if (spoofed) fake.filter { it.deletedAt == null } else real
    }

    private val trashedEntries: Flow<List<ClipboardEntry>> = combine(
        realTrashed,
        sandboxFlow,
        FakeClipboardSandbox.entries(),
    ) { real, spoofed, fake ->
        if (spoofed) fake.filter { it.deletedAt != null } else real
    }

    private val attachmentMap: Flow<Map<Int, List<ClipboardAttachment>>> = combine(
        realAttachments,
        sandboxFlow,
    ) { attachments, spoofed ->
        if (spoofed) emptyMap() else attachments.groupBy { it.clipId }
    }

    private data class HistorySnapshot(
        val entries: List<ClipboardEntry>,
        val trashed: List<ClipboardEntry>,
        val attachments: Map<Int, List<ClipboardAttachment>>,
    )

    private val history: Flow<HistorySnapshot> = combine(
        activeEntries,
        trashedEntries,
        attachmentMap,
    ) { entries, trashed, attachments ->
        HistorySnapshot(entries, trashed, attachments)
    }

    // ── Settings ──────────────────────────────────────────────────────────

    private data class SettingsA(
        val monitoring: Boolean,
        val ai: Boolean,
        val autoAi: Boolean,
        val retention: Int,
    )

    private data class SettingsB(
        val exclude: Boolean,
        val onboarding: Boolean,
        val biometric: Boolean,
        val itemQuota: Int,
        val totalQuota: Int,
    )

    private data class SettingsC(
        val maxEntries: Int,
        val backupWarning: Boolean,
        val offline: Boolean,
        val captureScreenshots: Boolean,
    )

    private val settingsA = combine(
        settingsRepository.clipboardMonitoringEnabled,
        settingsRepository.clipboardAiEnabled,
        settingsRepository.aiClipboardMonitoringEnabled,
        settingsRepository.clipboardRetentionDays,
    ) { monitoring, ai, autoAi, retention ->
        SettingsA(monitoring, ai, autoAi, retention)
    }

    private val settingsB = combine(
        settingsRepository.clipboardExcludeSensitive,
        settingsRepository.clipboardOnboardingSeen,
        settingsRepository.clipboardBiometricLock,
        settingsRepository.clipboardItemQuotaMb,
        settingsRepository.clipboardTotalQuotaMb,
    ) { exclude, onboarding, biometric, itemQuota, totalQuota ->
        SettingsB(exclude, onboarding, biometric, itemQuota, totalQuota)
    }

    private val settingsC = combine(
        settingsRepository.clipboardMaxEntries,
        settingsRepository.clipboardBackupWarningSeen,
        settingsRepository.offlineModeEnabled,
        settingsRepository.clipboardCaptureScreenshots,
    ) { maxEntries, backupWarning, offline, captureScreenshots ->
        SettingsC(maxEntries, backupWarning, offline, captureScreenshots)
    }

    private val settingsState: Flow<ClipboardSettings> = combine(
        settingsA,
        settingsB,
        settingsC,
    ) { a, b, c ->
        ClipboardSettings(
            monitoring = a.monitoring,
            aiEnabled = a.ai,
            autoAi = a.autoAi,
            retentionDays = a.retention,
            excludeSensitive = b.exclude,
            onboardingSeen = b.onboarding,
            biometricLock = b.biometric,
            itemQuotaMb = b.itemQuota,
            totalQuotaMb = b.totalQuota,
            maxEntries = c.maxEntries,
            backupWarningSeen = c.backupWarning,
            offline = c.offline,
            captureScreenshots = c.captureScreenshots,
        )
    }

    // ── Mutable UI state ──────────────────────────────────────────────────

    private val _gateInfo = MutableStateFlow(ClipboardGateInfo())
    private val _stats = MutableStateFlow(ClipboardRetentionManager.Stats())

    private data class Extras(
        val query: String = "",
        val filter: ClipboardFilter = ClipboardFilter.ALL,
        val showTrash: Boolean = false,
        val selection: Set<Int> = emptySet(),
        val revealed: Set<Int> = emptySet(),
        val aiRunning: Set<Int> = emptySet(),
        val busy: Boolean = false,
        val aiConfigured: Boolean = false,
        val loaded: Boolean = false,
    )

    private val _extras = MutableStateFlow(Extras())

    private val _events = Channel<ClipboardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var lastDeletedIds: List<Int> = emptyList()
    private var wasSandboxed = false
    private var appOpAutoGrantAttempted = false
    private val manualCaptureMutex = Mutex()

    val uiState: StateFlow<ClipboardUiState> = combine(
        history,
        settingsState,
        _gateInfo,
        _stats,
        _extras,
    ) { snapshot, settings, gateInfo, stats, extras ->
        val source = if (extras.showTrash) snapshot.trashed else snapshot.entries
        val visibleIds = source.mapTo(HashSet()) { it.id }
        val selection = extras.selection intersect visibleIds
        val filtered = filterEntries(
            entries = source,
            attachments = snapshot.attachments,
            query = extras.query,
            filter = extras.filter,
            revealed = extras.revealed,
        )
        ClipboardUiState(
            entries = snapshot.entries,
            trashed = snapshot.trashed,
            visibleEntries = filtered,
            attachmentsByClip = snapshot.attachments,
            filter = extras.filter,
            query = extras.query,
            showTrash = extras.showTrash,
            selection = selection,
            revealedIds = extras.revealed,
            aiRunningIds = extras.aiRunning,
            busy = extras.busy,
            aiConfigured = extras.aiConfigured,
            gate = gateInfo,
            settings = settings,
            // Counts always come from the live history snapshot. The stats
            // row is otherwise only refreshed on explicit actions, which made
            // it drift after captures made by the background service.
            stats = stats.copy(
                activeCount = snapshot.entries.size,
                trashedCount = snapshot.trashed.size,
                sensitiveCount = snapshot.entries.count { it.isSensitive || it.isLocked },
                itemQuotaMb = settings.itemQuotaMb,
                totalQuotaMb = settings.totalQuotaMb,
                maxEntries = settings.maxEntries,
            ),
            sandboxActive = clipboardSandboxActive,
            loaded = extras.loaded,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ClipboardUiState(),
    )

    init {
        ClipboardCleanupWorker.schedule(application)
        viewModelScope.launch {
            settingsState.collect { settings ->
                syncGate(settings)
                autoGrantAppOpIfNeeded(settings)
            }
        }
        viewModelScope.launch {
            sandboxFlow.collect { spoofed ->
                if (spoofed && !wasSandboxed) {
                    FakeClipboardSandbox.reset()
                }
                wasSandboxed = spoofed
                refreshGate()
                refreshStats()
            }
        }
        viewModelScope.launch {
            // Wait for the real settings and history before showing onboarding,
            // the setup banner or an empty state. Without this the screen
            // briefly renders defaults (onboarding unseen, setup required,
            // zero clips) and visibly flashes before the data arrives.
            try {
                settingsState.first()
                history.first()
                aiProcessor.resetStalledRuns()
                refreshStatsNow()
            } catch (e: Exception) {
                // A transient DB/settings failure must never leave the screen
                // stuck on the loading state forever.
                android.util.Log.w("ClipboardViewModel", "Initial clipboard load failed", e)
            } finally {
                _extras.update {
                    it.copy(
                        aiConfigured = runCatching { aiProcessor.isConfigured() }.getOrDefault(false),
                        loaded = true,
                    )
                }
            }
        }
    }

    // ── Gate / service lifecycle ──────────────────────────────────────────

    private suspend fun syncGate(settings: ClipboardSettings) {
        val status = if (clipboardSandboxActive) {
            ClipboardGate.Status.ACTIVE_SHIZUKU
        } else {
            ClipboardGate.evaluate(
                application,
                monitoringEnabled = settings.monitoring,
                onboardingSeen = settings.onboardingSeen,
            )
        }
        val capabilities = if (clipboardSandboxActive) {
            ClipboardGate.Capabilities(
                shizukuInstalled = true,
                shizukuRunning = true,
                shizukuAuthorized = true,
                onboardingSeen = true,
            )
        } else {
            ClipboardGate.capabilities(application, settings.onboardingSeen)
        }
        _gateInfo.value = ClipboardGateInfo(status, capabilities)
        applyServiceState(settings.monitoring, status)
    }

    fun refreshGate() {
        viewModelScope.launch {
            val settings = settingsState.first()
            syncGate(settings)
            autoGrantAppOpIfNeeded(settings)
        }
    }

    /**
     * Finishes setup for users who authorized Shizuku in an older build (or
     * from the Shizuku app): the READ_CLIPBOARD app-op is granted once per
     * process so background reads keep working after an update. Failures stay
     * silent here; the setup banner and the explicit grant button remain.
     */
    private suspend fun autoGrantAppOpIfNeeded(settings: ClipboardSettings) {
        if (appOpAutoGrantAttempted || clipboardSandboxActive) return
        if (!settings.onboardingSeen) return
        val caps = ClipboardGate.capabilities(application, settings.onboardingSeen)
        if (!caps.shizukuAuthorized || caps.appOpAllowed) return
        appOpAutoGrantAttempted = true
        if (ensureClipboardAppOp(notify = false)) {
            syncGate(settings)
        }
    }

    private fun applyServiceState(monitoring: Boolean, status: ClipboardGate.Status) {
        if (clipboardSandboxActive) {
            ClipboardService.stop(application)
            return
        }
        val shouldRun = monitoring &&
            (status == ClipboardGate.Status.ACTIVE_SHIZUKU ||
                status == ClipboardGate.Status.ACTIVE_APP_OP)
        if (shouldRun) {
            ClipboardService.startIfNeeded(application)
        } else {
            ClipboardService.stop(application)
        }
    }

    fun onScreenResumed() {
        refreshGate()
        refreshStats()
        refreshClipboard(notify = false)
        // Screenshots never reach the system clipboard on most ROMs. Scan
        // MediaStore when the screen returns so a capture taken while the
        // foreground service was stopped still lands in history.
        viewModelScope.launch {
            if (clipboardSandboxActive) return@launch
            try {
                val ingested = screenshotIngestor.ingestRecentScreenshots(isPoll = true)
                if (ingested > 0) refreshStatsNow()
            } catch (e: Exception) {
                android.util.Log.w("ClipboardViewModel", "Screenshot scan failed", e)
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch { refreshStatsNow() }
    }

    private suspend fun refreshStatsNow() {
        if (clipboardSandboxActive) {
            val fake = FakeClipboardSandbox.entries().value
            val settings = settingsState.first()
            _stats.value = ClipboardRetentionManager.Stats(
                activeCount = fake.count { it.deletedAt == null },
                trashedCount = fake.count { it.deletedAt != null },
                sensitiveCount = fake.count { it.isSensitive && it.deletedAt == null },
                attachmentBytes = 0,
                blobBytes = 0,
                itemQuotaMb = settings.itemQuotaMb,
                totalQuotaMb = settings.totalQuotaMb,
                maxEntries = settings.maxEntries,
            )
        } else {
            _stats.value = retentionManager.stats()
        }
        _extras.update { it.copy(aiConfigured = aiProcessor.isConfigured()) }
    }

    // ── Onboarding / setup ────────────────────────────────────────────────

    fun completeOnboarding() {
        viewModelScope.launch {
            // Close onboarding first. Granting the app-op goes through Shizuku
            // and can take seconds; keeping the sheet open until it finishes
            // made setup look frozen.
            settingsRepository.setClipboardOnboardingSeen(true)
            _events.send(ClipboardEvent.Message(ClipboardMessage.ONBOARDING_DONE))
            refreshGate()
            // If Shizuku was authorized during onboarding, also grant the
            // READ_CLIPBOARD app-op. That makes standard ClipboardManager
            // reads work in the background; the shell binder read is only a
            // secondary path. A failure is reported but never blocks setup.
            ensureClipboardAppOp()
            refreshGate()
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            settingsRepository.setClipboardOnboardingSeen(false)
            refreshGate()
        }
    }

    fun requestShizukuAuthorization() {
        if (ShizukuHelper.isAuthorized()) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.ALREADY_AUTHORIZED))
                // Authorization may have been granted from the Shizuku app
                // while Toolz was closed; finish the setup automatically.
                ensureClipboardAppOp()
                refreshGate()
            }
            return
        }
        if (!ShizukuHelper.isRunning()) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.SHIZUKU_NOT_RUNNING))
            }
            return
        }
        ShizukuHelper.requestAuthorization { granted ->
            viewModelScope.launch {
                _events.send(
                    ClipboardEvent.Message(
                        if (granted) ClipboardMessage.SHIZUKU_GRANTED
                        else ClipboardMessage.SHIZUKU_DENIED,
                    ),
                )
                if (granted) ensureClipboardAppOp()
                refreshGate()
            }
        }
    }

    fun grantClipboardAppOp() {
        viewModelScope.launch {
            if (!ShizukuHelper.isAuthorized()) {
                _events.send(ClipboardEvent.Message(ClipboardMessage.APP_OP_FAILED))
                refreshGate()
                return@launch
            }
            ensureClipboardAppOp()
            refreshGate()
        }
    }

    /**
     * Grants the `android:read_clipboard` app-op through Shizuku when needed.
     *
     * `appops set` can exit successfully even on ROMs that ignore the op, so
     * the result is verified through AppOpsManager instead of trusting the
     * exit code alone. Returns true only when the app-op is actually allowed.
     */
    private suspend fun ensureClipboardAppOp(notify: Boolean = true): Boolean {
        if (ClipboardGate.isClipboardAppOpAllowed(application)) return true
        if (!ShizukuHelper.isAuthorized()) return false
        val result = shizukuExecutor.grantClipboardAppOp()
        val allowed = ClipboardGate.isClipboardAppOpAllowed(application)
        if (notify) {
            _events.send(
                ClipboardEvent.Message(
                    if (allowed) ClipboardMessage.APP_OP_GRANTED else ClipboardMessage.APP_OP_FAILED,
                    detail = result.combinedOutput.takeIf { it.isNotBlank() && !allowed },
                ),
            )
        }
        return allowed
    }

    fun revokeClipboardAppOp() {
        viewModelScope.launch {
            val result = shizukuExecutor.revokeClipboardAppOp()
            _events.send(
                ClipboardEvent.Message(
                    if (result.isSuccess) ClipboardMessage.APP_OP_REVOKED
                    else ClipboardMessage.APP_OP_FAILED,
                    detail = result.combinedOutput.takeIf { it.isNotBlank() },
                ),
            )
            refreshGate()
        }
    }

    fun shizukuInstallIntent(): Intent = ClipboardGate.shizukuInstallIntent()

    fun appDetailsIntent(): Intent = ClipboardGate.appDetailsIntent(application)

    // ── Search / filters / selection ──────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _extras.update { it.copy(query = query) }
    }

    fun setFilter(filter: ClipboardFilter) {
        _extras.update { it.copy(filter = filter) }
    }

    fun setShowTrash(show: Boolean) {
        _extras.update { it.copy(showTrash = show, selection = emptySet()) }
    }

    fun toggleSelection(id: Int) {
        _extras.update { extras ->
            val next = extras.selection.toMutableSet()
            if (!next.add(id)) next.remove(id)
            extras.copy(selection = next)
        }
    }

    fun selectAll() {
        val ids = uiState.value.visibleEntries.mapTo(LinkedHashSet()) { it.id }
        _extras.update { it.copy(selection = ids) }
    }

    fun clearSelection() {
        _extras.update { it.copy(selection = emptySet()) }
    }

    fun markRevealed(id: Int) {
        _extras.update { it.copy(revealed = it.revealed + id) }
    }

    fun hideRevealed(id: Int) {
        _extras.update { it.copy(revealed = it.revealed - id) }
    }

    // ── Mutations ─────────────────────────────────────────────────────────

    fun togglePin(entry: ClipboardEntry) {
        val pinned = !entry.isPinned
        if (clipboardSandboxActive) {
            FakeClipboardSandbox.togglePin(entry.id)
        } else {
            viewModelScope.launch {
                clipboardDao.setPinnedWithExpiry(
                    id = entry.id,
                    pinned = pinned,
                    retentionMs = retentionMsOrZero(),
                )
                refreshStats()
            }
        }
        viewModelScope.launch {
            _events.send(
                ClipboardEvent.Message(
                    if (pinned) ClipboardMessage.PINNED else ClipboardMessage.UNPINNED,
                ),
            )
        }
    }

    fun setLocked(entry: ClipboardEntry, locked: Boolean) {
        if (clipboardSandboxActive) {
            FakeClipboardSandbox.setLocked(entry.id, locked)
            viewModelScope.launch {
                _events.send(
                    ClipboardEvent.Message(
                        if (locked) ClipboardMessage.LOCKED else ClipboardMessage.UNLOCKED,
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            if (locked) {
                clipboardDao.updateSensitivity(
                    id = entry.id,
                    sensitive = true,
                    reason = entry.sensitivityReason ?: ClipSensitivityReason.MANUAL,
                    vaultMatch = entry.passwordVaultMatch,
                    locked = true,
                )
                clipboardDao.setExpiresAt(entry.id, null)
                clipboardDao.clearAiDetails(entry.id, ClipAiState.SKIPPED_SENSITIVE)
            } else {
                val keepSensitive = entry.passwordVaultMatch ||
                    (entry.sensitivityReason != null &&
                        entry.sensitivityReason != ClipSensitivityReason.MANUAL)
                clipboardDao.updateSensitivity(
                    id = entry.id,
                    sensitive = keepSensitive,
                    reason = if (keepSensitive) entry.sensitivityReason else null,
                    vaultMatch = entry.passwordVaultMatch,
                    locked = false,
                )
                if (!keepSensitive) {
                    // Unlocking can clear sensitivity. Put the clip back on
                    // the normal retention schedule instead of keeping the
                    // "never expires" expiry that locking applied.
                    clipboardDao.setExpiresAt(entry.id, expiryFor(entry.timestamp))
                    clipboardDao.updateAiState(entry.id, ClipAiState.IDLE, null)
                }
            }
            _extras.update { it.copy(revealed = it.revealed - entry.id) }
            refreshStats()
            _events.send(
                ClipboardEvent.Message(
                    if (locked) ClipboardMessage.LOCKED else ClipboardMessage.UNLOCKED,
                ),
            )
        }
    }

    fun setSensitive(entry: ClipboardEntry, sensitive: Boolean) {
        if (entry.passwordVaultMatch) {
            // Password Vault matches are always sensitive and cannot be
            // downgraded from the clipboard UI.
            return
        }
        if (!sensitive && entry.isLocked) {
            // Locked clips must always remain sensitive. Unlocking is a
            // separate, explicit action so this can never create a locked but
            // non-sensitive row.
            return
        }
        if (clipboardSandboxActive) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.SANDBOX))
            }
            return
        }
        viewModelScope.launch {
            if (sensitive) {
                clipboardDao.updateSensitivity(
                    id = entry.id,
                    sensitive = true,
                    reason = entry.sensitivityReason ?: ClipSensitivityReason.MANUAL,
                    vaultMatch = false,
                    locked = entry.isLocked,
                )
                clipboardDao.setExpiresAt(entry.id, null)
                clipboardDao.clearAiDetails(entry.id, ClipAiState.SKIPPED_SENSITIVE)
            } else {
                clipboardDao.updateSensitivity(
                    id = entry.id,
                    sensitive = false,
                    reason = null,
                    vaultMatch = false,
                    locked = entry.isLocked,
                )
                if (!entry.isLocked) {
                    // Removing sensitivity re-enables retention for this clip.
                    clipboardDao.setExpiresAt(entry.id, expiryFor(entry.timestamp))
                    clipboardDao.updateAiState(entry.id, ClipAiState.IDLE, null)
                }
            }
            _extras.update { it.copy(revealed = it.revealed - entry.id) }
            refreshStats()
            _events.send(
                ClipboardEvent.Message(
                    if (sensitive) ClipboardMessage.MARKED_SENSITIVE
                    else ClipboardMessage.MARKED_NOT_SENSITIVE,
                ),
            )
        }
    }

    fun deleteEntry(entry: ClipboardEntry) {
        if (clipboardSandboxActive) {
            lastDeletedIds = listOf(entry.id)
            FakeClipboardSandbox.softDelete(entry.id)
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.DELETED, count = 1))
            }
            return
        }
        viewModelScope.launch {
            lastDeletedIds = listOf(entry.id)
            clipboardDao.softDelete(entry.id, System.currentTimeMillis())
            refreshStats()
            _events.send(ClipboardEvent.Message(ClipboardMessage.DELETED, count = 1))
        }
    }

    fun deleteSelected() {
        val ids = uiState.value.selection.toList()
        if (ids.isEmpty()) return
        clearSelection()
        if (clipboardSandboxActive) {
            lastDeletedIds = ids
            ids.forEach(FakeClipboardSandbox::softDelete)
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.DELETED, count = ids.size))
            }
            return
        }
        viewModelScope.launch {
            lastDeletedIds = ids
            clipboardDao.softDeleteMany(ids, System.currentTimeMillis())
            refreshStats()
            _events.send(ClipboardEvent.Message(ClipboardMessage.DELETED, count = ids.size))
        }
    }

    fun undoDelete() {
        val ids = lastDeletedIds
        if (ids.isEmpty()) return
        lastDeletedIds = emptyList()
        if (clipboardSandboxActive) {
            ids.forEach(FakeClipboardSandbox::restore)
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.RESTORED, count = ids.size))
            }
            return
        }
        viewModelScope.launch {
            clipboardDao.restoreMany(ids)
            refreshStats()
            _events.send(ClipboardEvent.Message(ClipboardMessage.RESTORED, count = ids.size))
        }
    }

    fun restoreEntry(entry: ClipboardEntry) {
        if (clipboardSandboxActive) {
            FakeClipboardSandbox.restore(entry.id)
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.RESTORED, count = 1))
            }
            return
        }
        viewModelScope.launch {
            clipboardDao.restore(entry.id)
            refreshStats()
            _events.send(ClipboardEvent.Message(ClipboardMessage.RESTORED, count = 1))
        }
    }

    fun restoreSelected() {
        val ids = uiState.value.selection.toList()
        if (ids.isEmpty()) return
        clearSelection()
        if (clipboardSandboxActive) {
            ids.forEach(FakeClipboardSandbox::restore)
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.RESTORED, count = ids.size))
            }
            return
        }
        viewModelScope.launch {
            clipboardDao.restoreMany(ids)
            refreshStats()
            _events.send(ClipboardEvent.Message(ClipboardMessage.RESTORED, count = ids.size))
        }
    }

    fun deleteSelectedForever() {
        val ids = uiState.value.selection.toList()
        if (ids.isEmpty()) return
        clearSelection()
        if (clipboardSandboxActive) {
            ids.forEach(FakeClipboardSandbox::delete)
            viewModelScope.launch {
                _events.send(
                    ClipboardEvent.Message(ClipboardMessage.DELETED_FOREVER, count = ids.size),
                )
            }
            return
        }
        viewModelScope.launch {
            ids.forEach { retentionManager.deleteNow(it) }
            refreshStats()
            _events.send(
                ClipboardEvent.Message(ClipboardMessage.DELETED_FOREVER, count = ids.size),
            )
        }
    }

    fun deleteForever(entry: ClipboardEntry) {
        if (clipboardSandboxActive) {
            FakeClipboardSandbox.delete(entry.id)
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.DELETED_FOREVER, count = 1))
            }
            return
        }
        viewModelScope.launch {
            retentionManager.deleteNow(entry.id)
            refreshStats()
            _events.send(ClipboardEvent.Message(ClipboardMessage.DELETED_FOREVER, count = 1))
        }
    }

    fun emptyTrash() {
        if (clipboardSandboxActive) {
            FakeClipboardSandbox.emptyTrash()
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.TRASH_EMPTY))
            }
            return
        }
        viewModelScope.launch {
            retentionManager.emptyTrash()
            refreshStats()
            _events.send(ClipboardEvent.Message(ClipboardMessage.TRASH_EMPTY))
        }
    }

    fun clearAll() {
        if (clipboardSandboxActive) {
            FakeClipboardSandbox.clearUnpinned()
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.CLEARED))
            }
            return
        }
        viewModelScope.launch {
            retentionManager.clearAllUnpinned()
            refreshStats()
            _events.send(ClipboardEvent.Message(ClipboardMessage.CLEARED))
        }
    }

    // ── AI ────────────────────────────────────────────────────────────────

    fun runAi(entry: ClipboardEntry) {
        if (entry.isSensitive || entry.isLocked) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.AI_SENSITIVE))
            }
            return
        }
        if (!entry.isTextLike) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.AI_TEXT_ONLY))
            }
            return
        }
        if (!uiState.value.settings.aiEnabled || uiState.value.settings.offline) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.AI_DISABLED))
            }
            return
        }
        if (!aiProcessor.isConfigured()) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.AI_NOT_CONFIGURED))
            }
            return
        }
        if (clipboardSandboxActive) {
            FakeClipboardSandbox.setAiResult(entry.id, entry.summary, entry.type)
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.SANDBOX))
            }
            return
        }
        viewModelScope.launch {
            _extras.update { it.copy(aiRunning = it.aiRunning + entry.id) }
            val result = aiProcessor.process(entry.id, automatic = false)
            _extras.update { it.copy(aiRunning = it.aiRunning - entry.id) }
            _events.send(
                ClipboardEvent.Message(
                    when {
                        result.success -> ClipboardMessage.AI_DONE
                        result.error == "sensitive" -> ClipboardMessage.AI_SENSITIVE
                        result.error == "disabled" -> ClipboardMessage.AI_DISABLED
                        result.error == "not_configured" -> ClipboardMessage.AI_NOT_CONFIGURED
                        else -> ClipboardMessage.AI_FAILED
                    },
                    detail = result.error,
                ),
            )
            refreshStats()
        }
    }

    fun runAiSelected() {
        val state = uiState.value
        val ids = state.selection
        if (ids.isEmpty()) return
        // Validate availability before clearing the selection so a disabled or
        // unconfigured provider does not silently throw the selection away.
        if (!state.settings.aiEnabled || state.settings.offline) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.AI_DISABLED))
            }
            return
        }
        if (!state.aiConfigured) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.AI_NOT_CONFIGURED))
            }
            return
        }
        val selected = state.visibleEntries.filter { it.id in ids }
        // AI is only ever sent text-like, non-sensitive clips. Attachments,
        // images and locked rows are silently excluded from the batch.
        val targets = selected.filter { it.isTextLike && !it.isSensitive && !it.isLocked }
        if (targets.isEmpty()) {
            // Tell the user why nothing was eligible. An all-sensitive
            // selection should not be reported as "text only".
            val message = if (selected.any { it.isSensitive || it.isLocked }) {
                ClipboardMessage.AI_SENSITIVE
            } else {
                ClipboardMessage.AI_TEXT_ONLY
            }
            viewModelScope.launch { _events.send(ClipboardEvent.Message(message)) }
            return
        }
        clearSelection()
        when {
            clipboardSandboxActive -> viewModelScope.launch {
                targets.forEach { FakeClipboardSandbox.setAiResult(it.id, it.summary, it.type) }
                _events.send(ClipboardEvent.Message(ClipboardMessage.SANDBOX))
            }
            else -> viewModelScope.launch {
                val runningIds = targets.mapTo(LinkedHashSet()) { it.id }
                _extras.update { it.copy(aiRunning = it.aiRunning + runningIds) }
                var success = 0
                var sensitive = 0
                var failed = 0
                var lastError: String? = null
                try {
                    for (entry in targets) {
                        val result = aiProcessor.process(entry.id, automatic = false)
                        when {
                            result.success -> success++
                            result.error == "sensitive" -> sensitive++
                            else -> {
                                failed++
                                lastError = result.error
                            }
                        }
                    }
                } finally {
                    _extras.update { it.copy(aiRunning = it.aiRunning - runningIds) }
                }
                refreshStats()
                _events.send(
                    ClipboardEvent.Message(
                        when {
                            success > 0 -> ClipboardMessage.AI_DONE
                            sensitive > 0 && failed == 0 -> ClipboardMessage.AI_SENSITIVE
                            else -> ClipboardMessage.AI_FAILED
                        },
                        count = success,
                        detail = lastError,
                    ),
                )
            }
        }
    }

    // ── Capture ───────────────────────────────────────────────────────────

    fun pasteCurrent() = refreshClipboard(notify = true)

    fun refreshClipboard(notify: Boolean) {
        if (clipboardSandboxActive) {
            if (notify) {
                viewModelScope.launch {
                    _events.send(ClipboardEvent.Message(ClipboardMessage.SANDBOX))
                }
            }
            return
        }
        if (!ToolzForegroundTracker.isForeground.value) {
            if (notify) {
                viewModelScope.launch {
                    _events.send(ClipboardEvent.Message(ClipboardMessage.NOT_FOREGROUND))
                }
            }
            return
        }
        // Silent refreshes are coalesced while a capture is already running.
        // An explicit paste waits its turn: dropping it would leave the popup
        // spinner spinning forever because no result event would ever arrive.
        if (!notify && !manualCaptureMutex.tryLock()) return
        viewModelScope.launch {
            if (notify) manualCaptureMutex.lock()
            _extras.update { it.copy(busy = true) }
            try {
                val exclude = settingsState.first().excludeSensitive
                // Prefer the rich system clipboard while Toolz is foreground:
                // it preserves HTML, URIs, images and multi-item clips. Shizuku
                // only exposes plain text, so it is strictly a fallback for
                // ROMs that deny the direct read.
                val payload = readSystemClipboard() ?: readShizukuPayload()
                if (payload == null) {
                    if (notify) emit(ClipboardMessage.EMPTY)
                } else {
                    val result = captureProcessor.process(payload, exclude)
                    // Silent refreshes (screen resume, popup open) must not
                    // produce snackbars/toasts for an unchanged clipboard.
                    if (notify) emitOutcome(result.outcome)
                    if (result.outcome == ClipboardCaptureProcessor.Outcome.INSERTED) {
                        aiProcessor.processQueued(1)
                    }
                }
                refreshStats()
            } catch (e: Exception) {
                if (notify) emit(ClipboardMessage.FAILED, detail = e.message)
            } finally {
                _extras.update { it.copy(busy = false) }
                manualCaptureMutex.unlock()
            }
        }
    }

    private suspend fun readShizukuPayload(): ClipboardPayload? {
        if (!ShizukuHelper.isAuthorized()) return null
        val text = try {
            shizukuExecutor.getClipboardText()
        } catch (e: Exception) {
            android.util.Log.w(
                "ClipboardViewModel",
                "Shizuku clipboard read failed: ${e.message}",
            )
            null
        }
        if (text.isNullOrBlank()) return null
        return ClipboardPayload(
            items = listOf(ClipboardPayloadItem(text = text)),
            source = ClipboardSource.MANUAL,
        )
    }

    private fun readSystemClipboard(): ClipboardPayload? {
        return try {
            val manager = application.getSystemService(Context.CLIPBOARD_SERVICE)
                as? ClipboardManager ?: return null
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val description = clip.description
            val items = buildList {
                for (index in 0 until clip.itemCount) {
                    val item = clip.getItemAt(index) ?: continue
                    add(
                        ClipboardPayloadItem(
                            text = item.text?.toString(),
                            html = item.htmlText,
                            uri = item.uri,
                            mimeType = description?.getMimeType(index),
                        ),
                    )
                }
            }
            if (items.all { item ->
                    item.text.isNullOrBlank() &&
                        item.html.isNullOrBlank() &&
                        item.uri == null
                }
            ) {
                return null
            }
            ClipboardPayload(
                items = items,
                label = description?.label?.toString(),
                source = ClipboardSource.MANUAL,
                isSensitiveExtra = description?.extras
                    ?.getBoolean("android.content.extra.IS_SENSITIVE", false) ?: false,
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun emitOutcome(outcome: ClipboardCaptureProcessor.Outcome) {
        when (outcome) {
            ClipboardCaptureProcessor.Outcome.INSERTED -> emit(ClipboardMessage.SAVED)
            ClipboardCaptureProcessor.Outcome.BUMPED -> emit(ClipboardMessage.BUMPED)
            ClipboardCaptureProcessor.Outcome.IGNORED_BLANK -> emit(ClipboardMessage.EMPTY)
            ClipboardCaptureProcessor.Outcome.IGNORED_SENSITIVE ->
                emit(ClipboardMessage.SENSITIVE_SKIPPED)
            ClipboardCaptureProcessor.Outcome.IGNORED_TOO_LARGE ->
                emit(ClipboardMessage.TOO_LARGE)
            ClipboardCaptureProcessor.Outcome.IGNORED_QUOTA -> emit(ClipboardMessage.QUOTA)
            ClipboardCaptureProcessor.Outcome.FAILED -> emit(ClipboardMessage.FAILED)
        }
    }

    private suspend fun emit(type: ClipboardMessage, detail: String? = null) {
        _events.send(ClipboardEvent.Message(type, detail = detail))
    }

    // ── Copy / share / open ───────────────────────────────────────────────

    fun copyEntry(entry: ClipboardEntry) {
        viewModelScope.launch {
            val attachments = uiState.value.attachmentsByClip[entry.id].orEmpty()
            val copied = try {
                copyToSystemClipboard(entry, attachments)
            } catch (e: Exception) {
                android.util.Log.w("ClipboardViewModel", "Copy failed", e)
                false
            }
            if (copied && !clipboardSandboxActive && entry.deletedAt == null) {
                // Copying from history is a fresh use of the clip: move it to
                // the top and extend its retention window instead of letting
                // it expire on the original schedule.
                try {
                    clipboardDao.markCopied(
                        id = entry.id,
                        timestamp = System.currentTimeMillis(),
                        retentionMs = retentionMsOrZero(),
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ClipboardViewModel", "Could not bump copied clip", e)
                }
            }
            _events.send(
                ClipboardEvent.Message(
                    if (copied) ClipboardMessage.COPIED else ClipboardMessage.FAILED,
                ),
            )
        }
    }

    fun copySelected() {
        val state = uiState.value
        val ids = state.selection
        if (ids.isEmpty()) return
        // Copy only what is actually visible and selected. Locked clips stay
        // out of bulk copy until individually revealed.
        val selected = state.visibleEntries.filter {
            it.id in ids && (!it.isLocked || it.id in state.revealedIds)
        }
        val texts = selected
            .sortedByDescending { it.timestamp }
            .mapNotNull { entry ->
                textForClipboard(entry, state.attachmentsByClip[entry.id].orEmpty())
                    ?.let { text -> entry to text }
            }
        if (texts.isEmpty()) {
            // Keep the selection so the user can adjust it instead of losing
            // everything just because attachment-only clips were suppressed.
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.NOTHING_TO_COPY))
            }
            return
        }
        // The binder transaction for a clip is ~1 MB. Cap the merged text well
        // below that so a huge bulk selection cannot crash the app; clips that
        // do not fit are skipped and only the ones actually copied are counted.
        val accepted = ArrayList<Pair<ClipboardEntry, String>>(texts.size)
        var total = 0
        for ((entry, text) in texts) {
            val extra = if (accepted.isEmpty()) text.length else text.length + 2
            if (total + extra > MAX_BULK_COPY_CHARS) continue
            accepted += entry to text
            total += extra
        }
        if (accepted.isEmpty()) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.TOO_LARGE))
            }
            return
        }
        val copied = try {
            val manager = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(
                ClipData.newPlainText(
                    CLIPBOARD_LABEL,
                    accepted.joinToString("\n\n") { it.second },
                ),
            )
            true
        } catch (e: Exception) {
            android.util.Log.w("ClipboardViewModel", "Bulk copy failed", e)
            false
        }
        if (!copied) {
            // Keep the selection so the user can retry with fewer clips.
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.FAILED))
            }
            return
        }
        clearSelection()
        viewModelScope.launch {
            if (!clipboardSandboxActive) {
                try {
                    clipboardDao.markCopiedMany(
                        ids = accepted.map { it.first.id },
                        timestamp = System.currentTimeMillis(),
                        retentionMs = retentionMsOrZero(),
                    )
                } catch (e: Exception) {
                    android.util.Log.w("ClipboardViewModel", "Could not bump bulk copy", e)
                }
            }
            _events.send(
                ClipboardEvent.Message(
                    ClipboardMessage.SELECTION_COPIED,
                    count = accepted.size,
                ),
            )
        }
    }

    /**
     * Text that is meaningful for an external tool (notepad, task, QR,
     * encrypt, link). Never returns an `[image]`/`[file]` placeholder.
     */
    fun textForEntry(entry: ClipboardEntry): String? =
        textForClipboard(entry, uiState.value.attachmentsByClip[entry.id].orEmpty())

    /**
     * Text that is safe and meaningful to put on the clipboard.
     *
     * Image/file clips store a filename or a `[image]`/`[file]` placeholder in
     * [ClipboardEntry.content] so the history row has something to show. That
     * placeholder must never be copied or shared as if it were real text.
     */
    private fun textForClipboard(
        entry: ClipboardEntry,
        attachments: List<ClipboardAttachment>,
    ): String? {
        if (entry.content.isBlank()) return null
        if (entry.isTextLike) return entry.content
        val hasText = entry.metadataJson?.let { raw ->
            runCatching { JSONObject(raw).optBoolean("hasText", false) }.getOrNull()
        }
        if (hasText == true) return entry.content
        if (hasText == false) return null
        // Legacy rows predate the metadata flag. Drop known placeholders and
        // bare attachment names, but keep genuine text stored in a MULTI clip.
        if (entry.content == "[image]" || entry.content == "[file]") return null
        if (attachments.any { it.fileName == entry.content }) return null
        return entry.content
    }

    private fun shareMimeType(
        entry: ClipboardEntry,
        attachments: List<ClipboardAttachment>,
    ): String {
        val mimes = attachments
            .mapNotNull { it.mimeType?.takeIf(String::isNotBlank) }
            .distinct()
        return when {
            mimes.size == 1 -> mimes.first()
            mimes.size > 1 -> "*/*"
            else -> entry.mimeType?.takeIf(String::isNotBlank) ?: "*/*"
        }
    }

    private suspend fun copyToSystemClipboard(
        entry: ClipboardEntry,
        attachments: List<ClipboardAttachment>,
    ): Boolean {
        val manager = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val uris = stageAttachments(attachments)
        val text = textForClipboard(entry, attachments)
        val html = entry.htmlContent?.takeIf { !text.isNullOrBlank() && it.isNotBlank() }
        if (text.isNullOrBlank() && uris.isEmpty()) return false

        val clip = when {
            !text.isNullOrBlank() && !html.isNullOrBlank() ->
                ClipData.newHtmlText(CLIPBOARD_LABEL, text, html)
            !text.isNullOrBlank() ->
                ClipData.newPlainText(CLIPBOARD_LABEL, text)
            else ->
                ClipData.newUri(application.contentResolver, CLIPBOARD_LABEL, uris.first())
        }
        // A URI-only clip already carries its first URI as the primary item.
        uris.drop(if (text.isNullOrBlank()) 1 else 0).forEach { uri ->
            clip.addItem(ClipData.Item(uri))
        }
        manager.setPrimaryClip(clip)
        return true
    }

    fun shareEntry(entry: ClipboardEntry) {
        viewModelScope.launch {
            val attachments = uiState.value.attachmentsByClip[entry.id].orEmpty()
            val intent = buildShareIntent(entry, attachments)
            if (intent == null) {
                _events.send(ClipboardEvent.Message(ClipboardMessage.NOTHING_TO_COPY))
            } else {
                _events.send(ClipboardEvent.Share(intent))
            }
        }
    }

    private suspend fun buildShareIntent(
        entry: ClipboardEntry,
        attachments: List<ClipboardAttachment>,
    ): Intent? {
        val uris = stageAttachments(attachments)
        val text = textForClipboard(entry, attachments)
        if (text.isNullOrBlank() && uris.isEmpty()) return null
        val mime = shareMimeType(entry, attachments)
        return when {
            uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
                if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text.orEmpty())
            }
        }
    }

    private suspend fun stageAttachments(
        attachments: List<ClipboardAttachment>,
    ): List<Uri> = attachments.mapNotNull { attachment ->
        val file = blobStore.copyToShareCache(attachment.blobId, attachment.fileName)
        if (file == null) {
            null
        } else {
            try {
                FileProvider.getUriForFile(
                    application,
                    "${application.packageName}.fileprovider",
                    file,
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun loadAttachmentBytes(attachment: ClipboardAttachment): ByteArray? =
        blobStore.readBytes(attachment.blobId)

    suspend fun loadAttachmentBitmap(
        attachment: ClipboardAttachment,
        maxDimension: Int = 512,
    ): android.graphics.Bitmap? = blobStore.bitmap(attachment.blobId, maxDimension)

    suspend fun openAttachment(attachment: ClipboardAttachment): Intent? {
        val file = blobStore.copyToShareCache(attachment.blobId, attachment.fileName)
            ?: return null
        val uri = try {
            FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                file,
            )
        } catch (_: Exception) {
            return null
        }
        val mime = attachment.mimeType ?: "*/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun attachmentFileName(uri: Uri): String? = try {
        application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Exception) {
        null
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun setMonitoringEnabled(enabled: Boolean) {
        if (clipboardSandboxActive) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.SANDBOX))
            }
            return
        }
        viewModelScope.launch {
            settingsRepository.setClipboardMonitoringEnabled(enabled)
            refreshGate()
        }
    }

    fun setClipboardAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardAiEnabled(enabled)
            if (enabled) {
                // Clips skipped while AI was off become eligible again.
                clipboardDao.requeueStalledAi()
                _extras.update { it.copy(aiConfigured = aiProcessor.isConfigured()) }
                // Start draining the queue immediately; the periodic worker is
                // only a safety net and can be up to 15 minutes away.
                aiProcessor.processQueued(2)
            }
        }
    }

    fun setAutoAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAiClipboardMonitoringEnabled(enabled)
            if (enabled) {
                clipboardDao.requeueStalledAi()
                aiProcessor.processQueued(2)
            }
        }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setClipboardRetentionDays(days)
            // Recompute expiry for existing non-sensitive clips. Otherwise a
            // clip created under a 7-day retention would still be deleted on
            // day 7 after the user raises retention to 30 days.
            clipboardDao.recomputeExpiry(days.coerceAtLeast(0) * DAY_MS)
            ClipboardCleanupWorker.schedule(application)
            retentionManager.enforceAll()
            refreshStats()
        }
    }

    fun setExcludeSensitive(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setClipboardExcludeSensitive(enabled) }
    }

    fun setCaptureScreenshots(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardCaptureScreenshots(enabled)
            // Turning the feature on should not wait for the next screenshot;
            // pick up anything captured recently that is still missing.
            if (enabled) {
                screenshotIngestor.ingestRecentScreenshots(isPoll = true)
                refreshStatsNow()
            }
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setClipboardBiometricLock(enabled) }
    }

    fun setItemQuotaMb(mb: Int) {
        viewModelScope.launch {
            settingsRepository.setClipboardItemQuotaMb(mb)
            refreshStats()
        }
    }

    fun setTotalQuotaMb(mb: Int) {
        viewModelScope.launch {
            settingsRepository.setClipboardTotalQuotaMb(mb)
            retentionManager.enforceAll()
            refreshStats()
        }
    }

    fun setMaxEntries(count: Int) {
        viewModelScope.launch {
            settingsRepository.setClipboardMaxEntries(count)
            retentionManager.enforceAll()
            refreshStats()
        }
    }

    fun markBackupWarningSeen() {
        viewModelScope.launch { settingsRepository.setClipboardBackupWarningSeen(true) }
    }

    // ── Grouping helpers ──────────────────────────────────────────────────

    fun groupedEntries(entries: List<ClipboardEntry>): List<ClipboardGroup> {
        val pinned = entries.filter { it.isPinned }
        val unpinned = entries.filterNot { it.isPinned }
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = startOfToday.timeInMillis
        val yesterday = (startOfToday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val todayItems = unpinned.filter { it.timestamp >= today }
        val yesterdayItems = unpinned.filter { it.timestamp in yesterday until today }
        val olderItems = unpinned.filter { it.timestamp < yesterday }
        return buildList {
            if (pinned.isNotEmpty()) add(ClipboardGroup(ClipboardDateGroup.PINNED, pinned))
            if (todayItems.isNotEmpty()) add(ClipboardGroup(ClipboardDateGroup.TODAY, todayItems))
            if (yesterdayItems.isNotEmpty()) {
                add(ClipboardGroup(ClipboardDateGroup.YESTERDAY, yesterdayItems))
            }
            if (olderItems.isNotEmpty()) add(ClipboardGroup(ClipboardDateGroup.OLDER, olderItems))
        }
    }

    private fun filterEntries(
        entries: List<ClipboardEntry>,
        attachments: Map<Int, List<ClipboardAttachment>>,
        query: String,
        filter: ClipboardFilter,
        revealed: Set<Int>,
    ): List<ClipboardEntry> {
        val normalized = query.trim()
        return entries.filter { entry ->
            matchesFilter(entry, attachments[entry.id].orEmpty(), filter) &&
                (normalized.isBlank() ||
                    matchesQuery(entry, attachments[entry.id].orEmpty(), normalized, revealed))
        }
    }

    private fun matchesFilter(
        entry: ClipboardEntry,
        attachments: List<ClipboardAttachment>,
        filter: ClipboardFilter,
    ): Boolean {
        val type = entry.type.uppercase()
        val hasImage = attachments.any { it.mimeType?.startsWith("image/") == true } ||
            type == ClipboardType.IMAGE
        return when (filter) {
            ClipboardFilter.ALL -> true
            ClipboardFilter.TEXT -> entry.isTextLike &&
                type !in setOf(
                    ClipboardType.LINK,
                    ClipboardType.URL,
                    ClipboardType.SOCIAL,
                    ClipboardType.CODE,
                    ClipboardType.OTP,
                    ClipboardType.CARD,
                    ClipboardType.SECRET,
                )
            ClipboardFilter.LINKS -> type in setOf(
                ClipboardType.LINK,
                ClipboardType.URL,
                ClipboardType.SOCIAL,
            )
            ClipboardFilter.CODE -> type == ClipboardType.CODE
            ClipboardFilter.OTP -> type == ClipboardType.OTP
            ClipboardFilter.IMAGES -> hasImage
            ClipboardFilter.FILES -> type in setOf(ClipboardType.FILE, ClipboardType.MULTI) ||
                (attachments.isNotEmpty() && !hasImage)
            ClipboardFilter.PINNED -> entry.isPinned
            ClipboardFilter.SENSITIVE -> entry.isSensitive || entry.isLocked
        }
    }

    private fun matchesQuery(
        entry: ClipboardEntry,
        attachments: List<ClipboardAttachment>,
        query: String,
        revealed: Set<Int>,
    ): Boolean {
        val contentVisible = (!entry.isSensitive && !entry.isLocked) || entry.id in revealed
        if (contentVisible) {
            if (entry.content.contains(query, ignoreCase = true)) return true
            if (entry.summary?.contains(query, ignoreCase = true) == true) return true
            if (attachments.any { it.fileName?.contains(query, ignoreCase = true) == true }) {
                return true
            }
        }
        if (entry.type.contains(query, ignoreCase = true)) return true
        return entry.sourceApp?.contains(query, ignoreCase = true) == true
    }

    /** True when the clip is safe to display without a biometric check. */
    fun isRevealable(entry: ClipboardEntry): Boolean =
        !entry.isLocked || entry.id in uiState.value.revealedIds

    /**
     * Retention window in milliseconds; `0` means "never expire". Read live so
     * a copy/pin right after a settings change uses the new value.
     */
    private suspend fun retentionMsOrZero(): Long {
        val days = settingsState.first().retentionDays
        return if (days > 0) days * DAY_MS else 0L
    }

    /**
     * Expiry a non-sensitive clip should have under the current retention
     * preference. `0` days means "never expire".
     */
    private suspend fun expiryFor(timestamp: Long): Long? {
        val retention = retentionMsOrZero()
        return if (retention > 0) timestamp + retention else null
    }

    companion object {
        const val CLIPBOARD_LABEL = "Toolz"
        val AI_IDLE_STATES = setOf(null, ClipAiState.IDLE, ClipAiState.DISABLED)
        private const val DAY_MS = 24L * 60L * 60L * 1000L

        /**
         * Safe merged text budget for a bulk copy. The binder limit is ~1 MB;
         * UTF-16 chars are roughly 2 bytes, so this stays far below it.
         */
        private const val MAX_BULK_COPY_CHARS = 400_000
    }
}
