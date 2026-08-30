/*
 * Copyright (C) 2026 Toolz Contributors
 */

package com.frerox.toolz.ui.screens.purgeshot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.purgeshot.PurgeShotEntity
import com.frerox.toolz.data.purgeshot.PurgeShotPreset
import com.frerox.toolz.data.purgeshot.PurgeShotRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.PurgeShotService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class PurgeShotViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PurgeShotRepository,
    private val settingsRepository: SettingsRepository,
    private val dao: com.frerox.toolz.data.purgeshot.PurgeShotDao
) : ViewModel() {

    val enabled = settingsRepository.purgeShotEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val smartAuto = settingsRepository.purgeShotSmartAuto.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoDurationMs = settingsRepository.purgeShotAutoDuration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15 * 60_000L)
    val pendingCount = repository.pendingCountFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingQueue: StateFlow<List<PurgeShotEntity>> = repository.pendingFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allQueue: StateFlow<List<PurgeShotEntity>> = repository.allFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Custom presets — Auto sentinel resolved to live autoDuration (so Auto button always reflects setting)
    val activePresets: StateFlow<List<PurgeShotPreset>> = combine(
        settingsRepository.purgeShotCustomPresets,
        autoDurationMs
    ) { json, autoDur ->
        val base = parsePresetsJson(json) ?: PurgeShotPreset.defaults()
        base.take(6).map { p ->
            if (p.label.equals("Auto", ignoreCase = true) || p.durationMillis == PurgeShotPreset.AUTO_SENTINEL) {
                p.copy(durationMillis = autoDur, iconName = "auto_awesome")
            } else p
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PurgeShotPreset.defaults().map {
        if (it.label == "Auto") it.copy(durationMillis = 15 * 60_000L) else it
    })

    val allOptions: List<PurgeShotPreset> = PurgeShotPreset.allOptions()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Polished stats — computed from queues (10x: storage saved, next purge, deleted count)
    val totalDeleted: StateFlow<Int> = allQueue.map { list -> list.count { it.status == PurgeShotEntity.STATUS_DELETED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val nextPurgeEntity: StateFlow<PurgeShotEntity?> = pendingQueue.map { it.minByOrNull { e -> e.scheduledDeleteAtMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Approximate storage (avg 2.8 MB per screenshot) + live size query for accuracy when possible
    // Fixed: run MediaStore query on IO dispatcher, not Main (was blocking UI via WhileSubscribed map)
    val estimatedPendingBytes: StateFlow<Long> = pendingQueue.map { list ->
        var sum = 0L
        for (e in list) {
            val sz = queryMediaSize(e.fileUriString) ?: 2_800_000L
            sum += sz
        }
        sum
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val estimatedSavedBytes: StateFlow<Long> = allQueue.map { list ->
        val deleted = list.filter { it.status == PurgeShotEntity.STATUS_DELETED }
        var sum = 0L
        for (e in deleted) {
            val sz = queryMediaSize(e.fileUriString) ?: 2_800_000L
            sum += sz
        }
        sum
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // Undo stack — last cancelled item within 5s window
    private var lastUndone: PurgeShotEntity? = null
    private val _undoAvailable = MutableStateFlow<PurgeShotEntity?>(null)
    val undoAvailable: StateFlow<PurgeShotEntity?> = _undoAvailable

    init {
        viewModelScope.launch {
            repository.ensureRestoredAndRescheduled()
        }
        // Auto-start/stop service based on enabled
        viewModelScope.launch {
            enabled.collect { isEnabled ->
                if (isEnabled) startService() else stopService()
            }
        }
    }

    fun hasAllFilesAccess(): Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
    } else true

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPurgeShotEnabled(value)
            if (value) startService() else stopService()
        }
    }

    fun setSmartAuto(value: Boolean) {
        viewModelScope.launch { settingsRepository.setPurgeShotSmartAuto(value) }
    }

    fun setAutoDuration(duration: Long) {
        viewModelScope.launch { settingsRepository.setPurgeShotAutoDuration(duration) }
    }

    fun saveCustomPresets(presets: List<PurgeShotPreset>) {
        val capped = presets.take(6)
        viewModelScope.launch {
            val json = presetsToJson(capped)
            settingsRepository.setPurgeShotCustomPresets(json)
        }
    }

    fun cancelEntry(id: Long) {
        viewModelScope.launch {
            val entity = dao.getById(id)
            lastUndone = entity
            _undoAvailable.value = entity
            repository.cancel(id)
            // Auto-clear undo after 5s
            kotlinx.coroutines.delay(5000)
            if (_undoAvailable.value?.id == id) _undoAvailable.value = null
        }
    }

    fun undoCancel() {
        val e = _undoAvailable.value ?: lastUndone ?: return
        viewModelScope.launch {
            _undoAvailable.value = null
            // Re-enqueue with same duration but new schedule (fresh timer)
            val uri = android.net.Uri.parse(e.fileUriString)
            repository.enqueue(uri, e.displayName, e.durationMillis, e.durationLabel, e.filePath)
        }
    }

    fun deleteNow(id: Long) {
        viewModelScope.launch { repository.deleteNow(id) }
    }

    fun clearAllPending() {
        viewModelScope.launch { repository.clearPending() }
    }

    fun extendEntry(id: Long, extraMillis: Long) {
        viewModelScope.launch {
            val e = dao.getById(id) ?: return@launch
            val newDelay = (e.scheduledDeleteAtMs - System.currentTimeMillis()) + extraMillis
            if (newDelay <= 0) return@launch
            // Cancel and re-enqueue with extended duration
            repository.cancel(id)
            val uri = android.net.Uri.parse(e.fileUriString)
            val newDuration = e.durationMillis + extraMillis
            val label = formatDurationLabel(newDuration)
            repository.enqueue(uri, e.displayName, newDuration.coerceAtMost(30L * 24 * 60 * 60 * 1000L), label, e.filePath)
        }
    }

    fun enqueueForPopup(uriStr: String?, displayName: String, path: String?, duration: Long, label: String) {
        if (uriStr == null) return
        viewModelScope.launch {
            val uri = Uri.parse(uriStr)
            repository.enqueue(uri, displayName, duration, label, path)
        }
    }

    // Debug only — gated behind BuildConfig.DEBUG to avoid leaking dummy queue entries in release
    fun debugEnqueueDummy() {
        if (!com.frerox.toolz.BuildConfig.DEBUG) return
        viewModelScope.launch {
            val uri = Uri.parse("content://media/external/images/media/999999")
            repository.enqueue(uri, "Screenshot_debug_${System.currentTimeMillis()}.jpg", 60_000L, "1 min", null)
        }
    }

    private fun startService() {
        try {
            val svc = Intent(context, PurgeShotService::class.java).apply { action = PurgeShotService.ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(svc) else context.startService(svc)
        } catch (_: Exception) {}
    }

    private fun stopService() {
        try {
            val svc = Intent(context, PurgeShotService::class.java).apply { action = PurgeShotService.ACTION_STOP }
            context.startService(svc)
        } catch (_: Exception) {}
    }

    private fun queryMediaSize(uriStr: String): Long? = try {
        val uri = android.net.Uri.parse(uriStr)
        context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
                if (idx != -1) c.getLong(idx).takeIf { it > 0 } else null
            } else null
        }
    } catch (_: Exception) { null }

    private fun formatDurationLabel(millis: Long): String = when (millis) {
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
        else -> "${millis / 60_000} min"
    }

    // --- JSON helpers for presets (max 6 buttons, entirely customizable) ---
    private fun presetsToJson(presets: List<PurgeShotPreset>): String {
        val arr = JSONArray()
        for (p in presets) {
            val o = JSONObject()
            o.put("label", p.label)
            o.put("duration", p.durationMillis)
            o.put("icon", p.iconName)
            arr.put(o)
        }
        return arr.toString()
    }

    private fun parsePresetsJson(json: String?): List<PurgeShotPreset>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<PurgeShotPreset>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    PurgeShotPreset(
                        label = o.getString("label"),
                        durationMillis = o.getLong("duration"),
                        iconName = o.optString("icon", "timer")
                    )
                )
            }
            if (list.isEmpty()) null else list.take(6)
        } catch (_: Exception) { null }
    }
}
