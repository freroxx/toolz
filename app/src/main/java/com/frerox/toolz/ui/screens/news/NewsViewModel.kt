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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    val settingsRepository: SettingsRepository,
    private val gate: com.frerox.toolz.data.developer.SpoofGate
) : ViewModel() {

    // DEV-SPOOF: news demo — the feed, sync, popup, notifications and seen
    // markers all run against a RAM backlog; the network sync, Room cache and
    // system notifications are skipped entirely.
    private val UPDATE_NEWS = com.frerox.toolz.data.developer.DevToolId.UPDATE_NEWS
    private val gateActive = gate.active(UPDATE_NEWS)

    private val seenIdsDemo = com.frerox.toolz.data.developer.FakeNewsSandbox.seen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet<String>())

    val seenIds: StateFlow<Set<String>> = combine(
        settingsRepository.newsSeenIds,
        seenIdsDemo,
        gateActive,
    ) { real, fixtureSeen, demo -> if (demo) fixtureSeen else real }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val newsEnabled = settingsRepository.newsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val newsNotificationsEnabled = settingsRepository.newsNotificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Epoch of the last successful sync — powers the honest "Updated X ago" stamp. */
    val lastSync = settingsRepository.newsLastSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

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

    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /**
     * Full-history search results over every cached item; `null` while search
     * is inactive (query empty AND filter "all"). Non-null results replace the
     * paged history in the UI so search never silently hides older items.
     */
    private val _searchResults = MutableStateFlow<List<NewsEntity>?>(null)
    val searchResults: StateFlow<List<NewsEntity>?> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null

    private var sessionPopupShown = false
    private var popupJob: Job? = null
    private var historyPage = 0

    // Seen-on-visible batching: cards report visibility; the write is
    // coalesced so scrolling never thrashes the DataStore JSON setter.
    private val pendingSeen = mutableSetOf<String>()
    private var seenJob: Job? = null

    init {
        // DEV-SPOOF: fresh backlog per demo arm; RAM only, nothing persisted.
        viewModelScope.launch {
            com.frerox.toolz.data.developer.FakeNewsSandbox.reset()
            gateActive.collect { demo ->
                if (demo) {
                    com.frerox.toolz.data.developer.FakeNewsSandbox.arm()
                    android.util.Log.w("DevSpoof", "News sandbox on — RAM editorial backlog")
                } else {
                    com.frerox.toolz.data.developer.FakeNewsSandbox.reset()
                }
                _unreadCount.value = if (demo) com.frerox.toolz.data.developer.FakeNewsSandbox.unreadCount() else 0
                _searchResults.value = null
            }
        }
    }

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

    /** Dashboard/foreground entry: show the eligible candidate immediately.
     * 1-per-session for normal items, but a critical candidate always replaces
     * a shown non-critical one (never drop outages behind a promo). */
    fun evaluatePopup() {
        popupJob?.cancel()
        popupJob = viewModelScope.launch {
            try {
                // DEV-SPOOF: the fixture's pinned item is the popup candidate;
                // nothing is marked shown in the repo.
                if (gate.sandboxed(UPDATE_NEWS)) {
                    val candidate = com.frerox.toolz.data.developer.FakeNewsSandbox.all()
                        .firstOrNull { it.pinned }
                        ?: com.frerox.toolz.data.developer.FakeNewsSandbox.all().firstOrNull()
                        ?: return@launch
                    _popup.value = candidate
                    _popupVisible.value = true
                    sessionPopupShown = true
                    refreshUnread()
                    return@launch
                }
                // No foreground delay: news shows as soon as it reaches the
                // user, dashboard-only (this host lives in DashboardScreen).
                val candidate = newsRepository.popupCandidate() ?: return@launch
                val shown = _popup.value
                if (shown != null && !NewsRepository.shouldReplacePopup(shown, candidate)) return@launch
                if (shown == null && sessionPopupShown && candidate.priority != "critical") return@launch
                if (shown != null && shown.id == candidate.id && _popupVisible.value) return@launch
                _popup.value = candidate
                _popupVisible.value = true
                sessionPopupShown = true
                newsRepository.markShown(candidate.id)
                refreshUnread()
            } catch (_: Exception) { }
        }
    }

    /** Foreground arrival check: sync, then immediately notify anything new,
     * and re-evaluate the popup so critically-new items surface mid-session
     * even while the user stays parked on the dashboard. */
    fun checkNotifications() {
        viewModelScope.launch {
            try {
                // DEV-SPOOF: no sync, no posted notifications in demo mode.
                if (!gate.sandboxed(UPDATE_NEWS)) {
                    newsRepository.syncAndNotify()
                }
                refreshUnread()
                evaluatePopup()
            } catch (_: Exception) { }
        }
    }

    fun onPopupAction() {
        _popupVisible.value = false
    }

    fun onPopupLater() {
        // DEV-SPOOF: no snooze persistence in demo mode.
        if (gate.sandboxed(UPDATE_NEWS)) {
            _popupVisible.value = false
            return
        }
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
                // DEV-SPOOF: dismissal is RAM-only (no impression bookkeeping).
                if (!gate.sandboxed(UPDATE_NEWS)) {
                    _popup.value?.id?.let { newsRepository.dismiss(it) }
                }
                _popupVisible.value = false
                refreshUnread()
            } catch (_: Exception) { }
        }
    }

    fun refreshUnread() {
        // DEV-SPOOF: unread = fixture minus RAM seen.
        if (gate.sandboxed(UPDATE_NEWS)) {
            _unreadCount.value = com.frerox.toolz.data.developer.FakeNewsSandbox.unreadCount()
            return
        }
        viewModelScope.launch {
            try { _unreadCount.value = newsRepository.unreadCount() } catch (_: Exception) { }
        }
    }

    /**
     * Seen semantics live at the viewport, not the page load: cards report
     * visibility, batched forces are coalesced (400ms) and unread dots fade
     * out only for content the user actually scrolled past.
     */
    fun markSeenOnVisible(ids: Collection<String>) {
        if (ids.isEmpty()) return
        // DEV-SPOOF: seen tracking in RAM — the DataStore setter and real
        // unread state are left alone.
        if (gate.sandboxed(UPDATE_NEWS)) {
            com.frerox.toolz.data.developer.FakeNewsSandbox.markSeen(ids)
            refreshUnread()
            return
        }
        pendingSeen.addAll(ids)
        seenJob?.cancel()
        seenJob = viewModelScope.launch {
            delay(400)
            val batch = pendingSeen.toList()
            pendingSeen.clear()
            if (batch.isEmpty()) return@launch
            try {
                newsRepository.markSeen(batch)
                refreshUnread()
            } catch (_: Exception) { }
        }
    }

    /**
     * Search runs against the full cached history regardless of how many pages
     * were loaded, so results are honest; priority filter applies on top.
     */
    fun requestSearch(query: String, priority: String) {
        val trimmed = query.trim()
        val active = trimmed.isNotEmpty() || priority != "all"
        if (!active) {
            searchJob?.cancel()
            _searchResults.value = null
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(180) // coalesce typing
            _searching.value = true
            try {
                // DEV-SPOOF: search the RAM backlog in demo mode.
                if (gate.sandboxed(UPDATE_NEWS)) {
                    _searchResults.value =
                        com.frerox.toolz.data.developer.FakeNewsSandbox.search(trimmed, priority)
                    return@launch
                }
                val base = newsRepository.searchHistory(trimmed)
                _searchResults.value =
                    if (priority == "all") base else base.filter { it.priority == priority }
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _searching.value = false
            }
        }
    }

    fun onScreenEntered() {
        if (_history.value.isEmpty()) {
            loadHistory(force = false)
        } else {
            refreshUnread()
        }
        if (!gate.sandboxed(UPDATE_NEWS)) {
            viewModelScope.launch {
                try {
                    val changed = newsRepository.syncIfChanged()
                    if (changed) {
                        historyPage = 0
                        _historyEnd.value = false
                        val items = newsRepository.historyPage(0)
                        _history.value = items
                        if (items.size < NewsRepository.HISTORY_PAGE_SIZE) _historyEnd.value = true
                        else historyPage = 1
                        refreshUnread()
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun loadHistory(force: Boolean = false) {
        viewModelScope.launch {
            try {
                // DEV-SPOOF: page the RAM backlog, no network, no Room.
                if (gate.sandboxed(UPDATE_NEWS)) {
                    _historyError.value = null
                    if (force) { _syncing.value = true; delay(500); _syncing.value = false }
                    historyPage = 0
                    _historyEnd.value = false
                    _historyLoading.value = true
                    delay(250)
                    _history.value = com.frerox.toolz.data.developer.FakeNewsSandbox.all()
                        .take(NewsRepository.HISTORY_PAGE_SIZE)
                    if (com.frerox.toolz.data.developer.FakeNewsSandbox.all().size <= NewsRepository.HISTORY_PAGE_SIZE) {
                        _historyEnd.value = true
                    } else {
                        historyPage++
                    }
                    refreshUnread()
                    return@launch
                }
                _historyError.value = null
                if (force) {
                    _syncing.value = true
                    val ok = newsRepository.syncIfStale(force = true)
                    _syncing.value = false
                    if (!ok) _historyError.value = "Sync failed — showing cached news."
                    historyPage = 0
                    _historyEnd.value = false
                }
                _historyLoading.value = true
                val items = newsRepository.historyPage(historyPage)
                _history.value = if (historyPage == 0) items else _history.value + items
                if (items.size < NewsRepository.HISTORY_PAGE_SIZE) _historyEnd.value = true
                else historyPage++
                refreshUnread()
            } catch (_: Exception) {
                _historyError.value = "Couldn't load news."
            } finally {
                _historyLoading.value = false
                _syncing.value = false
            }
        }
    }

    fun loadMore() {
        if (_historyLoading.value || _historyEnd.value) return
        viewModelScope.launch {
            try {
                // DEV-SPOOF: RAM backlog pagination ends after page one.
                if (gate.sandboxed(UPDATE_NEWS)) {
                    _historyLoading.value = true
                    delay(250)
                    _historyLoading.value = false
                    _historyEnd.value = true
                    return@launch
                }
                _historyLoading.value = true
                // historyPage always points at the next not-yet-loaded page:
                // loadHistory() loads page N then advances to N+1.
                val items = newsRepository.historyPage(historyPage)
                if (items.isEmpty() || items.size < NewsRepository.HISTORY_PAGE_SIZE) _historyEnd.value = true
                if (items.isNotEmpty()) {
                    historyPage++
                    _history.value = _history.value + items
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
                _syncing.value = true
                // DEV-SPOOF: refresh repages the RAM backlog.
                if (gate.sandboxed(UPDATE_NEWS)) {
                    delay(500)
                    _history.value = com.frerox.toolz.data.developer.FakeNewsSandbox.all()
                        .take(NewsRepository.HISTORY_PAGE_SIZE)
                    refreshUnread()
                    return@launch
                }
                newsRepository.syncIfStale(force = true)
                refreshUnread()
            } catch (_: Exception) { } finally {
                _syncing.value = false
            }
        }
    }

    fun dismissAllNotifications() {
        viewModelScope.launch {
            try { newsRepository.cancelAllNotifications() } catch (_: Exception) { }
        }
    }

    fun refreshNow() {
        historyPage = 0
        _historyEnd.value = false
        loadHistory(force = true)
    }
}
