package com.frerox.toolz.ui.screens.pdf

import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.pdf.PdfAnnotationDao
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.data.pdf.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PdfToolMode { NAVIGATE, HIGHLIGHTER, PEN, FORMULA_CAPTURE }
enum class NightProfile { OFF, SOLARIZED_DARK, AMOLED_BLACK }

data class PdfWorkspaceTab(
    val id: String,
    val uri: Uri,
    val title: String,
    val page: Int = 0,
    val pageCount: Int = 1,
    val zoom: Float = 1f,
    val lastTool: PdfToolMode = PdfToolMode.NAVIGATE,
    val lastOpenedAt: Long = System.currentTimeMillis()
)

data class FormulaOcrResult(
    val plainText: String,
    val latex: String,
    val confidence: Float
)

data class FormulaCaptureState(
    val isRunning: Boolean = false,
    val result: FormulaOcrResult? = null,
    val error: String? = null,
    val selectedRegion: Rect? = null
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val repository: PdfRepository,
    private val annotationDao: PdfAnnotationDao,
    private val dataStore: DataStore<Preferences>,
    private val formulaOcrProcessor: FormulaOcrProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private val _pdfFiles = MutableStateFlow<List<PdfFile>>(emptyList())
    val pdfFiles: StateFlow<List<PdfFile>> = _pdfFiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _nightProfile = MutableStateFlow(NightProfile.OFF)
    val nightProfile = _nightProfile.asStateFlow()

    private val _openTabs = MutableStateFlow<List<PdfWorkspaceTab>>(emptyList())
    val openTabs = _openTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId = _activeTabId.asStateFlow()

    private val _formulaState = MutableStateFlow(FormulaCaptureState())
    val formulaState = _formulaState.asStateFlow()

    private val _termLookup = MutableStateFlow<Map<String, Int>>(emptyMap())
    val termLookup = _termLookup.asStateFlow()

    private val _activeTab = combine(_openTabs, _activeTabId) { tabs, activeId ->
        tabs.firstOrNull { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val annotations = _activeTab.flatMapLatest { tab ->
        if (tab == null) flowOf(emptyList())
        else annotationDao.getAnnotationsForFile(tab.uri.toString())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPdfFiles()
    }

    fun loadPdfFiles() {
        viewModelScope.launch {
            _pdfFiles.value = repository.getPdfFiles()
        }
    }

    fun openPdf(uri: Uri, title: String = uri.lastPathSegment ?: "PDF") {
        val existing = _openTabs.value.firstOrNull { it.uri == uri }
        if (existing != null) {
            _activeTabId.value = existing.id
            _uiState.value = PdfUiState.Viewer(existing.uri)
            return
        }

        viewModelScope.launch {
            val restored = restoreSession(uri)
            val tab = PdfWorkspaceTab(
                id = uri.toString(),
                uri = uri,
                title = title,
                page = restored.page,
                zoom = restored.zoom,
                lastTool = restored.lastTool
            )
            _openTabs.value = _openTabs.value + tab
            _activeTabId.value = tab.id
            _uiState.value = PdfUiState.Viewer(uri)
            rebuildGlossaryIndex()
        }
    }

    fun switchTab(tabId: String) {
        val tab = _openTabs.value.firstOrNull { it.id == tabId } ?: return
        _activeTabId.value = tabId
        _uiState.value = PdfUiState.Viewer(tab.uri)
    }

    fun closeTab(tabId: String) {
        val tabs = _openTabs.value
        val removed = tabs.firstOrNull { it.id == tabId } ?: return
        persistTabSession(removed)
        val updated = tabs.filterNot { it.id == tabId }
        _openTabs.value = updated
        _activeTabId.value = updated.lastOrNull()?.id
        _uiState.value = if (updated.isEmpty()) PdfUiState.Idle else PdfUiState.Viewer(updated.last().uri)
    }

    fun updatePage(page: Int, pageCount: Int = _activeTab.value?.pageCount ?: 1) {
        updateActiveTab { it.copy(page = page.coerceAtLeast(0), pageCount = pageCount.coerceAtLeast(1)) }
    }

    fun updateZoom(zoom: Float) {
        updateActiveTab { it.copy(zoom = zoom.coerceIn(0.5f, 5f)) }
    }

    fun updateLastTool(tool: PdfToolMode) {
        updateActiveTab { it.copy(lastTool = tool) }
    }

    fun toggleNightProfile() {
        _nightProfile.value = when (_nightProfile.value) {
            NightProfile.OFF -> NightProfile.SOLARIZED_DARK
            NightProfile.SOLARIZED_DARK -> NightProfile.AMOLED_BLACK
            NightProfile.AMOLED_BLACK -> NightProfile.OFF
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startFormulaCapture(region: Rect) {
        _formulaState.value = _formulaState.value.copy(selectedRegion = region, error = null)
    }

    fun runFormulaCapture(rawText: String) {
        viewModelScope.launch {
            _formulaState.value = _formulaState.value.copy(isRunning = true, error = null)
            runCatching {
                withContext(Dispatchers.Default) {
                    formulaOcrProcessor.fromRecognizedText(rawText)
                }
            }.onSuccess { result ->
                _formulaState.value = FormulaCaptureState(isRunning = false, result = result)
            }.onFailure { e ->
                _formulaState.value = FormulaCaptureState(isRunning = false, error = e.message ?: "Failed to read formula")
            }
        }
    }

    fun clearFormulaResult() {
        _formulaState.value = FormulaCaptureState()
    }

    fun lookupTerm(term: String): Int {
        return _termLookup.value[term.lowercase()] ?: 0
    }

    fun persistActiveTabSession() {
        _activeTab.value?.let { persistTabSession(it) }
    }

    private fun rebuildGlossaryIndex() {
        viewModelScope.launch {
            val tabs = _openTabs.value
            val mentions = mutableMapOf<String, Int>()
            tabs.forEach { tab ->
                annotationDao.getAnnotationsForFile(tab.uri.toString()).first().forEach { ann ->
                    ann.data
                        .split(" ", "\n", "\t", ",", ".", ":", ";", "(", ")")
                        .map { it.trim().lowercase() }
                        .filter { it.length > 3 }
                        .forEach { token -> mentions[token] = (mentions[token] ?: 0) + 1 }
                }
            }
            _termLookup.value = mentions
        }
    }

    private fun updateActiveTab(transform: (PdfWorkspaceTab) -> PdfWorkspaceTab) {
        val activeId = _activeTabId.value ?: return
        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.id == activeId) transform(tab.copy(lastOpenedAt = System.currentTimeMillis())) else tab
        }
    }

    private fun persistTabSession(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            val keyPrefix = tab.uri.hashCode()
            dataStore.edit { pref ->
                pref[intPreferencesKey("pdf_page_$keyPrefix")] = tab.page
                pref[floatPreferencesKey("pdf_zoom_$keyPrefix")] = tab.zoom
                pref[stringPreferencesKey("pdf_tool_$keyPrefix")] = tab.lastTool.name
                pref[booleanPreferencesKey("pdf_last_read")] = true
                pref[stringPreferencesKey("pdf_last_uri")] = tab.uri.toString()
            }
        }
    }

    private suspend fun restoreSession(uri: Uri): RestoredSession {
        val keyPrefix = uri.hashCode()
        val prefs = dataStore.data.first()
        val toolName = prefs[stringPreferencesKey("pdf_tool_$keyPrefix")] ?: PdfToolMode.NAVIGATE.name
        return RestoredSession(
            page = prefs[intPreferencesKey("pdf_page_$keyPrefix")] ?: 0,
            zoom = prefs[floatPreferencesKey("pdf_zoom_$keyPrefix")] ?: 1f,
            lastTool = runCatching { PdfToolMode.valueOf(toolName) }.getOrDefault(PdfToolMode.NAVIGATE)
        )
    }

    private data class RestoredSession(
        val page: Int,
        val zoom: Float,
        val lastTool: PdfToolMode
    )

    fun closeViewer() {
        persistActiveTabSession()
        _uiState.value = PdfUiState.Idle
        _activeTabId.value = null
        _searchQuery.value = ""
    }

    sealed class PdfUiState {
        data object Idle : PdfUiState()
        data object Loading : PdfUiState()
        data class Viewer(val uri: Uri) : PdfUiState()
        data class Error(val message: String) : PdfUiState()
        data object PasswordRequired : PdfUiState()
    }
}
