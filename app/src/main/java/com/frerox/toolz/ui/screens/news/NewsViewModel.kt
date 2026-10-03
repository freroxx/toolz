package com.frerox.toolz.ui.screens.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.news.NewsEntity
import com.frerox.toolz.data.news.NewsRepository
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    val settingsRepository: SettingsRepository
) : ViewModel() {

    val newsEnabled = settingsRepository.newsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val newsNotificationsEnabled = settingsRepository.newsNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _popup = MutableStateFlow<NewsEntity?>(null)
    val popup: StateFlow<NewsEntity?> = _popup.asStateFlow()

    private val _popupVisible = MutableStateFlow(false)
    val popupVisible: StateFlow<Boolean> = _popupVisible.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _history = MutableStateFlow<List<NewsEntity>>( emptyList())
    val history: StateFlow<List<NewsEntity>> = _history.asStateFlow()

    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()

    private val _historyEnd = MutableStateFlow(false)
    val historyEnd: StateFlow<Boolean> = _historyEnd.asStateFlow()

    val seenIds: StateFlow<Set<String>> = settingsRepository.newsSeenIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var sessionPopupShown = false
    private var popupJob: Job? = null
    private var historyPage = 0

    fun toggleNews(enabled: Boolean) {
        viewModelScope.launch {
            try { settingsRepository.setNewsEnabled(enabled) } catch (_: Exception) { }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            try { settingsRepository.setNewsNotificationsEnabled(enabled) } catch (_: Exception) { }
        }
    }

    /** Dashboard/foreground entry: evaluate candidate and show after its delaySeconds. */
    fun evaluatePopup() {
        if (sessionPopupShown) return
        popupJob?.cancel()
        popupJob = viewModelScope.launch {
            try {
                val candidate = newsRepository.popupCandidate() ?: return@launch
                if (candidate.delaySeconds > 0) delay(candidate.delaySeconds * 1000L)
                if (sessionPopupShown) return@launch
                val fresh = newsRepository.popupCandidate()
                if (fresh?.id != candidate.id) return@launch
                _popup.value = candidate
                _popupVisible.value = true
                sessionPopupShown = true
                newsRepository.markShown(candidate.id)
                refreshUnread()
            } catch (_: Exception) { }
        }
    }

    fun onPopupAction() {
        _popupVisible.value = false
    }

    fun onPopupLater() {
        viewModelScope.launch {
            try {
                _popup.value?.id?.let { newsRepository.snooze24h(it) }
            } catch (_: Exception) { }
            _popupVisible.value = false
        }
    }

    fun onPopupDismiss() {
        viewModelScope.launch {
            try {
                _popup.value?.id?.let { newsRepository.dismiss(it) }
            } catch (_: Exception) { }
            _popupVisible.value = false
            refreshUnread()
        }
    }

    fun refreshUnread() {
        viewModelScope.launch {
            try { _unreadCount.value = newsRepository.unreadCount() } catch (_: Exception) { }
        }
    }

    fun loadHistory(force: Boolean = false) {
        viewModelScope.launch {
            try {
                if (force) {
                    newsRepository.syncIfStale(force = true)
                    historyPage = 0
                    _historyEnd.value = false
                }
                _historyLoading.value = true
                val items = newsRepository.historyPage(historyPage)
                _history.value = if (historyPage == 0) items else _history.value + items
                if (items.size < NewsRepository.HISTORY_PAGE_SIZE) _historyEnd.value = true
                else historyPage++
                val seen = _history.value.map { it.id }
                newsRepository.markSeen(seen)
                refreshUnread()
            } catch (_: Exception) {
            } finally {
                _historyLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_historyLoading.value || _historyEnd.value) return
        viewModelScope.launch {
            try {
                _historyLoading.value = true
                // historyPage always points at the next not-yet-loaded page:
                // loadHistory() loads page N then advances to N+1.
                val items = newsRepository.historyPage(historyPage)
                if (items.isEmpty() || items.size < NewsRepository.HISTORY_PAGE_SIZE) _historyEnd.value = true
                if (items.isNotEmpty()) {
                    historyPage++
                    _history.value = _history.value + items
                    newsRepository.markSeen(items.map { it.id })
                    refreshUnread()
                }
            } catch (_: Exception) {
            } finally {
                _historyLoading.value = false
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            try {
                newsRepository.syncIfStale(force = true)
                refreshUnread()
            } catch (_: Exception) { }
        }
    }

    fun refreshNow() {
        historyPage = 0
        _historyEnd.value = false
        loadHistory(force = true)
    }
}
