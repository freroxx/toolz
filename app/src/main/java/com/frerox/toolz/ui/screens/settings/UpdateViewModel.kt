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
    private val settingsRepository: SettingsRepository,
    private val gate: com.frerox.toolz.data.developer.SpoofGate
) : ViewModel() {

    // DEV-SPOOF: the whole update lifecycle is simulated in RAM while demo is
    // armed — no GitHub fetch, no APK download, no install-session touch.
    private val UPDATE = com.frerox.toolz.data.developer.DevToolId.UPDATE_NEWS

    private val _demoState = kotlinx.coroutines.flow.MutableStateFlow<UpdateManagerState?>(null)

    val managerState: StateFlow<UpdateManagerState> = kotlinx.coroutines.flow.combine(
        updateManager.state, _demoState, gate.active(UPDATE),
    ) { real, demo, demoOn ->
        if (demoOn && demo != null) demo else real
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateManagerState.Idle)

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

    /** DEV-SPOOF: fixture release the fake lifecycle stages around. */
    private fun fakeUpdate(): com.frerox.toolz.data.update.PendingUpdate =
        com.frerox.toolz.data.update.PendingUpdate(
            version = "9.9.9-demo",
            versionCode = 999999,
            changelog = "## Demo release notes\n- Showcase build — no APK was downloaded.\n" +
                "- Toolz Demo Mode banner removal.\n- Cleaner, vault and news sandboxes.",
            downloadUrl = "https://fake.local/toolz/demo.apk",
            sizeBytes = 46_600_000L,
            sha256 = null,
            isCritical = false,
            abi = "universal (demo)",
            source = "demo-fixture",
        )

    fun checkForUpdates(force: Boolean = true) {
        // DEV-SPOOF: staged fake check — Checking → Available, no network.
        if (gate.sandboxed(UPDATE)) {
            android.util.Log.w("DevSpoof", "Update sandbox check (RAM fixture)")
            viewModelScope.launch {
                _demoState.value = UpdateManagerState.Checking
                kotlinx.coroutines.delay(700)
                _demoState.value = UpdateManagerState.Available(fakeUpdate())
            }
            return
        }
        viewModelScope.launch { updateManager.check(showNotification = false, force = force) }
    }

    fun startDownload(update: PendingUpdate? = null) {
        // DEV-SPOOF: fake download that climbs to ReadyToInstall in RAM.
        if (gate.sandboxed(UPDATE)) {
            val demo = fakeUpdate()
            viewModelScope.launch {
                val total = demo.sizeBytes ?: 46_600_000L
                var downloaded = 0L
                while (downloaded < total) {
                    downloaded = (downloaded + total / 18).coerceAtMost(total)
                    _demoState.value = UpdateManagerState.Downloading(
                        update = demo,
                        bytesDownloaded = downloaded,
                        totalBytes = total,
                        speedBps = 4_600_000L + (total % 300_000),
                        progress = (downloaded * 100f / total),
                    )
                    kotlinx.coroutines.delay(140)
                }
                _demoState.value = UpdateManagerState.ReadyToInstall(demo, java.io.File("/dev/demo.apk"))
            }
            return
        }
        updateManager.download(update, autoInstall = true)
    }

    fun cancelDownload() {
        // DEV-SPOOF: cancel re-arms the fake Available card.
        if (gate.sandboxed(UPDATE)) {
            _demoState.value = UpdateManagerState.Available(fakeUpdate())
            return
        }
        updateManager.cancelDownload()
    }

    fun installReady() {
        // DEV-SPOOF: installing a demo APK is refused honestly.
        if (gate.sandboxed(UPDATE)) {
            _demoState.value = UpdateManagerState.Error(
                "Demo mode: this release is a local fixture — no APK was downloaded and nothing will be installed.",
                retryable = false,
            )
            return
        }
        updateManager.installReady()
    }

    fun needsInstallPermission(): Boolean =
        // DEV-SPOOF: no install permission dance in demo mode.
        if (gate.sandboxed(UPDATE)) false else updateManager.needsInstallPermission()

    fun openInstallSettings(context: android.content.Context) {
        if (gate.sandboxed(UPDATE)) return
        updateManager.openInstallSettings(context)
    }

    fun skipVersion(version: String) {
        if (gate.sandboxed(UPDATE)) return
        updateManager.skipVersion(version)
    }

    fun resetState() {
        _demoState.value = null
        // Kept for the legacy bottom-sheet dismiss path; manager Idle is sticky-safe.
    }

    // Legacy download entry kept for old callers (now routes to the manager).
    fun startDownload(url: String) {
        // DEV-SPOOF: legacy entry drives the same fake lifecycle.
        if (gate.sandboxed(UPDATE)) {
            startDownload(null as PendingUpdate?)
            return
        }
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
