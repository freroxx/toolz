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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

private const val TAG = "NowPlayingAiVM"
private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MODEL = "llama-3.3-70b-versatile"

@HiltViewModel
class NowPlayingAiViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val catalogRepository: com.frerox.toolz.data.catalog.CatalogRepository,
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
        viewModelScope.launch {
            settingsRepository.musicLyricsLayout.collect { layoutStr ->
                val layout = try { LyricsLayout.valueOf(layoutStr) } catch (e: Exception) { LyricsLayout.RIGHT }
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(layout = layout)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicLyricsSeekEnabled.collect { enabled ->
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isSeekEnabled = enabled)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicLyricsFont.collect { fontStr ->
                val font = try { LyricsFont.valueOf(fontStr) } catch (e: Exception) { LyricsFont.SANS_SERIF }
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(fontFamily = font)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.performanceMode.collect { enabled ->
                _uiState.update { it.copy(performanceMode = enabled) }
            }
        }
    }

    fun updateSong(track: MusicTrack) {
        val oldState = _uiState.value
        val hasMetadataChanged = track.aiLyrics != oldState.lyricsState.lyrics ||
                track.aiArtistVitals != oldState.moreInfoState.artistVitals ||
                track.aiSongMeaning != oldState.moreInfoState.songMeaning

        if (currentTrackUri == track.uri && !hasMetadataChanged) return
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
                lyricsState = it.lyricsState.copy(
                    lyrics = track.aiLyrics ?: "",
                    syncedLyrics = parseLrc(track.aiLyrics ?: ""),
                    isSynced = track.aiLyrics?.contains("[0") == true
                ),
                moreInfoState = AiMoreInfoState(
                    artistVitals = track.aiArtistVitals ?: "",
                    songMeaning = track.aiSongMeaning ?: ""
                ),
                tasteState = AiTasteState(
                    curatedRecommendations = track.aiRecommendationsJson?.let { json ->
                        try { recommendationAdapter.fromJson(json) } catch (e: Exception) { null }
                    } ?: emptyList()
                )
            ) 
        }

        // Proactively fetch lyrics if they are missing, regardless of selected tab
        if (_uiState.value.lyricsState.lyrics.isEmpty()) {
            fetchLyrics()
        }

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
            AiTab.MusicTaste -> if (forceRefresh || (_uiState.value.tasteState.curatedRecommendations.isEmpty() && _uiState.value.tasteState.artistRecommendations.isEmpty())) fetchAiRecommendations()
        }
    }

    private fun fetchLyrics() {
        val uri = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return
        
        val artist = if (song.artist.contains("Unknown", ignoreCase = true)) "" else song.artist
        val album = if (song.album.contains("Unknown", ignoreCase = true)) "" else song.album

        viewModelScope.launch {
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = true, lyrics = ""), error = null) }
            try {
                val allTracks = musicRepository.allTracks.first()
                val track = allTracks.find { it.uri == uri }
                
                // For catalog tracks, sourceUrl is the YouTube link needed for captions
                val sourceUrl = track?.sourceUrl ?: _uiState.value.currentSong?.coverUrl?.takeIf { it.startsWith("http") } // Fallback check

                var lyricsContent: String? = null
                var isSynced = false

                // 1. Try local .lrc file first for local tracks
                if (track?.path != null) {
                    try {
                        val musicFile = java.io.File(track.path)
                        val lrcFile = java.io.File(musicFile.parent, musicFile.nameWithoutExtension + ".lrc")
                        if (lrcFile.exists()) {
                            lyricsContent = lrcFile.readText()
                            isSynced = lyricsContent.contains("[0")
                            Log.d(TAG, "Found local lyrics: ${lrcFile.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading local lrc file", e)
                    }
                }

                // 2. Try LrcLib if no local lyrics found
                if (lyricsContent == null) {
                    try {
                        // Clean up title (remove things like (Official Video), [Lyrics], etc.)
                        val cleanTitle = song.title
                            .replace(Regex("\\(.*?\\)"), "")
                            .replace(Regex("\\[.*?\\]"), "")
                            .replace(Regex("- (Official|Lyric|Music) Video", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("(?i)feat\\..*"), "")
                            .replace(Regex("(?i)ft\\..*"), "")
                            .trim()

                        var response: LrcResponse? = null
                        
                        try {
                            response = lrcLibService.getLyrics(
                                trackName = cleanTitle,
                                artistName = artist,
                                albumName = album.ifEmpty { null },
                                durationInSeconds = (song.durationInMillis / 1000).toInt()
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "LrcLib get failed, trying search")
                        }

                        if (response?.syncedLyrics == null) {
                            val searchResults = lrcLibService.searchLyrics(
                                trackName = cleanTitle,
                                artistName = artist
                            )
                            // Prefer synced lyrics, then plain lyrics
                            response = searchResults.firstOrNull { it.syncedLyrics != null } 
                                       ?: searchResults.firstOrNull { it.plainLyrics != null }
                                       ?: response
                        }
                        
                        lyricsContent = response?.syncedLyrics ?: response?.plainLyrics
                        isSynced = response?.syncedLyrics != null
                    } catch (e: Exception) {
                        Log.e(TAG, "LrcLib error", e)
                    }
                }

                // 3. If LrcLib fails and it's a catalog track, try YouTube captions
                if (lyricsContent == null && sourceUrl != null) {
                    try {
                        lyricsContent = catalogRepository.fetchCaptions(sourceUrl)
                        isSynced = lyricsContent?.contains("[0") == true
                    } catch (e: Exception) {
                        Log.e(TAG, "YouTube captions error", e)
                    }
                }

                if (lyricsContent != null) {
                    _uiState.update { 
                        it.copy(lyricsState = it.lyricsState.copy(
                            lyrics = lyricsContent, 
                            isLoading = false,
                            syncedLyrics = parseLrc(lyricsContent),
                            isSynced = isSynced
                        )) 
                    }
                    musicRepository.updateTrackAiData(uri, lyrics = lyricsContent)
                } else {
                    _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false), error = "Lyrics not found.") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch lyrics error", e)
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false), error = "Lyrics not found.") }
            }
        }
    }

    private fun parseLrc(lrc: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val lineRegex = Regex("\\[(\\d{2}):(\\d{2})[\\.|:](\\d{2,3})\\](.*)")
        val wordRegex = Regex("<(\\d{2}):(\\d{2})[\\.|:](\\d{2,3})>([^<]*)")

        lrc.lines().forEach { line ->
            val match = lineRegex.find(line)
            if (match != null) {
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
                val startTime = (min * 60 * 1000) + (sec * 1000) + ms
                val remainder = match.groupValues[4]
                
                val words = mutableListOf<LyricsWord>()
                val wordMatches = wordRegex.findAll(remainder).toList()
                
                if (wordMatches.isNotEmpty()) {
                    wordMatches.forEachIndexed { index, wordMatch ->
                        val wMin = wordMatch.groupValues[1].toLong()
                        val wSec = wordMatch.groupValues[2].toLong()
                        val wMsPart = wordMatch.groupValues[3]
                        val wMs = wMsPart.toLong().let { 
                            when (wMsPart.length) {
                                2 -> it * 10
                                1 -> it * 100
                                else -> it
                            }
                        }
                        val wStartTime = (wMin * 60 * 1000) + (wSec * 1000) + wMs
                        val text = wordMatch.groupValues[4]
                        
                        val duration = if (index < wordMatches.size - 1) {
                            val nextMatch = wordMatches[index + 1]
                            val nMin = nextMatch.groupValues[1].toLong()
                            val nSec = nextMatch.groupValues[2].toLong()
                            val nMsPart = nextMatch.groupValues[3]
                            val nMs = nMsPart.toLong().let { 
                                when (nMsPart.length) {
                                    2 -> it * 10
                                    1 -> it * 100
                                    else -> it
                                }
                            }
                            ((nMin * 60 * 1000) + (nSec * 1000) + nMs) - wStartTime
                        } else {
                            500L // default duration for last word
                        }
                        
                        words.add(LyricsWord(text, wStartTime, duration))
                    }
                    lines.add(LyricsLine(startTime, remainder.replace(wordRegex, "$4").trim(), words))
                } else {
                    lines.add(LyricsLine(startTime, remainder.trim()))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun getGroqKey(): String {
        return settingsManager.getApiKey("Groq").ifBlank { settingsManager.getApiKey() }
    }

    private fun fetchAiMoreInfo() {
        val uri = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return

        viewModelScope.launch {
            val key = getGroqKey()
            if (key.isBlank()) {
                _uiState.update { it.copy(error = "Configure Groq key in AI Settings.") }
                return@launch
            }

            _uiState.update { it.copy(moreInfoState = it.moreInfoState.copy(isLoading = true, artistVitals = "", songMeaning = ""), error = null) }

            try {
                val prompt = """
                    Act as an expert Music Curator and historian. Analyze "${song.title}" by "${song.artist}" from the album "${song.album}".
                    Provide two sophisticated, punchy sections (max 3 sentences each):
                    1. THE VITALS: Essential artist background, genre significance, or interesting production facts.
                    2. THE MEANING: A deep dive into the lyrical themes and emotional core of this specific track.
                    
                    Format your response exactly like this:
                    VITALS: [Your text]
                    MEANING: [Your text]
                """.trimIndent()

                val response = runGroqRequest(key) { requestKey -> callGroq(requestKey, prompt) } ?: ""
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
            if (key.isBlank()) return@launch

            _uiState.update { 
                it.copy(
                    tasteState = it.tasteState.copy(
                        isLoadingCurated = true, 
                        isLoadingArtist = true,
                        curatedRecommendations = emptyList(),
                        artistRecommendations = emptyList()
                    ), 
                    error = null
                ) 
            }

            // 1. Curated For You (Based on vibe)
            launch {
                try {
                    val prompt = """
                        As an elite Music Curator, analyze the vibe and style of "${song.title}" by "${song.artist}".
                        Recommend 5 tracks that a listener would love next based on this vibe.
                        Return ONLY a JSON array of objects: [{"title": "...", "artist": "...", "explanation": "..."}]
                    """.trimIndent()

                    val response = runGroqRequest(key) { requestKey -> callGroq(requestKey, prompt) } ?: "[]"
                    val json = response.substringAfter("[").substringBeforeLast("]").let { "[$it]" }
                    val initial = recommendationAdapter.fromJson(json) ?: emptyList()
                    val enriched = initial.take(5).map { rec ->
                        async {
                            val results = try { catalogRepository.search("${rec.title} ${rec.artist}").first } catch (e: Exception) { emptyList() }
                            val bestMatch = results.firstOrNull()
                            rec.copy(thumbnailUrl = bestMatch?.thumbnailUrl, videoId = bestMatch?.id)
                        }
                    }.awaitAll()
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(curatedRecommendations = enriched, isLoadingCurated = false)) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(isLoadingCurated = false)) }
                }
            }

            // 2. Same Artist (Similar songs from same artist)
            launch {
                try {
                    val prompt = """
                        Recommend 5 other great songs specifically by the artist "${song.artist}".
                        Focus on tracks similar in style to "${song.title}".
                        Return ONLY a JSON array of objects: [{"title": "...", "artist": "${song.artist}", "explanation": "..."}]
                    """.trimIndent()

                    val response = runGroqRequest(key) { requestKey -> callGroq(requestKey, prompt) } ?: "[]"
                    val json = response.substringAfter("[").substringBeforeLast("]").let { "[$it]" }
                    val initial = recommendationAdapter.fromJson(json) ?: emptyList()
                    val enriched = initial.take(5).map { rec ->
                        async {
                            val results = try { catalogRepository.search("${rec.title} ${song.artist}").first } catch (e: Exception) { emptyList() }
                            val bestMatch = results.firstOrNull()
                            rec.copy(thumbnailUrl = bestMatch?.thumbnailUrl, videoId = bestMatch?.id)
                        }
                    }.awaitAll()
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(artistRecommendations = enriched, isLoadingArtist = false)) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(isLoadingArtist = false)) }
                }
            }
        }
    }

    private suspend fun <T> runGroqRequest(
        initialKey: String,
        requestBlock: suspend (String) -> T,
    ): T {
        try {
            return requestBlock(initialKey)
        } catch (e: HttpException) {
            if (e.code() == 401 && !settingsManager.hasUserApiKey("Groq")) {
                val refreshed = settingsManager.refreshRemoteKeyAfterAuthFailure("Groq", initialKey)
                if ((refreshed.source == ApiKeySource.REMOTE || refreshed.source == ApiKeySource.DEFAULT) &&
                    refreshed.value.isNotBlank() &&
                    refreshed.value != initialKey
                ) {
                    return requestBlock(refreshed.value)
                }
                throw IllegalStateException(
                    "The Toolz default key for Groq is invalid or unavailable. Please add your own key in AI settings."
                )
            }
            throw e
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
    
    fun toggleSeekEnabled() {
        viewModelScope.launch {
            val newState = !_uiState.value.lyricsState.isSeekEnabled
            settingsRepository.setMusicLyricsSeekEnabled(newState)
            _uiState.update { 
                it.copy(lyricsState = it.lyricsState.copy(isSeekEnabled = newState)) 
            }
        }
    }

    fun toggleExpandedPill() {
        _uiState.update { it.copy(isExpandedPill = !it.isExpandedPill) }
    }

    fun setLyricsLayout(layout: LyricsLayout) {
        viewModelScope.launch {
            settingsRepository.setMusicLyricsLayout(layout.name)
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(layout = layout)) }
        }
    }

    fun setLyricsFont(font: LyricsFont) {
        viewModelScope.launch {
            settingsRepository.setMusicLyricsFont(font.name)
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(fontFamily = font)) }
        }
    }
}
