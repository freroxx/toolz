package com.frerox.toolz.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.notifications.NotificationEntry
import com.frerox.toolz.data.notifications.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationVaultViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    val notifications: StateFlow<List<NotificationEntry>> = combine(
        repository.allNotifications,
        _searchQuery,
        _selectedCategory
    ) { list, query, category ->
        list.filter { 
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
}
