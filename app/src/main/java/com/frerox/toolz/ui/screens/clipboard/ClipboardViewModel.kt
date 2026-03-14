package com.frerox.toolz.ui.screens.clipboard

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.service.ClipboardService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ClipboardGroup(
    val label: String,
    val entries: List<ClipboardEntry>
)

@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val application: Application,
    private val clipboardDao: ClipboardDao,
    val classifier: ClipboardClassifier
) : AndroidViewModel(application) {

    val entries: StateFlow<List<ClipboardEntry>> = clipboardDao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun groupedEntries(allEntries: List<ClipboardEntry>): List<ClipboardGroup> {
        val pinned = allEntries.filter { it.isPinned }
        val unpinned = allEntries.filter { !it.isPinned }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val yesterday = today - 24 * 60 * 60 * 1000L

        val todayItems = unpinned.filter { it.timestamp >= today }
        val yesterdayItems = unpinned.filter { it.timestamp in yesterday until today }
        val olderItems = unpinned.filter { it.timestamp < yesterday }

        return buildList {
            if (pinned.isNotEmpty()) add(ClipboardGroup("📌 Pinned", pinned))
            if (todayItems.isNotEmpty()) add(ClipboardGroup("Today", todayItems))
            if (yesterdayItems.isNotEmpty()) add(ClipboardGroup("Yesterday", yesterdayItems))
            if (olderItems.isNotEmpty()) add(ClipboardGroup("Older", olderItems))
        }
    }

    fun deleteEntry(entry: ClipboardEntry) {
        viewModelScope.launch { clipboardDao.delete(entry) }
    }

    fun togglePin(id: Int) {
        viewModelScope.launch { clipboardDao.togglePin(id) }
    }

    fun clearAll() {
        viewModelScope.launch { clipboardDao.clearAllUnpinned() }
    }

    fun startService() {
        val intent = Intent(application, ClipboardService::class.java)
        application.startForegroundService(intent)
        _isServiceRunning.value = true
    }

    fun stopService() {
        val intent = Intent(application, ClipboardService::class.java)
        application.stopService(intent)
        _isServiceRunning.value = false
    }
}
