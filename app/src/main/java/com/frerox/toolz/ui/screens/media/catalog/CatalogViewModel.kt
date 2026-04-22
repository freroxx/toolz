package com.frerox.toolz.ui.screens.media.catalog

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.settings.SettingsRepository
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page
import javax.inject.Inject

enum class CatalogMode { TRENDING, SEARCH }

data class CatalogUiState(
    val quickPicks: List<CatalogTrack> = emptyList(),
    val trending: List<CatalogTrack> = emptyList(),
    val tracks: List<CatalogTrack> = emptyList(), // Main results (search or more trending)
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isResolving: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val mode: CatalogMode = CatalogMode.TRENDING,
    val downloadingTracks: Map<String, Float> = emptyMap(),
    val activeDownload: CatalogTrack? = null,
    val showDownloadPopup: Boolean = false,
    val selectedGenre: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState = _uiState.asStateFlow()

    val hasSeenOnboarding: StateFlow<Boolean> = settingsRepository.catalogOnboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadFormat: StateFlow<String> = settingsRepository.downloadFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "M4A")

    val downloadQuality: StateFlow<String> = settingsRepository.downloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "HIGH")

    val showBetaCard: StateFlow<Boolean> = settingsRepository.showCatalogBetaCard
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var nextPage: Page? = null
    private var currentSearchJob: Job? = null
    private val searchQueryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        // Observe all music downloads from WorkManager
        WorkManager.getInstance(context).getWorkInfosByTagFlow("music_download")
            .onEach { workInfos ->
                val downloads = workInfos
                    .filter { !it.state.isFinished }
                    .associate { info ->
                        val trackId = info.tags.find { it.startsWith("download_") }?.removePrefix("download_") ?: ""
                        trackId to info.progress.getFloat("progress", 0f)
                    }
                    .filter { it.key.isNotEmpty() }
                
                _uiState.update { it.copy(downloadingTracks = downloads) }
            }
            .launchIn(viewModelScope)

        // Debounced search
        viewModelScope.launch {
            searchQueryFlow
                .debounce(400)
                .collect { query ->
                    if (query.isNotBlank()) {
                        performSearch(query, isNewSearch = true)
                    } else {
                        loadTrending()
                    }
                }
        }

        // Load initial content
        loadStorefront()
    }

    fun loadStorefront() {
        currentSearchJob?.cancel()
        currentSearchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    mode = CatalogMode.TRENDING,
                    query = "",
                    selectedGenre = null
                )
            }
            try {
                // Fetch multiple sections in parallel
                val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                
                val quickPicksDeferred = viewModelScope.launch {
                    val (tracks, _) = repository.search("music hits $year")
                    _uiState.update { it.copy(quickPicks = tracks.take(6)) }
                }

                val trendingDeferred = viewModelScope.launch {
                    val (tracks, page) = repository.getTrending()
                    nextPage = page
                    _uiState.update { it.copy(trending = tracks, tracks = tracks) }
                }

                quickPicksDeferred.join()
                trendingDeferred.join()
                
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Failed to load storefront"
                    )
                }
            }
        }
    }

    fun loadTrending() {
        loadStorefront()
    }

    fun onGenreSelected(genre: String?) {
        _uiState.update { it.copy(selectedGenre = genre, query = genre ?: "") }
        if (genre != null) {
            viewModelScope.launch {
                performSearch(genre, isNewSearch = true)
            }
        } else {
            loadStorefront()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchQueryFlow.tryEmit(query)
    }

    private suspend fun performSearch(query: String, isNewSearch: Boolean) {
        currentSearchJob?.cancel()
        if (isNewSearch) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    mode = CatalogMode.SEARCH,
                    tracks = emptyList()
                )
            }
        }

        try {
            val (tracks, page) = repository.search(query, if (isNewSearch) null else nextPage)
            nextPage = page
            _uiState.update { state ->
                state.copy(
                    tracks = if (isNewSearch) tracks else state.tracks + tracks,
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (isNewSearch && tracks.isEmpty()) "No results for \"$query\"" else null
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = e.localizedMessage ?: "Search failed"
                )
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || nextPage == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val query = _uiState.value.query
            if (query.isNotBlank()) {
                performSearch(query, isNewSearch = false)
            } else {
                try {
                    val (tracks, page) = repository.search(
                        "trending music ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)}",
                        nextPage
                    )
                    nextPage = page
                    _uiState.update { state ->
                        state.copy(
                            tracks = state.tracks + tracks,
                            isLoadingMore = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    fun downloadTrack(track: CatalogTrack) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDownload = track, showDownloadPopup = true) }
            
            val workRequest = OneTimeWorkRequestBuilder<com.frerox.toolz.worker.MusicDownloadWorker>()
                .setInputData(
                    workDataOf(
                        com.frerox.toolz.worker.MusicDownloadWorker.KEY_TRACK_ID to track.id,
                        com.frerox.toolz.worker.MusicDownloadWorker.KEY_TRACK_TITLE to track.title,
                        com.frerox.toolz.worker.MusicDownloadWorker.KEY_TRACK_ARTIST to track.artist,
                        com.frerox.toolz.worker.MusicDownloadWorker.KEY_SOURCE_URL to track.sourceUrl,
                        com.frerox.toolz.worker.MusicDownloadWorker.KEY_THUMBNAIL_URL to track.thumbnailUrl,
                        com.frerox.toolz.worker.MusicDownloadWorker.KEY_DURATION to track.duration,
                        "format" to downloadFormat.value,
                        "quality" to downloadQuality.value
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("download_${track.id}")
                .addTag("music_download")
                .build()

            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(workRequest)
        }
    }

    fun dismissOnboarding() {
        viewModelScope.launch {
            settingsRepository.setCatalogOnboardingCompleted(true)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            settingsRepository.setCatalogOnboardingCompleted(false)
        }
    }

    fun setDownloadFormat(format: String) {
        viewModelScope.launch { settingsRepository.setDownloadFormat(format) }
    }

    fun setDownloadQuality(quality: String) {
        viewModelScope.launch { settingsRepository.setDownloadQuality(quality) }
    }

    fun setShowBetaCard(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowCatalogBetaCard(show) }
    }

    fun hideDownloadPopup() {
        _uiState.update { it.copy(showDownloadPopup = false) }
    }

    fun showDownloadPopup() {
        _uiState.update { it.copy(showDownloadPopup = true) }
    }

    fun cancelDownload(trackId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$trackId")
        _uiState.update { 
            it.copy(
                activeDownload = if (it.activeDownload?.id == trackId) null else it.activeDownload,
                showDownloadPopup = if (it.activeDownload?.id == trackId) false else it.showDownloadPopup,
                downloadingTracks = it.downloadingTracks - trackId
            )
        }
    }

    /**
     * Resolves the audio stream URL and returns it.
     * The caller (UI) is responsible for passing it to the MusicPlayerViewModel.
     */
    fun resolveAndPlay(
        track: CatalogTrack,
        onStreamResolved: (Uri, String, String, String, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true) }
            try {
                val streamUrl = repository.resolveAudioStream(track.sourceUrl)
                if (streamUrl != null) {
                    onStreamResolved(
                        Uri.parse(streamUrl),
                        track.title,
                        track.artist,
                        track.thumbnailUrl,
                        track.sourceUrl
                    )
                } else {
                    _uiState.update { it.copy(error = "Could not resolve audio stream") }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.localizedMessage ?: "Stream resolution failed")
                }
            } finally {
                _uiState.update { it.copy(isResolving = false) }
            }
        }
    }

    /**
     * Fetch LRC-formatted lyrics from YouTube captions.
     */
    fun fetchCaptionsAsLrc(
        track: CatalogTrack,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val lrc = repository.fetchCaptions(track.sourceUrl)
                onResult(lrc)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun addToPlaylist(playlist: com.frerox.toolz.data.music.Playlist, track: CatalogTrack) {
        viewModelScope.launch {
            val musicTrack = com.frerox.toolz.data.music.MusicTrack(
                uri = track.sourceUrl,
                title = track.title,
                artist = track.artist,
                album = "Catalog",
                duration = track.duration,
                thumbnailUri = track.thumbnailUrl,
                sourceUrl = track.sourceUrl
            )
            musicRepository.insertTrack(musicTrack)
            musicRepository.updatePlaylist(playlist.copy(
                trackUris = (playlist.trackUris + musicTrack.uri).distinct()
            ))
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
