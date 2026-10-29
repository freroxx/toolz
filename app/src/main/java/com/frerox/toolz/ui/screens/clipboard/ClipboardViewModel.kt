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
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
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
) {
    val isSelectionMode: Boolean get() = selection.isNotEmpty()
    val canCaptureInBackground: Boolean get() = gate.capabilities.backgroundCapable
    val showSetupBanner: Boolean
        get() = settings.monitoring && !gate.capabilities.backgroundCapable &&
            gate.status != ClipboardGate.Status.PAUSED
}

enum class ClipboardMessage {
    SAVED,
    BUMPED,
    EMPTY,
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
    ) { maxEntries, backupWarning, offline ->
        SettingsC(maxEntries, backupWarning, offline)
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
    )

    private val _extras = MutableStateFlow(Extras())

    private val _events = Channel<ClipboardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var lastDeletedIds: List<Int> = emptyList()
    private var wasSandboxed = false
    private val manualCaptureInFlight = AtomicBoolean(false)

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
            stats = stats,
            sandboxActive = clipboardSandboxActive,
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
            refreshStats()
            _extras.update { it.copy(aiConfigured = aiProcessor.isConfigured()) }
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
            syncGate(settingsState.first())
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
    }

    fun refreshStats() {
        viewModelScope.launch {
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
    }

    // ── Onboarding / setup ────────────────────────────────────────────────

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepository.setClipboardOnboardingSeen(true)
            _events.send(ClipboardEvent.Message(ClipboardMessage.ONBOARDING_DONE))
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
                refreshGate()
            }
        }
    }

    fun grantClipboardAppOp() {
        viewModelScope.launch {
            val result = shizukuExecutor.grantClipboardAppOp()
            _events.send(
                ClipboardEvent.Message(
                    if (result.isSuccess) ClipboardMessage.APP_OP_GRANTED
                    else ClipboardMessage.APP_OP_FAILED,
                    detail = result.combinedOutput.takeIf { it.isNotBlank() },
                ),
            )
            refreshGate()
        }
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
                clipboardDao.setPinned(entry.id, pinned)
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
        val ids = uiState.value.selection.toList()
        if (ids.isEmpty()) return
        clearSelection()
        ids.forEach { id ->
            uiState.value.entries.firstOrNull { it.id == id }?.let(::runAi)
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
                    _events.send(ClipboardEvent.Message(ClipboardMessage.EMPTY))
                }
            }
            return
        }
        // One manual capture at a time; rapid button taps should not enqueue
        // duplicate reads or flash the busy state.
        if (!manualCaptureInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            _extras.update { it.copy(busy = true) }
            try {
                val exclude = settingsState.first().excludeSensitive
                val shizukuText = if (ShizukuHelper.isAuthorized()) {
                    try {
                        shizukuExecutor.getClipboardText()
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "ClipboardViewModel",
                            "Shizuku clipboard read failed: ${e.message}",
                        )
                        null
                    }
                } else {
                    null
                }
                if (!shizukuText.isNullOrBlank()) {
                    val result = captureProcessor.processText(shizukuText, "MANUAL", exclude)
                    emitOutcome(result)
                    if (result == ClipboardCaptureProcessor.Outcome.INSERTED) {
                        aiProcessor.processQueued(1)
                    }
                } else {
                    // `cmd clipboard get` can be unavailable on some ROMs.
                    // Toolz is foreground here, so read the clipboard directly.
                    val payload = readSystemClipboard()
                    if (payload == null) {
                        if (notify) emit(ClipboardMessage.EMPTY)
                    } else {
                        val result = captureProcessor.process(payload, exclude)
                        emitOutcome(result.outcome)
                        if (result.outcome == ClipboardCaptureProcessor.Outcome.INSERTED) {
                            aiProcessor.processQueued(1)
                        }
                    }
                }
                refreshStats()
            } catch (e: Exception) {
                if (notify) emit(ClipboardMessage.FAILED, detail = e.message)
            } finally {
                _extras.update { it.copy(busy = false) }
                manualCaptureInFlight.set(false)
            }
        }
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
                    val item = clip.getItemAt(index)
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
            if (items.all { it.text.isNullOrBlank() && it.uri == null }) return null
            ClipboardPayload(items = items, source = "MANUAL")
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
            if (copyToSystemClipboard(entry, attachments)) {
                _events.send(ClipboardEvent.Message(ClipboardMessage.COPIED))
            } else {
                _events.send(ClipboardEvent.Message(ClipboardMessage.NOTHING_TO_COPY))
            }
        }
    }

    fun copySelected() {
        val state = uiState.value
        val ids = state.selection
        if (ids.isEmpty()) return
        // Locked clips stay out of bulk copy until individually unlocked.
        val selected = state.entries.filter {
            it.id in ids && it.deletedAt == null &&
                (!it.isLocked || it.id in state.revealedIds)
        }
        val text = selected
            .sortedByDescending { it.timestamp }
            .mapNotNull { it.content.takeIf { value -> value.isNotBlank() } }
            .joinToString("\n\n")
        clearSelection()
        if (text.isBlank()) {
            viewModelScope.launch {
                _events.send(ClipboardEvent.Message(ClipboardMessage.NOTHING_TO_COPY))
            }
            return
        }
        val manager = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("Toolz", text))
        viewModelScope.launch {
            _events.send(ClipboardEvent.Message(ClipboardMessage.SELECTION_COPIED, count = selected.size))
        }
    }

    private suspend fun copyToSystemClipboard(
        entry: ClipboardEntry,
        attachments: List<ClipboardAttachment>,
    ): Boolean {
        val manager = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val uris = stageAttachments(attachments)
        val text = entry.content
        val html = entry.htmlContent
        if (text.isBlank() && uris.isEmpty()) return false

        val clip = if (text.isNotBlank()) {
            if (!html.isNullOrBlank()) {
                ClipData.newHtmlText("Toolz", text, html)
            } else {
                ClipData.newPlainText("Toolz", text)
            }
        } else {
            ClipData.newUri(application.contentResolver, "Toolz", uris.first())
        }
        val firstUriIndex = if (text.isNotBlank()) 0 else 1
        uris.drop(firstUriIndex).forEach { uri ->
            clip.addItem(ClipData.Item(uri))
        }
        manager.setPrimaryClip(clip)
        return true
    }

    fun shareEntry(entry: ClipboardEntry) {
        viewModelScope.launch {
            val attachments = uiState.value.attachmentsByClip[entry.id].orEmpty()
            val intent = buildShareIntent(entry, attachments)
            _events.send(ClipboardEvent.Share(intent))
        }
    }

    private suspend fun buildShareIntent(
        entry: ClipboardEntry,
        attachments: List<ClipboardAttachment>,
    ): Intent {
        val uris = stageAttachments(attachments)
        val text = entry.content
        return if (uris.size > 1) {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = attachments.firstOrNull()?.mimeType ?: entry.mimeType ?: "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = attachments.firstOrNull()?.mimeType ?: entry.mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
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
        viewModelScope.launch { settingsRepository.setClipboardAiEnabled(enabled) }
    }

    fun setAutoAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAiClipboardMonitoringEnabled(enabled) }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setClipboardRetentionDays(days)
            ClipboardCleanupWorker.schedule(application)
            retentionManager.enforceAll()
            refreshStats()
        }
    }

    fun setExcludeSensitive(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setClipboardExcludeSensitive(enabled) }
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

    companion object {
        const val CLIPBOARD_LABEL = "Toolz"
        val AI_IDLE_STATES = setOf(null, ClipAiState.IDLE, ClipAiState.DISABLED)
    }
}
