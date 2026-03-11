package com.frerox.toolz.ui.screens.pdf

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.pdf.PdfAnnotation
import com.frerox.toolz.data.pdf.PdfAnnotationDao
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.data.pdf.PdfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val repository: PdfRepository,
    private val annotationDao: PdfAnnotationDao,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private val _pdfFiles = MutableStateFlow<List<PdfFile>>(emptyList())
    val pdfFiles: StateFlow<List<PdfFile>> = _pdfFiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode = _isNightMode.asStateFlow()

    private val _currentFileUri = MutableStateFlow<Uri?>(null)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val annotations = _currentFileUri.flatMapLatest { uri ->
        if (uri == null) flowOf(emptyList())
        else annotationDao.getAnnotationsForFile(uri.toString())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastPage = MutableStateFlow(0)
    val lastPage = _lastPage.asStateFlow()

    init {
        loadPdfFiles()
    }

    fun loadPdfFiles() {
        viewModelScope.launch {
            _pdfFiles.value = repository.getPdfFiles()
        }
    }

    fun openPdf(uri: Uri) {
        _currentFileUri.value = uri
        _uiState.value = PdfUiState.Viewer(uri)
        loadLastPage(uri)
    }

    private fun loadLastPage(uri: Uri) {
        viewModelScope.launch {
            val key = intPreferencesKey("last_page_${uri.hashCode()}")
            dataStore.data.map { it[key] ?: 0 }.first().let {
                _lastPage.value = it
            }
        }
    }

    fun saveLastPage(pageIndex: Int) {
        val uri = _currentFileUri.value ?: return
        viewModelScope.launch {
            val key = intPreferencesKey("last_page_${uri.hashCode()}")
            dataStore.edit { it[key] = pageIndex }
        }
    }

    fun closeViewer() {
        _uiState.value = PdfUiState.Idle
        _currentFileUri.value = null
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleNightMode() {
        _isNightMode.value = !_isNightMode.value
    }

    sealed class PdfUiState {
        object Idle : PdfUiState()
        object Loading : PdfUiState()
        data class Viewer(val uri: Uri) : PdfUiState()
        data class Error(val message: String) : PdfUiState()
        object PasswordRequired : PdfUiState()
    }
}
