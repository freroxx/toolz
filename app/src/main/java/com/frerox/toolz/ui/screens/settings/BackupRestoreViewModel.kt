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

package com.frerox.toolz.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.R
import com.frerox.toolz.data.backup.BackupCategory
import com.frerox.toolz.data.backup.BackupExportResult
import com.frerox.toolz.data.backup.BackupHistoryRequiresPasswordException
import com.frerox.toolz.data.backup.BackupImportResult
import com.frerox.toolz.data.backup.BackupItem
import com.frerox.toolz.data.backup.BackupPasswordRequiredException
import com.frerox.toolz.data.backup.BackupProtection
import com.frerox.toolz.data.backup.BackupWrongPasswordException
import com.frerox.toolz.data.backup.LocalBackupManager
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.NotificationHelper
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
    val backupNotifications: Boolean = true,
    val selectedItems: Set<BackupItem> = BackupItem.entries.toSet(),
    /** Set when a picked archive is encrypted and the UI must ask for its password. */
    val pendingEncryptedRestore: Uri? = null,
    /** True when the last completed export used password protection. */
    val lastExportEncrypted: Boolean = false,
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
            repository.backupNotifications.collect { enabled ->
                _uiState.value = _uiState.value.copy(backupNotifications = enabled)
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

    fun setBackupNotifications(enabled: Boolean) = viewModelScope.launch {
        repository.setBackupNotifications(enabled)
    }

    fun createBackup(protection: BackupProtection = BackupProtection.None) = viewModelScope.launch {
        if (_uiState.value.selectedItems.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.st_Backup_Select_AtLeastOne), Toast.LENGTH_SHORT).show()
            }
            return@launch
        }
        
        _uiState.value = _uiState.value.copy(isExporting = true, error = null, exportResult = null)
        try {
            val result = localBackupManager.exportReusableBackup(
                reason = "manual",
                items = _uiState.value.selectedItems,
                protection = protection,
            )
            _uiState.value = _uiState.value.copy(
                isExporting = false,
                exportResult = result,
                lastExportEncrypted = result.encrypted,
            )
            vibrationManager.vibrateSuccess()
            NotificationHelper.showBackupSuccess(context, result.fileName)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.st_Backup_Created, result.fileName), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val message = when (e) {
                is BackupHistoryRequiresPasswordException ->
                    context.getString(R.string.st_Backup_History_RequiresPassword)
                else -> e.localizedMessage
            }
            _uiState.value = _uiState.value.copy(isExporting = false, error = message)
            vibrationManager.vibrateError()
            NotificationHelper.showBackupFailure(context, message)
        }
    }

    /**
     * Called as soon as the user picks a file. Encrypted archives are parked in
     * [BackupRestoreUiState.pendingEncryptedRestore] so the screen can ask for
     * the password; plain archives restore immediately.
     */
    fun prepareRestore(uri: Uri) = viewModelScope.launch {
        val encrypted = runCatching { localBackupManager.isEncryptedBackup(uri) }.getOrDefault(false)
        if (encrypted) {
            _uiState.value = _uiState.value.copy(pendingEncryptedRestore = uri, error = null)
        } else {
            restoreBackup(uri, secret = null)
        }
    }

    fun cancelPendingRestore() {
        _uiState.value = _uiState.value.copy(pendingEncryptedRestore = null)
    }

    fun restoreBackup(uri: Uri, secret: String? = null) = viewModelScope.launch {
        if (_uiState.value.selectedItems.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.st_Backup_Restore_AtLeastOne), Toast.LENGTH_SHORT).show()
            }
            return@launch
        }

        _uiState.value = _uiState.value.copy(
            isImporting = true,
            error = null,
            importResult = null,
            pendingEncryptedRestore = null,
        )
        try {
            val result = localBackupManager.importReusableBackup(
                uri = uri,
                itemsToRestore = _uiState.value.selectedItems,
                secret = secret,
            )
            _uiState.value = _uiState.value.copy(isImporting = false, importResult = result)
            vibrationManager.vibrateSuccess()
            NotificationHelper.showRestoreSuccess(context)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.st_Backup_Restored), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val message = when (e) {
                is BackupWrongPasswordException -> context.getString(R.string.st_Backup_Unlock_Wrong)
                is BackupPasswordRequiredException -> context.getString(R.string.st_Backup_Unlock_Required)
                else -> e.localizedMessage
            }
            _uiState.value = _uiState.value.copy(isImporting = false, error = message)
            vibrationManager.vibrateError()
            NotificationHelper.showRestoreFailure(context, message)
        }
    }
}
