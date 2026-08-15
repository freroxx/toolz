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

package com.frerox.toolz.ui.screens.clipboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.ClipboardService
import com.frerox.toolz.ToolzApplication
import com.frerox.toolz.util.ConnectivityObserver
import com.frerox.toolz.util.NetworkConnectivityObserver
import com.frerox.toolz.util.shizuku.ShizukuHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.ClipboardManager as AndroidClipboardManager
import java.util.*
import javax.inject.Inject

data class ClipboardGroup(
    val label: String,
    val entries: List<ClipboardEntry>
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ClipboardViewModel @Inject constructor(
    private val application: Application,
    private val clipboardDao: ClipboardDao,
    private val aiRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val shizukuExecutor: com.frerox.toolz.util.shizuku.ShizukuShellExecutor,
    val classifier: ClipboardClassifier
) : AndroidViewModel(application) {

    private val connectivityObserver = NetworkConnectivityObserver(application)
    private val networkStatus = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectivityObserver.Status.Unavailable)

    val entries: StateFlow<List<ClipboardEntry>> = clipboardDao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offlineModeEnabled = settingsRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _shizukuAuthorized = MutableStateFlow(ShizukuHelper.isAuthorized())
    val shizukuAuthorized = _shizukuAuthorized.asStateFlow()

    private val _isSummarizing = MutableStateFlow<Int?>(null)
    val isSummarizing = _isSummarizing.asStateFlow()

    private val _isAiSearching = MutableStateFlow(false)
    val isAiSearching = _isAiSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _aiSearchResults = MutableStateFlow<List<Int>?>(null)
    val aiSearchResults = _aiSearchResults.asStateFlow()

    val filteredEntries = combine(entries, _searchQuery, _aiSearchResults) { allEntries, query, aiResults ->
        if (query.isBlank()) {
            allEntries
        } else if (aiResults != null && aiResults.isNotEmpty()) {
            val aiMapped = aiResults.mapNotNull { id -> allEntries.find { it.id == id } }
            val remainingDirectMatches = allEntries.filter { 
                it.id !in aiResults && (it.content.contains(query, ignoreCase = true) || it.summary?.contains(query, ignoreCase = true) == true)
            }
            aiMapped + remainingDirectMatches
        } else {
            allEntries.filter { it.content.contains(query, ignoreCase = true) || it.summary?.contains(query, ignoreCase = true) == true }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        ensureServiceRunning()
        setupSearchDebounce()
    }

    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(1000)
                .filter { it.length > 2 }
                .collect { query ->
                    performAiSearch(query)
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _aiSearchResults.value = null
        }
    }

    private fun isWifiAvailable(): Boolean {
        val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isAppInForeground(): Boolean {
        return ToolzApplication.isFocused.value
    }

    private fun performAiSearch(query: String) {
        if (!isWifiAvailable()) return

        viewModelScope.launch {
            if (settingsRepository.offlineModeEnabled.first()) return@launch
            if (!settingsRepository.aiSearchEnabled.first()) return@launch
            if (!settingsRepository.aiClipboardMonitoringEnabled.first()) return@launch

            val currentEntries = entries.value
            if (currentEntries.isEmpty()) return@launch

            _isAiSearching.value = true
            try {
                // Semantic context: Include both summary and category for better matching
                val contextData = currentEntries.take(40).joinToString("\n") { 
                    "[ID:${it.id}] Category: ${it.type}, Content: ${it.summary ?: it.content.take(120)}" 
                }
                
                val prompt = """
                    You are a semantic clipboard search engine. 
                    Given a user query and clipboard items, return the IDs of items that match the context or intent.
                    Query: "$query"
                    
                    Items:
                    $contextData
                    
                    Respond ONLY with a comma-separated list of IDs (max 8) that are most relevant. 
                    If no good match, respond with "NONE".
                """.trimIndent()

                aiRepository.getChatResponse(prompt, emptyList(), null).collect { result ->
                    result.onSuccess { chunk ->
                        val response = chunk.text
                        if (response.trim().uppercase() != "NONE") {
                            val ids = response.split(",")
                                .mapNotNull { it.trim().filter { char -> char.isDigit() }.toIntOrNull() }
                            _aiSearchResults.value = ids
                        } else {
                            _aiSearchResults.value = emptyList()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAiSearching.value = false
            }
        }
    }

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

    fun summarizeEntry(entry: ClipboardEntry) {
        viewModelScope.launch {
            _isSummarizing.value = entry.id
            try {
                val prompt = """
                    Classify this clipboard content and provide a punchy 1-sentence summary (max 15 words).
                    You can use standard categories (TEXT, URL, SOCIAL, PHONE, OTP, EMAIL, MATHS, PERSONAL, CODE, ADDRESS, CRYPTO, TODO, RECIPE, FLIGHT, EVENT, QUOTE) 
                    or CREATE A NEW ONE if it fits better.
                    Keep category names uppercase and single-word.
                    
                    Content: ${entry.content.take(1500)}
                    
                    Respond in JSON format: {"category": "CATEGORY_NAME", "summary": "Short summary"}
                """.trimIndent()

                aiRepository.getChatResponse(prompt, emptyList(), null, "openai/gpt-oss-20b").collect { result ->
                    result.onSuccess { chunk ->
                        val response = chunk.text
                        val category = Regex("\"category\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1) ?: entry.type
                        val summary = Regex("\"summary\":\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1) ?: ""
                        clipboardDao.updateAiDetails(entry.id, summary, category)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSummarizing.value = null
            }
        }
    }

    fun categorizeAllWithAi() {
        viewModelScope.launch {
            val unproccessed = entries.value.filter { !it.isAiProcessed }.take(10)
            if (unproccessed.isEmpty()) {
                refreshClipboard()
                return@launch
            }

            unproccessed.forEach { entry ->
                summarizeEntry(entry)
            }
        }
    }

    fun refreshClipboard() {
        _shizukuAuthorized.value = ShizukuHelper.isAuthorized()
        viewModelScope.launch {
            try {
                // Primary: Try Shizuku if available (even if authorized just now)
                if (ShizukuHelper.isAuthorized()) {
                    val text = shizukuExecutor.getClipboardText()
                    if (text != null) {
                        processClipboardTextLocally(text)
                    }
                    return@launch
                }

                // Fallback: Standard ClipboardManager (Only if foreground)
                if (isAppInForeground()) {
                    try {
                        val cm = application.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
                        val clip = cm.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).coerceToText(application).toString()
                            processClipboardTextLocally(text)
                        }
                    } catch (e: Exception) {
                        // Silent catch for denial errors
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun processClipboardTextLocally(text: String) {
        if (text.isNotBlank()) {
            val latest = clipboardDao.getLatestEntry()
            if (latest?.content != text) {
                val type = classifier.classify(text)
                val entry = ClipboardEntry(
                    content = text,
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    isAiProcessed = false
                )
                clipboardDao.insert(entry)
            }
        }
    }

    private fun ensureServiceRunning() {
        try {
            val intent = Intent(application, ClipboardService::class.java)
            application.startForegroundService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
