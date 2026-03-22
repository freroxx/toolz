package com.frerox.toolz.ui.screens.cleaner

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.cleaner.CleanerRepository
import com.frerox.toolz.data.cleaner.ScanState
import com.frerox.toolz.data.cleaner.StorageInfo
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
    private val repository: CleanerRepository
) : ViewModel() {

    val scanState: StateFlow<ScanState> = repository.scanState
    val storageInfo: StateFlow<StorageInfo> = repository.storageInfo
    val currentPath: StateFlow<String> = repository.currentPath

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    init {
        checkPermission()
        repository.refreshStorageInfo()
    }

    fun checkPermission() {
        _hasStoragePermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Below R, READ/WRITE_EXTERNAL_STORAGE handled elsewhere
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

    fun toggleDuplicateFile(groupHash: String, filePath: String) {
        repository.toggleDuplicateFile(groupHash, filePath)
    }

    fun toggleCorpse(path: String) {
        repository.toggleCorpse(path)
    }

    fun deleteSelected() {
        viewModelScope.launch {
            repository.deleteSelected()
        }
    }

    fun resetState() {
        repository.resetState()
        repository.refreshStorageInfo()
    }
}
