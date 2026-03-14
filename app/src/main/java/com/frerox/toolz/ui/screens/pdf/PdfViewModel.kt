package com.frerox.toolz.ui.screens.pdf
 
import com.google.mlkit.vision.text.Text
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.data.pdf.PdfMetadata
import com.frerox.toolz.data.pdf.PdfMetadataDao
import com.frerox.toolz.data.pdf.PdfRepository
import com.frerox.toolz.data.notepad.Note
import com.frerox.toolz.data.notepad.NoteDao
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PdfToolMode { NAVIGATE, TEXT_SELECT, SEARCH }
enum class NightProfile { OFF, SOLARIZED_DARK, AMOLED_BLACK }
enum class PdfSortOrder { NAME, SIZE, RECENT }

data class PdfWorkspaceTab(
    val id: String,
    val uri: Uri,
    val title: String,
    val page: Int = 0,
    val zoom: Float = 1f,
    val lastTool: PdfToolMode = PdfToolMode.NAVIGATE,
    val isOcrActive: Boolean = false,
    val ocrProgress: Float = 0f,
    val ocrLanguage: OcrLanguage = OcrLanguage.LATIN,
    val pageCount: Int = 0,
    val lastOpenedAt: Long = System.currentTimeMillis()
)

data class DocumentState(
    val totalPages: Int = 0,
    val currentPageIndex: Int = 0,
    val isReady: Boolean = false,
    val searchResults: List<Int> = emptyList(),
    val isSearching: Boolean = false,
    val searchKeyword: String = "",
    val showPages: Boolean = true
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val repository: PdfRepository,
    private val metadataDao: PdfMetadataDao,
    private val noteDao: NoteDao,
    private val dataStore: DataStore<Preferences>,
    private val ocrProcessor: FormulaOcrProcessor,
    private val moshi: Moshi
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private val _docState = MutableStateFlow(DocumentState())
    val docState = _docState.asStateFlow()

    private val _rawPdfFiles = MutableStateFlow<List<PdfFile>>(emptyList())
    private val _metadata = metadataDao.getAllMetadata().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _sortOrder = MutableStateFlow(PdfSortOrder.RECENT)
    val sortOrder = _sortOrder.asStateFlow()

    private val bitmapCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

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

    private val _activeTab = combine(_openTabs, _activeTabId) { tabs, activeId ->
        tabs.firstOrNull { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val extractedText = _activeTab.flatMapLatest { tab ->
        if (tab == null) flowOf(null)
        else metadataDao.getMetadataFlow(tab.uri.toString()).map { it?.ocrContent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ocrData = _activeTab.flatMapLatest { tab ->
        if (tab == null) flowOf(null)
        else metadataDao.getMetadataFlow(tab.uri.toString()).map { meta ->
            meta?.structuredOcrData?.let { data ->
                try {
                    moshi.adapter(OcrDocumentData::class.java).fromJson(data)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private var searchJob: Job? = null

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

        // Set state immediately to reserve the viewer screen
        _uiState.value = PdfUiState.Viewer(uri)
        
        viewModelScope.launch {
            val restored = restoreSession(uri)
            val file = pdfFiles.value.find { it.uri == uri }
            val tab = PdfWorkspaceTab(
                id = uri.toString(),
                uri = uri,
                title = title,
                page = restored.page,
                zoom = restored.zoom,
                lastTool = restored.lastTool,
                pageCount = file?.pageCount ?: 0
            )
            _openTabs.value = _openTabs.value + tab
            _activeTabId.value = tab.id
            _uiState.value = PdfUiState.Viewer(uri)
            
            _docState.update { it.copy(
                totalPages = tab.pageCount,
                currentPageIndex = restored.page,
                isReady = true
            ) }
        }
    }

    fun switchTab(tabId: String) {
        val tab = _openTabs.value.firstOrNull { it.id == tabId } ?: return
        _activeTabId.value = tabId
        _uiState.value = PdfUiState.Viewer(tab.uri)
        
        _docState.update { it.copy(
            totalPages = tab.pageCount,
            currentPageIndex = tab.page,
            isReady = true
        ) }
    }

    fun toggleShowPages() {
        _docState.update { it.copy(showPages = !it.showPages) }
    }

    fun closeTab(tabId: String) {
        val tabs = _openTabs.value
        val removed = tabs.firstOrNull { it.id == tabId } ?: return
        persistTabSession(removed)
        val updated = tabs.filterNot { it.id == tabId }
        _openTabs.value = updated
        _activeTabId.value = updated.lastOrNull()?.id
        _uiState.value = if (updated.isEmpty()) PdfUiState.Idle else PdfUiState.Viewer(updated.last().uri)
        
        if (updated.isEmpty()) {
            _docState.value = DocumentState()
            bitmapCache.evictAll()
        }
    }

    fun updatePage(page: Int) {
        if (_docState.value.currentPageIndex != page) {
            _docState.update { it.copy(currentPageIndex = page) }
            updateActiveTab { it.copy(page = page) }
        }
    }

    fun updateZoom(zoom: Float) {
        updateActiveTab { it.copy(zoom = zoom.coerceIn(0.5f, 5f)) }
    }

    fun updateLastTool(tool: PdfToolMode) {
        val tab = _activeTab.value ?: return
        updateActiveTab { it.copy(lastTool = tool) }
        if (tool == PdfToolMode.TEXT_SELECT) {
            checkAndRunOcr(tab)
        }
    }

    fun setOcrLanguage(language: OcrLanguage) {
        val tab = _activeTab.value ?: return
        updateActiveTab { it.copy(ocrLanguage = language) }
        // Rerun OCR if it's already extracted, or if the user is in TEXT_SELECT mode
        if (tab.lastTool == PdfToolMode.TEXT_SELECT) {
            runFullDocumentOcr(tab.copy(ocrLanguage = language))
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
            val pageDataList = mutableListOf<OcrPageData>()
            
            for (i in 0 until pageCount) {
                val bitmap = repository.getOcrBitmap(tab.uri, i)
                if (bitmap != null) {
                    try {
                        val result = ocrProcessor.processImage(bitmap, language = tab.ocrLanguage)
                        val text = ocrProcessor.getStructuredText(result)
                        ocrResults.add(text)
                        
                        val blocks = result.textBlocks.map { block ->
                            OcrBlockData(
                                text = block.text,
                                left = block.boundingBox?.left?.toFloat() ?: 0f,
                                top = block.boundingBox?.top?.toFloat() ?: 0f,
                                right = block.boundingBox?.right?.toFloat() ?: 0f,
                                bottom = block.boundingBox?.bottom?.toFloat() ?: 0f,
                                confidence = 1f
                            )
                        }
                        pageDataList.add(OcrPageData(i, blocks))
                    } catch (e: Exception) {
                        ocrResults.add("[OCR Error on page $i]")
                    } finally {
                        bitmap.recycle()
                    }
                }
                updateActiveTab { it.copy(ocrProgress = (i + 1).toFloat() / pageCount) }
            }
            
            val combinedOcr = ocrResults.joinToString("\n\n--- PAGE BREAK ---\n\n")
            val structuredData = moshi.adapter(OcrDocumentData::class.java).toJson(OcrDocumentData(pageDataList))
            
            val currentMeta = metadataDao.getMetadata(tab.uri.toString())
            if (currentMeta == null) {
                metadataDao.insertMetadata(PdfMetadata(tab.uri.toString(), ocrContent = combinedOcr, structuredOcrData = structuredData))
            } else {
                metadataDao.updateOcrContent(tab.uri.toString(), combinedOcr)
                metadataDao.updateStructuredOcrData(tab.uri.toString(), structuredData)
            }
            updateActiveTab { it.copy(isOcrActive = false) }
        }
    }

    fun searchInDocument(keyword: String) {
        val tab = _activeTab.value ?: return
        if (keyword.isBlank()) {
            _docState.update { it.copy(searchResults = emptyList(), isSearching = false, searchKeyword = "") }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _docState.update { it.copy(isSearching = true, searchKeyword = keyword) }
            val results = withContext(Dispatchers.Default) {
                val meta = metadataDao.getMetadata(tab.uri.toString())
                val content = meta?.ocrContent ?: ""
                val pages = content.split("--- PAGE BREAK ---")
                val foundIndices = mutableListOf<Int>()
                pages.forEachIndexed { index, text ->
                    if (text.contains(keyword, ignoreCase = true)) {
                        foundIndices.add(index)
                    }
                }
                foundIndices
            }
            _docState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        val tab = _activeTab.value ?: return null
        val cacheKey = "${tab.uri}_$pageIndex"
        val cached = bitmapCache.get(cacheKey)
        if (cached != null) return cached

        val bitmap = repository.getPageBitmap(tab.uri, pageIndex, 2.0f) 
        if (bitmap != null) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    suspend fun getThumbnail(pageIndex: Int): Bitmap? {
        val tab = _activeTab.value ?: return null
        val cacheKey = "thumb_${tab.uri}_$pageIndex"
        val cached = bitmapCache.get(cacheKey)
        if (cached != null) return cached
        
        val bitmap = repository.getPageBitmap(tab.uri, pageIndex, 0.2f)
        if (bitmap != null) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
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
        _docState.value = DocumentState()
        bitmapCache.evictAll()
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
