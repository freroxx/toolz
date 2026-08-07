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

package com.frerox.toolz.ui.screens.media.catalog

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.catalog.CatalogSearchEntry
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.catalog.normalizeYoutubeUrl
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.worker.MusicDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.Page
import java.util.Calendar
import javax.inject.Inject

enum class CatalogMode { TRENDING, SEARCH }
enum class LayoutMode { GRID, LIST, FEATURED }

data class CatalogUiState(
    val quickPicks: List<CatalogTrack> = emptyList(),
    val trending: List<CatalogTrack> = emptyList(),
    val justForYou: List<CatalogTrack> = emptyList(),
    val tracks: List<CatalogTrack> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingRecommendations: Boolean = false,
    val isLoadingMoreRecommendations: Boolean = false,
    val isResolving: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val mode: CatalogMode = CatalogMode.TRENDING,
    val layoutMode: LayoutMode = LayoutMode.LIST,
    val downloadingTracks: Map<String, Float> = emptyMap(),
    val activeDownload: CatalogTrack? = null,
    val showDownloadPopup: Boolean = false,
    val downloadError: String? = null,
    val selectedGenre: String? = null,
    val recommendationTitle: String = "Just for you",
    val canLoadMore: Boolean = true,
    val offlineSongToPlay: CatalogTrack? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val appDatabase: com.frerox.toolz.data.AppDatabase,
    val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState = _uiState.asStateFlow()

    val hasSeenOnboarding: StateFlow<Boolean> = settingsRepository.catalogOnboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val downloadFormat: StateFlow<String> = settingsRepository.downloadFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "M4A")

    val downloadQuality: StateFlow<String> = settingsRepository.downloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "HIGH")

    val catalogStreamQuality: StateFlow<String> = settingsRepository.catalogStreamQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "AUTO")

    val showBetaCard: StateFlow<Boolean> = settingsRepository.showCatalogBetaCard
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var nextPage: Page? = null
    private var currentSearchJob: Job? = null
    private val searchQueryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private var recommendationPage: Page? = null
    private var recommendationQueries: List<String> = emptyList()
    private var recommendationQueryIndex = 0
    private var recommendationCycle = 0
    private var storefrontRefreshCount = 0
    private var currentContextTrack: MusicTrack? = null
    private var lastRefreshTime = 0L
    private val REFRESH_THRESHOLD = 30_000L // 30 seconds
    private var storefrontJob: Job? = null
    private val networkSemaphore = kotlinx.coroutines.sync.Semaphore(8)

    init {
        // Restore active download track if any
        viewModelScope.launch {
            val json = settingsRepository.activeDownloadJson.first()
            if (json != null) {
                try {
                    val track = Json.decodeFromString<CatalogTrack>(json)
                    _uiState.update { it.copy(activeDownload = track) }
                } catch (_: Exception) {}
            }
        }

        val handler = CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("CatalogVM", "Unhandled exception: ${throwable.message}")
            _uiState.update { it.copy(isLoading = false, error = "Extraction engine encountered an error. Please restart the tab.") }
        }

        WorkManager.getInstance(context).getWorkInfosByTagFlow(MusicDownloadWorker.TAG_MUSIC_DOWNLOAD)
            .onEach { workInfos ->
                val taggedInfos = workInfos.mapNotNull { info ->
                    val trackId = info.tags
                        .firstOrNull { it.startsWith(MusicDownloadWorker.TAG_DOWNLOAD_PREFIX) }
                        ?.removePrefix(MusicDownloadWorker.TAG_DOWNLOAD_PREFIX)
                        ?.takeIf { it.isNotBlank() }
                    trackId?.let { it to info }
                }

                val effectiveInfos = taggedInfos
                    .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                    .map { (trackId, infos) ->
                        val info = infos.firstOrNull { !it.state.isFinished }
                            ?: infos.firstOrNull { it.state == WorkInfo.State.FAILED }
                            ?: infos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                            ?: infos.first()
                        trackId to info
                    }

                _uiState.update { current ->
                    var downloads = current.downloadingTracks
                    var activeDownload = current.activeDownload
                    var showDownloadPopup = current.showDownloadPopup
                    var downloadError: String? = null

                    effectiveInfos.forEach { (trackId, info) ->
                        when (info.state) {
                            WorkInfo.State.ENQUEUED,
                            WorkInfo.State.RUNNING,
                            WorkInfo.State.BLOCKED -> {
                                val previous = downloads[trackId] ?: 0.02f
                                val next = info.progress
                                    .getFloat(MusicDownloadWorker.KEY_PROGRESS, previous)
                                    .coerceIn(0.02f, 0.99f)
                                downloads = downloads + (trackId to maxOf(previous, next))
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                downloads = (downloads + (trackId to 1f))
                                if (activeDownload?.id == trackId) {
                                    activeDownload = null
                                    showDownloadPopup = false
                                    viewModelScope.launch { settingsRepository.setActiveDownloadJson(null) }
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                downloads = downloads - trackId
                                if (activeDownload?.id == trackId) {
                                    activeDownload = null
                                    showDownloadPopup = false
                                    downloadError = "Download failed. Check the format, storage access, or connection."
                                    viewModelScope.launch { settingsRepository.setActiveDownloadJson(null) }
                                }
                            }
                            WorkInfo.State.CANCELLED -> {
                                downloads = downloads - trackId
                                if (activeDownload?.id == trackId) {
                                    activeDownload = null
                                    showDownloadPopup = false
                                    viewModelScope.launch { settingsRepository.setActiveDownloadJson(null) }
                                }
                            }
                        }
                    }

                    current.copy(
                        downloadingTracks = downloads.filterValues { it < 1f },
                        activeDownload = activeDownload,
                        showDownloadPopup = showDownloadPopup && activeDownload != null,
                        downloadError = downloadError
                    )
                }
            }
            .launchIn(viewModelScope + handler)

        viewModelScope.launch(handler) {
            searchQueryFlow
                .debounce(500)
                .collect { query ->
                    currentSearchJob?.cancel()
                    if (query.isBlank()) {
                        loadStorefront(currentContextTrack)
                    } else {
                        currentSearchJob = (viewModelScope + handler).launch {
                            try {
                                performSearch(query, isNewSearch = true)
                            } catch (t: Throwable) {
                                android.util.Log.e("CatalogVM", "Inner search error: ${t.message}")
                            }
                        }
                    }
                }
        }

        loadStorefront()
    }

    fun refreshOnOpen(currentTrack: MusicTrack?, force: Boolean = false) {
        currentContextTrack = currentTrack
        val now = System.currentTimeMillis()
        if (_uiState.value.query.isBlank() && (force || now - lastRefreshTime > REFRESH_THRESHOLD)) {
            loadStorefront(currentTrack)
        }
    }

    fun loadStorefront(currentTrack: MusicTrack? = currentContextTrack, isRefresh: Boolean = false) {
        currentContextTrack = currentTrack
        storefrontJob?.cancel()
        storefrontJob = viewModelScope.launch {
            val isInitialLoad = _uiState.value.quickPicks.isEmpty()
            lastRefreshTime = System.currentTimeMillis()
            storefrontRefreshCount++
            
            _uiState.update {
                it.copy(
                    isLoading = isInitialLoad && !isRefresh,
                    isRefreshing = isRefresh,
                    isLoadingRecommendations = true,
                    error = null,
                    mode = CatalogMode.TRENDING,
                    query = "",
                    selectedGenre = null,
                    canLoadMore = true
                )
            }

            try {
                coroutineScope {
                    val quickPicksDeferred = async { fetchQuickPicks(currentTrack) }
                    val trendingDeferred = async { fetchTrendingTracks() }

                    val quickPicks = quickPicksDeferred.await()
                    val trending = trendingDeferred.await()

                    _uiState.update { state ->
                        state.copy(
                            quickPicks = quickPicks,
                            trending = trending,
                            isLoading = if (state.query.isBlank()) false else state.isLoading,
                            tracks = if (state.query.isBlank()) emptyList() else state.tracks
                        )
                    }

                    loadJustForYou(reset = true, currentTrack = currentTrack)
                }
            } catch (e: CancellationException) {
                // Ignore cancellation
            } catch (t: Throwable) {
                android.util.Log.e("CatalogVM", "Storefront load failed: ${t.message}")
                _uiState.update { state ->
                    state.copy(
                        isLoading = if (state.query.isBlank()) false else state.isLoading,
                        isLoadingRecommendations = false,
                        error = "Unable to reach extraction engine. Search might still work."
                    )
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun loadTrending() {
        loadStorefront(currentContextTrack, isRefresh = true)
    }

    fun onGenreSelected(genre: String?) {
        _uiState.update { it.copy(selectedGenre = genre, query = genre ?: "") }
        if (genre == null) {
            loadStorefront(currentContextTrack)
        } else {
            searchQueryFlow.tryEmit(genre)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(query = query, isLoading = query.isNotBlank()) }
        searchQueryFlow.tryEmit(query)
    }

    fun loadMore() {
        if (_uiState.value.query.isNotBlank()) {
            if (_uiState.value.isLoading || _uiState.value.isLoadingMore || nextPage == null) return
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                try {
                    performSearch(_uiState.value.query, isNewSearch = false)
                } catch (t: Throwable) {
                    android.util.Log.e("CatalogVM", "Load more search error: ${t.message}")
                }
            }
            return
        }

        if (_uiState.value.isLoadingRecommendations || _uiState.value.isLoadingMoreRecommendations) return
        viewModelScope.launch { 
            try {
                loadJustForYou(reset = false, currentTrack = currentContextTrack)
            } catch (t: Throwable) {
                android.util.Log.e("CatalogVM", "Load more recs error: ${t.message}")
            }
        }
    }

    fun downloadTrack(track: CatalogTrack) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeDownload = track,
                    showDownloadPopup = true,
                    downloadingTracks = it.downloadingTracks + (track.id to 0.02f),
                    downloadError = null
                )
            }
            settingsRepository.setActiveDownloadJson(Json.encodeToString(track))

            val workRequest = OneTimeWorkRequestBuilder<MusicDownloadWorker>()
                .setInputData(
                    workDataOf(
                        MusicDownloadWorker.KEY_TRACK_ID to track.id,
                        MusicDownloadWorker.KEY_TRACK_TITLE to track.title,
                        MusicDownloadWorker.KEY_TRACK_ARTIST to track.artist,
                        MusicDownloadWorker.KEY_SOURCE_URL to track.sourceUrl,
                        MusicDownloadWorker.KEY_THUMBNAIL_URL to track.thumbnailUrl,
                        MusicDownloadWorker.KEY_DURATION to track.duration,
                        MusicDownloadWorker.KEY_FORMAT to downloadFormat.value,
                        MusicDownloadWorker.KEY_QUALITY to downloadQuality.value
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("${MusicDownloadWorker.TAG_DOWNLOAD_PREFIX}${track.id}")
                .addTag(MusicDownloadWorker.TAG_MUSIC_DOWNLOAD)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "music_download_${track.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    fun setLayoutMode(mode: LayoutMode) {
        _uiState.update { it.copy(layoutMode = mode) }
    }

    fun dismissOnboarding() {
        viewModelScope.launch { settingsRepository.setCatalogOnboardingCompleted(true) }
    }

    fun resetOnboarding() {
        viewModelScope.launch { settingsRepository.setCatalogOnboardingCompleted(false) }
    }

    fun setDownloadFormat(format: String) {
        viewModelScope.launch { settingsRepository.setDownloadFormat(format) }
    }

    fun setDownloadQuality(quality: String) {
        viewModelScope.launch { settingsRepository.setDownloadQuality(quality) }
    }

    fun setCatalogStreamQuality(quality: String) {
        viewModelScope.launch { settingsRepository.setCatalogStreamQuality(quality) }
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

    fun hideOfflineNotice() {
        _uiState.update { it.copy(offlineSongToPlay = null) }
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
        if (trackId == _uiState.value.activeDownload?.id) {
            viewModelScope.launch { settingsRepository.setActiveDownloadJson(null) }
        }
    }

    fun resolveAndPlay(
        track: CatalogTrack,
        forceOnline: Boolean = false,
        onStreamResolved: (Uri, String, String, String, String) -> Unit
    ) {
        viewModelScope.launch {
            // 1. Check if the song has already been downloaded (offline playback)
            val normalizedUrl = track.sourceUrl.normalizeYoutubeUrl()
            val localTrack = withContext(Dispatchers.IO) {
                musicRepository.getTrackBySourceUrl(normalizedUrl) ?: musicRepository.getTrackBySourceUrl(track.sourceUrl)
            }
            
            if (localTrack != null && localTrack.uri.isNotBlank() && !forceOnline) {
                // If user didn't explicitly ask for online, and it's their first time clicking this track today,
                // show the smart notice popup.
                if (_uiState.value.offlineSongToPlay == null) {
                    _uiState.update { it.copy(offlineSongToPlay = track) }
                    return@launch
                }
                
                val localUri = Uri.parse(localTrack.uri)
                onStreamResolved(
                    localUri,
                    localTrack.title,
                    localTrack.artist ?: "Unknown Artist",
                    localTrack.thumbnailUri ?: track.thumbnailUrl ?: "",
                    track.sourceUrl
                )
                _uiState.update { it.copy(offlineSongToPlay = null) }
                return@launch
            }

            _uiState.update { it.copy(isResolving = true, offlineSongToPlay = null) }
            try {
                // 2. Resolve stream online
                var streamUrl: String? = null
                var retryCount = 0
                val maxRetries = 3
                var lastError: Throwable? = null
                
                while (streamUrl == null && retryCount < maxRetries) {
                    if (retryCount > 0) {
                        // Exponential backoff: 1s, 2s, 4s...
                        val delayMs = (1000L * Math.pow(2.0, (retryCount - 1).toDouble())).toLong()
                        delay(delayMs)
                        android.util.Log.d("CatalogVM", "Retrying resolution (attempt ${retryCount + 1}) after ${delayMs}ms")
                    }
                    
                    try {
                        // Use requested quality for first attempt, then AUTO/MEDIUM as fallbacks
                        val attemptQuality = when (retryCount) {
                            0 -> catalogStreamQuality.value
                            1 -> "AUTO"
                            else -> "MEDIUM"
                        }
                        
                        streamUrl = withContext(Dispatchers.IO) {
                            repository.resolveAudioStream(track.sourceUrl, attemptQuality)
                        }
                    } catch (e: Exception) {
                        lastError = e
                        if (e is CancellationException) throw e
                        
                        android.util.Log.w("CatalogVM", "Resolution attempt $retryCount failed: ${e.message}")
                        
                        // If it's a "Forbidden" error, retrying might not help immediately, 
                        // but we try with different qualities just in case.
                    }
                    retryCount++
                }
                
                if (streamUrl != null) {
                    onStreamResolved(
                        Uri.parse(streamUrl),
                        track.title,
                        track.artist,
                        track.thumbnailUrl,
                        track.sourceUrl
                    )
                } else {
                    val errorMessage = when (val e = lastError) {
                        is CatalogRepository.StreamResolutionException -> {
                            when (e.causeType) {
                                "REGION_RESTRICTED" -> "This content is not available in your region."
                                "AGE_RESTRICTED" -> "This content is age-restricted and cannot be played."
                                "FORBIDDEN" -> "Access denied by provider. Please try again later."
                                "UNPLAYABLE" -> "This track cannot be played at the moment."
                                else -> e.message ?: "Could not resolve audio stream."
                            }
                        }
                        else -> "Could not resolve audio stream. Check your connection."
                    }
                    _uiState.update { it.copy(error = errorMessage) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage ?: "Stream resolution failed") }
            } finally {
                _uiState.update { it.copy(isResolving = false) }
            }
        }
    }

    fun fetchCaptionsAsLrc(track: CatalogTrack, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.fetchCaptions(track.sourceUrl) }
                .onSuccess(onResult)
                .onFailure { onResult(null) }
        }
    }

    fun addToPlaylist(playlist: Playlist, track: CatalogTrack) {
        viewModelScope.launch {
            val musicTrack = MusicTrack(
                uri = track.sourceUrl,
                title = track.title,
                artist = track.artist,
                album = "Catalog",
                duration = track.duration,
                thumbnailUri = track.thumbnailUrl,
                sourceUrl = track.sourceUrl
            )
            musicRepository.insertTrack(musicTrack)
            musicRepository.updatePlaylist(
                playlist.copy(trackUris = (playlist.trackUris + musicTrack.uri).distinct())
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun performSearch(query: String, isNewSearch: Boolean) {
        if (isNewSearch) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    mode = CatalogMode.SEARCH,
                    tracks = emptyList(),
                    canLoadMore = true
                )
            }
        } else {
            _uiState.update { it.copy(isLoadingMore = true) }
        }

        try {
            val (tracks, page) = repository.search(query, if (isNewSearch) null else nextPage)
            nextPage = page
            val sanitized = sanitizeTracks(tracks)

            if (isNewSearch && query.isNotBlank()) {
                viewModelScope.launch(Dispatchers.IO) {
                    appDatabase.catalogSearchDao().insertSearch(CatalogSearchEntry(query = query))
                }
            }

            _uiState.update { state ->
                state.copy(
                    tracks = if (isNewSearch) sanitized else (state.tracks + sanitized).distinctBy { it.sourceUrl },
                    isLoading = false,
                    isLoadingMore = false,
                    canLoadMore = page != null,
                    error = if (isNewSearch && sanitized.isEmpty()) "No results for \"$query\"" else null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.e("CatalogVM", "Search failed: ${t.message}")
            val errorMessage = when {
                t.message?.contains("403") == true -> "YouTube restricted access. Please try again later."
                t.message?.contains("429") == true -> "Too many requests. YouTube is rate-limiting the app."
                t.message?.contains("HTML") == true -> "YouTube returned an unexpected response. Retrying search might help."
                else -> t.localizedMessage ?: "Search failed. Check your connection."
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = errorMessage
                )
            }
        }
    }

    private suspend fun fetchQuickPicks(currentTrack: MusicTrack?): List<CatalogTrack> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val timeEnergy = when {
            hour in 5..10 -> "acoustic morning chill"
            hour in 11..14 -> "upbeat workday focus"
            hour in 15..18 -> "high energy hits"
            hour in 19..22 -> "evening lounge vibes"
            else -> "midnight deep focus lo-fi"
        }

        val queries = buildList {
            currentTrack?.artist?.takeIf { it.isNotBlank() }?.let { add("$it essentials official audio") }
            currentTrack?.let { add("${cleanSeedTitle(it.title)} ${it.artist.orEmpty()} official audio") }
            add("fresh $timeEnergy $year")
            add("best songs $year official audio")
            add("trending $timeEnergy")
        }

        return rotateTracks(
            candidates = collectDistinctTracks(queries, limit = 32),
            desired = 6,
            seedOffset = storefrontRefreshCount * 3
        )
    }

    private suspend fun fetchTrendingTracks(): List<CatalogTrack> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.US).orEmpty()
        val queries = listOf(
            "top songs $year official audio",
            "viral songs $month $year official audio",
            "trending music $year official audio",
            "global hits $year official audio"
        )

        return rotateTracks(
            candidates = collectDistinctTracks(queries, limit = 40),
            desired = 9,
            seedOffset = storefrontRefreshCount * 5
        )
    }

    private suspend fun loadJustForYou(reset: Boolean, currentTrack: MusicTrack?) {
        if (reset) {
            recommendationCycle++
            recommendationPage = null
            recommendationQueryIndex = 0
            recommendationQueries = buildRecommendationQueries(currentTrack, recommendationCycle)
            val title = buildRecommendationTitle(currentTrack)
            _uiState.update {
                it.copy(
                    justForYou = emptyList(),
                    isLoadingRecommendations = true,
                    isLoadingMoreRecommendations = false,
                    recommendationTitle = title
                )
            }
        } else {
            _uiState.update { it.copy(isLoadingMoreRecommendations = true) }
        }

        val existingUrls = _uiState.value.justForYou.mapTo(mutableSetOf()) { it.sourceUrl }
        var attempts = 0
        var appended = emptyList<CatalogTrack>()
        val maxAttempts = 3 // Don't loop forever if YouTube is blocking us

        while (attempts < recommendationQueries.size.coerceAtMost(maxAttempts) && appended.isEmpty()) {
            if (recommendationQueries.isEmpty()) {
                recommendationQueries = buildRecommendationQueries(currentTrack, recommendationCycle)
            }

            val query = recommendationQueries.getOrElse(recommendationQueryIndex) { "discover new music official audio" }
            try {
                val (tracks, page) = repository.search(query, recommendationPage)
                val fresh = sanitizeTracks(tracks).filterNot { existingUrls.contains(it.sourceUrl) }

                if (fresh.isNotEmpty()) {
                    appended = fresh
                    recommendationPage = page
                } else if (page != null) {
                    recommendationPage = page
                    // Increment attempts if we got no fresh tracks to eventually break the loop
                    attempts++
                } else {
                    recommendationPage = null
                    recommendationQueryIndex = (recommendationQueryIndex + 1) % recommendationQueries.size.coerceAtLeast(1)
                    attempts++
                }
            } catch (t: Throwable) {
                android.util.Log.e("CatalogVM", "Recommendation query failed: $query - ${t.message}")
                recommendationPage = null
                recommendationQueryIndex = (recommendationQueryIndex + 1) % recommendationQueries.size.coerceAtLeast(1)
                attempts++
                delay(500)
            }
        }

        if (appended.isEmpty() && !reset) {
            recommendationCycle++
            recommendationPage = null
            recommendationQueryIndex = 0
            recommendationQueries = buildRecommendationQueries(currentTrack, recommendationCycle)
        }

        _uiState.update { state ->
            state.copy(
                justForYou = (state.justForYou + appended).distinctBy { it.sourceUrl },
                isLoadingRecommendations = false,
                isLoadingMoreRecommendations = false,
                canLoadMore = appended.isNotEmpty() || (recommendationPage != null && attempts < maxAttempts)
            )
        }
    }

    private suspend fun collectDistinctTracks(queries: List<String>, limit: Int): List<CatalogTrack> = coroutineScope {
        // Controlled concurrency using a Semaphore to prevent crashes and bot detection
        val jobs = queries.distinct().take(12).map { query ->
            async {
                networkSemaphore.withPermit {
                    try {
                        repository.search(query).first
                    } catch (t: Throwable) {
                        android.util.Log.e("CatalogVM", "Parallel search failed for $query: ${t.message}")
                        emptyList<CatalogTrack>()
                    }
                }
            }
        }

        val results = jobs.awaitAll()
        val collected = LinkedHashMap<String, CatalogTrack>()
        
        results.forEach { tracks ->
            sanitizeTracks(tracks).forEach { track ->
                if (collected.size < limit) {
                    collected.putIfAbsent(track.sourceUrl, track)
                }
            }
        }
        
        collected.values.toList()
    }

    private fun sanitizeTracks(tracks: List<CatalogTrack>): List<CatalogTrack> {
        return tracks
            .filter { it.sourceUrl.isNotBlank() && it.duration >= 45_000L }
            .distinctBy { it.sourceUrl }
    }

    private fun rotateTracks(candidates: List<CatalogTrack>, desired: Int, seedOffset: Int): List<CatalogTrack> {
        if (candidates.size <= desired) return candidates
        val start = seedOffset.mod(candidates.size)
        return List(desired) { index -> candidates[(start + index) % candidates.size] }
    }

    private suspend fun buildRecommendationQueries(currentTrack: MusicTrack?, cycle: Int): List<String> {
        val now = System.currentTimeMillis()
        val allTracks = musicRepository.allTracks.first()
        val playlists = musicRepository.allPlaylists.first()
        val userSearches = appDatabase.catalogSearchDao().getAllSearchesSync()
        
        val playlistTrackUris = playlists.filterNot { it.isSystemPlaylist }.flatMap { it.trackUris }.toSet()
        
        // Tier 0: User Search History (highest priority discovery)
        val searchSeeds = userSearches.sortedByDescending { it.timestamp }.take(15).map { it.query }

        // Folder Intelligence: Identify dominant folders
        val folderKeywords = allTracks
            .mapNotNull { track -> 
                track.path?.let { p ->
                    val file = java.io.File(p)
                    file.parentFile?.name?.takeIf { it.length > 3 && it != "Music" && it != "Download" && it != "Toolz Downloads" }
                }
            }
            .groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }.take(8).map { it.first }

        val scoredSeeds = allTracks
            .filter { 
                it.playCount > 0 || 
                it.lastPlayed > 0L || 
                it.isFavorite || 
                it.uri == currentTrack?.uri || 
                it.album == "Toolz Downloads" ||
                playlistTrackUris.contains(it.uri)
            }
            .sortedByDescending { track ->
                val daysSinceLastPlay = if (track.lastPlayed > 0L) {
                    ((now - track.lastPlayed).coerceAtLeast(0L) / 86_400_000L).coerceAtMost(365L)
                } else 365L
                
                val recencyScore = (365L - daysSinceLastPlay) * 6.0f // Heavy weighting for recency
                
                val isDownloaded = track.album == "Toolz Downloads" || track.path != null
                val isInPlaylist = playlistTrackUris.contains(track.uri)
                val loyaltyScore = (track.playCount * 800L) + // Massive play count boost
                                  (if (track.isFavorite) 12000L else 0L) + 
                                  (if (isDownloaded) 8000L else 0L) +
                                  (if (isInPlaylist) 5000L else 0L)
                
                val currentBoost = if (track.uri == currentTrack?.uri) 20000L else 0L
                
                loyaltyScore + recencyScore + currentBoost
            }
            .distinctBy { "${cleanSeedTitle(it.title)}|${it.artist.orEmpty().lowercase()}" }

        val coreSeeds = scoredSeeds.take(60)
        val highIntentSeeds = scoredSeeds.filter { it.isFavorite || it.playCount > 3 || it.album == "Toolz Downloads" }.take(30)
        
        val topArtists = coreSeeds.mapNotNull { it.artist }.filter { it != "Unknown Artist" && it.length > 2 }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(15).map { it.first }
            
        val genres = coreSeeds.flatMap { track ->
            val aiThemes = track.aiSongMeaning?.split(" ")?.filter { it.length > 6 }?.take(3) ?: emptyList()
            listOfNotNull(track.album, track.artist).filter { it.length > 4 && it != "Unknown Artist" && it != "Toolz Downloads" } + aiThemes
        }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(20).map { it.first }

        val playlistNames = playlists.filterNot { it.isSystemPlaylist || it.name.length < 4 }.map { it.name }

        val explorers = listOf(
            "Underground gems", "Alternative discovery", "Neo soul essentials",
            "Indie electronic", "Synthwave 2024", "Deep house selections",
            "Acoustic morning", "Lo-fi study beats", "Phonk gym mix",
            "Afrobeats dance", "Amapiano viral", "K-pop new wave",
            "Modern jazz fusion", "Ambient sleep", "Post-rock soundscapes"
        )

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val timeContext = when {
            hour in 5..10 -> listOf("Morning coffee acoustic", "Productive energy", "Sunrise vibes")
            hour in 11..14 -> listOf("Lunchtime chill", "Workday focus", "Midday electronic")
            hour in 15..18 -> listOf("Afternoon boost", "Commute radio", "Alternative drive")
            hour in 19..22 -> listOf("Evening lounge", "Sunset chill", "Late night R&B")
            else -> listOf("Midnight study", "Ambient night", "Sleepy piano", "Deep lo-fi")
        }
        
        // Tiered Seed System
        val tier1Seeds = buildList {
            // New Tier 0: Recent user searches (Highest discovery signal)
            addAll(searchSeeds.map { "$it official audio" })
            if (cycle % 2 == 0) addAll(searchSeeds.map { "songs similar to $it" })

            currentTrack?.let { track ->
                val title = cleanSeedTitle(track.title)
                val artist = track.artist.orEmpty().trim()
                if (title.isNotBlank() && artist.isNotBlank()) {
                    add("$title $artist official")
                    add("more from $artist")
                }
            }
            // Favorites boost
            highIntentSeeds.filter { it.isFavorite }.take(10).forEach { track ->
                add("${cleanSeedTitle(track.title)} ${track.artist.orEmpty()} studio")
            }
        }

        // Tier 2: Related Artists (Asynchronous expansion)
        val relatedArtists = if (currentTrack != null && currentTrack.sourceUrl != null) {
            val videoId = currentTrack.sourceUrl.substringAfter("v=").substringBefore("&")
            repository.innerTubeClient.getRelatedArtists(videoId)
        } else emptyList<String>()

        val tier2Seeds = buildList {
            relatedArtists.forEach { artist ->
                add("$artist essentials mix")
                add("best of $artist studio")
                if (cycle % 2 == 0) add("similar to $artist")
            }
        }

        val queries = buildList {
            addAll(tier1Seeds)
            addAll(tier2Seeds)

            // Tier 2.5: Top Artists discovery
            topArtists.forEach { artist ->
                add("$artist essential songs")
                if (cycle % 3 == 0) add("new releases from $artist")
            }

            // Tier 3: Moods & Genres
            genres.forEach { seed ->
                add("best of $seed")
                add("modern $seed mix")
                if (cycle % 2 == 0) add("underrated $seed gems")
            }

            // Tier 4: Folders
            folderKeywords.forEach { folder ->
                add("$folder music mix")
                if (cycle % 2 == 0) add("songs like $folder")
            }

            playlistNames.forEach { name ->
                add("$name official audio")
            }

            timeContext.forEach { add(it) }

            repeat(4) { i ->
                add(explorers[(cycle + i) % explorers.size])
            }
            
            add("global viral music 2024")
        }

        return queries
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .let { list ->
                // Strong weight to Tier 1 and 2
                val prioritized = list.take(15) 
                val others = list.drop(15).shuffled()
                (prioritized + others).take(128)
            }
    }

    private suspend fun buildRecommendationTitle(currentTrack: MusicTrack?): String {
        val allTracks = musicRepository.allTracks.first()
        val playlists = musicRepository.allPlaylists.first()

        // High priority: Currently playing context
        if (currentTrack != null) {
            val topArtist = currentTrack.artist.takeIf { it != "Unknown Artist" }
            if (topArtist != null && Math.random() > 0.3) return "Inspired by $topArtist"
        }

        // Mid priority: Most played artist
        val topArtist = allTracks.filter { it.artist != "Unknown Artist" }
            .groupingBy { it.artist }.eachCount()
            .maxByOrNull { it.value }?.key
        if (topArtist != null && Math.random() > 0.5) return "Because you like $topArtist"

        // Mid priority: Dominant Folder
        val topFolder = allTracks.mapNotNull { track -> 
            track.path?.let { p ->
                val file = java.io.File(p)
                file.parentFile?.name?.takeIf { it.length > 3 && it != "Music" && it != "Download" }
            }
        }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        if (topFolder != null && Math.random() > 0.6) return "From your $topFolder collection"

        // Mid priority: Custom Playlist
        val topPlaylist = playlists.filterNot { it.isSystemPlaylist }.maxByOrNull { it.trackUris.size }?.name
        if (topPlaylist != null && Math.random() > 0.7) return "More like $topPlaylist"

        return listOf(
            "Just for you",
            "Your daily mix",
            "Discover new sounds",
            "Fresh picks for you",
            "Explore more styles",
            "Trending for you"
        ).random()
    }

    private fun cleanSeedTitle(title: String): String {
        return title
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
            .replace(Regex("official|audio|video|lyrics|visualizer", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
