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
import com.frerox.toolz.data.focus.CaffeinateApp
import com.frerox.toolz.data.focus.CaffeinateRepository
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

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    val isAutoRunning = CaffeinateService.isAutoRunningFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val isAutoAllAppsEnabled = settingsRepository.caffeinateAutoAllApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    val elapsedTime = CaffeinateService.elapsedTimeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val allApps = repository.allApps.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val autoEnabledAppsCount = allApps.map { apps -> apps.count { it.isAutoEnabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    private val _reminderInterval = MutableStateFlow(30)
    val reminderInterval: StateFlow<Int> = _reminderInterval.asStateFlow()

    private val _isInfinite = MutableStateFlow(false)
    val isInfinite: StateFlow<Boolean> = _isInfinite.asStateFlow()

    private val _isCategorizing = MutableStateFlow(false)
    val isCategorizing: StateFlow<Boolean> = _isCategorizing.asStateFlow()

    private val _aiStatus = MutableStateFlow("")
    val aiStatus: StateFlow<String> = _aiStatus.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(true)
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    init {
        checkServiceStatus()
        refreshAccessibilityStatus()
        viewModelScope.launch {
            allApps.collect { apps ->
                if (apps.isEmpty()) {
                    repository.refreshAppsWithAi()
                }
            }
        }
        
        // Periodic check to keep UI in sync if service stops from outside
        viewModelScope.launch {
            while (true) {
                _isServiceRunning.value = CaffeinateService.isRunning
                kotlinx.coroutines.delay(2000)
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

    fun toggleService(themeColor: Int = android.graphics.Color.BLUE) {
        if (CaffeinateService.isRunning) {
            val intent = Intent(context, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_STOP
            }
            context.startService(intent)
        } else {
            val intent = Intent(context, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_START
                putExtra(CaffeinateService.EXTRA_INTERVAL, _reminderInterval.value)
                putExtra(CaffeinateService.EXTRA_INFINITE, _isInfinite.value)
                putExtra(CaffeinateService.EXTRA_COLOR, themeColor)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            checkServiceStatus()
        }
    }

    fun setReminderInterval(minutes: Int) {
        _reminderInterval.value = minutes
    }

    fun setInfinite(infinite: Boolean) {
        _isInfinite.value = infinite
    }

    fun refreshAppCategories() {
        viewModelScope.launch {
            _isCategorizing.value = true
            _aiStatus.value = "Analyzing installed apps..."
            repository.refreshAppsWithAi()
            _aiStatus.value = "Categorization complete!"
            _isCategorizing.value = false
            kotlinx.coroutines.delay(2000)
            _aiStatus.value = ""
        }
    }

    fun toggleAppAutoEnable(app: CaffeinateApp) {
        viewModelScope.launch {
            repository.updateAppAutoEnable(app, !app.isAutoEnabled)
        }
    }

    fun toggleAutoAllApps() {
        viewModelScope.launch {
            settingsRepository.setCaffeinateAutoAllApps(!isAutoAllAppsEnabled.value)
        }
    }

    fun manualAddAppToCategory(packageName: String, category: String) {
        viewModelScope.launch {
            repository.manualAddApp(packageName, category)
        }
    }
}
