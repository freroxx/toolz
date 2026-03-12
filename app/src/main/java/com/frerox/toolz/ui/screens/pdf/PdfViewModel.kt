package com.frerox.toolz.ui.screens.pdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
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
import com.frerox.toolz.data.pdf.PdfAnnotation
import com.frerox.toolz.data.pdf.PdfAnnotationDao
import com.frerox.toolz.data.pdf.AnnotationType
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.data.pdf.PdfMetadata
import com.frerox.toolz.data.pdf.PdfMetadataDao
import com.frerox.toolz.data.pdf.PdfRepository
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack
import javax.inject.Inject

enum class PdfToolMode { NAVIGATE, TEXT_SELECT, HIGHLIGHTER }
enum class NightProfile { OFF, SOLARIZED_DARK, AMOLED_BLACK }
enum class PdfSortOrder { NAME, SIZE, RECENT }

data class PdfWorkspaceTab(
    val id: String,
    val uri: Uri,
    val title: String,
    val page: Int = 0,
    val pageCount: Int = 1,
    val zoom: Float = 1f,
    val lastTool: PdfToolMode = PdfToolMode.NAVIGATE,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val isOcrActive: Boolean = false,
    val ocrProgress: Float = 0f
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
    private val metadataDao: PdfMetadataDao,
    private val noteDao: NoteDao,
    private val dataStore: DataStore<Preferences>,
    private val formulaOcrProcessor: FormulaOcrProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private val _rawPdfFiles = MutableStateFlow<List<PdfFile>>(emptyList())
    private val _metadata = metadataDao.getAllMetadata().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _sortOrder = MutableStateFlow(PdfSortOrder.RECENT)
    val sortOrder = _sortOrder.asStateFlow()

    private val redoStack = Stack<PdfAnnotation>()

    val pdfFiles = combine(_rawPdfFiles, _metadata, _sortOrder) { files, meta, sort ->
        val metaMap = meta.associateBy { it.uri }
        files.sortedWith { a, b ->
            val metaA = metaMap[a.uri.toString()]
            val metaB = metaMap[b.uri.toString()]
            
            if ((metaA?.isPinned ?: false) != (metaB?.isPinned ?: false)) {
                return@sortedWith if (metaA?.isPinned == true) -1 else 1
            }

            when (sort) {
                PdfSortOrder.NAME -> a.name.compareTo(b.name)
                PdfSortOrder.SIZE -> b.size.compareTo(a.size)
                PdfSortOrder.RECENT -> b.lastModified.compareTo(a.lastModified)
            }
        }.map { file ->
            file.copy(isPinned = metaMap[file.uri.toString()]?.isPinned ?: false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private val _activeTab = combine(_openTabs, _activeTabId) { tabs, activeId ->
        tabs.firstOrNull { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val annotations = _activeTab.flatMapLatest { tab ->
        if (tab == null) flowOf(emptyList())
        else annotationDao.getAnnotationsForFile(tab.uri.toString())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val extractedText = _activeTab.flatMapLatest { tab ->
        if (tab == null) flowOf(null)
        else metadataDao.getMetadataFlow(tab.uri.toString()).map { it?.ocrContent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadPdfFiles()
    }

    fun loadPdfFiles() {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading
            try {
                _rawPdfFiles.value = repository.getPdfFiles()
                _uiState.value = PdfUiState.Idle
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.message ?: "Failed to load PDFs")
            }
        }
    }

    fun setSortOrder(order: PdfSortOrder) {
        _sortOrder.value = order
    }

    fun togglePin(file: PdfFile) {
        viewModelScope.launch {
            val current = metadataDao.getMetadata(file.uri.toString())
            if (current == null) {
                metadataDao.insertMetadata(PdfMetadata(file.uri.toString(), isPinned = true))
            } else {
                metadataDao.updatePinned(file.uri.toString(), !current.isPinned)
            }
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
        }
    }

    fun switchTab(tabId: String) {
        val tab = _openTabs.value.firstOrNull { it.id == tabId } ?: return
        _activeTabId.value = tabId
        _uiState.value = PdfUiState.Viewer(tab.uri)
        redoStack.clear()
    }

    fun closeTab(tabId: String) {
        val tabs = _openTabs.value
        val removed = tabs.firstOrNull { it.id == tabId } ?: return
        persistTabSession(removed)
        val updated = tabs.filterNot { it.id == tabId }
        _openTabs.value = updated
        _activeTabId.value = updated.lastOrNull()?.id
        _uiState.value = if (updated.isEmpty()) PdfUiState.Idle else PdfUiState.Viewer(updated.last().uri)
        redoStack.clear()
    }

    fun updatePage(page: Int, pageCount: Int = _activeTab.value?.pageCount ?: 1) {
        updateActiveTab { it.copy(page = page.coerceAtLeast(0), pageCount = pageCount.coerceAtLeast(1)) }
    }

    fun updateZoom(zoom: Float) {
        updateActiveTab { it.copy(zoom = zoom.coerceIn(0.5f, 5f)) }
    }

    fun updateLastTool(tool: PdfToolMode) {
        val tab = _activeTab.value ?: return
        updateActiveTab { it.copy(lastTool = tool) }
        
        if (tool == PdfToolMode.TEXT_SELECT || tool == PdfToolMode.HIGHLIGHTER) {
            checkAndRunOcr(tab)
        }
    }

    private fun checkAndRunOcr(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            val meta = metadataDao.getMetadata(tab.uri.toString())
            if (meta?.ocrContent == null || meta.ocrContent.isEmpty()) {
                runFullDocumentOcr(tab)
            }
        }
    }

    private fun runFullDocumentOcr(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            updateActiveTab { it.copy(isOcrActive = true, ocrProgress = 0f) }
            val pageCount = tab.pageCount
            val ocrResults = mutableListOf<String>()
            
            for (i in 0 until pageCount) {
                val bitmap = repository.getPageBitmap(tab.uri, i)
                if (bitmap != null) {
                    val text = formulaOcrProcessor.processImage(bitmap)
                    ocrResults.add(text)
                }
                updateActiveTab { it.copy(ocrProgress = (i + 1).toFloat() / pageCount) }
            }
            
            val combinedOcr = ocrResults.joinToString("\n---\n")
            val currentMeta = metadataDao.getMetadata(tab.uri.toString())
            if (currentMeta == null) {
                metadataDao.insertMetadata(PdfMetadata(tab.uri.toString(), ocrContent = combinedOcr))
            } else {
                metadataDao.updateOcrContent(tab.uri.toString(), combinedOcr)
            }
            updateActiveTab { it.copy(isOcrActive = false) }
        }
    }

    fun addHighlight(rect: Rect, color: Int) {
        val tab = _activeTab.value ?: return
        viewModelScope.launch {
            val annotation = PdfAnnotation(
                fileUri = tab.uri.toString(),
                pageIndex = tab.page,
                type = AnnotationType.HIGHLIGHTER,
                data = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                color = color,
                thickness = 1f
            )
            annotationDao.insertAnnotation(annotation)
            redoStack.clear()
        }
    }

    fun undoLastAnnotation() {
        val tab = _activeTab.value ?: return
        viewModelScope.launch {
            val fileAnnotations = annotationDao.getAnnotationsForFile(tab.uri.toString()).first()
            if (fileAnnotations.isNotEmpty()) {
                val last = fileAnnotations.last()
                annotationDao.deleteAnnotation(last)
                redoStack.push(last)
            }
        }
    }

    fun redoAnnotation() {
        if (redoStack.isNotEmpty()) {
            val annotation = redoStack.pop()
            viewModelScope.launch {
                annotationDao.insertAnnotation(annotation)
            }
        }
    }

    fun clearAllAnnotations() {
        val tab = _activeTab.value ?: return
        viewModelScope.launch {
            annotationDao.clearAnnotations(tab.uri.toString())
            redoStack.clear()
        }
    }

    fun resetOcr() {
        val tab = _activeTab.value ?: return
        viewModelScope.launch {
            metadataDao.updateOcrContent(tab.uri.toString(), "")
            runFullDocumentOcr(tab)
        }
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

    fun clearFormulaResult() {
        _formulaState.value = FormulaCaptureState()
    }

    fun persistActiveTabSession() {
        _activeTab.value?.let { persistTabSession(it) }
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

    fun deleteFile(file: PdfFile) {
        viewModelScope.launch {
            if (repository.deletePdf(file.uri)) {
                loadPdfFiles()
                if (_activeTabId.value == file.uri.toString()) {
                    closeViewer()
                }
                _openTabs.value = _openTabs.value.filterNot { it.id == file.uri.toString() }
            }
        }
    }

    fun renameFile(file: PdfFile, newName: String) {
        viewModelScope.launch {
            if (repository.renamePdf(file.uri, newName)) {
                loadPdfFiles()
                _openTabs.value = _openTabs.value.map {
                    if (it.id == file.uri.toString()) it.copy(title = newName) else it
                }
            }
        }
    }

    fun navigateToNotesForPdf(file: PdfFile, onNavigate: (Int?) -> Unit) {
        viewModelScope.launch {
            val allNotes = noteDao.getAllNotes().first()
            val existingNote = allNotes.find { it.attachedPdfUri == file.uri.toString() }
            if (existingNote != null) {
                onNavigate(existingNote.id)
            } else {
                onNavigate(null)
            }
        }
    }

    fun createNoteForPdf(file: PdfFile, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val note = Note(
                title = "Notes: ${file.name.removeSuffix(".pdf")}",
                content = "",
                color = 0xFFFFF9C4.toInt(),
                attachedPdfUri = file.uri.toString()
            )
            noteDao.insertNote(note)
            val allNotes = noteDao.getAllNotes().first()
            val created = allNotes.find { it.attachedPdfUri == file.uri.toString() }
            created?.let { onCreated(it.id) }
        }
    }

    fun closeViewer() {
        persistActiveTabSession()
        _uiState.value = PdfUiState.Idle
        _activeTabId.value = null
        _searchQuery.value = ""
    }

    private data class RestoredSession(
        val page: Int,
        val zoom: Float,
        val lastTool: PdfToolMode
    )

    sealed class PdfUiState {
        data object Idle : PdfUiState()
        data object Loading : PdfUiState()
        data class Viewer(val uri: Uri) : PdfUiState()
        data class Error(val message: String) : PdfUiState()
        data object PasswordRequired : PdfUiState()
    }
}
