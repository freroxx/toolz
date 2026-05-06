package com.frerox.toolz.ui.screens.media.catalog

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import org.schabi.newpipe.extractor.Page

enum class CatalogMode { TRENDING, SEARCH }
enum class LayoutMode { GRID, LIST }

data class CatalogUiState(
    val quickPicks: List<CatalogTrack> = emptyList(),
    val trending: List<CatalogTrack> = emptyList(),
    val justForYou: List<CatalogTrack> = emptyList(),
    val tracks: List<CatalogTrack> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoadingRecommendations: Boolean = false,
    val isLoadingMoreRecommendations: Boolean = false,
    val isResolving: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val mode: CatalogMode = CatalogMode.TRENDING,
    val layoutMode: LayoutMode = LayoutMode.GRID,
    val downloadingTracks: Map<String, Float> = emptyMap(),
    val activeDownload: CatalogTrack? = null,
    val showDownloadPopup: Boolean = false,
    val selectedGenre: String? = null,
    val recommendationTitle: String = "Just for you"
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

    init {
        WorkManager.getInstance(context).getWorkInfosByTagFlow("music_download")
            .onEach { workInfos ->
                val downloads = workInfos
                    .filter { !it.state.isFinished }
                    .associate { info ->
                        val trackId = info.tags.find { it.startsWith("download_") }?.removePrefix("download_") ?: ""
                        trackId to info.progress.getFloat("progress", 0f)
                    }
                    .filterKeys { it.isNotEmpty() }

                _uiState.update { it.copy(downloadingTracks = downloads) }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            searchQueryFlow
                .debounce(350)
                .collect { query ->
                    if (query.isBlank()) {
                        loadStorefront(currentContextTrack)
                    } else {
                        performSearch(query, isNewSearch = true)
                    }
                }
        }

        loadStorefront()
    }

    fun refreshOnOpen(currentTrack: MusicTrack?) {
        currentContextTrack = currentTrack
        if (_uiState.value.query.isBlank()) {
            loadStorefront(currentTrack)
        }
    }

    fun loadStorefront(currentTrack: MusicTrack? = currentContextTrack) {
        currentContextTrack = currentTrack
        currentSearchJob?.cancel()
        currentSearchJob = viewModelScope.launch {
            storefrontRefreshCount++
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isLoadingRecommendations = true,
                    error = null,
                    mode = CatalogMode.TRENDING,
                    query = "",
                    selectedGenre = null,
                    quickPicks = emptyList(),
                    trending = emptyList(),
                    justForYou = emptyList(),
                    tracks = emptyList()
                )
            }

            try {
                val quickPicks = fetchQuickPicks(currentTrack)
                val trending = fetchTrendingTracks()

                _uiState.update {
                    it.copy(
                        quickPicks = quickPicks,
                        trending = trending,
                        isLoading = false
                    )
                }

                loadJustForYou(reset = true, currentTrack = currentTrack)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingRecommendations = false,
                        error = e.localizedMessage ?: "Failed to load catalog"
                    )
                }
            }
        }
    }

    fun loadTrending() {
        loadStorefront(currentContextTrack)
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
        _uiState.update { it.copy(query = query) }
        searchQueryFlow.tryEmit(query)
    }

    fun loadMore() {
        if (_uiState.value.query.isNotBlank()) {
            if (_uiState.value.isLoading || _uiState.value.isLoadingMore || nextPage == null) return
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMore = true) }
                performSearch(_uiState.value.query, isNewSearch = false)
            }
            return
        }

        if (_uiState.value.isLoadingRecommendations || _uiState.value.isLoadingMoreRecommendations) return
        viewModelScope.launch { loadJustForYou(reset = false, currentTrack = currentContextTrack) }
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

            WorkManager.getInstance(context).enqueue(workRequest)
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

    fun resolveAndPlay(
        track: CatalogTrack,
        onStreamResolved: (Uri, String, String, String, String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isResolving = true) }
            try {
                val quality = catalogStreamQuality.value
                val streamUrl = repository.resolveAudioStream(track.sourceUrl, quality)
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
            musicRepository.updatePlaylist(
                playlist.copy(trackUris = (playlist.trackUris + musicTrack.uri).distinct())
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
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
            val sanitized = sanitizeTracks(tracks)

            _uiState.update { state ->
                state.copy(
                    tracks = if (isNewSearch) sanitized else (state.tracks + sanitized).distinctBy { it.sourceUrl },
                    isLoading = false,
                    isLoadingMore = false,
                    error = if (isNewSearch && sanitized.isEmpty()) "No results for \"$query\"" else null
                )
            }
        } catch (e: CancellationException) {
            throw e
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

    private suspend fun fetchQuickPicks(currentTrack: MusicTrack?): List<CatalogTrack> {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val queries = buildList {
            currentTrack?.artist?.takeIf { it.isNotBlank() }?.let { add("$it essentials official audio") }
            currentTrack?.let { add("${cleanSeedTitle(it.title)} ${it.artist.orEmpty()} official audio") }
            add("fresh hits $year official audio")
            add("best songs $year official audio")
            add("new music releases $year official audio")
        }

        return rotateTracks(
            candidates = collectDistinctTracks(queries, limit = 24),
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
            desired = 8,
            seedOffset = storefrontRefreshCount * 5
        )
    }

    private suspend fun loadJustForYou(reset: Boolean, currentTrack: MusicTrack?) {
        if (reset) {
            recommendationCycle++
            recommendationPage = null
            recommendationQueryIndex = 0
            recommendationQueries = buildRecommendationQueries(currentTrack, recommendationCycle)
            _uiState.update {
                it.copy(
                    justForYou = emptyList(),
                    isLoadingRecommendations = true,
                    isLoadingMoreRecommendations = false,
                    recommendationTitle = buildRecommendationTitle(currentTrack)
                )
            }
        } else {
            _uiState.update { it.copy(isLoadingMoreRecommendations = true) }
        }

        val existingUrls = _uiState.value.justForYou.mapTo(mutableSetOf()) { it.sourceUrl }
        var attempts = 0
        var appended = emptyList<CatalogTrack>()

        while (attempts < recommendationQueries.size.coerceAtLeast(1) && appended.isEmpty()) {
            if (recommendationQueries.isEmpty()) {
                recommendationQueries = buildRecommendationQueries(currentTrack, recommendationCycle)
            }

            val query = recommendationQueries.getOrElse(recommendationQueryIndex) { "discover new music official audio" }
            val (tracks, page) = repository.search(query, recommendationPage)
            val fresh = sanitizeTracks(tracks).filterNot { existingUrls.contains(it.sourceUrl) }

            if (fresh.isNotEmpty()) {
                appended = fresh
                recommendationPage = page
            } else if (page != null) {
                recommendationPage = page
            } else {
                recommendationPage = null
                recommendationQueryIndex = (recommendationQueryIndex + 1) % recommendationQueries.size.coerceAtLeast(1)
                attempts++
            }

            if (page == null && fresh.isNotEmpty()) {
                recommendationQueryIndex = (recommendationQueryIndex + 1) % recommendationQueries.size.coerceAtLeast(1)
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
                isLoadingMoreRecommendations = false
            )
        }
    }

    private suspend fun collectDistinctTracks(queries: List<String>, limit: Int): List<CatalogTrack> {
        val collected = LinkedHashMap<String, CatalogTrack>()
        for (query in queries.distinct()) {
            val (tracks, _) = repository.search(query)
            sanitizeTracks(tracks).forEach { track ->
                if (collected.size < limit) {
                    collected.putIfAbsent(track.sourceUrl, track)
                }
            }
            if (collected.size >= limit) break
        }
        return collected.values.toList()
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
        val listenedSeeds = musicRepository.allTracks.first()
            .filter { it.playCount > 0 || it.lastPlayed > 0L || it.isFavorite || it.uri == currentTrack?.uri }
            .sortedByDescending { track ->
                val daysSinceLastPlay = if (track.lastPlayed > 0L) {
                    ((now - track.lastPlayed).coerceAtLeast(0L) / 86_400_000L).coerceAtMost(400L)
                } else 400L
                val recencyBoost = 400L - daysSinceLastPlay
                val favoriteBoost = if (track.isFavorite) 600L else 0L
                val currentBoost = if (track.uri == currentTrack?.uri) 1_200L else 0L
                (track.playCount * 100L) + recencyBoost + favoriteBoost + currentBoost
            }
            .distinctBy { "${cleanSeedTitle(it.title)}|${it.artist.orEmpty().lowercase()}" }
            .take(36)

        val artists = listenedSeeds
            .mapNotNull { track ->
                track.artist?.takeIf { artist -> artist.isNotBlank() && artist != "Unknown Artist" }
            }
            .distinct()
            .take(18)

        val descriptors = listOf(
            "songs like",
            "similar songs",
            "music mix",
            "recommended songs",
            "discover playlist"
        )

        val queries = buildList {
            listenedSeeds.forEachIndexed { index, track ->
                val title = cleanSeedTitle(track.title)
                val artist = track.artist.orEmpty().trim()
                val descriptor = descriptors[(index + cycle) % descriptors.size]

                if (title.isNotBlank()) {
                    add("$title $artist $descriptor".trim())
                    add("$title similar songs official audio")
                }
                if (artist.isNotBlank() && artist != "Unknown Artist") {
                    add("$artist radio")
                    add("$artist related artists mix")
                }
            }

            listenedSeeds.windowed(size = 2, step = 2, partialWindows = false).take(10).forEach { pair ->
                val firstTitle = cleanSeedTitle(pair[0].title)
                val secondTitle = cleanSeedTitle(pair[1].title)
                if (firstTitle.isNotBlank() && secondTitle.isNotBlank()) {
                    add("$firstTitle $secondTitle similar songs")
                }

                val firstArtist = pair[0].artist.orEmpty().trim()
                val secondArtist = pair[1].artist.orEmpty().trim()
                if (firstArtist.isNotBlank() && secondArtist.isNotBlank() && firstArtist != secondArtist) {
                    add("$firstArtist $secondArtist music mix")
                }
            }

            artists.chunked(3).take(6).forEach { group ->
                if (group.size >= 2) add("${group.joinToString(" ")} similar songs")
            }

            add("discover new music official audio")
            add("fresh songs official audio")
        }

        return queries
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(48)
    }

    private fun buildRecommendationTitle(currentTrack: MusicTrack?): String {
        return if (currentTrack != null) "Just for you · Based on your listening" else "Just for you"
    }

    private fun cleanSeedTitle(title: String): String {
        return title
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
            .replace(Regex("official|audio|video|lyrics|visualizer", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
