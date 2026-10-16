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

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.PendingUpdate
import com.frerox.toolz.data.update.UpdateManager
import com.frerox.toolz.data.update.UpdateManagerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val managerState: StateFlow<UpdateManagerState> = updateManager.state

    val preferredAbi: StateFlow<String> = settingsRepository.preferredAbi.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO"
    )

    val autoUpdateEnabled: StateFlow<Boolean> =
        settingsRepository.autoUpdateEnabled.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), false
        )

    // Legacy alias kept so existing call sites keep compiling during migration.
    val uiState: StateFlow<UpdateUiState> = kotlinx.coroutines.flow.MutableStateFlow(UpdateUiState.Idle)

    fun setPreferredAbi(abi: String) {
        viewModelScope.launch { settingsRepository.setPreferredAbi(abi) }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoUpdateEnabled(enabled) }
    }

    fun getDeviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    fun checkForUpdates(force: Boolean = true) {
        viewModelScope.launch { updateManager.check(showNotification = false, force = force) }
    }

    fun startDownload(update: PendingUpdate? = null) {
        updateManager.download(update, autoInstall = true)
    }

    fun cancelDownload() = updateManager.cancelDownload()

    fun installReady() = updateManager.installReady()

    fun needsInstallPermission(): Boolean = updateManager.needsInstallPermission()

    fun openInstallSettings(context: android.content.Context) =
        updateManager.openInstallSettings(context)

    fun skipVersion(version: String) = updateManager.skipVersion(version)

    fun resetState() {
        // Kept for the legacy bottom-sheet dismiss path; manager Idle is sticky-safe.
    }

    // Legacy download entry kept for old callers (now routes to the manager).
    fun startDownload(url: String) {
        val pending = updateManager.currentPendingOrNull()
        if (pending != null && pending.downloadUrl == url) {
            updateManager.download(pending, autoInstall = true)
        }
    }
}

// Legacy UI state (kept for binary compatibility; new UI reads managerState).
sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class Downloading(val progress: Float) : UpdateUiState()
    data class Success(val release: com.frerox.toolz.data.update.GitHubRelease) : UpdateUiState()
    data class ManifestSuccess(val manifest: com.frerox.toolz.data.update.UpdateManifest) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}
