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

package com.frerox.toolz.ui.screens.pdf

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.notepad.NoteAttachment
import com.frerox.toolz.data.notepad.NoteAttachmentDao
import com.frerox.toolz.data.notepad.NoteDao
import com.frerox.toolz.data.notepad.PdfAttachResult
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.data.pdf.PdfMetadata
import com.frerox.toolz.data.pdf.PdfMetadataDao
import com.frerox.toolz.data.pdf.PdfPageMatch
import com.frerox.toolz.data.pdf.PdfRenderEngine
import com.frerox.toolz.data.pdf.PdfRepository
import com.frerox.toolz.data.pdf.PdfTextEngine
import com.frerox.toolz.data.pdf.PdfTocEntry
import com.frerox.toolz.data.settings.SettingsRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "PdfViewModel"

enum class PdfToolMode { NAVIGATE, OCR }
enum class PdfSortOrder { NAME, SIZE, RECENT }
enum class PdfViewMode { LIST, GRID }
enum class PdfReadingMode { CONTINUOUS, PAGED }
enum class PdfPaperMode { PAPER, SEPIA, NIGHT }
enum class PdfTextTab { SCAN, TEXT }

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
    val isReady: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
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
    private val attachmentDao: NoteAttachmentDao,
    private val renderEngine: PdfRenderEngine,
    private val textEngine: PdfTextEngine,
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
    private val _metadata = metadataDao.getAllMetadata()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _sortOrder = MutableStateFlow(PdfSortOrder.RECENT)
    val sortOrder = _sortOrder.asStateFlow()

    // ── Remake V2: library state ─────────────────────────────────────────────
    /** Exposed for covers — same singleton the repository uses (shared cache). */
    val pdfRenderEngine: PdfRenderEngine get() = renderEngine

    /**
     * Full-disk reads (MediaStore PDFs owned by other apps) need All-files
     * access on API 30+. Without it thumbnails, page counts and opens fail
     * with SecurityException — the library shows icon-only rows.
     */
    fun hasAllFilesAccess(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R ||
            android.os.Environment.isExternalStorageManager()

    /** Decode the first covers ahead of the rows so library
     *  thumbnails are already cached when they scroll into view.
     *  Concurrent — per-URI lock stripes keep files independent. */
    fun warmThumbnails(files: List<PdfFile>, count: Int = 12) {
        viewModelScope.launch(Dispatchers.IO) {
            files.take(count).map { f ->
                async {
                    try {
                        renderEngine.renderThumbnail(f.uri)
                    } catch (_: Exception) {
                    }
                }
            }.awaitAll()
        }
    }

    /** Lazy per-file enrichment: fills title/author/pageCount once, then stops. */
    private val prefetching = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun prefetchInfo(file: PdfFile) {
        val key = file.uri.toString()
        if (file.pageCount > 0 && file.docTitle != null) return
        if (!prefetching.add(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val meta = try { metadataDao.getMetadata(key) } catch (_: Exception) { null }
                if (meta != null && meta.pageCount > 0 && meta.title != null) return@launch
                val count = try { repository.getPageCount(file.uri) } catch (_: Exception) { 0 }
                if (count < 0) return@launch
                val info = try { repository.getDocInfo(file.uri) } catch (_: Exception) { null }
                if (meta == null) {
                    metadataDao.insertMetadata(
                        PdfMetadata(key, pageCount = count.coerceAtLeast(0), title = info?.title, author = info?.author)
                    )
                } else if (meta.pageCount <= 0 || meta.title == null) {
                    metadataDao.updateDocInfo(key, info?.title ?: meta.title, info?.author ?: meta.author, count.coerceAtLeast(0))
                }
            } catch (_: Exception) {
            } finally {
                prefetching.remove(key)
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _viewMode = MutableStateFlow(PdfViewMode.LIST)
    val viewMode = _viewMode.asStateFlow()

    val performanceMode = settingsRepository.performanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val offlineModeEnabled = settingsRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pdfAiEnabled = settingsRepository.pdfAiToolsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val fullscreenTipSeen = settingsRepository.pdfFullscreenTipSeen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun dismissFullscreenTip() {
        viewModelScope.launch { settingsRepository.setPdfFullscreenTipSeen() }
    }

    private val bitmapCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Enriched + filtered + sorted vault list. */
    val pdfFiles = combine(_rawPdfFiles, _metadata, _sortOrder, _searchQuery) { files, meta, sort, query ->
        val metaMap = meta.associateBy { it.uri }
        var list = files.map { file ->
            val m = metaMap[file.uri.toString()]
            file.copy(
                isPinned = m?.isPinned ?: false,
                docTitle = m?.title,
                author = m?.author,
                pageCount = if (m != null && m.pageCount > 0) m.pageCount else file.pageCount,
                lastPage = m?.lastPage ?: 0,
                lastAccessed = m?.lastAccessed ?: 0L
            )
        }
        if (query.isNotBlank()) {
            val q = query.trim()
            list = list.filter {
                it.name.contains(q, true) ||
                    (it.docTitle?.contains(q, true) == true) ||
                    (it.author?.contains(q, true) == true)
            }
        }
        list.sortedWith { a, b ->
            if (a.isPinned != b.isPinned) return@sortedWith if (a.isPinned) -1 else 1
            when (sort) {
                PdfSortOrder.NAME -> a.displayTitle.compareTo(b.displayTitle, ignoreCase = true)
                PdfSortOrder.SIZE -> b.size.compareTo(a.size)
                PdfSortOrder.RECENT -> {
                    val ra = if (a.lastAccessed > 0) a.lastAccessed else a.lastModified * 1000
                    val rb = if (b.lastAccessed > 0) b.lastAccessed else b.lastModified * 1000
                    rb.compareTo(ra)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Continue-reading shelf: top docs with saved progress. */
    val recentFiles = combine(_rawPdfFiles, _metadata) { files, meta ->
        val byUri = files.associateBy { it.uri.toString() }
        meta.filter { it.lastAccessed > 0 && it.lastPage >= 0 }
            .sortedByDescending { it.lastAccessed }
            .take(10)
            .mapNotNull { m ->
                byUri[m.uri]?.copy(
                    isPinned = m.isPinned,
                    docTitle = m.title,
                    author = m.author,
                    pageCount = if (m.pageCount > 0) m.pageCount else 0,
                    lastPage = m.lastPage,
                    lastAccessed = m.lastAccessed
                ) ?: run {
                    // File vanished from MediaStore (e.g. app-private import) — still show
                    // from metadata so progress isn't lost.
                    try {
                        PdfFile(
                            uri = Uri.parse(m.uri),
                            name = m.title ?: m.uri.substringAfterLast('/'),
                            size = 0L,
                            lastModified = m.lastAccessed / 1000,
                            pageCount = m.pageCount,
                            isPinned = m.isPinned,
                            docTitle = m.title,
                            author = m.author,
                            lastPage = m.lastPage,
                            lastAccessed = m.lastAccessed
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Active document ──────────────────────────────────────────────────────
    private val _openTabs = MutableStateFlow<List<PdfWorkspaceTab>>(emptyList())
    val openTabs = _openTabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId = _activeTabId.asStateFlow()

    val activeTab = combine(_openTabs, _activeTabId) { tabs, id ->
        tabs.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _activeTab = activeTab

    private val _activeUri = MutableStateFlow<Uri?>(null)
    val activeUri = _activeUri.asStateFlow()

    private val _activeTitle = MutableStateFlow("Document")
    val activeTitle = _activeTitle.asStateFlow()

    private val _readingMode = MutableStateFlow(PdfReadingMode.CONTINUOUS)
    val readingMode = _readingMode.asStateFlow()

    private val _paperMode = MutableStateFlow(PdfPaperMode.PAPER)
    val paperMode = _paperMode.asStateFlow()

    // ── Text layer state ─────────────────────────────────────────────────────
    private val _pageTexts = MutableStateFlow<List<String>>(emptyList())
    val pageTexts = _pageTexts.asStateFlow()

    private val _textReady = MutableStateFlow(false)
    val textReady = _textReady.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PdfPageMatch>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _toc = MutableStateFlow<List<PdfTocEntry>>(emptyList())
    val toc = _toc.asStateFlow()

    private var openJob: Job? = null
    private var persistJob: Job? = null

    val ocrData: StateFlow<OcrDocumentData?> = activeTab.flatMapLatest { tab ->
        if (tab == null) flowOf(null)
        else metadataDao.getMetadataFlow(tab.uri.toString()).map { meta ->
            meta?.structuredOcrData?.let {
                try { moshi.adapter(OcrDocumentData::class.java).fromJson(it) }
                catch (_: Exception) { null }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadPdfFiles()
        viewModelScope.launch {
            val vm = dataStore.data.map { it[stringPreferencesKey("pdf_view_mode")] ?: "LIST" }.first()
            _viewMode.value = if (vm == "GRID") PdfViewMode.GRID else PdfViewMode.LIST
        }
    }

    // ── Library ──────────────────────────────────────────────────────────────

    private fun loadPdfFiles() {
        viewModelScope.launch {
            // Keep Viewer state if a doc is open — refresh list silently underneath.
            val inViewer = _uiState.value is PdfUiState.Viewer
            if (!inViewer) _uiState.value = PdfUiState.Loading
            try {
                val files = repository.getPdfFiles()
                _rawPdfFiles.value = files
                if (!inViewer) _uiState.value = PdfUiState.Success(files)
            } catch (e: Exception) {
                if (!inViewer) _uiState.value = PdfUiState.Error(e.message ?: "Failed to load PDFs")
            }
        }
    }

    fun refresh() = loadPdfFiles()

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun setViewMode(mode: PdfViewMode) {
        _viewMode.value = mode
        viewModelScope.launch {
            dataStore.edit { it[stringPreferencesKey("pdf_view_mode")] = mode.name }
        }
    }

    // ── AI PDF summarisation (compat) ────────────────────────────────────────

    private val _pdfSummary = MutableStateFlow<String?>(null)
    val pdfSummary: StateFlow<String?> = _pdfSummary.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    fun summarizePdf(text: String) {
        viewModelScope.launch {
            _isSummarizing.value = true
            _pdfSummary.value = ocrProcessor.summarizePdf(text)
            _isSummarizing.value = false
        }
    }

    fun clearSummary() { _pdfSummary.value = null }

    // ── AI sheet: document summary + smart extraction ────────────────────────

    private val _enhancedText = MutableStateFlow<String?>(null)
    val enhancedText: StateFlow<String?> = _enhancedText.asStateFlow()

    private val _isEnhancing = MutableStateFlow(false)
    val isEnhancing: StateFlow<Boolean> = _isEnhancing.asStateFlow()

    private fun fullDocumentText(): String =
        _pageTexts.value.filter { it.isNotBlank() }.joinToString("\n\n")

    /** Summarise the whole document (embedded text). Null result = AI unavailable. */
    fun summarizeDocument() {
        val text = fullDocumentText()
        if (text.isBlank()) return
        summarizePdf(text)
    }

    /** Rewrite the whole document text as clean Markdown for reading. */
    fun enhanceDocument() {
        val text = fullDocumentText()
        if (text.isBlank() || _isEnhancing.value) return
        viewModelScope.launch {
            _isEnhancing.value = true
            _enhancedText.value = ocrProcessor.enhanceForReading(text)
            _isEnhancing.value = false
        }
    }

    fun clearAiResults() {
        _pdfSummary.value = null
        _enhancedText.value = null
    }

    // ── File management ─────────────────────────────────────────────────────

    fun setPdfFiles(files: List<PdfFile>) {
        _rawPdfFiles.value = files
        if (_uiState.value !is PdfUiState.Viewer) _uiState.value = PdfUiState.Success(files)
    }

    fun setSortOrder(order: PdfSortOrder) { _sortOrder.value = order }

    fun openDocument(uri: Uri, title: String, startPage: Int? = null) {
        openJob?.cancel()
        openJob = viewModelScope.launch {
            _uiState.value = PdfUiState.Loading
            _docState.value = DocumentState(isLoading = true)
            _activeUri.value = uri
            _activeTitle.value = title
            _searchResults.value = emptyList()
            _pageTexts.value = emptyList()
            _textReady.value = false
            _toc.value = emptyList()
            _pdfSummary.value = null
            _enhancedText.value = null

            val existing = _openTabs.value.find { it.uri == uri }
            val keyPrefix = uri.toString().hashCode()
            val savedPageDs = dataStore.data.map { it[intPreferencesKey("pdf_page_$keyPrefix")] ?: 0 }.first()
            val savedZoomDs = dataStore.data.map { it[floatPreferencesKey("pdf_zoom_$keyPrefix")] ?: 1f }.first()
            val meta = try { metadataDao.getMetadata(uri.toString()) } catch (_: Exception) { null }

            val count = try { repository.getPageCount(uri) } catch (_: Exception) { 0 }
            if (count == -1) {
                _docState.value = DocumentState(error = "NO_ACCESS")
                _uiState.value = PdfUiState.Error("NO_ACCESS")
                return@launch
            }
            if (count <= 0) {
                _docState.value = DocumentState(error = "UNREADABLE")
                _uiState.value = PdfUiState.Error("UNREADABLE")
                return@launch
            }

            val start = (startPage ?: meta?.lastPage ?: savedPageDs).coerceIn(0, count - 1)
            _readingMode.value = try {
                PdfReadingMode.valueOf(meta?.readingMode ?: "CONTINUOUS")
            } catch (_: Exception) { PdfReadingMode.CONTINUOUS }
            _paperMode.value = try {
                PdfPaperMode.valueOf(meta?.paperMode ?: "PAPER")
            } catch (_: Exception) { PdfPaperMode.PAPER }

            val tab: PdfWorkspaceTab
            if (existing != null) {
                _activeTabId.value = existing.id
                tab = existing.copy(page = start, pageCount = count)
                _openTabs.value = _openTabs.value.map { if (it.id == existing.id) tab else it }
            } else {
                val id = java.util.UUID.randomUUID().toString()
                tab = PdfWorkspaceTab(
                    id, uri,
                    title = meta?.title?.takeIf { it.isNotBlank() } ?: title,
                    page = start, zoom = savedZoomDs, pageCount = count
                )
                _openTabs.value = _openTabs.value + tab
                _activeTabId.value = id
            }

            _docState.value = DocumentState(
                totalPages = count, currentPageIndex = start, isReady = true
            )
            _uiState.value = PdfUiState.Viewer

            // Touch recents (insert row if first open so progress survives).
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val cur = metadataDao.getMetadata(uri.toString())
                    if (cur == null) {
                        metadataDao.insertMetadata(
                            PdfMetadata(uri.toString(), lastAccessed = System.currentTimeMillis(), lastPage = start, pageCount = count)
                        )
                    } else {
                        metadataDao.updateLastPage(uri.toString(), start)
                        if (cur.pageCount != count) {
                            metadataDao.updateDocInfo(uri.toString(), cur.title, cur.author, count)
                        }
                    }
                } catch (_: Exception) { }
            }

            // Background: doc identity + text index (never blocks first paint).
            launch(Dispatchers.IO) {
                try {
                    val info = repository.getDocInfo(uri)
                    if (info != null && !info.isEncrypted) {
                        val cur = metadataDao.getMetadata(uri.toString())
                        metadataDao.updateDocInfo(
                            uri.toString(),
                            info.title ?: cur?.title,
                            info.author ?: cur?.author,
                            info.pageCount.takeIf { it > 0 } ?: count
                        )
                        if (!info.title.isNullOrBlank()) _activeTitle.value = info.title
                    }
                } catch (_: Exception) { }
            }
            launch(Dispatchers.IO) {
                try {
                    val texts = textEngine.getAllPageTexts(uri)
                    _pageTexts.value = texts
                    _textReady.value = texts.any { it.isNotBlank() }
                } catch (_: Exception) {
                    _textReady.value = false
                }
            }
            launch(Dispatchers.IO) {
                try {
                    _toc.value = textEngine.getOutline(uri)
                } catch (_: Exception) { }
            }
        }
    }

    fun openPdf(uri: Uri, title: String = "Document") = openDocument(uri, title)

    fun openPdfAtPage(uri: Uri, title: String, page: Int) = openDocument(uri, title, page)

    fun closeViewer() {
        openJob?.cancel()
        persistNow()
        _activeUri.value = null
        _uiState.value = PdfUiState.Success(_rawPdfFiles.value)
    }

    fun retryOpen() {
        val uri = _activeUri.value ?: return
        openDocument(uri, _activeTitle.value, _docState.value.currentPageIndex)
    }

    fun closeTab(id: String) {
        _openTabs.value.find { it.id == id }?.let { persistTabSession(it) }
        _openTabs.value = _openTabs.value.filter { it.id != id }
        if (_activeTabId.value == id) _activeTabId.value = _openTabs.value.lastOrNull()?.id
    }

    fun selectTab(id: String) {
        val tab = _openTabs.value.find { it.id == id } ?: return
        _activeTabId.value = id
        openDocument(tab.uri, tab.title, tab.page)
    }

    fun updatePage(page: Int) {
        val total = _docState.value.totalPages
        val safe = if (total > 0) page.coerceIn(0, total - 1) else page.coerceAtLeast(0)
        updateActiveTab { it.copy(page = safe) }
        _docState.value = _docState.value.copy(currentPageIndex = safe)
        persistDebounced()
    }

    fun updateZoom(zoom: Float) {
        updateActiveTab { it.copy(zoom = zoom) }
        persistDebounced()
    }

    fun setReadingMode(mode: PdfReadingMode) {
        _readingMode.value = mode
        val uri = _activeUri.value?.toString() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { metadataDao.updateReadingMode(uri, mode.name) } catch (_: Exception) { }
        }
    }

    fun setPaperMode(mode: PdfPaperMode) {
        _paperMode.value = mode
        val uri = _activeUri.value?.toString() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { metadataDao.updatePaperMode(uri, mode.name) } catch (_: Exception) { }
        }
    }

    fun setTool(tool: PdfToolMode) {
        val tab = _activeTab.value ?: return
        updateActiveTab { it.copy(lastTool = tool) }
        if (tool == PdfToolMode.OCR) checkAndRunOcr(tab)
    }

    fun updateLastTool(tool: PdfToolMode) = setTool(tool)

    // ── In-document search (embedded text) ───────────────────────────────────

    fun searchInDocument(query: String) {
        val uri = _activeUri.value ?: return
        viewModelScope.launch {
            if (query.trim().length < 2) {
                _searchResults.value = emptyList()
                return@launch
            }
            _isSearching.value = true
            try {
                _searchResults.value = withContext(Dispatchers.IO) {
                    textEngine.search(uri, query.trim())
                }
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearDocSearch() { _searchResults.value = emptyList() }

    suspend fun pageText(pageIndex: Int): String? {
        val uri = _activeUri.value ?: return null
        _pageTexts.value.getOrNull(pageIndex)?.let { return it }
        return try { textEngine.getPageText(uri, pageIndex) } catch (_: Exception) { null }
    }

    suspend fun linkedNotesFor(uri: Uri): List<NoteAttachment> = try {
        attachmentDao.findNotesWithUri(uri.toString())
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun recentNotes(limit: Int = 20): List<com.frerox.toolz.data.notepad.Note> = try {
        noteDao.getAllNotesSync().take(limit)
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Attach [file] to [noteId], guaranteeing the stored URI stays readable:
     * persistable grant where supported, verified read, app-private copy as
     * fallback. Never inserts a dead row.
     */
    suspend fun attachPdfToNote(noteId: Int, file: PdfFile, page: Int = 0): PdfAttachResult =
        withContext(Dispatchers.IO) {
            try {
                if (attachmentDao.countByKind(noteId, NoteAttachment.KIND_PDF) >= 10) {
                    return@withContext PdfAttachResult.Capped
                }
                repository.ensurePersistableGrant(file.uri)
                var usable = file.uri.toString()
                if (!repository.isReadable(file.uri)) {
                    val imported = try {
                        repository.importPdf(file.uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "attachPdfToNote: import copy failed", e)
                        null
                    }
                    if (imported == null || !repository.isReadable(imported)) {
                        Log.w(TAG, "attachPdfToNote: source unreadable and no copy possible: ${file.uri}")
                        return@withContext PdfAttachResult.Unreadable
                    }
                    usable = imported.toString()
                }
                // Re-read display fields from the stored URI so the row is self-consistent.
                val stored = Uri.parse(usable)
                attachmentDao.insert(
                    NoteAttachment(
                        noteId = noteId,
                        kind = NoteAttachment.KIND_PDF,
                        uri = usable,
                        displayName = try { repository.queryDisplayName(stored) } catch (_: Exception) { file.displayTitle },
                        sizeBytes = try { repository.querySize(stored).takeIf { it > 0 } ?: file.size } catch (_: Exception) { file.size },
                        pageHint = page
                    )
                )
                PdfAttachResult.Attached
            } catch (e: Exception) {
                Log.e(TAG, "attachPdfToNote failed for note $noteId", e)
                PdfAttachResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

    fun importPdf(sourceUri: Uri, onDone: (Uri?) -> Unit = {}) {
        viewModelScope.launch {
            val imported = try { repository.importPdf(sourceUri) } catch (_: Exception) { null }
            if (imported != null) {
                loadPdfFiles()
                textEngine.invalidate(imported)
            }
            onDone(imported)
        }
    }

    // ── OCR fallback (compat — per-doc full pass only on demand) ─────────────

    private fun checkAndRunOcr(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            val meta = try { metadataDao.getMetadata(tab.uri.toString()) } catch (_: Exception) { null }
            if (meta?.ocrContent == null || meta.ocrContent.isEmpty()) runFullDocumentOcr(tab)
        }
    }

    private fun runFullDocumentOcr(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            updateActiveTab { it.copy(isOcrActive = true, ocrProgress = 0f) }
            val ocrResults = mutableListOf<String>()
            val pageDataList = mutableListOf<OcrPageData>()

            for (i in 0 until tab.pageCount) {
                val bitmap = try { repository.getOcrBitmap(tab.uri, i) } catch (_: Exception) { null }
                if (bitmap != null) {
                    try {
                        val result = ocrProcessor.processImage(
                            bitmap = bitmap,
                            options = OcrOptions(language = tab.ocrLanguage, enableAiCleaner = true),
                        )
                        ocrResults.add(result.rawText)
                        val blocks = result.blocks.map { block ->
                            OcrBlockData(
                                text = block.text,
                                left = block.left.toFloat(),
                                top = block.top.toFloat(),
                                right = block.right.toFloat(),
                                bottom = block.bottom.toFloat(),
                                confidence = block.confidence,
                                type = block.type,
                            )
                        }
                        pageDataList.add(OcrPageData(i, blocks, result.rawText))
                    } catch (_: Exception) {
                        ocrResults.add("[OCR Error on page $i]")
                    } finally {
                        try { bitmap.recycle() } catch (_: Exception) { }
                    }
                }
                updateActiveTab { it.copy(ocrProgress = (i + 1).toFloat() / tab.pageCount.coerceAtLeast(1)) }
            }

            val combined = ocrResults.joinToString("\n\n--- PAGE BREAK ---\n\n")
            val structuredData = try {
                moshi.adapter(OcrDocumentData::class.java).toJson(OcrDocumentData(pageDataList))
            } catch (_: Exception) { null }

            try {
                val current = metadataDao.getMetadata(tab.uri.toString())
                if (current == null) {
                    metadataDao.insertMetadata(
                        PdfMetadata(tab.uri.toString(), ocrContent = combined, structuredOcrData = structuredData)
                    )
                } else {
                    metadataDao.updateOcrContent(tab.uri.toString(), combined)
                    if (structuredData != null) metadataDao.updateStructuredOcrData(tab.uri.toString(), structuredData)
                }
            } catch (_: Exception) { }
            updateActiveTab { it.copy(isOcrActive = false) }
        }
    }

    suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        val tab = _activeTab.value ?: return null
        val cacheKey = "${tab.uri}_$pageIndex"
        synchronized(bitmapCache) { bitmapCache.get(cacheKey)?.let { return it } }
        val scale = if (performanceMode.value) 1.5f else 2.5f
        val bitmap = try { repository.getPageBitmap(tab.uri, pageIndex, scale) } catch (_: Exception) { null }
        if (bitmap != null) synchronized(bitmapCache) { bitmapCache.put(cacheKey, bitmap) }
        return bitmap
    }

    private fun updateActiveTab(transform: (PdfWorkspaceTab) -> PdfWorkspaceTab) {
        val id = _activeTabId.value ?: return
        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.id == id) transform(tab.copy(lastOpenedAt = java.lang.System.currentTimeMillis())) else tab
        }
    }

    private fun persistDebounced() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(800)
            persistNow()
        }
    }

    private fun persistNow() {
        val tab = _openTabs.value.find { it.id == _activeTabId.value } ?: return
        persistTabSession(tab)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                metadataDao.updateLastPage(tab.uri.toString(), tab.page)
                metadataDao.updateLastZoom(tab.uri.toString(), tab.zoom)
            } catch (_: Exception) { }
        }
    }

    private fun persistTabSession(tab: PdfWorkspaceTab) {
        viewModelScope.launch {
            try {
                val kp = tab.uri.toString().hashCode()
                dataStore.edit { pref ->
                    pref[intPreferencesKey("pdf_page_$kp")] = tab.page
                    pref[floatPreferencesKey("pdf_zoom_$kp")] = tab.zoom
                }
            } catch (_: Exception) { }
        }
    }

    fun togglePin(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = metadataDao.getMetadata(uri)
                if (current == null) metadataDao.insertMetadata(PdfMetadata(uri, isPinned = true))
                else metadataDao.updatePinned(uri, !current.isPinned)
            } catch (_: Exception) { }
        }
    }

    fun deleteFile(file: PdfFile) {
        viewModelScope.launch {
            val ok = try { repository.deletePdf(file.uri) } catch (_: Exception) { false }
            if (ok) {
                _rawPdfFiles.value = _rawPdfFiles.value.filter { it.uri != file.uri }
                withContext(Dispatchers.IO) {
                    try { metadataDao.deleteByUri(file.uri.toString()) } catch (_: Exception) { }
                }
                textEngine.invalidate(file.uri)
                if (_activeUri.value == file.uri) closeViewer()
            }
        }
    }

    fun renameFile(file: PdfFile, newName: String) {
        viewModelScope.launch {
            val ok = try { repository.renamePdf(file.uri, newName) } catch (_: Exception) { false }
            if (ok) loadPdfFiles()
        }
    }

    fun sharePdf(context: android.content.Context, uri: Uri, title: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share $title"))
        } catch (_: Exception) { }
    }

    fun printPdf(context: android.content.Context, uri: Uri, title: String) {
        try {
            val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
            val jobName = "Toolz - $title"
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return
            printManager.print(jobName, object : android.print.PrintDocumentAdapter() {
                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
                        val output = java.io.FileOutputStream(destination?.fileDescriptor)
                        input.copyTo(output)
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }

                override fun onLayout(
                    oldAttributes: android.print.PrintAttributes?,
                    newAttributes: android.print.PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(title)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }
            }, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
