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
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.ToolzForegroundTracker
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.clipboard.ClipboardCaptureProcessor
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.clipboard.ClipboardGate
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ClipboardService
import com.frerox.toolz.util.shizuku.ShizukuHelper
import com.frerox.toolz.worker.ClipboardCleanupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.ClipboardManager as AndroidClipboardManager
import java.util.*
import javax.inject.Inject

data class ClipboardGroup(
    val label: String,
    val entries: List<ClipboardEntry>
)

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val application: Application,
    private val clipboardDao: ClipboardDao,
    private val aiRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val shizukuExecutor: com.frerox.toolz.util.shizuku.ShizukuShellExecutor,
    private val captureProcessor: ClipboardCaptureProcessor,
    val classifier: ClipboardClassifier
) : AndroidViewModel(application) {

    val entries: StateFlow<List<ClipboardEntry>> = clipboardDao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineModeEnabled = settingsRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Revamp prefs
    val monitoringEnabled = settingsRepository.clipboardMonitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val clipboardAiEnabled = settingsRepository.clipboardAiEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val autoAiEnabled = settingsRepository.aiClipboardMonitoringEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val retentionDays = settingsRepository.clipboardRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)
    val excludeSensitive = settingsRepository.clipboardExcludeSensitive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Master AI visibility: master toggle ON and not offline. Auto-AI needs autoAiEnabled too. */
    val canShowAi: StateFlow<Boolean> = combine(clipboardAiEnabled, offlineModeEnabled) { master, offline ->
        master && !offline
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _gateStatus = MutableStateFlow(ClipboardGate.Status.SETUP_REQUIRED)
    val gateStatus = _gateStatus.asStateFlow()

    private val _shizukuAuthorized = MutableStateFlow(ShizukuHelper.isAuthorized())
    val shizukuAuthorized = _shizukuAuthorized.asStateFlow()

    private val _accessibilityEnabled = MutableStateFlow(false)
    val accessibilityEnabled = _accessibilityEnabled.asStateFlow()

    private val _isSummarizing = MutableStateFlow<Int?>(null)
    val isSummarizing = _isSummarizing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var lastDeleted: ClipboardEntry? = null

    /** Plain local filter — no network, no AI. */
    val filteredEntries = combine(entries, _searchQuery) { allEntries, query ->
        if (query.isBlank()) allEntries
        else allEntries.filter { it.content.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        ClipboardCleanupWorker.schedule(application)
        // Wait for the real persisted prefs before touching the service —
        // reading .value straight away races DataStore and kills the service.
        viewModelScope.launch {
            val monitoring = settingsRepository.clipboardMonitoringEnabled.first()
            if (!monitoring &&
                (ShizukuHelper.isAuthorized() || ClipboardGate.isToolzAccessibilityEnabled(application))
            ) {
                // Heal: a previous build defaulted the toggle OFF, which silently
                // disabled capture even with Shizuku/accessibility ready.
                settingsRepository.setClipboardMonitoringEnabled(true)
            }
            refreshGate()
            ensureServiceState()
        }
    }

    private fun computeGate(monitoring: Boolean): ClipboardGate.Status {
        _shizukuAuthorized.value = ShizukuHelper.isAuthorized()
        _accessibilityEnabled.value = ClipboardGate.isToolzAccessibilityEnabled(application)
        return ClipboardGate.evaluate(application, monitoring)
    }

    fun refreshGate() {
        _gateStatus.value = computeGate(monitoringEnabled.value)
    }

    // ── Settings passthrough ──────────────────────────────────────────────

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setClipboardMonitoringEnabled(enabled)
            // Compute with the explicit value — the StateFlow hasn't re-emitted yet.
            _gateStatus.value = computeGate(enabled)
            applyServiceState(enabled, _gateStatus.value)
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
        }
    }

    fun setExcludeSensitive(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setClipboardExcludeSensitive(enabled) }
    }

    // ── Search ────────────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // ── Grouping ──────────────────────────────────────────────────────────

    fun groupedEntries(allEntries: List<ClipboardEntry>): List<ClipboardGroup> {
        val pinned = allEntries.filter { it.isPinned }
        val unpinned = allEntries.filter { !it.isPinned }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterday = today - 24 * 60 * 60 * 1000L

        val todayItems = unpinned.filter { it.timestamp >= today }
        val yesterdayItems = unpinned.filter { it.timestamp in yesterday until today }
        val olderItems = unpinned.filter { it.timestamp < yesterday }

        return buildList {
            if (pinned.isNotEmpty()) add(ClipboardGroup("Pinned", pinned))
            if (todayItems.isNotEmpty()) add(ClipboardGroup("Today", todayItems))
            if (yesterdayItems.isNotEmpty()) add(ClipboardGroup("Yesterday", yesterdayItems))
            if (olderItems.isNotEmpty()) add(ClipboardGroup("Older", olderItems))
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────────

    fun deleteEntry(entry: ClipboardEntry) {
        viewModelScope.launch {
            lastDeleted = entry
            clipboardDao.delete(entry)
            _events.emit("deleted")
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            val cached = lastDeleted ?: return@launch
            lastDeleted = null
            clipboardDao.insert(
                cached.copy(id = 0, timestamp = System.currentTimeMillis()),
            )
        }
    }

    fun togglePin(id: Int) {
        viewModelScope.launch { clipboardDao.togglePin(id) }
    }

    fun clearAll() {
        viewModelScope.launch { clipboardDao.clearAllUnpinned() }
    }

    fun summarizeEntry(entry: ClipboardEntry) {
        if (!clipboardAiEnabled.value) return
        viewModelScope.launch {
            _isSummarizing.value = entry.id
            try {
                val prompt = """
                    Classify this clipboard content and provide a punchy 1-sentence summary (max 15 words).
                    You can use standard categories (TEXT, URL, SOCIAL, PHONE, OTP, EMAIL, MATHS, PERSONAL, CODE, ADDRESS, CRYPTO, TODO, RECIPE, FLIGHT, EVENT, QUOTE)
                    or CREATE A NEW ONE if it fits better.
                    Keep category names uppercase and single-word.

                    Content: ${entry.content.take(1500)}

                    Respond in JSON format: {"category": "CATEGORY_NAME", "summary": "Short summary"}
                """.trimIndent()

                aiRepository.getChatResponse(prompt, emptyList(), null, "openai/gpt-oss-20b").collect { result ->
                    result.onSuccess { chunk ->
                        val response = chunk.text
                        val category = Regex("\"category\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1) ?: entry.type
                        val summary = Regex("\"summary\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1) ?: ""
                        clipboardDao.updateAiDetails(entry.id, summary, category)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSummarizing.value = null
            }
        }
    }

    fun categorizeAllWithAi() {
        // Kept for API compat — just re-reads the system clipboard.
        refreshClipboard()
    }

    // ── Capture ───────────────────────────────────────────────────────────

    private fun isAppInForeground(): Boolean {
        return ToolzForegroundTracker.isForeground.value
    }

    /**
     * Manual "Paste current" — always allowed while the screen is visible
     * (foreground read is permitted by Android). Emits a user message.
     */
    fun pasteCurrent() {
        viewModelScope.launch {
            try {
                if (ShizukuHelper.isAuthorized()) {
                    val text = shizukuExecutor.getClipboardText()
                    val outcome = captureProcessor.processText(
                        text, "manual-shizuku", excludeSensitive.value,
                    )
                    emitOutcome(outcome)
                    return@launch
                }
                if (!isAppInForeground()) {
                    _events.emit("open_app_to_paste")
                    return@launch
                }
                try {
                    val cm = application.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
                    val clip = cm.primaryClip
                    if (clip == null || clip.itemCount == 0) {
                        _events.emit("clipboard_empty")
                        return@launch
                    }
                    val item = clip.getItemAt(0)
                    if (item.uri != null && item.text == null && item.htmlText == null) {
                        _events.emit("non_text_clip")
                        return@launch
                    }
                    val text = item.coerceToText(application)?.toString()
                    val outcome = captureProcessor.processText(
                        text, "manual", excludeSensitive.value,
                    )
                    emitOutcome(outcome)
                } catch (_: SecurityException) {
                    _events.emit("clipboard_denied")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun emitOutcome(outcome: ClipboardCaptureProcessor.Outcome) {
        when (outcome) {
            ClipboardCaptureProcessor.Outcome.INSERTED -> _events.emit("saved")
            ClipboardCaptureProcessor.Outcome.BUMPED -> _events.emit("bumped")
            ClipboardCaptureProcessor.Outcome.IGNORED_SENSITIVE -> _events.emit("sensitive_skipped")
            ClipboardCaptureProcessor.Outcome.IGNORED_BLANK -> _events.emit("clipboard_empty")
        }
    }

    fun refreshClipboard() {
        refreshGate()
        viewModelScope.launch {
            try {
                if (ShizukuHelper.isAuthorized()) {
                    val text = shizukuExecutor.getClipboardText()
                    if (text != null) {
                        captureProcessor.processText(text, "foreground-shizuku", excludeSensitive.value)
                    }
                    return@launch
                }
                if (isAppInForeground()) {
                    try {
                        val cm = application.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
                        val clip = cm.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val item = clip.getItemAt(0)
                            if (item.uri == null || item.text != null || item.htmlText != null) {
                                val text = item.coerceToText(application)?.toString()
                                captureProcessor.processText(text, "foreground", excludeSensitive.value)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun ensureServiceState() {
        applyServiceState(monitoringEnabled.value, _gateStatus.value)
    }

    private fun applyServiceState(monitoring: Boolean, status: ClipboardGate.Status) {
        // PAUSED — clipboard tool is under development. Never start the service;
        // always stop it if it is still running from a previous build.
        // To re-enable: restore the gate check + ClipboardService.startIfNeeded().
        try {
            application.stopService(Intent(application, ClipboardService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
