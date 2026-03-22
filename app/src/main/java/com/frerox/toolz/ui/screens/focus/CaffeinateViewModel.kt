package com.frerox.toolz.ui.screens.focus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.focus.CaffeinateApp
import com.frerox.toolz.data.focus.CaffeinateRepository
import com.frerox.toolz.service.CaffeinateService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaffeinateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CaffeinateRepository
) : ViewModel() {

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    val allApps = repository.allApps.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
        viewModelScope.launch {
            allApps.collect { apps ->
                if (apps.isEmpty()) {
                    repository.refreshAppsWithAi()
                }
            }
        }
    }

    fun checkServiceStatus() {
        _isServiceRunning.value = isServiceRunning(CaffeinateService::class.java)
        checkNotificationPermission()
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
        if (_isServiceRunning.value) {
            val intent = Intent(context, CaffeinateService::class.java).apply {
                action = CaffeinateService.ACTION_STOP
            }
            context.stopService(intent)
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
            // Give it a moment to start/stop
            kotlinx.coroutines.delay(500)
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

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
