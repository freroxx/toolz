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
import com.frerox.toolz.R
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofGate
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.data.update.PendingUpdate
import com.frerox.toolz.data.update.UpdateError
import com.frerox.toolz.data.update.UpdateManager
import com.frerox.toolz.data.update.UpdateManagerState
import com.frerox.toolz.data.update.pendingUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager,
    private val settingsRepository: SettingsRepository,
    private val gate: SpoofGate,
) : ViewModel() {

    // DEV-SPOOF: the whole update lifecycle is simulated in RAM while the
    // sandbox is active — no GitHub fetch, APK download or install-session touch.
    private val updateTool = DevToolId.UPDATE_NEWS

    private val sandboxState = MutableStateFlow<UpdateManagerState?>(null)

    val managerState: StateFlow<UpdateManagerState> = combine(
        updateManager.state, sandboxState, gate.active(updateTool),
    ) { real, fixture, sandboxed ->
        if (sandboxed && fixture != null) fixture else real
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateManagerState.Idle)

    val pending: StateFlow<PendingUpdate?> = managerState
        .map { it.pendingUpdate() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val preferredAbi: StateFlow<String> = settingsRepository.preferredAbi.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), "AUTO"
    )

    val autoUpdateEnabled: StateFlow<Boolean> =
        settingsRepository.autoUpdateEnabled.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), false
        )

    val lastChecked: StateFlow<Long> = settingsRepository.lastUpdateCheck.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L
    )

    init {
        updateManager.restore()
    }

    fun checkForUpdates(force: Boolean = true) {
        if (gate.sandboxed(updateTool)) {
            viewModelScope.launch {
                sandboxState.value = UpdateManagerState.Checking
                delay(700)
                sandboxState.value = UpdateManagerState.Available(fakeUpdate())
            }
            return
        }
        viewModelScope.launch {
            updateManager.check(showNotification = false, force = force)
        }
    }

    fun startDownload(update: PendingUpdate? = null) {
        if (gate.sandboxed(updateTool)) {
            val fixture = fakeUpdate()
            viewModelScope.launch {
                val total = fixture.sizeBytes ?: 46_600_000L
                var downloaded = 0L
                while (downloaded < total) {
                    downloaded = (downloaded + total / 18).coerceAtMost(total)
                    sandboxState.value = UpdateManagerState.Downloading(
                        update = fixture,
                        bytesDownloaded = downloaded,
                        totalBytes = total,
                        speedBps = 4_600_000L,
                        progress = downloaded * 100f / total,
                    )
                    delay(140)
                }
                sandboxState.value = UpdateManagerState.ReadyToInstall(
                    update = fixture,
                    apkFile = File("/dev/fixture.apk"),
                    needsPermission = false,
                )
            }
            return
        }
        updateManager.download(update, autoInstall = true)
    }

    fun cancelDownload() {
        if (gate.sandboxed(updateTool)) {
            sandboxState.value = UpdateManagerState.Available(fakeUpdate())
            return
        }
        updateManager.cancelDownload()
    }

    fun installReady() {
        if (gate.sandboxed(updateTool)) {
            sandboxState.value = UpdateManagerState.Error(
                UpdateError.Verification(
                    overrideMessageRes = R.string.st_Update_DeveloperModeBlocked,
                ),
                retryDownload = false,
            )
            return
        }
        updateManager.installReady()
    }

    /** Retry button: re-download when the failure was file-related. */
    fun retry() {
        val state = managerState.value
        if (state is UpdateManagerState.Error && state.retryDownload) {
            if (gate.sandboxed(updateTool)) {
                startDownload(null)
                return
            }
            val target = updateManager.currentPendingOrNull()
                ?: updateManager.pendingFromStore()
            if (target != null) {
                startDownload(target)
                return
            }
        }
        checkForUpdates(force = true)
    }

    fun refreshInstallPermission() {
        if (gate.sandboxed(updateTool)) return
        updateManager.refreshInstallPermission()
    }

    fun openInstallSettings(context: android.content.Context) {
        if (gate.sandboxed(updateTool)) return
        updateManager.openInstallSettings(context)
    }

    fun skipVersion(version: String) {
        if (gate.sandboxed(updateTool)) {
            sandboxState.value = null
            return
        }
        updateManager.skipVersion(version)
    }

    fun dismissInstalled() {
        if (gate.sandboxed(updateTool)) {
            sandboxState.value = null
            return
        }
        updateManager.dismissInstalled()
    }

    fun setPreferredAbi(abi: String) {
        viewModelScope.launch { settingsRepository.setPreferredAbi(abi) }
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoUpdateEnabled(enabled) }
    }

    fun getDeviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    /** DEV-SPOOF: fixture release the fake lifecycle stages around. */
    private fun fakeUpdate(): PendingUpdate = PendingUpdate(
        version = "9.9.9-fixture",
        versionCode = 999999,
        changelog = "## Fixture release notes\n" +
            "- Showcase build — no APK is downloaded.\n" +
            "- Developer Mode console refresh.\n" +
            "- Cleaner, vault and news sandboxes.",
        downloadUrl = "https://fake.local/toolz/fixture.apk",
        sizeBytes = 46_600_000L,
        sha256 = null,
        isCritical = false,
        abi = "universal (fixture)",
        source = "fixture",
    )
}
