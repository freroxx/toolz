/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.cleaner

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.data.cleaner.engine.CleanerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CleanerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: CleanerEngine,
    private val accessGate: com.frerox.toolz.data.cleaner.access.AccessGate,
    private val exclusionStore: com.frerox.toolz.data.cleaner.engine.CleanerExclusionStore
) : ViewModel() {

    val scanState: StateFlow<ScanState> = engine.scanState
    val storageInfo: StateFlow<StorageInfo> = engine.storageInfo
    val isShizukuGranted: StateFlow<Boolean> = engine.isShizukuGranted

    private val _access = MutableStateFlow<List<com.frerox.toolz.data.cleaner.access.GateStatus>>(emptyList())
    val accessState: StateFlow<List<com.frerox.toolz.data.cleaner.access.GateStatus>> = _access.asStateFlow()

    val usageAccessGranted: Boolean get() = accessGate.usageAccessGranted()

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val _gridCategory = MutableStateFlow<CleanCategory?>(null)
    val gridCategory: StateFlow<CleanCategory?> = _gridCategory.asStateFlow()

    private val _undoEvent = MutableStateFlow<String?>(null)
    val undoEvent: StateFlow<String?> = _undoEvent.asStateFlow()

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning: StateFlow<Boolean> = _isCleaning.asStateFlow()

    init {
        checkPermission()
        engine.refreshShizuku()
        engine.refreshStorageInfo()
        viewModelScope.launch {
            scanState.collect { state ->
                if (state is ScanState.Results) {
                    _gridCategory.value?.let { current ->
                        _gridCategory.value = state.categories.find { it.id == current.id }
                    }
                } else if (state !is ScanState.Results) {
                    _gridCategory.value = null
                }
            }
        }
    }

    fun checkPermission() {
        _hasStoragePermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
        refreshAccess()
    }

    fun refreshAccess() { _access.value = accessGate.statuses() }

    fun openGate(id: com.frerox.toolz.data.cleaner.access.GateId) {
        try { context.startActivity(accessGate.intentFor(id).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
    }

    fun openAppSettings(packageName: String) {
        try {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) {}
    }

    fun showPermissionDialog() { _showPermissionDialog.value = true }
    fun dismissPermissionDialog() { _showPermissionDialog.value = false }

    fun startScan() {
        // Mandatory gate enforced at call site too (UI locks the button, this is defense in depth)
        if (!accessGate.canScan()) { refreshAccess(); return }
        engine.refreshStorageInfo()
        engine.startScan(viewModelScope)
    }

    /** Missing mandatory gates (All-files, +Media on 33+). Empty = scan allowed. */
    fun mandatoryMissing(): List<com.frerox.toolz.data.cleaner.access.GateId> = accessGate.mandatoryUnmet()

    fun cancelScan() { engine.cancelScan() }

    fun toggleCategoryItem(categoryId: String, itemId: String) { engine.toggleSelection(categoryId, itemId) }

    fun toggleDuplicateFile(categoryId: String, groupHash: String, path: String) { engine.toggleDuplicateFile(categoryId, groupHash, path) }

    fun setCategorySelected(categoryId: String, selected: Boolean) { engine.setCategorySelected(categoryId, selected) }

    fun setAllSelected(selected: Boolean) { engine.setAllSelected(selected) }

    fun isCategorySelected(cat: CleanCategory): Boolean = engine.isCategoryFullySelected(cat)

    fun areAllSelected(cats: List<CleanCategory>): Boolean = engine.areAllSelected(cats)

    fun analyzerCount(): Int = engine.analyzerCount()

    fun deleteSelected() {
        if (_isCleaning.value) return
        _isCleaning.value = true
        viewModelScope.launch {
            try { engine.deleteSelected() } finally { _isCleaning.value = false }
        }
    }

    fun undoClean() {
        viewModelScope.launch {
            val n = try { engine.undoLastClean() } catch (_: Exception) { 0 }
            _undoEvent.value = "Restored $n item(s)"
            engine.refreshStorageInfo()
        }
    }

    fun consumeUndo() { _undoEvent.value = null }

    val exclusions: kotlinx.coroutines.flow.Flow<Set<String>> = exclusionStore.exclusionsFlow

    fun exclude(path: String) { viewModelScope.launch { try { exclusionStore.addExclusion(path) } catch (_: Exception) {} } }
    fun removeExclusion(path: String) { viewModelScope.launch { try { exclusionStore.removeExclusion(path) } catch (_: Exception) {} } }

    fun scheduleWeeklyReminder() {
        try {
            val req = androidx.work.PeriodicWorkRequestBuilder<com.frerox.toolz.worker.CleanerScheduleWorker>(7, java.util.concurrent.TimeUnit.DAYS)
                .addTag("cleaner_weekly").build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cleaner_weekly", androidx.work.ExistingPeriodicWorkPolicy.KEEP, req)
            _undoEvent.value = "Weekly reminder scheduled"
        } catch (_: Exception) {}
    }

    fun resetState() {
        engine.resetState()
        engine.refreshStorageInfo()
        _gridCategory.value = null
    }

    fun openGridView(category: CleanCategory) { _gridCategory.value = category }
    fun closeGridView() { _gridCategory.value = null }
    fun refreshShizuku() { engine.refreshShizuku() }
    fun requestShizukuPermission(requestCode: Int = 1001) {
        try { com.frerox.toolz.util.shizuku.ShizukuHelper.requestPermission(requestCode) } catch(_:Exception){}
    }

    // --- V4 Auto-clear (accessibility) ---

    val autoClear: kotlinx.coroutines.flow.StateFlow<com.frerox.toolz.service.AutoClearState> =
        com.frerox.toolz.service.CleanerAccessibilityService.autoState

    fun isAutoClearAvailable(): Boolean = try {
        com.frerox.toolz.service.CleanerAccessibilityService.isEnabled(context)
    } catch (_: Exception) { false }

    /** Selected AppCache apps as (package, label) pairs for auto-clear runs. */
    fun selectedAppCache(): List<Pair<String, String>> {
        val state = engine.scanState.value as? ScanState.Results ?: return emptyList()
        return state.categories.flatMap { it.items }
            .filterIsInstance<com.frerox.toolz.data.cleaner.CleanItem.AppCache>()
            .filter { it.entry.isSelected }
            .map { it.entry.packageName to it.entry.appName }
    }

    fun startAutoClear(pkgs: List<String>): Boolean {
        val svc = com.frerox.toolz.service.CleanerAccessibilityService.instance ?: return false
        return try { svc.startAutoClear(pkgs) } catch (_: Exception) { false }
    }

    fun stopAutoClear() {
        try { com.frerox.toolz.service.CleanerAccessibilityService.instance?.stopAutoClear() } catch (_: Exception) {}
    }

    fun resetAutoClear() {
        try { com.frerox.toolz.service.CleanerAccessibilityService.instance?.resetAutoClear() } catch (_: Exception) {}
    }
}
