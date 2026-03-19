package com.frerox.toolz.ui.screens.clipboard

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.ChatRepository
import com.frerox.toolz.data.clipboard.ClipboardClassifier
import com.frerox.toolz.data.clipboard.ClipboardDao
import com.frerox.toolz.data.clipboard.ClipboardEntry
import com.frerox.toolz.service.ClipboardService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.ClipboardManager as AndroidClipboardManager
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
    private val aiRepository: ChatRepository,
    val classifier: ClipboardClassifier
) : AndroidViewModel(application) {

    val entries: StateFlow<List<ClipboardEntry>> = clipboardDao.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSummarizing = MutableStateFlow<Int?>(null)
    val isSummarizing = _isSummarizing.asStateFlow()

    init {
        // Always start the clipboard service automatically
        ensureServiceRunning()
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
                    Classify this clipboard content into one of these categories: 
                    TEXT, URL, PHONE, EMAIL, MATHS, PERSONAL, CODE, ADDRESS, CRYPTO, TODO.
                    Also provide a very short, punchy 1-sentence summary (max 15 words).
                    
                    Content: ${entry.content.take(1500)}
                    
                    Respond in JSON format: {"category": "CATEGORY", "summary": "Short summary"}
                """.trimIndent()

                aiRepository.getChatResponse(prompt, emptyList(), null).collect { result ->
                    result.onSuccess { response ->
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

    fun refreshClipboard() {
        viewModelScope.launch {
            try {
                val cm = application.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(application).toString()
                    if (text.isNotBlank()) {
                        val latest = clipboardDao.getLatestEntry()
                        if (latest?.content != text) {
                            val type = classifier.classify(text)
                            val entry = ClipboardEntry(
                                content = text,
                                timestamp = System.currentTimeMillis(),
                                type = type
                            )
                            clipboardDao.insert(entry)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
