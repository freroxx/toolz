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
    private val engine: CleanerEngine
) : ViewModel() {

    val scanState: StateFlow<ScanState> = engine.scanState
    val storageInfo: StateFlow<StorageInfo> = engine.storageInfo
    val isShizukuGranted: StateFlow<Boolean> = engine.isShizukuGranted

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val _gridCategory = MutableStateFlow<CleanCategory?>(null)
    val gridCategory: StateFlow<CleanCategory?> = _gridCategory.asStateFlow()

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
    }

    fun showPermissionDialog() { _showPermissionDialog.value = true }
    fun dismissPermissionDialog() { _showPermissionDialog.value = false }

    fun startScan() {
        engine.refreshStorageInfo()
        engine.startScan(viewModelScope)
    }

    fun cancelScan() { engine.cancelScan() }

    fun toggleCategoryItem(categoryId: String, itemId: String) { engine.toggleSelection(categoryId, itemId) }

    fun toggleDuplicateFile(categoryId: String, groupHash: String, path: String) { engine.toggleDuplicateFile(categoryId, groupHash, path) }

    fun deleteSelected() {
        viewModelScope.launch { engine.deleteSelected() }
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
}
