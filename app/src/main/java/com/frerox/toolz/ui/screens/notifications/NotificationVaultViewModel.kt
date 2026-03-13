package com.frerox.toolz.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.notifications.NotificationEntry
import com.frerox.toolz.data.notifications.NotificationRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    val hiddenApps = settingsRepository.hiddenNotificationApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val categories = settingsRepository.customNotificationCategories
        .map { it.toList().sortedBy { c -> if (c == "All") "" else c } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All", "Social", "Finance", "Work", "General"))

    val appMappings = settingsRepository.appCategoryMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val distinctPackages = repository.getDistinctPackages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationEntry>> = combine(
        repository.allNotifications,
        _searchQuery,
        _selectedCategory,
        hiddenApps
    ) { list, query, category, hidden ->
        list.filter { 
            !hidden.contains(it.packageName) &&
            (category == "All" || it.category == category) &&
            (query.isEmpty() || it.appName.contains(query, ignoreCase = true) || 
             (it.title?.contains(query, ignoreCase = true) == true) || 
             (it.text?.contains(query, ignoreCase = true) == true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun hideApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.addHiddenNotificationApp(packageName)
        }
    }

    fun unhideApp(packageName: String) {
        viewModelScope.launch {
            settingsRepository.removeHiddenNotificationApp(packageName)
        }
    }

    suspend fun getAppDetails(packageName: String): AppDetails {
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
}
