package com.frerox.toolz.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.backup.BackupCategory
import com.frerox.toolz.data.backup.BackupExportResult
import com.frerox.toolz.data.backup.BackupImportResult
import com.frerox.toolz.data.backup.BackupItem
import com.frerox.toolz.data.backup.LocalBackupManager
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.VibrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupRestoreUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportResult: BackupExportResult? = null,
    val importResult: BackupImportResult? = null,
    val error: String? = null,
    val progress: String? = null,
    val backupFrequency: String = "Never",
    val customAutoBackupDays: Int = 1,
    val selectedItems: Set<BackupItem> = BackupItem.entries.toSet()
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localBackupManager: LocalBackupManager,
    private val repository: SettingsRepository,
    private val vibrationManager: VibrationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.backupFrequency.collect { freq ->
                _uiState.value = _uiState.value.copy(backupFrequency = freq)
            }
        }
        viewModelScope.launch {
            repository.autoBackupCustomDays.collect { days ->
                _uiState.value = _uiState.value.copy(customAutoBackupDays = days)
            }
        }
        viewModelScope.launch {
            localBackupManager.progress.collect { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
            }
        }
    }

    fun toggleItem(item: BackupItem) {
        val current = _uiState.value.selectedItems
        val updated = if (current.contains(item)) current - item else current + item
        _uiState.value = _uiState.value.copy(selectedItems = updated)
    }

    fun toggleCategory(category: BackupCategory) {
        val current = _uiState.value.selectedItems
        val categoryItems = BackupItem.entries.filter { it.category == category }.toSet()
        val allInCategorySelected = current.containsAll(categoryItems)
        
        val updated = if (allInCategorySelected) {
            current - categoryItems
        } else {
            current + categoryItems
        }
        _uiState.value = _uiState.value.copy(selectedItems = updated)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(selectedItems = BackupItem.entries.toSet())
    }

    fun selectNone() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }

    fun setBackupFrequency(freq: String) = viewModelScope.launch {
        repository.setBackupFrequency(freq)
    }

    fun setCustomAutoBackupDays(days: Int) = viewModelScope.launch {
        repository.setAutoBackupCustomDays(days)
    }

    fun createBackup() = viewModelScope.launch {
        if (_uiState.value.selectedItems.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please select at least one item to backup", Toast.LENGTH_SHORT).show()
            }
            return@launch
        }
        
        _uiState.value = _uiState.value.copy(isExporting = true, error = null, exportResult = null)
        try {
            val result = localBackupManager.exportReusableBackup(
                reason = "manual",
                items = _uiState.value.selectedItems
            )
            _uiState.value = _uiState.value.copy(isExporting = false, exportResult = result)
            vibrationManager.vibrateSuccess()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Backup created: ${result.fileName}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(isExporting = false, error = e.localizedMessage)
            vibrationManager.vibrateError()
        }
    }

    fun restoreBackup(uri: Uri) = viewModelScope.launch {
        if (_uiState.value.selectedItems.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Please select at least one item to restore", Toast.LENGTH_SHORT).show()
            }
            return@launch
        }

        _uiState.value = _uiState.value.copy(isImporting = true, error = null, importResult = null)
        try {
            val result = localBackupManager.importReusableBackup(
                uri = uri,
                itemsToRestore = _uiState.value.selectedItems
            )
            _uiState.value = _uiState.value.copy(isImporting = false, importResult = result)
            vibrationManager.vibrateSuccess()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(isImporting = false, error = e.localizedMessage)
            vibrationManager.vibrateError()
        }
    }
}
