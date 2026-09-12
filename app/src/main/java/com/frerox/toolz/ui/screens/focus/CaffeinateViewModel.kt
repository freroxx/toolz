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

package com.frerox.toolz.ui.screens.focus

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.focus.CaffeinateRepository
import com.frerox.toolz.data.focus.InstalledAppInfo
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.CaffeinateService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaffeinateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CaffeinateRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _isServiceRunning = MutableStateFlow(CaffeinateService.isRunning)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    val isAutoRunning: StateFlow<Boolean> = CaffeinateService.isAutoRunningFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val elapsedTime: StateFlow<Long> = CaffeinateService.elapsedTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(true)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    val caffeinateNotificationsEnabled: StateFlow<Boolean> = settingsRepository.caffeinateNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val reminderEnabled: StateFlow<Boolean> = settingsRepository.caffeinateReminderEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val reminderMins: StateFlow<Int> = settingsRepository.caffeinateReminderMins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val autoStopEnabled: StateFlow<Boolean> = settingsRepository.caffeinateAutoStopEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoStopMins: StateFlow<Int> = settingsRepository.caffeinateAutoStopMins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    val caffeinateEverything: StateFlow<Boolean> = settingsRepository.caffeinateEverything
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoPkgs: StateFlow<Set<String>> = settingsRepository.caffeinateAutoPkgs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val autoEnabledAppsCount: StateFlow<Int> = autoPkgs
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    init {
        checkServiceStatus()
        refreshAccessibilityStatus()
        // Sync Room apps to DataStore on first launch if DataStore autoPkgs is empty but Room has items
        viewModelScope.launch {
            val currentPkgs = autoPkgs.first()
            if (currentPkgs.isEmpty()) {
                val roomPkgs = repository.getAutoEnabledPackages()
                if (roomPkgs.isNotEmpty()) {
                    settingsRepository.setCaffeinateAutoPkgs(roomPkgs)
                }
            }
        }
    }

    fun checkServiceStatus() {
        _isServiceRunning.value = CaffeinateService.isRunning
        checkNotificationPermission()
        refreshAccessibilityStatus()
    }

    fun refreshAccessibilityStatus() {
        val enabled = isAccessibilityServiceEnabled(context)
        _isAccessibilityEnabled.value = enabled
        if (enabled) {
            viewModelScope.launch {
                settingsRepository.setAccessibilityBridgeWasActive(true)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)?.any {
                it.resolveInfo.serviceInfo.packageName == context.packageName
            } == true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            _hasNotificationPermission.value = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            _hasNotificationPermission.value = true
        }
    }

    fun toggleService() {
        if (CaffeinateService.isRunning) {
            stopCaffeinate()
        } else {
            startInfinite()
        }
    }

    fun startInfinite() {
        val intent = Intent(context, CaffeinateService::class.java).apply {
            action = CaffeinateService.ACTION_START_INFINITE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            _isServiceRunning.value = CaffeinateService.isRunning
        }
    }

    fun stopCaffeinate() {
        val intent = Intent(context, CaffeinateService::class.java).apply {
            action = CaffeinateService.ACTION_STOP
        }
        context.startService(intent)
        viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            _isServiceRunning.value = CaffeinateService.isRunning
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCaffeinateReminderEnabled(enabled)
        }
    }

    fun setReminderMins(mins: Int) {
        viewModelScope.launch {
            settingsRepository.setCaffeinateReminderMins(mins)
        }
    }

    fun setAutoStopEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCaffeinateAutoStopEnabled(enabled)
        }
    }

    fun setAutoStopMins(mins: Int) {
        viewModelScope.launch {
            settingsRepository.setCaffeinateAutoStopMins(mins)
        }
    }

    fun toggleEverything() {
        viewModelScope.launch {
            settingsRepository.setCaffeinateEverything(!caffeinateEverything.value)
        }
    }

    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            val current = autoPkgs.value.toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
                repository.updateAppAutoEnable(packageName, false)
            } else {
                current.add(packageName)
                repository.updateAppAutoEnable(packageName, true)
            }
            settingsRepository.setCaffeinateAutoPkgs(current)
        }
    }

    fun saveAutoPkgs(pkgs: Set<String>) {
        viewModelScope.launch {
            settingsRepository.setCaffeinateAutoPkgs(pkgs)
            repository.syncAutoPkgsToRoom(pkgs)
        }
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty() || _isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                val apps = repository.getInstalledUserApps()
                _installedApps.value = apps
            } catch (_: Exception) {
            } finally {
                _isLoadingApps.value = false
            }
        }
    }
}
