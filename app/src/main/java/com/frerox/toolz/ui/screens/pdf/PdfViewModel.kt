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

sealed class PdfUiState {
    object Idle : PdfUiState()
    object Loading : PdfUiState()
    data class Success(val files: List<PdfFile>) : PdfUiState()
    object Viewer : PdfUiState()
    data class Error(val message: String) : PdfUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Loading)
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

    val activeTab = combine(_openTabs, _activeTabId) { tabs, id ->
        tabs.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _activeTab = activeTab

    val ocrData: StateFlow<OcrDocumentData?> = activeTab.flatMapLatest { tab ->
        if (tab == null) flowOf(null)
        else metadataDao.getMetadataFlow(tab.uri.toString()).map { meta ->
            meta?.structuredOcrData?.let {
                try { moshi.adapter(OcrDocumentData::class.java).fromJson(it) }
                catch (e: Exception) { null }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadPdfFiles()
    }

    private fun loadPdfFiles() {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading
            try {
                val files = repository.getPdfFiles()
                _rawPdfFiles.value = files
                _uiState.value = PdfUiState.Success(files)
            } catch (e: Exception) {
                _uiState.value = PdfUiState.Error(e.message ?: "Failed to load PDFs")
            }
        }
    }

    // ── AI PDF summarisation ────────────────────────────────────────────────

    private val _pdfSummary    = MutableStateFlow<String?>(null)
    val pdfSummary: StateFlow<String?> = _pdfSummary.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    fun summarizePdf(text: String) {
        viewModelScope.launch {
            _isSummarizing.value = true
            _pdfSummary.value    = ocrProcessor.summarizePdf(text)
            _isSummarizing.value = false
        }
    }

    fun clearSummary() { _pdfSummary.value = null }

    // ── File management ─────────────────────────────────────────────────────

    fun setPdfFiles(files: List<PdfFile>) {
        _rawPdfFiles.value = files
        _uiState.value = PdfUiState.Success(files)
    }

    fun setSortOrder(order: PdfSortOrder) { _sortOrder.value = order }

    fun openDocument(uri: Uri, title: String) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading
            val existing = _openTabs.value.find { it.uri == uri }
            if (existing != null) {
                _activeTabId.value = existing.id
            } else {
                val id = java.util.UUID.randomUUID().toString()
                val keyPrefix = uri.hashCode()
                val savedPage = dataStore.data.map { it[intPreferencesKey("pdf_page_$keyPrefix")] ?: 0 }.first()
                val savedZoom = dataStore.data.map { it[floatPreferencesKey("pdf_zoom_$keyPrefix")] ?: 1f }.first()
                val pageCount = repository.getPageCount(uri)
                val newTab    = PdfWorkspaceTab(id, uri, title, page = savedPage, zoom = savedZoom, pageCount = pageCount)
                _openTabs.value  = _openTabs.value + newTab
                _activeTabId.value = id
            }
            _uiState.value = PdfUiState.Viewer
            val tab = _activeTab.value
            if (tab != null) {
                _docState.value = DocumentState(totalPages = tab.pageCount, currentPageIndex = tab.page, isReady = true)
            }
        }
    }

    fun openPdf(uri: Uri, title: String = "Document") = openDocument(uri, title)

    fun closeViewer() { _uiState.value = PdfUiState.Success(_rawPdfFiles.value) }

    fun closeTab(id: String) {
        _openTabs.value.find { it.id == id }?.let { persistTabSession(it) }
        _openTabs.value = _openTabs.value.filter { it.id != id }
        if (_activeTabId.value == id) _activeTabId.value = _openTabs.value.lastOrNull()?.id
    }

    fun selectTab(id: String) { _activeTabId.value = id }

    fun updatePage(page: Int) {
        updateActiveTab { it.copy(page = page) }
        _docState.value = _docState.value.copy(currentPageIndex = page)
    }

    fun updateZoom(zoom: Float) { updateActiveTab { it.copy(zoom = zoom) } }

    fun setTool(tool: PdfToolMode) {
        val tab = _activeTab.value ?: return
        updateActiveTab { it.copy(lastTool = tool) }
        if (tool == PdfToolMode.OCR) checkAndRunOcr(tab)
    }

    fun updateLastTool(tool: PdfToolMode) = setTool(tool)

    private fun checkAndRunOcr(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            val meta = metadataDao.getMetadata(tab.uri.toString())
            if (meta?.ocrContent == null || meta.ocrContent.isEmpty()) runFullDocumentOcr(tab)
        }
    }

    private fun runFullDocumentOcr(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            updateActiveTab { it.copy(isOcrActive = true, ocrProgress = 0f) }
            val ocrResults   = mutableListOf<String>()
            val pageDataList = mutableListOf<OcrPageData>()

            for (i in 0 until tab.pageCount) {
                val bitmap = repository.getOcrBitmap(tab.uri, i)
                if (bitmap != null) {
                    try {
                        val result = ocrProcessor.processImage(
                            bitmap  = bitmap,
                            options = OcrOptions(language = tab.ocrLanguage, enableAiCleaner = true),
                        )
                        ocrResults.add(result.rawText)
                        val blocks = result.blocks.map { block ->
                            OcrBlockData(
                                text       = block.text,
                                left       = block.left.toFloat(),
                                top        = block.top.toFloat(),
                                right      = block.right.toFloat(),
                                bottom     = block.bottom.toFloat(),
                                confidence = block.confidence,
                                type       = block.type,
                            )
                        }
                        pageDataList.add(OcrPageData(i, blocks, result.rawText))
                    } catch (e: Exception) {
                        ocrResults.add("[OCR Error on page $i]")
                    } finally {
                        bitmap.recycle()
                    }
                }
                updateActiveTab { it.copy(ocrProgress = (i + 1).toFloat() / tab.pageCount) }
            }

            val combined       = ocrResults.joinToString("\n\n--- PAGE BREAK ---\n\n")
            val structuredData = moshi.adapter(OcrDocumentData::class.java)
                .toJson(OcrDocumentData(pageDataList))

            val current = metadataDao.getMetadata(tab.uri.toString())
            if (current == null) {
                metadataDao.insertMetadata(
                    PdfMetadata(tab.uri.toString(), ocrContent = combined, structuredOcrData = structuredData)
                )
            } else {
                metadataDao.updateOcrContent(tab.uri.toString(), combined)
                metadataDao.updateStructuredOcrData(tab.uri.toString(), structuredData)
            }
            updateActiveTab { it.copy(isOcrActive = false) }
        }
    }

    suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        val tab      = _activeTab.value ?: return null
        val cacheKey = "${tab.uri}_$pageIndex"
        bitmapCache.get(cacheKey)?.let { return it }
        val scale  = if (performanceMode.value) 1.5f else 2.5f
        val bitmap = repository.getPageBitmap(tab.uri, pageIndex, scale)
        if (bitmap != null) bitmapCache.put(cacheKey, bitmap)
        return bitmap
    }

    private fun updateActiveTab(transform: (PdfWorkspaceTab) -> PdfWorkspaceTab) {
        val id = _activeTabId.value ?: return
        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.id == id) transform(tab.copy(lastOpenedAt = java.lang.System.currentTimeMillis())) else tab
        }
    }

    private fun persistTabSession(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            val kp = tab.uri.hashCode()
            dataStore.edit { pref ->
                pref[intPreferencesKey("pdf_page_$kp")]  = tab.page
                pref[floatPreferencesKey("pdf_zoom_$kp")] = tab.zoom
            }
        }
    }

    fun togglePin(uri: String) {
        viewModelScope.launch {
            val current = metadataDao.getMetadata(uri)
            if (current == null) metadataDao.insertMetadata(PdfMetadata(uri, isPinned = true))
            else metadataDao.updatePinned(uri, !current.isPinned)
        }
    }

    fun deleteFile(file: PdfFile) {
        viewModelScope.launch {
            repository.deletePdf(file.uri)
            _rawPdfFiles.value = _rawPdfFiles.value.filter { it.uri != file.uri }
        }
    }
}