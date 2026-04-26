package com.frerox.toolz.ui.screens.notifications

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
    private val moshi: Moshi
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _selectedDateFilter = MutableStateFlow("Anytime")
    val selectedDateFilter = _selectedDateFilter.asStateFlow()

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
                    Toast.makeText(context, "Logs exported to: ${file.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
