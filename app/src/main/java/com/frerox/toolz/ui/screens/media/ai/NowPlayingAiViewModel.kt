package com.frerox.toolz.ui.screens.media.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frerox.toolz.data.ai.*
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.settings.SettingsRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "NowPlayingAiVM"
private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MODEL = "llama-3.3-70b-versatile"

@HiltViewModel
class NowPlayingAiViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsManager: AiSettingsManager,
    private val openAiService: OpenAiService,
    private val lrcLibService: LrcLibService,
    private val moshi: Moshi
) : ViewModel() {

    private val _uiState = MutableStateFlow(NowPlayingAiUiState())
    val uiState: StateFlow<NowPlayingAiUiState> = _uiState.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _scrollPosition = MutableStateFlow(0f)
    val scrollPosition: StateFlow<Float> = _scrollPosition.asStateFlow()

    val keepScreenOn = settingsRepository.musicKeepScreenOnLyrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val recommendationAdapter = moshi.adapter<List<AiRecommendation>>(
        Types.newParameterizedType(List::class.java, AiRecommendation::class.java)
    )

    private var currentTrackUri: String? = null

    init {
        viewModelScope.launch {
            settingsRepository.musicAiEnabled.collect { enabled ->
                _uiState.update { it.copy(isAiEnabled = enabled) }
            }
        }
    }

    /**
     * Updates the current song context and loads cached data.
     */
    fun updateSong(track: MusicTrack) {
        if (currentTrackUri == track.uri) return
        currentTrackUri = track.uri

        val song = AiSong(
            title = track.title,
            artist = track.artist ?: "Unknown Artist",
            album = track.album ?: "Unknown Album",
            durationInMillis = track.duration,
            coverUrl = track.thumbnailUri
        )
        
        _uiState.update { 
            it.copy(
                currentSong = song,
                lyricsState = AiLyricsState(
                    lyrics = track.aiLyrics ?: "",
                    isAutoScrollEnabled = it.lyricsState.isAutoScrollEnabled,
                    syncedLyrics = parseLrc(track.aiLyrics ?: ""),
                    isSynced = track.aiLyrics?.contains("[0") == true
                ),
                moreInfoState = AiMoreInfoState(
                    artistVitals = track.aiArtistVitals ?: "",
                    songMeaning = track.aiSongMeaning ?: ""
                ),
                tasteState = AiTasteState(
                    recommendations = track.aiRecommendationsJson?.let { json ->
                        try { recommendationAdapter.fromJson(json) } catch (e: Exception) { null }
                    } ?: emptyList()
                )
            ) 
        }

        // Only auto-load if data is missing
        loadDataForTab(_uiState.value.selectedTab, forceRefresh = false)
    }

    fun updateProgress(positionMs: Long) {
        _playbackPositionMs.value = positionMs
        val duration = _uiState.value.currentSong?.durationInMillis ?: 0L
        if (duration > 0) {
            _scrollPosition.value = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
        }
    }

    fun selectTab(tab: AiTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        loadDataForTab(tab, forceRefresh = false)
    }

    fun refreshCurrentTab() {
        loadDataForTab(_uiState.value.selectedTab, forceRefresh = true)
    }

    private fun loadDataForTab(tab: AiTab, forceRefresh: Boolean) {
        when (tab) {
            AiTab.Lyrics -> if (forceRefresh || _uiState.value.lyricsState.lyrics.isEmpty()) fetchLyrics()
            AiTab.MoreInfo -> if (forceRefresh || _uiState.value.moreInfoState.artistVitals.isEmpty()) fetchAiMoreInfo()
            AiTab.MusicTaste -> if (forceRefresh || _uiState.value.tasteState.recommendations.isEmpty()) fetchAiRecommendations()
        }
    }

    private fun fetchLyrics() {
        val uri = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = true, lyrics = ""), error = null) }
            try {
                // Try direct get first
                var response = try {
                    lrcLibService.getLyrics(
                        trackName = song.title,
                        artistName = song.artist,
                        albumName = song.album,
                        durationInSeconds = (song.durationInMillis / 1000).toInt()
                    )
                } catch (e: Exception) {
                    null
                }

                // If direct get fails or has no synced lyrics, try searching
                if (response?.syncedLyrics == null) {
                    val searchResults = lrcLibService.searchLyrics(
                        trackName = song.title,
                        artistName = song.artist
                    )
                    response = searchResults.firstOrNull { it.syncedLyrics != null } ?: searchResults.firstOrNull() ?: response
                }
                
                val lyricsContent = response?.syncedLyrics ?: response?.plainLyrics ?: "Lyrics not found."
                val isSynced = response?.syncedLyrics != null
                
                _uiState.update { 
                    it.copy(lyricsState = it.lyricsState.copy(
                        lyrics = lyricsContent, 
                        isLoading = false,
                        syncedLyrics = parseLrc(lyricsContent),
                        isSynced = isSynced
                    )) 
                }
                musicRepository.updateTrackAiData(uri, lyrics = lyricsContent)
            } catch (e: Exception) {
                Log.e(TAG, "LrcLib error", e)
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false), error = "Lyrics not found.") }
            }
        }
    }

    private fun parseLrc(lrc: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        // Improved regex to handle various LRC formats
        val regex = Regex("\\[(\\d{2}):(\\d{2})[\\.|:](\\d{2,3})\\](.*)")
        lrc.lines().forEach { line ->
            val matches = regex.findAll(line)
            matches.forEach { match ->
                val min = match.groupValues[1].toLong()
                val sec = match.groupValues[2].toLong()
                val msPart = match.groupValues[3]
                val ms = msPart.toLong().let { 
                    when (msPart.length) {
                        2 -> it * 10
                        1 -> it * 100
                        else -> it
                    }
                }
                val time = (min * 60 * 1000) + (sec * 1000) + ms
                val content = match.groupValues[4].trim()
                if (content.isNotEmpty()) {
                    lines.add(LyricsLine(time, content))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private suspend fun getGroqKey(): String? {
        return settingsManager.getApiKey("Groq").ifBlank { settingsManager.getApiKey() }.takeIf { it.isNotBlank() }
    }

    private fun fetchAiMoreInfo() {
        val uri = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return

        viewModelScope.launch {
            val key = getGroqKey()
            if (key == null) {
                _uiState.update { it.copy(error = "Configure Groq key in AI Settings.") }
                return@launch
            }

            _uiState.update { it.copy(moreInfoState = it.moreInfoState.copy(isLoading = true, artistVitals = "", songMeaning = ""), error = null) }

            try {
                val prompt = """
                    Act as a high-end music critic. Analyze the track: "${song.title}" by "${song.artist}" from "${song.album}".
                    Provide two deep but VERY CONCISE sections (max 3 sentences each):
                    1. THE VITALS: Key artist facts and song context.
                    2. THE MEANING: What the song is actually about.
                    
                    Format:
                    VITALS: [Text]
                    MEANING: [Text]
                """.trimIndent()

                val response = callGroq(key, prompt) ?: ""
                val vitals = response.substringAfter("VITALS:", "").substringBefore("MEANING:").trim()
                val meaning = response.substringAfter("MEANING:", "").trim()

                _uiState.update {
                    it.copy(moreInfoState = it.moreInfoState.copy(artistVitals = vitals, songMeaning = meaning, isLoading = false))
                }
                musicRepository.updateTrackAiData(uri, vitals = vitals, meaning = meaning)
            } catch (e: Exception) {
                _uiState.update { it.copy(moreInfoState = it.moreInfoState.copy(isLoading = false), error = "AI Insight failed.") }
            }
        }
    }

    private fun fetchAiRecommendations() {
        val uri = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return

        viewModelScope.launch {
            val key = getGroqKey()
            if (key == null) return@launch

            _uiState.update { it.copy(tasteState = it.tasteState.copy(isLoading = true, recommendations = emptyList()), error = null) }

            try {
                val prompt = """
                    Analyze "${song.title}" by "${song.artist}". Recommend 4 similar songs.
                    Return ONLY a JSON array of objects: [{"title": "...", "artist": "...", "explanation": "..."}]
                    Explanation must be 1 concise sentence about why it matches. No other text.
                """.trimIndent()

                val response = callGroq(key, prompt) ?: "[]"
                val json = response.substringAfter("[").substringBeforeLast("]").let { "[$it]" }
                val recommendations = recommendationAdapter.fromJson(json) ?: emptyList()
                val recommendationsJson = recommendationAdapter.toJson(recommendations)

                _uiState.update {
                    it.copy(tasteState = it.tasteState.copy(recommendations = recommendations, isLoading = false))
                }
                musicRepository.updateTrackAiData(uri, recommendationsJson = recommendationsJson)
            } catch (e: Exception) {
                _uiState.update { it.copy(tasteState = it.tasteState.copy(isLoading = false), error = "Taste failed.") }
            }
        }
    }

    private suspend fun callGroq(apiKey: String, prompt: String): String? {
        val request = OpenAiRequest(
            model = GROQ_MODEL,
            messages = listOf(
                OpenAiMessage("system", MessageContent.Text("You are an elite music intelligence for Toolz. Be insightful, stylish, and brief.")),
                OpenAiMessage("user", MessageContent.Text(prompt))
            ),
            maxTokens = 2048
        )
        return openAiService.getChatCompletion(url = GROQ_URL, authHeader = "Bearer $apiKey", request = request)
            .choices.firstOrNull()?.message?.content?.trim()
    }

    fun toggleAutoScroll() {
        _uiState.update { 
            val newState = !it.lyricsState.isAutoScrollEnabled
            it.copy(lyricsState = it.lyricsState.copy(isAutoScrollEnabled = newState)) 
        }
    }

    fun onManualScroll() {
        if (_uiState.value.lyricsState.isAutoScrollEnabled) {
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isAutoScrollEnabled = false)) }
        }
    }
    
    fun toggleExpandedPill() {
        _uiState.update { it.copy(isExpandedPill = !it.isExpandedPill) }
    }
}
