package com.frerox.toolz.ui.screens.media.ai

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.frerox.toolz.data.ai.*
import com.frerox.toolz.data.catalog.CatalogRepository
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.util.VibrationManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

private const val TAG = "NowPlayingAiVM"
private const val GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MODEL = "llama-3.3-70b-versatile"

// How long we wait after a transient error before restarting the recognizer.
// Indexed by SpeechRecognizer error codes (0 = default bucket).
private val ERROR_RESTART_DELAY_MS = mapOf(
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT   to 400L,
    SpeechRecognizer.ERROR_NO_MATCH         to 300L,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT  to 2000L,
    SpeechRecognizer.ERROR_NETWORK          to 2000L,
    SpeechRecognizer.ERROR_SERVER           to 3000L,
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY  to 800L,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS to 3000L,
    0                                        to 1200L   // default
)

// Errors that are fatal – we stop the session rather than retry.
private val FATAL_ERRORS = setOf(
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
    SpeechRecognizer.ERROR_AUDIO,
    SpeechRecognizer.ERROR_CLIENT
)

@HiltViewModel
class NowPlayingAiViewModel @Inject constructor(
    private val musicRepository    : MusicRepository,
    private val catalogRepository  : CatalogRepository,
    private val settingsRepository : SettingsRepository,
    private val settingsManager    : AiSettingsManager,
    private val openAiService      : OpenAiService,
    private val lrcLibService      : LrcLibService,
    private val moshi              : Moshi,
    private val vibrationManager   : VibrationManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NowPlayingAiUiState())
    val uiState: StateFlow<NowPlayingAiUiState> = _uiState.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _scrollPosition = MutableStateFlow(0f)
    val scrollPosition: StateFlow<Float> = _scrollPosition.asStateFlow()

    private data class TokenEvent(val token: String, val timestamp: Long)
    private val recognitionBuffer = mutableListOf<TokenEvent>()
    var onSetMutedByAi: ((Boolean) -> Unit)? = null

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been", "being",
        "in", "on", "at", "to", "for", "with", "by", "of", "from", "up", "down", "out", "over", "under"
    )

    val keepScreenOn = settingsRepository.musicKeepScreenOnLyrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val recommendationAdapter = moshi.adapter<List<AiRecommendation>>(
        Types.newParameterizedType(List::class.java, AiRecommendation::class.java)
    )

    // ── Speech recognizer state ───────────────────────────────────────────────
    // The recognizer MUST be created, used, and destroyed on the MAIN thread.
    // We never touch it from a coroutine dispatcher other than Main.
    private var speechRecognizer   : SpeechRecognizer? = null
    private var recognitionIntent  : Intent?           = null
    private var restartJob         : Job?              = null
    private var isListening        : Boolean           = false
    private var instrumentalSearchJob: Job?            = null

    private var _instrumentalPlayer: ExoPlayer? = null
    private val instrumentalPlayer: ExoPlayer
        get() {
            if (_instrumentalPlayer == null) {
                _instrumentalPlayer = ExoPlayer.Builder(context).build().apply {
                    val attr = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()
                    setAudioAttributes(attr, false)
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e(TAG, "Instrumental Player Error: ${error.message}", error)
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            Log.d(TAG, "Instrumental Player State: $state")
                        }
                    })
                    playWhenReady = true
                }
            }
            return _instrumentalPlayer!!
        }

    fun setInstrumentalPlayerVolume(volume: Float) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            instrumentalPlayer.volume = volume
        }
    }

    private var currentTrackUri    : String?           = null

    // ─────────────────────────────────────────────────────────────────────────
    // Init: observe settings
    // ─────────────────────────────────────────────────────────────────────────

    init {
        viewModelScope.launch {
            settingsRepository.musicAiEnabled.collect { enabled ->
                _uiState.update { it.copy(isAiEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicLyricsLayout.collect { layoutStr ->
                val layout = runCatching { LyricsLayout.valueOf(layoutStr) }.getOrDefault(LyricsLayout.LEFT)
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
                val font = runCatching { LyricsFont.valueOf(fontStr) }.getOrDefault(LyricsFont.SANS_SERIF)
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(fontFamily = font)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicLyricsAlwaysSync.collect { enabled ->
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(alwaysSync = enabled)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.musicLyricsWordSyncEnabled.collect { enabled ->
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isWordSyncEnabled = enabled)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.karaokeWordSyncEnabled.collect { enabled ->
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isKaraokeWordSyncEnabled = enabled)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.performanceMode.collect { enabled ->
                _uiState.update { it.copy(performanceMode = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.karaokeSingConfidentlyEnabled.collect { enabled ->
                _uiState.update { it.copy(karaokeSingConfidentlyEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.karaokeSpeechCorrectionEnabled.collect { enabled ->
                _uiState.update { it.copy(karaokeSpeechCorrectionEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.karaokeQuickSingEnabled.collect { enabled ->
                _uiState.update { it.copy(quickSingEnabled = enabled) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Song update
    // ─────────────────────────────────────────────────────────────────────────

    fun updateSong(track: MusicTrack) {
        val old = _uiState.value
        val hasMetadataChanged =
            track.aiLyrics != old.lyricsState.lyrics ||
                    track.aiArtistVitals != old.moreInfoState.artistVitals ||
                    track.aiSongMeaning  != old.moreInfoState.songMeaning

        if (currentTrackUri == track.uri && !hasMetadataChanged) return
        currentTrackUri = track.uri

        stopSingConfidently()

        val song = AiSong(
            title            = track.title,
            artist           = track.artist ?: "Unknown Artist",
            album            = track.album  ?: "Unknown Album",
            durationInMillis = track.duration,
            coverUrl         = track.thumbnailUri
        )

        _uiState.update {
            it.copy(
                currentSong  = song,
                instrumentalMatch = null,
                isSingConfidentlyActive = false,
                instrumentalStreamUrl = null,
                lyricsState  = it.lyricsState.copy(
                    lyrics        = track.aiLyrics ?: "",
                    syncedLyrics  = parseLrc(track.aiLyrics ?: ""),
                    isSynced      = track.aiLyrics?.contains("[0") == true
                ),
                moreInfoState = AiMoreInfoState(
                    artistVitals = track.aiArtistVitals ?: "",
                    songMeaning  = track.aiSongMeaning  ?: ""
                ),
                tasteState    = AiTasteState(
                    curatedRecommendations = track.aiRecommendationsJson?.let { json ->
                        runCatching { recommendationAdapter.fromJson(json) }.getOrNull()
                    } ?: emptyList()
                ),
                karaokeScore            = 0,
                karaokeCorrectWords     = 0,
                karaokeTotalWords       = 0,
                karaokeStreak           = 0,
                karaokeMaxStreak        = 0,
                karaokeMissedStreak     = 0,
                karaokeMostAccurateLine = null
            )
        }
        synchronized(recognitionBuffer) { recognitionBuffer.clear() }

        if (_uiState.value.lyricsState.lyrics.isEmpty()) fetchLyrics()
        loadDataForTab(_uiState.value.selectedTab, forceRefresh = false)

        if (_uiState.value.karaokeSingConfidentlyEnabled) {
            searchInstrumental(track)
        }
    }

    fun updateProgress(positionMs: Long) {
        _playbackPositionMs.value = positionMs
        val duration = _uiState.value.currentSong?.durationInMillis ?: 0L
        if (duration > 0) {
            _scrollPosition.value = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
        }

        // Periodic check for missed words (every ~1s based on progress updates)
        if (_uiState.value.isKaraokeRecording && positionMs % 1000 < 200) {
            checkMissedWords(positionMs)
        }
    }

    private fun checkMissedWords(currentTime: Long) {
        val speechCorrect = _uiState.value.karaokeSpeechCorrectionEnabled
        if (!speechCorrect) return

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { state ->
                var changed = false
                val updatedLyrics = state.lyricsState.syncedLyrics.map { line ->
                    // Only check lines that have ended or are about to end
                    if (line.timeMs > currentTime) return@map line

                    val updatedWords = line.words.map { word ->
                        val missedGrace = word.startTimeMs + word.durationMs + 6000L
                        if (currentTime > missedGrace && word.karaokeStatus == KaraokeWordStatus.PENDING) {
                            changed = true
                            word.copy(karaokeStatus = KaraokeWordStatus.MISSED)
                        } else {
                            word
                        }
                    }
                    if (changed) line.copy(words = updatedWords) else line
                }

                if (changed) {
                    val totalWords   = updatedLyrics.sumOf { it.words.size }
                    val correctWords = updatedLyrics.sumOf { l -> l.words.count { it.karaokeStatus == KaraokeWordStatus.CORRECT } }
                    val score        = if (totalWords > 0) (correctWords * 100 / totalWords) else 0

                    state.copy(
                        lyricsState = state.lyricsState.copy(syncedLyrics = updatedLyrics),
                        karaokeScore = score,
                        karaokeCorrectWords = correctWords,
                        karaokeTotalWords = totalWords
                    )
                } else state
            }
        }
    }

    fun checkQuickSing(onSeek: (Long) -> Unit) {
        if (!_uiState.value.quickSingEnabled) return
        val lyrics      = _uiState.value.lyricsState.syncedLyrics
        if (lyrics.isEmpty()) return
        val currentTime = _playbackPositionMs.value

        // 1. Skip long intro
        val firstLineTime = lyrics.first().timeMs
        if (currentTime < firstLineTime - 5_000L) {
            onSeek((firstLineTime - 3_000L).coerceAtLeast(0L))
            return
        }

        // 2. Skip internal gaps
        val currentIndex = lyrics.indexOfLast { it.timeMs <= currentTime }
        if (currentIndex in 0 until lyrics.size - 1) {
            val cur  = lyrics[currentIndex]
            val next = lyrics[currentIndex + 1]
            val lineDur = if (cur.words.isNotEmpty()) {
                cur.words.last().let { it.startTimeMs + it.durationMs - cur.timeMs }
            } else 2_000L
            val gapStart = cur.timeMs + lineDur + 3_000L
            if (currentTime > gapStart && next.timeMs - currentTime > 5_000L) {
                onSeek(next.timeMs - 2_500L)
                return
            }
        }

        // 3. Skip outro
        val last    = lyrics.last()
        val lastEnd = if (last.words.isNotEmpty())
            last.words.last().let { it.startTimeMs + it.durationMs }
        else last.timeMs + 2_000L
        val dur = _uiState.value.currentSong?.durationInMillis ?: 0L
        if (dur > 0 && currentTime > lastEnd + 4_000L && dur - currentTime > 5_000L) {
            onSeek(dur - 1_500L)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab selection
    // ─────────────────────────────────────────────────────────────────────────

    fun selectTab(tab: AiTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        loadDataForTab(tab, forceRefresh = false)
    }

    fun refreshCurrentTab() = loadDataForTab(_uiState.value.selectedTab, forceRefresh = true)

    private fun loadDataForTab(tab: AiTab, forceRefresh: Boolean) {
        when (tab) {
            is AiTab.Lyrics    ->
                if (forceRefresh || _uiState.value.lyricsState.lyrics.isEmpty()) fetchLyrics()
            is AiTab.MoreInfo  ->
                if (forceRefresh || _uiState.value.moreInfoState.artistVitals.isEmpty()) fetchAiMoreInfo()
            is AiTab.MusicTaste ->
                if (forceRefresh || (_uiState.value.tasteState.curatedRecommendations.isEmpty()
                            && _uiState.value.tasteState.artistRecommendations.isEmpty()))
                    fetchAiRecommendations()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lyrics fetch
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchLyrics() {
        val uri  = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return

        val artist = song.artist.takeUnless { it.contains("Unknown", true) } ?: ""
        val album  = song.album.takeUnless  { it.contains("Unknown", true) } ?: ""

        viewModelScope.launch {
            val offline = settingsRepository.offlineModeEnabled.first()
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = true, lyrics = ""), error = null) }
            try {
                val allTracks = musicRepository.allTracks.first()
                val track     = allTracks.find { it.uri == uri }
                val sourceUrl = track?.sourceUrl

                var lyricsContent: String? = null
                var isSynced              = false

                // 1. Local .lrc file
                if (track?.path != null) {
                    runCatching {
                        val lrc = java.io.File(java.io.File(track.path).parent,
                            java.io.File(track.path).nameWithoutExtension + ".lrc")
                        if (lrc.exists()) {
                            lyricsContent = lrc.readText()
                            isSynced      = lyricsContent?.contains("[0") == true
                            Log.d(TAG, "Local .lrc found: ${lrc.absolutePath}")
                        }
                    }
                }

                if (!offline) {
                    // 2. LrcLib
                    if (lyricsContent == null) {
                        runCatching {
                            val cleanTitle = song.title
                                .replace(Regex("\\(.*?\\)"), "")
                                .replace(Regex("\\[.*?\\]"), "")
                                .replace(Regex("(?i)- (Official|Lyric|Music).*"), "")
                                .replace(Regex("(?i)(feat|ft)\\..*"), "")
                                .trim()

                            var response = runCatching {
                                lrcLibService.getLyrics(
                                    trackName = cleanTitle,
                                    artistName = artist,
                                    albumName = album.ifEmpty { null },
                                    durationInSeconds = (song.durationInMillis / 1000).toInt()
                                )
                            }.getOrNull()

                            if (response?.syncedLyrics == null) {
                                val results = lrcLibService.searchLyrics(cleanTitle, artist)
                                response = results.firstOrNull { it.syncedLyrics != null }
                                    ?: results.firstOrNull { it.plainLyrics != null }
                                            ?: response
                            }

                            lyricsContent = response?.syncedLyrics ?: response?.plainLyrics
                            isSynced = response?.syncedLyrics != null
                        }
                    }

                    // 3. YouTube captions (catalog tracks)
                    if (lyricsContent == null && sourceUrl != null) {
                        runCatching {
                            lyricsContent = catalogRepository.fetchCaptions(sourceUrl)
                            isSynced = lyricsContent?.contains("[0") == true
                        }
                    }
                }

                if (lyricsContent != null) {
                    _uiState.update {
                        it.copy(lyricsState = it.lyricsState.copy(
                            lyrics       = lyricsContent!!,
                            isLoading    = false,
                            syncedLyrics = parseLrc(lyricsContent!!),
                            isSynced     = isSynced
                        ))
                    }
                    musicRepository.updateTrackAiData(uri, lyrics = lyricsContent)
                } else {
                    _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false), error = if (offline) null else "Lyrics not found.") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchLyrics error", e)
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false), error = if (offline) null else "Lyrics not found.") }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LRC parser
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseLrc(lrc: String): List<LyricsLine> {
        val lines     = mutableListOf<LyricsLine>()
        val lineRegex = Regex("\\[(\\d{2}):(\\d{2})[.:|](\\d{2,3})\\](.*)")
        val wordRegex = Regex("<(\\d{2}):(\\d{2})[.:|](\\d{2,3})>([^<]*)")

        val rawLines = lrc.lines()
        rawLines.forEachIndexed { lineIdx, raw ->
            val match = lineRegex.find(raw) ?: return@forEachIndexed

            fun parseMsGroup(min: String, sec: String, ms: String): Long {
                val msVal = ms.toLong().let {
                    when (ms.length) { 2 -> it * 10L; 1 -> it * 100L; else -> it }
                }
                return min.toLong() * 60_000L + sec.toLong() * 1_000L + msVal
            }

            val startTime = parseMsGroup(
                match.groupValues[1], match.groupValues[2], match.groupValues[3]
            )
            val remainder = match.groupValues[4]
            val words     = mutableListOf<LyricsWord>()
            val wordMs    = wordRegex.findAll(remainder).toList()

            if (wordMs.isNotEmpty()) {
                wordMs.forEachIndexed { wIdx, wm ->
                    val wStart = parseMsGroup(wm.groupValues[1], wm.groupValues[2], wm.groupValues[3])
                    val text   = wm.groupValues[4].trim()
                    val dur    = if (wIdx < wordMs.size - 1) {
                        val nm = wordMs[wIdx + 1]
                        parseMsGroup(nm.groupValues[1], nm.groupValues[2], nm.groupValues[3]) - wStart
                    } else 800L
                    words.add(LyricsWord(text, wStart, dur))
                }
                lines.add(LyricsLine(startTime, remainder.replace(wordRegex, "$4").trim(), words))
            } else {
                // Estimate word timings from the gap to the next line
                val content  = remainder.trim()
                val wordList = content.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (wordList.isNotEmpty()) {
                    var nextLineStart: Long? = null
                    for (i in lineIdx + 1 until rawLines.size) {
                        val nm = lineRegex.find(rawLines[i]) ?: continue
                        nextLineStart = parseMsGroup(nm.groupValues[1], nm.groupValues[2], nm.groupValues[3])
                        break
                    }
                    val lineDur  = if (nextLineStart != null)
                        (nextLineStart - startTime).coerceIn(1_000L, 8_000L)
                    else 5_000L
                    val wordDur  = lineDur / wordList.size
                    wordList.forEachIndexed { i, w ->
                        words.add(LyricsWord(w, startTime + i * wordDur, wordDur, KaraokeWordStatus.PENDING))
                    }
                }
                lines.add(LyricsLine(startTime, content, words))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI – More Info & Recommendations
    // ─────────────────────────────────────────────────────────────────────────

    private fun getGroqKey(): String =
        settingsManager.getApiKey("Groq").ifBlank { settingsManager.getApiKey() }

    private fun fetchAiMoreInfo() {
        val uri  = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return

        viewModelScope.launch {
            if (settingsRepository.offlineModeEnabled.first()) return@launch
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

                val response = runGroqRequest(key) { k -> callGroq(k, prompt) } ?: ""
                val vitals   = response.substringAfter("VITALS:").substringBefore("MEANING:").trim()
                val meaning  = response.substringAfter("MEANING:").trim()

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
        val uri  = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return

        viewModelScope.launch {
            if (settingsRepository.offlineModeEnabled.first()) return@launch
            val key = getGroqKey()
            if (key.isBlank()) return@launch

            _uiState.update {
                it.copy(tasteState = it.tasteState.copy(
                    isLoadingCurated = true, isLoadingArtist = true,
                    curatedRecommendations = emptyList(), artistRecommendations = emptyList()
                ), error = null)
            }

            launch {
                runCatching {
                    val prompt = """
                        As an elite Music Curator, analyze "${song.title}" by "${song.artist}".
                        Recommend 5 tracks a listener would love next based on this vibe.
                        Return ONLY a JSON array: [{"title":"...","artist":"...","explanation":"..."}]
                    """.trimIndent()
                    val response = runGroqRequest(key) { k -> callGroq(k, prompt) } ?: "[]"
                    val json     = "[${response.substringAfter("[").substringBeforeLast("]")}]"
                    val items    = (recommendationAdapter.fromJson(json) ?: emptyList()).take(5)
                    val enriched = items.map { rec ->
                        async {
                            val results = runCatching { catalogRepository.search("${rec.title} ${rec.artist}").first }.getOrElse { emptyList() }
                            rec.copy(thumbnailUrl = results.firstOrNull()?.thumbnailUrl, videoId = results.firstOrNull()?.id)
                        }
                    }.awaitAll()
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(curatedRecommendations = enriched, isLoadingCurated = false)) }
                }.onFailure {
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(isLoadingCurated = false)) }
                }
            }

            launch {
                runCatching {
                    val prompt = """
                        Recommend 5 other great songs specifically by "${song.artist}" similar to "${song.title}".
                        Return ONLY a JSON array: [{"title":"...","artist":"${song.artist}","explanation":"..."}]
                    """.trimIndent()
                    val response = runGroqRequest(key) { k -> callGroq(k, prompt) } ?: "[]"
                    val json     = "[${response.substringAfter("[").substringBeforeLast("]")}]"
                    val items    = (recommendationAdapter.fromJson(json) ?: emptyList()).take(5)
                    val enriched = items.map { rec ->
                        async {
                            val results = runCatching { catalogRepository.search("${rec.title} ${song.artist}").first }.getOrElse { emptyList() }
                            rec.copy(thumbnailUrl = results.firstOrNull()?.thumbnailUrl, videoId = results.firstOrNull()?.id)
                        }
                    }.awaitAll()
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(artistRecommendations = enriched, isLoadingArtist = false)) }
                }.onFailure {
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(isLoadingArtist = false)) }
                }
            }
        }
    }

    // ── Sing Confidently ──────────────────────────────────────────────────────

    private fun searchInstrumental(track: MusicTrack) {
        instrumentalSearchJob?.cancel()
        instrumentalSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSearchingInstrumental = true, instrumentalMatch = null) }
            try {
                val query = "${track.title} ${track.artist} instrumental"
                val (results, _) = catalogRepository.search(query)

                Log.d(TAG, "Instrumental search for '$query' found ${results.size} results")

                // Filtering/Validation: title contains instrumental/karaoke, duration +/- 10s
                val match = results.find { result ->
                    val lowTitle = result.title.lowercase()
                    val isInstrumental = lowTitle.contains("instrumental") || lowTitle.contains("karaoke")
                    val isDurationMatch = kotlin.math.abs(result.duration - track.duration) <= 10000L
                    isInstrumental && isDurationMatch
                }

                _uiState.update { it.copy(isSearchingInstrumental = false, instrumentalMatch = match) }
                if (match != null) {
                    Log.d(TAG, "Found instrumental match: ${match.title} (${match.duration}ms)")
                } else {
                    Log.w(TAG, "No suitable instrumental match found among ${results.size} results")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Instrumental search failed: ${e.message}")
                _uiState.update { it.copy(isSearchingInstrumental = false) }
            }
        }
    }

    fun forceSearchInstrumental(track: MusicTrack) {
        searchInstrumental(track)
    }

    fun searchInstrumentalCustom(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSearchingInstrumental = true, instrumentalSearchResults = emptyList()) }
            try {
                val (results, _) = catalogRepository.search(query)
                _uiState.update { it.copy(isSearchingInstrumental = false, instrumentalSearchResults = results) }
            } catch (e: Exception) {
                Log.e(TAG, "Custom instrumental search failed", e)
                _uiState.update { it.copy(isSearchingInstrumental = false) }
            }
        }
    }

    fun setInstrumentalMatch(track: CatalogTrack) {
        _uiState.update { it.copy(instrumentalMatch = track, instrumentalStreamUrl = null) }
    }

    fun setSingConfidentlyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKaraokeSingConfidentlyEnabled(enabled)
            if (!enabled) {
                stopSingConfidently()
            }
        }
    }

    fun toggleSingConfidentlyActive(active: Boolean, onSync: (Long) -> Unit) {
        Log.d(TAG, "toggleSingConfidentlyActive: active=$active")
        if (active) {
            val match = _uiState.value.instrumentalMatch ?: run {
                Log.w(TAG, "No instrumental match found")
                return
            }
            
            // If already have a stream URL and it's the same track, just play
            if (_uiState.value.instrumentalStreamUrl != null && _uiState.value.isSingConfidentlyActive) {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    instrumentalPlayer.play()
                    onSetMutedByAi?.invoke(true)
                }
                Log.d(TAG, "Instrumental player resumed, original muted")
                return
            }

            // Immediately mute original track and show loading state
            _uiState.update { it.copy(isResolvingInstrumental = true) }
            onSetMutedByAi?.invoke(true)

            viewModelScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Resolving instrumental stream for: ${match.sourceUrl}")
                val streamUrl = runCatching { catalogRepository.resolveAudioStream(match.sourceUrl) }.getOrNull()
                withContext(Dispatchers.Main.immediate) {
                    if (streamUrl != null) {
                        Log.d(TAG, "Resolved instrumental stream: $streamUrl")
                        _uiState.update { it.copy(
                            isSingConfidentlyActive = true, 
                            isResolvingInstrumental = false,
                            instrumentalStreamUrl = streamUrl 
                        ) }
                        instrumentalPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                        instrumentalPlayer.prepare()
                        onSync(_playbackPositionMs.value)
                        instrumentalPlayer.seekTo(_playbackPositionMs.value)
                        instrumentalPlayer.play()
                        onSetMutedByAi?.invoke(true) // Re-confirm muting
                        Log.d(TAG, "Instrumental player started, original track muted")
                    } else {
                        Log.w(TAG, "Failed to resolve instrumental stream, falling back to original")
                        _uiState.update { it.copy(isSingConfidentlyActive = false, isResolvingInstrumental = false) }
                        onSetMutedByAi?.invoke(false) // Restore original
                        onSync(_playbackPositionMs.value)
                    }
                }
            }
        } else {
            Log.d(TAG, "Pausing instrumental player, restoring original")
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (_instrumentalPlayer != null) {
                    instrumentalPlayer.pause()
                }
                onSetMutedByAi?.invoke(false)
            }
            _uiState.update { it.copy(isSingConfidentlyActive = false, isResolvingInstrumental = false) }
        }
    }

    fun seekTo(positionMs: Long) {
        if (_uiState.value.isSingConfidentlyActive) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                instrumentalPlayer.seekTo(positionMs)
            }
        }
        updateProgress(positionMs)
    }

    fun stopSingConfidently() {
        Log.d(TAG, "Stopping Sing Confidently mode")
        _uiState.update { it.copy(isSingConfidentlyActive = false, instrumentalStreamUrl = null) }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (_instrumentalPlayer != null) {
                instrumentalPlayer.pause()
                instrumentalPlayer.stop()
                instrumentalPlayer.clearMediaItems()
            }
            onSetMutedByAi?.invoke(false)
        }
    }

    private suspend fun <T> runGroqRequest(initialKey: String, requestBlock: suspend (String) -> T): T {
        return try {
            requestBlock(initialKey)
        } catch (e: HttpException) {
            if (e.code() == 401 && !settingsManager.hasUserApiKey("Groq")) {
                val refreshed = settingsManager.refreshRemoteKeyAfterAuthFailure("Groq", initialKey)
                if ((refreshed.source == ApiKeySource.REMOTE || refreshed.source == ApiKeySource.DEFAULT)
                    && refreshed.value.isNotBlank()
                    && refreshed.value != initialKey
                ) {
                    return requestBlock(refreshed.value)
                }
                throw IllegalStateException("Toolz default Groq key is invalid. Please add your own key in AI Settings.")
            }
            throw e
        }
    }

    private suspend fun callGroq(apiKey: String, prompt: String): String? {
        val request = OpenAiRequest(
            model    = GROQ_MODEL,
            messages = listOf(
                OpenAiMessage("system", MessageContent.Text("You are an elite music intelligence for Toolz. Be insightful, stylish, and brief.")),
                OpenAiMessage("user",   MessageContent.Text(prompt))
            ),
            maxTokens = 2048
        )
        return openAiService
            .getChatCompletion(url = GROQ_URL, authHeader = "Bearer $apiKey", request = request)
            .choices.firstOrNull()?.message?.content?.trim()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI toggles
    // ─────────────────────────────────────────────────────────────────────────

    fun toggleAutoScroll() {
        _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isAutoScrollEnabled = !it.lyricsState.isAutoScrollEnabled)) }
    }

    fun onManualScroll() {
        if (_uiState.value.lyricsState.isAutoScrollEnabled) {
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isAutoScrollEnabled = false)) }
        }
    }

    fun toggleSeekEnabled() {
        viewModelScope.launch {
            val new = !_uiState.value.lyricsState.isSeekEnabled
            settingsRepository.setMusicLyricsSeekEnabled(new)
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isSeekEnabled = new)) }
        }
    }

    fun toggleExpandedPill() = _uiState.update { it.copy(isExpandedPill = !it.isExpandedPill) }

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

    fun toggleAlwaysSync() {
        viewModelScope.launch {
            val new = !_uiState.value.lyricsState.alwaysSync
            settingsRepository.setMusicLyricsAlwaysSync(new)
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(alwaysSync = new)) }
        }
    }

    fun toggleWordSyncEnabled() {
        viewModelScope.launch {
            val new = !_uiState.value.lyricsState.isWordSyncEnabled
            settingsRepository.setMusicLyricsWordSyncEnabled(new)
        }
    }

    fun toggleKaraokeWordSyncEnabled() {
        viewModelScope.launch {
            val new = !_uiState.value.lyricsState.isKaraokeWordSyncEnabled
            settingsRepository.setKaraokeWordSyncEnabled(new)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Karaoke settings
    // ─────────────────────────────────────────────────────────────────────────

    fun setKaraokeSpeechCorrectionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKaraokeSpeechCorrectionEnabled(enabled) }
    }

    fun setQuickSingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKaraokeQuickSingEnabled(enabled) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Speech recognition – clean, stable, on-device first
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts a karaoke recognition session.
     *
     * Strategy:
     *  1. Try createOnDeviceSpeechRecognizer (no beeps, no network, fast).
     *  2. Fall back to the default (online) recognizer when on-device is unavailable.
     *
     * The recognizer runs on the MAIN thread (mandatory for SpeechRecognizer).
     * Only score/state updates are posted back through _uiState.
     */
    fun startKaraokeRecording() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer not available on this device")
            return
        }

        // Pre-calculate total words for immediate UI feedback
        val totalWordsCount = _uiState.value.lyricsState.syncedLyrics.sumOf { it.words.size }

        // Reset scoring and word statuses
        _uiState.update {
            it.copy(
                isKaraokeRecording  = true,
                karaokeScore        = 0,
                karaokeCorrectWords = 0,
                karaokeTotalWords   = totalWordsCount,
                karaokeMostAccurateLine = null,
                karaokeStreak       = 0,
                karaokeMaxStreak    = 0,
                karaokeMissedStreak = 0,
                lyricsState         = it.lyricsState.copy(
                    syncedLyrics = it.lyricsState.syncedLyrics.map { line ->
                        line.copy(words = line.words.map { w -> w.copy(karaokeStatus = KaraokeWordStatus.PENDING) })
                    }
                )
            )
        }

        // All recognizer work must happen on Main
        viewModelScope.launch(Dispatchers.Main) {
            destroyRecognizer()          // Safety – always start from a clean slate
            delay(100)                   // Brief breather for hardware
            buildRecognizer()
            startListening()
        }
    }

    fun stopKaraokeRecording() {
        _uiState.update { it.copy(isKaraokeRecording = false, micRms = 0f) }
        restartJob?.cancel()
        restartJob = null
        viewModelScope.launch(Dispatchers.Main) {
            destroyRecognizer()
        }
    }

    /**
     * Must be called on the MAIN thread.
     * Prefers the on-device recognizer; falls back to the default recognizer
     * when on-device is unavailable.
     */
    private fun buildRecognizer() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val isOnline = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        val onDeviceAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        
        // If online, use standard recognizer (prefers cloud).
        // If offline and on-device is available, use on-device specifically.
        speechRecognizer = if (!isOnline && onDeviceAvailable) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        Log.d(TAG, "SpeechRecognizer created – online=$isOnline, on-device=$onDeviceAvailable")

        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Highest sensitivity: specify voice recognition as source for hardware optimization
            // This ensures the mic is tuned for speech even with background music
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, MediaRecorder.AudioSource.VOICE_RECOGNITION)
            }
            
            // Improvements for singing: keep the mic open longer and be more sensitive to partials
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 20000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 7000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)

            // Prefer offline only if we are actually offline
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isOnline)
        }

        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    /** Must be called on the MAIN thread. */
    private fun destroyRecognizer() {
        isListening = false
        runCatching { speechRecognizer?.stopListening() }
        runCatching { speechRecognizer?.cancel() }
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        recognitionIntent = null
    }

    /** Must be called on the MAIN thread. */
    private fun startListening() {
        val recognizer = speechRecognizer ?: return
        val intent     = recognitionIntent ?: return
        if (isListening || !_uiState.value.isKaraokeRecording) return

        runCatching {
            recognizer.startListening(intent)
            isListening = true
        }.onFailure {
            Log.e(TAG, "startListening failed", it)
            isListening = false
        }
    }

    /**
     * Schedules a recognizer restart after [delayMs].
     * Cancels any pending restart first so we never double-start.
     */
    private fun scheduleRestart(delayMs: Long) {
        restartJob?.cancel()
        restartJob = viewModelScope.launch(Dispatchers.Main) {
            delay(delayMs)
            if (_uiState.value.isKaraokeRecording) {
                isListening = false
                startListening()
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            Log.v(TAG, "Recognizer ready")
        }
        override fun onBeginningOfSpeech() {
            isListening = true
            Log.v(TAG, "Speech beginning")
        }
        override fun onEndOfSpeech()       { isListening = false }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            if (_uiState.value.isKaraokeRecording) {
                // Log occasionally to verify mic is active without flooding
                if (System.currentTimeMillis() % 1000 < 50) {
                    Log.v(TAG, "Mic RMS: $rmsdB")
                }
                _uiState.update { it.copy(micRms = rmsdB) }
            }
        }

        override fun onError(error: Int) {
            isListening = false
            _uiState.update { it.copy(micRms = 0f) }

            if (!_uiState.value.isKaraokeRecording) return

            val errorMsg = when(error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error $error"
            }

            if (error in FATAL_ERRORS) {
                Log.e(TAG, "Fatal SpeechRecognizer error: $errorMsg – stopping session")
                stopKaraokeRecording()
                return
            }

            val delay = ERROR_RESTART_DELAY_MS[error] ?: ERROR_RESTART_DELAY_MS[0]!!
            Log.d(TAG, "SpeechRecognizer error: $errorMsg – restarting in ${delay}ms")
            scheduleRestart(delay)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            _uiState.update { it.copy(micRms = 0f) }

            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!texts.isNullOrEmpty()) {
                Log.d(TAG, "Speech Results: $texts")
                processRecognizedTexts(texts)
            }

            // Restart immediately for the next phrase
            if (_uiState.value.isKaraokeRecording) scheduleRestart(50L)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!texts.isNullOrEmpty()) {
                // Highly responsive word matching – don't log to avoid flood
                processRecognizedTexts(texts)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enhanced Word scoring  (Improved fuzzy match with phonetic similarity, runs on Default dispatcher)
    // ─────────────────────────────────────────────────────────────────────────

    private fun processRecognizedTexts(texts: List<String>) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentTime = _playbackPositionMs.value
            val speechCorrect = _uiState.value.karaokeSpeechCorrectionEnabled

            // 1. Update Buffer: Add new tokens with current playback timestamp
            val newTokens = texts
                .asSequence()
                .flatMap { it.split(Regex("\\s+")) }
                .map { normalizeAgnostic(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .map { TokenEvent(it, currentTime) }
                .toList()

            synchronized(recognitionBuffer) {
                recognitionBuffer.addAll(newTokens)
                // Keep only last 5 seconds of tokens to compensate for bursty STT
                recognitionBuffer.removeAll { currentTime - it.timestamp > 5000L }
            }

            val bufferedTokens = synchronized(recognitionBuffer) { recognitionBuffer.map { it.token }.distinct() }
            if (bufferedTokens.isEmpty()) return@launch

            _uiState.update { state ->
                var newWordsCounted = 0
                var missedCountInThisUpdate = 0

                val updatedLyrics = state.lyricsState.syncedLyrics.map { line ->
                    // Performance optimization: skip lines far away from current time
                    if (kotlin.math.abs(line.timeMs - currentTime) > 40_000L) return@map line

                    val updatedWords = line.words.map { word ->
                        val cleanTarget = normalizeAgnostic(word.word)
                        if (cleanTarget.isBlank()) return@map word

                        // Buffer compensation: check if word was spoken in the last 5 seconds
                        val windowStart = word.startTimeMs - 5000L
                        val windowEnd = word.startTimeMs + word.durationMs + 7000L
                        val missedGrace = word.startTimeMs + word.durationMs + 12000L

                        if (currentTime in windowStart..windowEnd && word.karaokeStatus == KaraokeWordStatus.PENDING) {
                            val matched = bufferedTokens.any { spoken ->
                                isWordMatch(spoken, cleanTarget)
                            }
                            if (matched) {
                                newWordsCounted++
                                word.copy(karaokeStatus = KaraokeWordStatus.CORRECT)
                            } else word
                        } else if (currentTime > missedGrace && word.karaokeStatus == KaraokeWordStatus.PENDING && speechCorrect) {
                            missedCountInThisUpdate++
                            word.copy(karaokeStatus = KaraokeWordStatus.MISSED)
                        } else {
                            word
                        }
                    }

                    // Dynamic "Singer's Grace" Threshold
                    val significantWords = updatedWords.filter { it.word.lowercase() !in STOP_WORDS }
                    val correctSignificant = significantWords.count { it.karaokeStatus == KaraokeWordStatus.CORRECT }

                    val threshold = when {
                        significantWords.size <= 3 -> 0.8f
                        significantWords.size >= 5 -> 0.6f
                        else -> 0.7f
                    }

                    val finalWords = if (significantWords.isNotEmpty() && (correctSignificant.toFloat() / significantWords.size >= threshold)) {
                        updatedWords.map {
                            if (it.karaokeStatus == KaraokeWordStatus.PENDING) it.copy(karaokeStatus = KaraokeWordStatus.CORRECT)
                            else it
                        }
                    } else {
                        updatedWords
                    }

                    line.copy(words = finalWords)
                }

                val totalWords = updatedLyrics.sumOf { it.words.size }
                val correctWords = updatedLyrics.sumOf { l -> l.words.count { it.karaokeStatus == KaraokeWordStatus.CORRECT } }
                val score = if (totalWords > 0) (correctWords * 100 / totalWords) else 0

                val bestLine = updatedLyrics
                    .filter { it.words.isNotEmpty() && it.words.any { w -> w.karaokeStatus != KaraokeWordStatus.PENDING } }
                    .maxByOrNull { l ->
                        l.words.count { it.karaokeStatus == KaraokeWordStatus.CORRECT }.toFloat() / l.words.size
                    }?.content

                val nextMissedStreak = if (newWordsCounted > 0) 0
                                      else if (missedCountInThisUpdate > 0) state.karaokeMissedStreak + missedCountInThisUpdate
                                      else state.karaokeMissedStreak

                val currentStreak = if (newWordsCounted > 0) state.karaokeStreak + newWordsCounted
                                   else if (nextMissedStreak >= 3) 0
                                   else state.karaokeStreak

                state.copy(
                    lyricsState             = state.lyricsState.copy(syncedLyrics = updatedLyrics),
                    karaokeScore            = score,
                    karaokeCorrectWords     = correctWords,
                    karaokeTotalWords       = totalWords,
                    karaokeMostAccurateLine = bestLine,
                    karaokeStreak           = currentStreak,
                    karaokeMaxStreak        = kotlin.math.max(state.karaokeMaxStreak, currentStreak),
                    karaokeMissedStreak     = nextMissedStreak
                )
            }
        }
    }

    /**
     * Multilingual Phonetic-Agnostic Scorer.
     * 1. Normalizes diacritics and symbols.
     * 2. Extracts Vowel Skeleton (loooove -> oe).
     * 3. Uses Jaro-Winkler for character cluster similarity.
     */
    private fun isWordMatch(spoken: String, target: String): Boolean {
        if (spoken == target) return true

        // Jaro-Winkler similarity: rewards character "clusters" regardless of length
        val similarity = jaroWinklerSimilarity(spoken, target)
        if (similarity >= 0.85) return true

        // Vowel Skeleton match: fallback if Jaro-Winkler is promising but not perfect.
        // Also protects against "Vowel Collision" on very short words by requiring 2+ vowels.
        if (similarity >= 0.7) {
            val targetSkeleton = getVowelSkeleton(target)
            if (targetSkeleton.length >= 2) {
                val spokenSkeleton = getVowelSkeleton(spoken)
                if (spokenSkeleton == targetSkeleton) return true
            }
        }

        return false
    }

    private fun normalizeAgnostic(text: String): String {
        // Strip diacritics (café -> cafe) and handle non-English characters
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}"), "") // Remove marks
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }

    private fun getVowelSkeleton(text: String): String {
        val vowels = "aeiouy"
        return text.lowercase()
            .filter { it in vowels }
            // deduplicate consecutive identical vowels: "loooove" -> "oe"
            .replace(Regex("([aeiouy])\\1+"), "$1")
    }

    private fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val matchDistance = (kotlin.math.max(len1, len2) / 2) - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)
        var matches = 0

        for (i in 0 until len1) {
            val start = kotlin.math.max(0, i - matchDistance)
            val end = kotlin.math.min(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0.0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        val jaro = (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0

        // Winkler enhancement
        val p = 0.1 // scaling factor
        var l = 0 // length of common prefix
        while (l < kotlin.math.min(4, kotlin.math.min(len1, len2)) && s1[l] == s2[l]) {
            l++
        }

        return jaro + (l * p * (1.0 - jaro))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        restartJob?.cancel()
        // speechRecognizer must be destroyed on Main; viewModelScope is cancelled
        // by the time onCleared() is called, so we use a direct Main.immediate post.
        val sr = speechRecognizer
        speechRecognizer = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { sr?.stopListening() }
            runCatching { sr?.cancel() }
            runCatching { sr?.destroy() }
            runCatching { instrumentalPlayer.release() }
        }
    }
}