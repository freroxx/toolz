package com.frerox.toolz.ui.screens.pdf
 
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
import com.frerox.toolz.data.settings.SettingsRepository
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

enum class PdfToolMode { NAVIGATE, OCR }
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
    val lastOpenedAt: Long = java.lang.System.currentTimeMillis()
)

data class DocumentState(
    val totalPages: Int = 0,
    val currentPageIndex: Int = 0,
    val isReady: Boolean = false
)

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val repository: PdfRepository,
    private val metadataDao: PdfMetadataDao,
    private val noteDao: NoteDao,
    private val dataStore: DataStore<Preferences>,
    private val ocrProcessor: FormulaOcrProcessor,
    private val settingsRepository: SettingsRepository,
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

    val performanceMode = settingsRepository.performanceMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val bitmapCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / (if (performanceMode.value) 16 else 8)).toInt()) {
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

    private val _openTabs = MutableStateFlow<List<PdfWorkspaceTab>>(emptyList())
    val openTabs = _openTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId = _activeTabId.asStateFlow()

    private val _activeTab = combine(_openTabs, _activeTabId) { tabs, activeId ->
        tabs.firstOrNull { it.id == activeId }
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

    fun openPdf(uri: Uri, title: String? = null) {
        val finalTitle = title ?: uri.lastPathSegment ?: "PDF Document"
        val existing = _openTabs.value.firstOrNull { it.uri == uri }
        if (existing != null) {
            _activeTabId.value = existing.id
            _uiState.value = PdfUiState.Viewer(existing.uri)
            return
        }

        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading
            val restored = restoreSession(uri)
            val pageCount = repository.getPageCount(uri)
            
            val tab = PdfWorkspaceTab(
                id = uri.toString(),
                uri = uri,
                title = finalTitle,
                page = restored.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                zoom = restored.zoom,
                lastTool = restored.lastTool,
                pageCount = pageCount
            )
            
            _openTabs.value = _openTabs.value + tab
            _activeTabId.value = tab.id
            _uiState.value = PdfUiState.Viewer(uri)
            
            _docState.update { it.copy(
                totalPages = tab.pageCount,
                currentPageIndex = tab.page,
                isReady = true
            ) }
        }
    }

    fun closeTab(tabId: String) {
        val tabs = _openTabs.value
        val removed = tabs.firstOrNull { it.id == tabId } ?: return
        persistTabSession(removed)
        val updated = tabs.filterNot { it.id == tabId }
        _openTabs.value = updated
        
        if (updated.isEmpty()) {
            _activeTabId.value = null
            _uiState.value = PdfUiState.Idle
            _docState.value = DocumentState()
            bitmapCache.evictAll()
        } else {
            val nextTab = updated.last()
            _activeTabId.value = nextTab.id
            _uiState.value = PdfUiState.Viewer(nextTab.uri)
            _docState.update { it.copy(
                totalPages = nextTab.pageCount,
                currentPageIndex = nextTab.page,
                isReady = true
            ) }
        }
    }

    fun updatePage(page: Int) {
        val validPage = page.coerceIn(0, (_docState.value.totalPages - 1).coerceAtLeast(0))
        if (_docState.value.currentPageIndex != validPage) {
            _docState.update { it.copy(currentPageIndex = validPage) }
            updateActiveTab { it.copy(page = validPage) }
        }
    }

    fun updateZoom(zoom: Float) {
        updateActiveTab { it.copy(zoom = zoom.coerceIn(0.5f, 5f)) }
    }

    fun updateLastTool(tool: PdfToolMode) {
        val tab = _activeTab.value ?: return
        updateActiveTab { it.copy(lastTool = tool) }
        if (tool == PdfToolMode.OCR) {
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

    suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        val tab = _activeTab.value ?: return null
        val cacheKey = "${tab.uri}_$pageIndex"
        val cached = bitmapCache.get(cacheKey)
        if (cached != null) return cached

        val scale = if (performanceMode.value) 1.5f else 2.5f
        val bitmap = repository.getPageBitmap(tab.uri, pageIndex, scale)
        if (bitmap != null) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun updateActiveTab(transform: (PdfWorkspaceTab) -> PdfWorkspaceTab) {
        val activeId = _activeTabId.value ?: return
        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.id == activeId) transform(tab.copy(lastOpenedAt = java.lang.System.currentTimeMillis())) else tab
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

    fun closeViewer() {
        _activeTab.value?.let { persistTabSession(it) }
        _uiState.value = PdfUiState.Idle
        _activeTabId.value = null
        _docState.update { DocumentState() }
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
    }
}
