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

package com.frerox.toolz.ui.screens.notifications
import com.frerox.toolz.R

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.notifications.NotificationEntry
import com.frerox.toolz.data.notifications.NotificationRepository
import com.frerox.toolz.data.settings.SettingsRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AppDetails(
    val appName: String,
    val packageName: String,
    val totalNotifications: Int,
    val lastNotification: NotificationEntry?
)

@HiltViewModel
class NotificationVaultViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val settingsRepository: SettingsRepository,
    private val moshi: Moshi,
    private val shizukuExecutor: com.frerox.toolz.util.shizuku.ShizukuShellExecutor,
    private val gate: com.frerox.toolz.data.developer.SpoofGate
) : ViewModel() {

    // DEV-SPOOF: the vault demo swap — a RAM backlog replaces the Room feed,
    // Shizuku is faked authorized and listener grants never run.
    private val NOTIFICATIONS = com.frerox.toolz.data.developer.DevToolId.NOTIFICATIONS
    private val gateActive = gate.active(NOTIFICATIONS)

    private val _shizukuAuthorized = MutableStateFlow(com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized())
    val shizukuAuthorized: StateFlow<Boolean> = _shizukuAuthorized
        .combine(gateActive) { granted, demo -> demo || granted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val fixtureHidden = com.frerox.toolz.data.developer.FakeNotificationSandbox.fixtureHidden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet<String>())

    init {
        // DEV-SPOOF: fresh backlog per demo arm; fixtures always RAM-only.
        viewModelScope.launch {
            com.frerox.toolz.data.developer.FakeNotificationSandbox.reset()
            gateActive.collect { demo ->
                if (demo) {
                    com.frerox.toolz.data.developer.FakeNotificationSandbox.arm()
                    android.util.Log.w("DevSpoof", "Notification vault sandbox on — RAM backlog only")
                } else {
                    com.frerox.toolz.data.developer.FakeNotificationSandbox.reset()
                }
            }
        }
        // Grant notification listener permission immediately if Shizuku is already authorized.
        // This avoids requiring a navigate-away-and-back to trigger ON_RESUME.
        if (com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()) {
            grantNotificationPermissionWithShizuku()
        }
    }

    fun refreshShizukuStatus() {
        // DEV-SPOOF: demo reports Shizuku authorized without any grant.
        if (gate.sandboxed(NOTIFICATIONS)) {
            _shizukuAuthorized.value = true
            return
        }
        val authorized = com.frerox.toolz.util.shizuku.ShizukuHelper.isAuthorized()
        _shizukuAuthorized.value = authorized
        if (authorized) {
            grantNotificationPermissionWithShizuku()
        }
    }

    private fun grantNotificationPermissionWithShizuku() {
        // DEV-SPOOF: no `cmd notification allow_listener` shells in demo mode.
        if (gate.sandboxed(NOTIFICATIONS)) return
        viewModelScope.launch {
            try {
                val command = "cmd notification allow_listener com.frerox.toolz/com.frerox.toolz.service.NotificationVaultService"
                shizukuExecutor.executeSingle(command)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _selectedDateFilter = MutableStateFlow("Anytime")
    val selectedDateFilter = _selectedDateFilter.asStateFlow()

    val hiddenApps: StateFlow<Set<String>> = combine(
        settingsRepository.hiddenNotificationApps,
        gateActive,
        fixtureHidden,
    ) { real, demo, demoSet ->
        if (demo) demoSet else real
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val appMappings = settingsRepository.appCategoryMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val categories = combine(
        settingsRepository.customNotificationCategories,
        appMappings
    ) { custom, mappings ->
        val allMappedCategories = mappings.values.toSet()
        (custom + allMappedCategories + "General")
            .toList()
            .filter { it != "All" }
            .sorted()
            .let { listOf("All") + it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Social", "Finance", "Work", "General"))

    val distinctPackages: StateFlow<List<String>> = combine(
        repository.getDistinctPackages(),
        gateActive,
        com.frerox.toolz.data.developer.FakeNotificationSandbox.fixtureNotifications,
    ) { real, demo, fixture ->
        // DEV-SPOOF: fixture-only package list while demo is armed.
        if (demo) fixture.distinctBy { it.packageName }.map { it.packageName }
        else real
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // DEV-SPOOF: RAM backlog swaps in at the source so every filter/
    // search/category date logic below runs on the same shapes.
    private val sourceNotifications: Flow<List<NotificationEntry>> =
        combine(repository.allNotifications, com.frerox.toolz.data.developer.FakeNotificationSandbox.fixtureNotifications, gateActive) { real, fixture, demo ->
            if (demo) fixture else real
        }

    val notifications: StateFlow<List<NotificationEntry>> = combine(
        sourceNotifications,
        _searchQuery,
        _selectedCategory,
        hiddenApps,
        selectedDateFilter
    ) { list, query, category, hidden, dateFilter ->
        val now = System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L

        list.filter {
            !hidden.contains(it.packageName) &&
            (category == "All" || it.category == category) &&
            (query.isEmpty() || it.appName.contains(query, ignoreCase = true) ||
             (it.title?.contains(query, ignoreCase = true) == true) ||
             (it.text?.contains(query, ignoreCase = true) == true)) &&
            when(dateFilter) {
                "Today" -> it.timestamp >= now - dayMillis
                "Yesterday" -> it.timestamp >= now - 2 * dayMillis && it.timestamp < now - dayMillis
                "Last 7 Days" -> it.timestamp >= now - 7 * dayMillis
                else -> true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setDateFilter(filter: String) {
        _selectedDateFilter.value = filter
    }

    fun deleteNotification(id: Long) {
        // DEV-SPOOF: RAM delete while demo is armed.
        if (gate.sandboxed(NOTIFICATIONS)) {
            com.frerox.toolz.data.developer.FakeNotificationSandbox.delete(id)
            return
        }
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAll() {
        if (gate.sandboxed(NOTIFICATIONS)) {
            com.frerox.toolz.data.developer.FakeNotificationSandbox.clearAll()
            return
        }
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun hideApp(packageName: String) {
        if (gate.sandboxed(NOTIFICATIONS)) {
            com.frerox.toolz.data.developer.FakeNotificationSandbox.hideApp(packageName)
            return
        }
        viewModelScope.launch {
            settingsRepository.addHiddenNotificationApp(packageName)
        }
    }

    fun unhideApp(packageName: String) {
        if (gate.sandboxed(NOTIFICATIONS)) {
            com.frerox.toolz.data.developer.FakeNotificationSandbox.unhideApp(packageName)
            return
        }
        viewModelScope.launch {
            settingsRepository.removeHiddenNotificationApp(packageName)
        }
    }

    suspend fun getAppDetails(packageName: String): AppDetails {
        // DEV-SPOOF: counts/last-notif answer from the fixture.
        if (gate.sandboxed(NOTIFICATIONS)) {
            val fake = com.frerox.toolz.data.developer.FakeNotificationSandbox
            val last = fake.lastForPackage(packageName)
            return AppDetails(last?.appName ?: packageName, packageName, fake.countForPackage(packageName), last)
        }
        val count = repository.getNotificationCountForPackage(packageName)
        val last = repository.getLastNotificationForPackage(packageName)
        val appName = last?.appName ?: packageName
        return AppDetails(appName, packageName, count, last)
    }

    fun addCategory(category: String) {
        viewModelScope.launch {
            val current = categories.value.toSet()
            settingsRepository.setNotificationCategories(current + category)
        }
    }

    fun removeCategory(category: String) {
        if (category == "All" || category == "General") return
        viewModelScope.launch {
            val current = categories.value.toSet()
            settingsRepository.setNotificationCategories(current - category)
            if (_selectedCategory.value == category) {
                _selectedCategory.value = "All"
            }
        }
    }

    fun mapAppToCategory(packageName: String, category: String) {
        viewModelScope.launch {
            settingsRepository.setAppCategoryMapping(packageName, category)
        }
    }

    fun exportLogs(context: android.content.Context, format: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = notifications.value
                val content = if (format == "JSON") {
                    val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, NotificationEntry::class.java)
                    val adapter = moshi.adapter<List<NotificationEntry>>(listType)
                    adapter.toJson(data)
                } else {
                    data.joinToString("\n\n") { 
                        "[${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.timestamp))}] " +
                        "${it.appName}: ${it.title} - ${it.text}"
                    }
                }

                val fileName = "toolz_notifications_${System.currentTimeMillis()}.${format.lowercase()}"
                val file = File(File(context.getExternalFilesDir(null), "Exports").apply { mkdirs() }, fileName)
                file.writeText(content)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.toast_logs_exported, file.name), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
