package com.frerox.toolz.ui.screens.cleaner

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.cleaner.*
import com.frerox.toolz.data.cleaner.CleanerRepository
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CleanerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CleanerRepository
) : ViewModel() {

    val scanState: StateFlow<ScanState> = repository.scanState
    val storageInfo: StateFlow<StorageInfo> = repository.storageInfo

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val _gridCategory = MutableStateFlow<CleanCategory?>(null)
    val gridCategory: StateFlow<CleanCategory?> = _gridCategory.asStateFlow()

    init {
        checkPermission()
        repository.refreshStorageInfo()
        
        // Update grid category when scan results change
        viewModelScope.launch {
            scanState.collect { state ->
                if (state is ScanState.Results) {
                    _gridCategory.value?.let { current ->
                        val updated = state.categories.find { it.id == current.id }
                        _gridCategory.value = updated
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
        } else {
            true
        }
    }

    fun showPermissionDialog() {
        _showPermissionDialog.value = true
    }

    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
    }

    fun startScan() {
        repository.refreshStorageInfo()
        repository.startScan(viewModelScope)
    }

    fun cancelScan() {
        repository.cancelScan()
    }

    fun toggleCategoryItem(categoryId: String, itemId: String) {
        repository.toggleSelection(categoryId, itemId)
    }

    fun toggleDuplicateFile(categoryId: String, groupHash: String, path: String) {
        repository.toggleDuplicateFile(categoryId, groupHash, path)
    }

    fun deleteSelected() {
        viewModelScope.launch {
            repository.deleteSelected()
        }
    }

    fun resetState() {
        repository.resetState()
        repository.refreshStorageInfo()
        _gridCategory.value = null
    }

    fun openGridView(category: CleanCategory) {
        _gridCategory.value = category
    }

    fun closeGridView() {
        _gridCategory.value = null
    }
}
