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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    // Custom presets parsed from DataStore JSON; fallback to defaults; always capped to 6
    val activePresets: StateFlow<List<PurgeShotPreset>> = settingsRepository.purgeShotCustomPresets.map { json ->
        parsePresetsJson(json) ?: PurgeShotPreset.defaults()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PurgeShotPreset.defaults())

    val allOptions: List<PurgeShotPreset> = PurgeShotPreset.allOptions()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

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
        viewModelScope.launch { repository.cancel(id) }
    }

    fun deleteNow(id: Long) {
        viewModelScope.launch { repository.deleteNow(id) }
    }

    fun clearAllPending() {
        viewModelScope.launch { repository.clearPending() }
    }

    fun enqueueForPopup(uriStr: String?, displayName: String, path: String?, duration: Long, label: String) {
        if (uriStr == null) return
        viewModelScope.launch {
            val uri = Uri.parse(uriStr)
            repository.enqueue(uri, displayName, duration, label, path)
        }
    }

    // Called from UI queue testing: manually add a dummy for preview (debug)
    fun debugEnqueueDummy() {
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
