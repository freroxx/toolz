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
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.cleaner.CleanCategory
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.StorageInfo
import com.frerox.toolz.data.cleaner.engine.CleanerEngine
import com.frerox.toolz.data.cleaner.trash.CleanerTrashEntity
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.FakeCleanerSandbox
import com.frerox.toolz.data.developer.SpoofGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CleanerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: CleanerEngine,
    private val accessGate: com.frerox.toolz.data.cleaner.access.AccessGate,
    private val exclusionStore: com.frerox.toolz.data.cleaner.engine.CleanerExclusionStore,
    private val gate: SpoofGate
) : ViewModel() {

    // DEV-SPOOF: the cleaner demo sandbox swaps every surface this VM exposes —
    // live scan/clean/trash fixtures drive the same UI states the real engine
    // emits, while the filesystem, Room trash table and accessibility service
    // stay untouched.
    private val CLEANER = DevToolId.CLEANER

    private val _fixtureScan = MutableStateFlow<ScanState>(ScanState.Idle)

    val scanState: StateFlow<ScanState> = combine(
        engine.scanState, _fixtureScan, gate.active(CLEANER),
    ) { real, fixture, demo ->
        if (demo) fixture else real
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScanState.Idle)

    val storageInfo: StateFlow<StorageInfo> = combine(
        engine.storageInfo, gate.active(CLEANER),
    ) { real, demo ->
        if (demo) FakeCleanerSandbox.storageInfo() else real
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StorageInfo())

    /** DEV-SPOOF: Shizuku reads no longer gate corpse cleanup in demo mode. */
    val isShizukuGranted: StateFlow<Boolean> = combine(
        engine.isShizukuGranted, gate.active(CLEANER),
    ) { real, demo ->
        demo || real
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _access = MutableStateFlow<List<com.frerox.toolz.data.cleaner.access.GateStatus>>(emptyList())
    val accessState: StateFlow<List<com.frerox.toolz.data.cleaner.access.GateStatus>> = _access.asStateFlow()

    val usageAccessGranted: Boolean get() =
        // DEV-SPOOF: the usage gate is part of the fake clean story.
        gate.sandboxed(CLEANER) || accessGate.usageAccessGranted()

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

    private val _trashEntries = MutableStateFlow<List<CleanerTrashEntity>>(emptyList())
    val trashEntries: StateFlow<List<CleanerTrashEntity>> = _trashEntries.asStateFlow()

    private val _trashTotalBytes = MutableStateFlow(0L)
    val trashTotalBytes: StateFlow<Long> = _trashTotalBytes.asStateFlow()

    init {
        FakeCleanerSandbox.reset()
        checkPermission()
        engine.refreshShizuku()
        engine.refreshStorageInfo()
        loadTrash()
        viewModelScope.launch {
            gate.active(CLEANER).collect { demo ->
                if (demo) {
                    // Fresh fixture per demo arm; real state untouched.
                    Log.w("DevSpoof", "Cleaner sandbox armed — RAM fixtures only")
                } else {
                    FakeCleanerSandbox.reset()
                }
                _fixtureScan.value = ScanState.Idle
                checkPermission()
            }
        }
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
        viewModelScope.launch {
            com.frerox.toolz.service.CleanerAccessibilityService.autoState.collect { s ->
                if (s.done && s.cleared.isNotEmpty()) {
                    engine.removeClearedAppCaches(s.cleared)
                }
            }
        }
    }

    fun checkPermission() {
        // DEV-SPOOF: demo mode skips the real all-files grant entirely.
        if (gate.sandboxed(CLEANER)) {
            _hasStoragePermission.value = true
            refreshAccess()
            return
        }
        _hasStoragePermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
        refreshAccess()
    }

    fun refreshAccess() {
        // DEV-SPOOF: no permission gates to satisfy in demo mode.
        _access.value = if (gate.sandboxed(CLEANER)) emptyList() else accessGate.statuses()
    }

    fun openGate(id: com.frerox.toolz.data.cleaner.access.GateId) {
        if (gate.sandboxed(CLEANER)) return
        try { context.startActivity(accessGate.intentFor(id).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) {}
    }

    fun openAppSettings(packageName: String) {
        if (gate.sandboxed(CLEANER)) return
        try {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", packageName, null)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) {}
    }

    fun showPermissionDialog() { _showPermissionDialog.value = true }
    fun dismissPermissionDialog() { _showPermissionDialog.value = false }

    fun startScan() {
        // DEV-SPOOF: RAM fixture scan — no real filesystem reads.
        if (gate.sandboxed(CLEANER)) {
            Log.w("DevSpoof", "Cleaner sandbox scan (RAM fixture)")
            viewModelScope.launch {
                val phases = listOf(
                    "Junk files" to 0.22f,
                    "App caches" to 0.45f,
                    "Empty folders" to 0.6f,
                    "Duplicates" to 0.75f,
                    "Large media" to 0.88f,
                    "Leftovers of uninstalled apps" to 0.96f,
                )
                var scanned = 0
                phases.forEach { (cat, progress) ->
                    delay(230)
                    scanned += 1354
                    _fixtureScan.value = ScanState.Scanning(cat, scanned, foundSize = scanned * 4603L, progress = progress)
                }
                delay(180)
                FakeCleanerSandbox.armScan()
                _fixtureScan.value = FakeCleanerSandbox.snapshotResults()
            }
            return
        }
        // Mandatory gate enforced at call site too (UI locks the button, this is defense in depth)
        if (!accessGate.canScan()) { refreshAccess(); return }
        engine.refreshStorageInfo()
        engine.startScan(viewModelScope)
    }

    /** Missing mandatory gates (All-files, +Media on 33+). Empty = scan allowed. */
    fun mandatoryMissing(): List<com.frerox.toolz.data.cleaner.access.GateId> =
        // DEV-SPOOF: demo scans everything, no gates required.
        if (gate.sandboxed(CLEANER)) emptyList() else accessGate.mandatoryUnmet()

    fun cancelScan() {
        if (gate.sandboxed(CLEANER)) {
            _fixtureScan.value = ScanState.Idle
            return
        }
        engine.cancelScan()
    }

    fun toggleCategoryItem(categoryId: String, itemId: String) {
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.toggleItem(categoryId, itemId)
        else engine.toggleSelection(categoryId, itemId)
    }

    fun toggleDuplicateFile(categoryId: String, groupHash: String, path: String) {
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.toggleDuplicateFile(categoryId, groupHash, path)
        else engine.toggleDuplicateFile(categoryId, groupHash, path)
    }

    fun setDuplicateKeeper(categoryId: String, groupHash: String, keeperPath: String) {
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.setDuplicateKeeper(categoryId, groupHash, keeperPath)
        else engine.setDuplicateKeeper(categoryId, groupHash, keeperPath)
    }

    fun setItemsSelected(categoryId: String, itemStableIds: Set<String>, selected: Boolean) {
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.setItemsSelected(categoryId, itemStableIds, selected)
        else engine.setItemsSelected(categoryId, itemStableIds, selected)
    }

    fun setCategorySelected(categoryId: String, selected: Boolean) {
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.setCategorySelected(categoryId, selected)
        else engine.setCategorySelected(categoryId, selected)
    }

    fun setAllSelected(selected: Boolean) {
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.setAllSelected(selected)
        else engine.setAllSelected(selected)
    }

    fun selectSafeOnly() {
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.selectSafeOnly()
        else engine.selectSafeOnly()
    }

    fun setAllDuplicateKeepers(newest: Boolean) {
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.setAllDuplicateKeepers(newest)
        else engine.setAllDuplicateKeepers("dupes", newest)
    }

    fun isCategorySelected(cat: CleanCategory): Boolean =
        if (gate.sandboxed(CLEANER)) FakeCleanerSandbox.isCategoryFullySelected(cat)
        else engine.isCategoryFullySelected(cat)

    fun areAllSelected(cats: List<CleanCategory>): Boolean =
        if (gate.sandboxed(CLEANER)) cats.all { FakeCleanerSandbox.isCategoryFullySelected(it) }
        else engine.areAllSelected(cats)

    fun analyzerCount(): Int = engine.analyzerCount()

    fun deleteSelected() {
        if (_isCleaning.value) return
        _isCleaning.value = true
        // DEV-SPOOF: RAM-only clean — no file access, no Room writes.
        if (gate.sandboxed(CLEANER)) {
            Log.w("DevSpoof", "Cleaner sandbox clean (RAM fixture)")
            viewModelScope.launch {
                try {
                    _fixtureScan.value = ScanState.Cleaning(0.45f, "exported_video_0420.mp4")
                    delay(420)
                    val result = FakeCleanerSandbox.deleteSelected()
                    _fixtureScan.value = ScanState.Cleaning(1f, "")
                    delay(240)
                    _fixtureScan.value = ScanState.Done(result)
                    loadTrash()
                } finally { _isCleaning.value = false }
            }
            return
        }
        viewModelScope.launch {
            try {
                engine.deleteSelected()
                loadTrash()
            } finally { _isCleaning.value = false }
        }
    }

    fun undoClean() {
        // DEV-SPOOF: undo reinserts the RAM fixture rows.
        if (gate.sandboxed(CLEANER)) {
            val n = FakeCleanerSandbox.restoreAll()
            loadTrash()
            _undoEvent.value = "Restored $n item(s)"
            return
        }
        viewModelScope.launch {
            val n = try { engine.undoLastClean() } catch (_: Exception) { 0 }
            _undoEvent.value = "Restored $n item(s)"
            loadTrash()
            engine.refreshStorageInfo()
        }
    }

    fun loadTrash() {
        // DEV-SPOOF: RAM trash rows while demo is armed.
        if (gate.sandboxed(CLEANER)) {
            _trashEntries.value = FakeCleanerSandbox.trashRows
            _trashTotalBytes.value = FakeCleanerSandbox.trashRows.sumOf { it.sizeBytes }
            return
        }
        viewModelScope.launch {
            _trashEntries.value = engine.trash.getTrashEntries()
            _trashTotalBytes.value = engine.trash.getTrashTotalBytes()
        }
    }

    fun restoreTrashItem(id: Long) {
        if (gate.sandboxed(CLEANER)) {
            if (FakeCleanerSandbox.restoreItem(id)) {
                _undoEvent.value = "Restored 1 item"
                loadTrash()
            }
            return
        }
        viewModelScope.launch {
            val ok = engine.trash.restoreItem(id)
            if (ok) {
                _undoEvent.value = "Restored 1 item"
                loadTrash()
                engine.refreshStorageInfo()
            }
        }
    }

    fun restoreAllTrash() {
        if (gate.sandboxed(CLEANER)) {
            val count = FakeCleanerSandbox.restoreAll()
            _undoEvent.value = "Restored $count item(s)"
            loadTrash()
            return
        }
        viewModelScope.launch {
            val count = engine.trash.restoreAll()
            _undoEvent.value = "Restored $count item(s)"
            loadTrash()
            engine.refreshStorageInfo()
        }
    }

    fun restoreSelectedTrash(ids: Set<Long>) {
        if (ids.isEmpty()) return
        if (gate.sandboxed(CLEANER)) {
            val count = FakeCleanerSandbox.restoreItems(ids)
            _undoEvent.value = "Restored $count item(s)"
            loadTrash()
            return
        }
        viewModelScope.launch {
            val count = engine.trash.restoreItems(ids)
            _undoEvent.value = "Restored $count item(s)"
            loadTrash()
            engine.refreshStorageInfo()
        }
    }

    fun deleteTrashItemPermanently(id: Long) {
        if (gate.sandboxed(CLEANER)) {
            if (FakeCleanerSandbox.deletePermanently(setOf(id)) > 0) {
                _undoEvent.value = "Permanently deleted"
                loadTrash()
            }
            return
        }
        viewModelScope.launch {
            val ok = engine.trash.deletePermanently(id)
            if (ok) {
                _undoEvent.value = "Permanently deleted"
                loadTrash()
                engine.refreshStorageInfo()
            }
        }
    }

    fun deleteSelectedTrashPermanently(ids: Set<Long>) {
        if (ids.isEmpty()) return
        if (gate.sandboxed(CLEANER)) {
            val count = FakeCleanerSandbox.deletePermanently(ids)
            _undoEvent.value = "Permanently deleted $count item(s)"
            loadTrash()
            return
        }
        viewModelScope.launch {
            val count = engine.trash.deletePermanently(ids)
            _undoEvent.value = "Permanently deleted $count item(s)"
            loadTrash()
            engine.refreshStorageInfo()
        }
    }

    fun emptyTrash() {
        if (gate.sandboxed(CLEANER)) {
            val count = FakeCleanerSandbox.emptyTrash()
            _undoEvent.value = "Emptied trash ($count items)"
            loadTrash()
            return
        }
        viewModelScope.launch {
            val count = engine.trash.emptyTrash()
            _undoEvent.value = "Emptied trash ($count items)"
            loadTrash()
            engine.refreshStorageInfo()
        }
    }

    fun consumeUndo() { _undoEvent.value = null }

    fun postNotice(text: String) { _undoEvent.value = text }

    val exclusions: kotlinx.coroutines.flow.Flow<Set<String>> = exclusionStore.exclusionsFlow

    fun exclude(path: String) {
        // DEV-SPOOF: demo doesn't persist exclusions.
        if (gate.sandboxed(CLEANER)) return
        viewModelScope.launch { try { exclusionStore.addExclusion(path) } catch (_: Exception) {} }
    }
    fun removeExclusion(path: String) {
        if (gate.sandboxed(CLEANER)) return
        viewModelScope.launch { try { exclusionStore.removeExclusion(path) } catch (_: Exception) {} }
    }

    fun scheduleWeeklyReminder() {
        // DEV-SPOOF: no WorkManager writes in demo mode.
        if (gate.sandboxed(CLEANER)) {
            _undoEvent.value = "Demo mode: schedule is a local fixture only"
            return
        }
        try {
            val req = androidx.work.PeriodicWorkRequestBuilder<com.frerox.toolz.worker.CleanerScheduleWorker>(7, java.util.concurrent.TimeUnit.DAYS)
                .addTag("cleaner_weekly").build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cleaner_weekly", androidx.work.ExistingPeriodicWorkPolicy.KEEP, req)
            _undoEvent.value = "Weekly reminder scheduled"
        } catch (_: Exception) {}
    }

    fun resetState() {
        // DEV-SPOOF: drop the fixture, back to a clean Idle — like a fresh scan.
        if (gate.sandboxed(CLEANER)) {
            FakeCleanerSandbox.reset()
            _fixtureScan.value = ScanState.Idle
            _gridCategory.value = null
            return
        }
        engine.resetState()
        engine.refreshStorageInfo()
        _gridCategory.value = null
    }

    fun openGridView(category: CleanCategory) { _gridCategory.value = category }
    fun closeGridView() { _gridCategory.value = null }
    fun refreshShizuku() {
        if (gate.sandboxed(CLEANER)) return
        engine.refreshShizuku()
    }
    fun requestShizukuPermission(requestCode: Int = 1001) {
        // DEV-SPOOF: Shizuku is faked granted in demo mode.
        if (gate.sandboxed(CLEANER)) {
            _undoEvent.value = "Demo mode: Shizuku is simulated as granted"
            return
        }
        try { com.frerox.toolz.util.shizuku.ShizukuHelper.requestPermission(requestCode) } catch(_:Exception){}
    }

    // --- V4 Auto-clear (accessibility) ---

    val autoClear: kotlinx.coroutines.flow.StateFlow<com.frerox.toolz.service.AutoClearState> =
        com.frerox.toolz.service.CleanerAccessibilityService.autoState

    fun isAutoClearAvailable(): Boolean {
        // DEV-SPOOF: demo runs its cache clear in RAM — no accessibility run.
        if (gate.sandboxed(CLEANER)) return false
        return try {
            com.frerox.toolz.service.CleanerAccessibilityService.isEnabled(context)
        } catch (_: Exception) { false }
    }

    /** Selected AppCache apps as (package, label) pairs for auto-clear runs. */
    fun selectedAppCache(): List<Pair<String, String>> {
        // DEV-SPOOF: read from the live fixture snapshot.
        if (gate.sandboxed(CLEANER)) {
            val state = _fixtureScan.value as? ScanState.Results ?: return emptyList()
            return state.categories.flatMap { it.items }
                .filterIsInstance<com.frerox.toolz.data.cleaner.CleanItem.AppCache>()
                .filter { it.entry.isSelected }
                .map { it.entry.packageName to it.entry.appName }
        }
        val state = engine.scanState.value as? ScanState.Results ?: return emptyList()
        return state.categories.flatMap { it.items }
            .filterIsInstance<com.frerox.toolz.data.cleaner.CleanItem.AppCache>()
            .filter { it.entry.isSelected }
            .map { it.entry.packageName to it.entry.appName }
    }

    fun startAutoClearApps(apps: List<Pair<String, String>>): Boolean {
        // DEV-SPOOF: no real accessibility run while demo is armed.
        if (gate.sandboxed(CLEANER)) return false
        val svc = com.frerox.toolz.service.CleanerAccessibilityService.instance ?: return false
        return try { svc.startAutoClear(apps) } catch (_: Exception) { false }
    }

    fun startAutoClear(pkgs: List<String>): Boolean =
        startAutoClearApps(pkgs.map { it to it })

    private var pendingAutoClearQueue: List<Pair<String, String>>? = null

    fun setPendingAutoClear(apps: List<Pair<String, String>>) {
        pendingAutoClearQueue = apps
    }

    fun pollPendingAutoClear(): List<Pair<String, String>>? {
        val q = pendingAutoClearQueue
        pendingAutoClearQueue = null
        return q
    }

    fun stopAutoClear() {
        try { com.frerox.toolz.service.CleanerAccessibilityService.instance?.stopAutoClear() } catch (_: Exception) {}
    }

    fun resetAutoClear() {
        try { com.frerox.toolz.service.CleanerAccessibilityService.instance?.resetAutoClear() } catch (_: Exception) {}
    }
}
