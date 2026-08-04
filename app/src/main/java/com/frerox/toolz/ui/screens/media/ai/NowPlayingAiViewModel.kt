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

package com.frerox.toolz.ui.screens.media.ai

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.RecognitionListener
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "NowPlayingAiVM"
private const val GROQ_URL   = "https://api.groq.com/openai/v1/chat/completions"
private const val GROQ_MODEL = "llama-3.3-70b-versatile"

// ─────────────────────────────────────────────────────────────────────────
// Speech recognizer tuning
//
// IMPORTANT HISTORY: this used to configure
// EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS = 20_000L. That forced the
// underlying recognition service to keep every single utterance session
// open for a *minimum* of 20 real seconds before it was allowed to finalize
// on silence. Most OEM recognition services do not handle that well: they
// either sit "open" without ever calling back (which is what made speech
// correction look completely dead — the mic light stays on but nothing
// happens), or they throw ERROR_CLIENT / ERROR_SERVER internally, which
// tore the whole session down. That single constant was the root cause of
// "speech correction doesn't work". Sane single-utterance dictation values
// are in the 1-2 second range.
// ─────────────────────────────────────────────────────────────────────────
private const val RECOGNIZER_MIN_SPEECH_LENGTH_MS = 1500L
private const val RECOGNIZER_COMPLETE_SILENCE_MS = 1200L
private const val RECOGNIZER_POSSIBLY_COMPLETE_SILENCE_MS = 800L

// Delay before requeuing startListening() after a *successful* onResults —
// kept tiny so there's effectively no audible/visible mic gap between
// phrases.
private const val QUICK_RESTART_DELAY_MS = 30L

// Delay before requeuing after a routine NO_MATCH / SPEECH_TIMEOUT (silence
// between lyric lines). Also tiny, and — critically — never touches the
// backoff/rebuild machinery below.
private const val ROUTINE_GAP_RESTART_DELAY_MS = 150L

// ERROR_RECOGNIZER_BUSY almost always means WE tried to restart before the
// recognition service had fully released the previous session — it is not
// a real failure. We retry a handful of times on a short fixed delay before
// treating it as something that needs a full rebuild.
private const val BUSY_RETRY_DELAY_MS = 250L
private const val MAX_BUSY_RETRIES = 8

// Base restart delay per SpeechRecognizer error code, used ONLY for genuine
// failures (network/server/too-many-requests/unknown). These get real
// exponential backoff since retrying instantly won't help.
private val ERROR_BASE_DELAY_MS = mapOf(
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT   to 1500L,
    SpeechRecognizer.ERROR_NETWORK           to 1500L,
    SpeechRecognizer.ERROR_SERVER            to 2000L,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS to 2500L,
    0                                         to 800L   // default bucket
)

private const val MAX_BACKOFF_MS = 5_000L
private const val REBUILD_AFTER_N_FAILURES = 3
private const val WATCHDOG_SILENCE_TIMEOUT_MS = 15_000L

// Drop low-confidence alternatives when the recognizer actually reports
// confidence scores. This mainly filters out garbage picked up from the
// instrumental track bleeding into the mic rather than the singer's voice.
private const val MIN_RESULT_CONFIDENCE = 0.3f

// Single source of truth for how long we wait, past a word's expected end
// time, before marking it MISSED. Using 8s because LRC word timings are
// estimated for non-word-synced lyrics and can be several seconds off.
private const val MISSED_WORD_GRACE_MS = 8_000L

// Recognition buffer deduplication window. 5s keeps tokens alive across a
// full lyric line (which is typically 3-6s) and allows partial + final
// results to both land within the same matching window.
private const val RECOGNITION_DEDUP_WINDOW_MS = 5_000L

// Errors that are fatal – we stop the session rather than retry.
// NOTE: ERROR_AUDIO is intentionally NOT here — it fires when MediaRecorder
// briefly competes for the mic. It resolves on a rebuild+retry and should
// never permanently stop a session.
private val FATAL_ERRORS = setOf(
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
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

    private data class TokenEvent(val token: String, val phoneticKey: String, val timestamp: Long)
    private val recognitionBuffer = mutableListOf<TokenEvent>()
    var onSetMutedByAi: ((Boolean) -> Unit)? = null
    var onPauseOriginal: ((Boolean) -> Unit)? = null

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
    private var speechRecognizer   : SpeechRecognizer? = null
    private var recognitionIntent  : Intent?           = null
    private var restartJob         : Job?              = null
    private var watchdogJob        : Job?              = null

    // Internal "is a startListening() session currently open" flag. This is
    // deliberately separate from uiState.isListening: the UI flag reflects
    // "the mic is conceptually active for karaoke" and should stay steady
    // for the whole recording session, while this one flips constantly
    // (every single utterance) as an implementation detail. Conflating the
    // two was what made the mic indicator visibly flicker on/off between
    // every line — the UI was mirroring an internal bookkeeping flag instead
    // of the actual "are we trying to listen" state.
    private var sessionActive      : Boolean            = false
    private var consecutiveFailures: Int                = 0
    private var lyricsSearchJob: Job?                   = null
    private var aiInsightJob: Job?                      = null
    private var instrumentalSearchJob: Job?             = null
    
    private var nextInstrumentalSearchPage: Page?       = null
    private var currentInstrumentalQuery: String?       = null

    private var busyRetryCount     : Int                = 0
    private var lastCallbackAtMs   : Long                = 0L
    private var lastMissedCheckMs  : Long                = 0L  // throttle for checkMissedWords

    // Mutex that serializes all lyrics-state mutations (checkMissedWords AND
    // processRecognizedTexts both update the same list). Using a Mutex
    // instead of @GuardedBy because we need suspend-friendly locking that
    // works across coroutine dispatchers.
    private val lyricsMutex = Mutex()

    // Per-track fetch jobs so a fast skip can't let a stale response overwrite
    // the state of whatever track the user is now looking at.
    private var lyricsJob        : Job? = null
    private var moreInfoJob      : Job? = null
    private var recommendationsJob: Job? = null
    private var prefetchJob      : Job? = null

    // Phonetic-key cache for lyric target words, keyed by normalized word text.
    private val targetPhoneticCache = mutableMapOf<String, String>()

    private var progressPollJob: Job? = null

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
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.update { it.copy(isInstrumentalPlaying = isPlaying) }
                            progressPollJob?.cancel()
                            if (isPlaying) {
                                progressPollJob = viewModelScope.launch {
                                    while (_uiState.value.isInstrumentalPlaying && _uiState.value.isSingConfidentlyActive) {
                                        val pos = _instrumentalPlayer?.currentPosition ?: 0L
                                        updateProgress(pos)
                                        delay(500)
                                    }
                                }
                            }
                        }
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

        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(10_000)
            preFetchAllLyrics()
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
            settingsRepository.karaokeSingConfidentlyMode.collect { modeStr ->
                val mode = try { SingConfidentlyMode.valueOf(modeStr) } catch (_: Exception) { SingConfidentlyMode.AUTO }
                _uiState.update { it.copy(singConfidentlyMode = mode) }
            }
        }
        // Legacy collection to keep boolean in sync just in case
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
        viewModelScope.launch {
            settingsRepository.karaokeAutoRecordEnabled.collect { enabled ->
                _uiState.update { it.copy(autoRecordEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            settingsRepository.userName.collect { name ->
                _uiState.update { it.copy(userName = name) }
            }
        }

        checkGroqKey()
    }

    private fun checkGroqKey() {
        val key = getGroqKey()
        _uiState.update { it.copy(isGroqKeyMissing = key.isBlank()) }
    }

    fun saveGroqKey(key: String) {
        settingsManager.setApiKey(key, "Groq")
        _uiState.update { it.copy(isGroqKeyMissing = false) }
        refreshCurrentTab()
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

        // Cancel any in-flight fetches from the previous track so a late
        // response can't clobber the new track's state.
        lyricsJob?.cancel()
        moreInfoJob?.cancel()
        recommendationsJob?.cancel()

        // Stops instrumental playback AND karaoke recording (the mic session
        // from the previous track must never bleed into the new track's timeline).
        stopSingConfidently()
        stopKaraokeRecording()
        // stopKaraokeRecording() is a no-op if a recognizer was only ever
        // pre-warmed (see searchInstrumental) but never actually started —
        // isKaraokeRecording would still be false in that case, so it would
        // return early and leak the pre-warmed instance. Always tear down
        // unconditionally as a final safety net.
        viewModelScope.launch(Dispatchers.Main) { destroyRecognizer() }

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
        rebuildPhoneticCache(_uiState.value.lyricsState.syncedLyrics)

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

        if (_uiState.value.isKaraokeRecording) {
            // Throttle to at most every 500ms to avoid hammering lyricsMutex
            val now = System.currentTimeMillis()
            if (now - lastMissedCheckMs >= 500L) {
                lastMissedCheckMs = now
                checkMissedWords(positionMs)
            }
        }
    }

    /**
     * Single source of truth for marking words MISSED once they've been
     * pending too long.
     *
     * Serialized through lyricsMutex so it never races with
     * processRecognizedTexts, which also writes to syncedLyrics.
     */
    private fun checkMissedWords(currentTime: Long) {
        if (!_uiState.value.karaokeSpeechCorrectionEnabled) return
        val sessionId = _uiState.value.karaokeSessionId

        viewModelScope.launch(Dispatchers.Default) {
            lyricsMutex.withLock {
                // Guard: abort if session changed while we were waiting for the lock
                if (_uiState.value.karaokeSessionId != sessionId) return@withLock

                _uiState.update { state ->
                    if (state.karaokeSessionId != sessionId) return@update state

                    var newlyMissed = 0
                    val updatedLyrics = state.lyricsState.syncedLyrics.map { line ->
                        if (line.timeMs > currentTime) return@map line

                        var lineChanged = false
                        val updatedWords = line.words.map { word ->
                            val missedAt = word.startTimeMs + word.durationMs + MISSED_WORD_GRACE_MS
                            if (currentTime > missedAt && word.karaokeStatus == KaraokeWordStatus.PENDING) {
                                lineChanged = true
                                newlyMissed++
                                word.copy(karaokeStatus = KaraokeWordStatus.MISSED)
                            } else {
                                word
                            }
                        }
                        if (lineChanged) line.copy(words = updatedWords) else line
                    }

                    if (newlyMissed == 0) return@update state

                    // Use stable totalWords from session-start to avoid score
                    // inflation from lines the singer hasn't reached yet.
                    val totalWords   = state.karaokeTotalWords.coerceAtLeast(1)
                    val correctWords = updatedLyrics.sumOf { l -> l.words.count { it.karaokeStatus == KaraokeWordStatus.CORRECT } }
                    val score        = (correctWords * 100 / totalWords).coerceIn(0, 100)
                    val nextMissedStreak = state.karaokeMissedStreak + newlyMissed
                    val nextStreak = if (nextMissedStreak >= 3) 0 else state.karaokeStreak

                    // Lines metric
                    val linesWithWords = updatedLyrics.filter { it.words.isNotEmpty() }
                    val correctLines = linesWithWords.count { l ->
                        val sig = l.words.filter { it.word.lowercase() !in STOP_WORDS }
                        sig.isNotEmpty() && sig.count { it.karaokeStatus == KaraokeWordStatus.CORRECT }.toFloat() / sig.size >= 0.6f
                    }

                    state.copy(
                        lyricsState         = state.lyricsState.copy(syncedLyrics = updatedLyrics),
                        karaokeScore        = score,
                        karaokeCorrectWords = correctWords,
                        karaokeMissedStreak = nextMissedStreak,
                        karaokeStreak       = nextStreak,
                        karaokeCorrectLines = correctLines
                    )
                }
            }
        }
    }

    fun checkQuickSing(onSeek: (Long) -> Unit) {
        if (!_uiState.value.quickSingEnabled) return
        val lyrics      = _uiState.value.lyricsState.syncedLyrics
        if (lyrics.isEmpty()) return
        val currentTime = _playbackPositionMs.value

        val firstLineTime = lyrics.first().timeMs
        if (currentTime < firstLineTime - 5_000L) {
            onSeek((firstLineTime - 3_000L).coerceAtLeast(0L))
            return
        }

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
        val songIdOnStart = uri

        val artist = song.artist.takeUnless { it.contains("Unknown", true) } ?: ""
        val album  = song.album.takeUnless  { it.contains("Unknown", true) } ?: ""

        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val offline = settingsRepository.offlineModeEnabled.first()
            _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = true, lyrics = "", syncedLyrics = emptyList(), isSynced = false), error = null) }
            try {
                val allTracks = musicRepository.allTracks.first()
                val track     = allTracks.find { it.uri == uri }
                val sourceUrl = track?.sourceUrl

                var lyricsContent: String? = null
                var isSynced              = false

                // 1. Check cached database lyrics first
                if (!track?.aiLyrics.isNullOrBlank()) {
                    lyricsContent = track?.aiLyrics
                    isSynced = lyricsContent?.contains("[0") == true || lyricsContent?.contains("[1") == true
                    Log.d(TAG, "Loaded cached lyrics from DB for: ${song.title}")
                }

                // 2. Check embedded audio metadata lyrics
                if (lyricsContent == null) {
                    val embedded = LyricsExtractor.extractEmbeddedLyrics(context, uri, track?.path)
                    if (embedded != null) {
                        lyricsContent = embedded
                        isSynced = embedded.contains("[0") || embedded.contains("[1")
                        Log.d(TAG, "Extracted embedded metadata lyrics for: ${song.title}")
                    }
                }

                // 3. Check local sidecar files (.lrc, .txt)
                if (lyricsContent == null && track?.path != null) {
                    val sidecar = LyricsExtractor.findLocalSidecarLyrics(track.path)
                    if (sidecar != null) {
                        lyricsContent = sidecar.first
                        isSynced = sidecar.second
                        Log.d(TAG, "Loaded local sidecar lyrics for: ${song.title}")
                    }
                }

                // 4. Online fetching with LRCLIB multi-stage fallback strategy
                if (!offline && lyricsContent == null) {
                    var effectiveArtist = artist.takeUnless { it.equals("Unknown Artist", true) || it.equals("Unknown", true) } ?: ""
                    var cleanTitle = song.title
                        .replace(Regex("(?i)\\.(mp3|m4a|flac|wav|aac|ogg|opus|wma)$"), "")
                        .replace(Regex("^\\d{1,3}[\\s._-]+"), "")
                        .replace('_', ' ')
                        .trim()

                    if (effectiveArtist.isBlank()) {
                        val dashRegex = Regex("\\s+[-–—]\\s+")
                        if (cleanTitle.contains(dashRegex)) {
                            val parts = cleanTitle.split(dashRegex, 2)
                            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                                effectiveArtist = parts[0].trim()
                                cleanTitle = parts[1].trim()
                            }
                        }
                    }

                    cleanTitle = cleanTitle
                        .replace(Regex("(?i)\\([^)]*\\)"), "")
                        .replace(Regex("(?i)\\[[^\\]]*\\]"), "")
                        .replace(Regex("(?i)-?\\s*(Official|Lyric|Lyrics|Music|Audio|HD|4K|Video)\\s*(Video)?.*"), "")
                        .replace(Regex("(?i)(feat|ft|featuring)\\..*"), "")
                        .replace(Regex("(?i)remastered.*"), "")
                        .replace(Regex("(?i)single.*"), "")
                        .trim()

                    runCatching {
                        val exactJob = async {
                            runCatching {
                                if (effectiveArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                                    lrcLibService.getLyrics(
                                        trackName = cleanTitle,
                                        artistName = effectiveArtist,
                                        albumName = album.ifEmpty { null },
                                        durationInSeconds = (song.durationInMillis / 1000).toInt()
                                    )
                                } else null
                            }.getOrNull()
                        }

                        val relaxedJob = async {
                            runCatching {
                                if (effectiveArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                                    lrcLibService.getLyrics(
                                        trackName = cleanTitle,
                                        artistName = effectiveArtist,
                                        albumName = null,
                                        durationInSeconds = null
                                    )
                                } else null
                            }.getOrNull()
                        }

                        val searchJob = async {
                            runCatching {
                                if (effectiveArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                                    lrcLibService.searchLyrics(cleanTitle, effectiveArtist)
                                } else emptyList()
                            }.getOrNull() ?: emptyList()
                        }

                        val queryJob = async {
                            runCatching {
                                val queryStr = if (effectiveArtist.isNotBlank()) "$cleanTitle $effectiveArtist" else cleanTitle
                                if (queryStr.isNotBlank()) {
                                    lrcLibService.searchLyricsByQuery(queryStr)
                                } else emptyList()
                            }.getOrNull() ?: emptyList()
                        }

                        val titleOnlyJob = async {
                            runCatching {
                                if (cleanTitle.isNotBlank()) {
                                    lrcLibService.searchLyricsByQuery(cleanTitle)
                                } else emptyList()
                            }.getOrNull() ?: emptyList()
                        }

                        val exactResp = exactJob.await()
                        val relaxedResp = relaxedJob.await()
                        val searchResps = searchJob.await()
                        val queryResps = queryJob.await()
                        val titleOnlyResps = titleOnlyJob.await()

                        val candidates = listOfNotNull(exactResp, relaxedResp) + searchResps + queryResps + titleOnlyResps
                        val syncedMatch = candidates.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
                        val plainMatch = candidates.firstOrNull { !it.plainLyrics.isNullOrBlank() }

                        if (syncedMatch != null) {
                            lyricsContent = syncedMatch.syncedLyrics
                            isSynced = true
                        } else if (plainMatch != null) {
                            lyricsContent = plainMatch.plainLyrics
                            isSynced = false
                        }
                    }

                    // 5. Catalog captions fallback for online streaming tracks
                    if (lyricsContent == null && sourceUrl != null) {
                        runCatching {
                            lyricsContent = catalogRepository.fetchCaptions(sourceUrl)
                            isSynced = lyricsContent?.contains("[0") == true || lyricsContent?.contains("[1") == true
                        }
                    }

                    // 6. Secondary public lyrics API fallback (Lyrist / lyrics.ovh)
                    if (lyricsContent == null && cleanTitle.isNotBlank()) {
                        runCatching {
                            val queryStr = if (effectiveArtist.isNotBlank()) "$cleanTitle $effectiveArtist" else cleanTitle
                            val encodedQuery = java.net.URLEncoder.encode(queryStr, "UTF-8")
                            val lyristUrl = "https://lyrist.vericatch.com/api/$encodedQuery"
                            val conn = java.net.URL(lyristUrl).openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 4000
                            conn.readTimeout = 4000
                            if (conn.responseCode == 200) {
                                val jsonStr = conn.inputStream.bufferedReader().readText()
                                val json = org.json.JSONObject(jsonStr)
                                val lyrics = json.optString("lyrics")
                                if (lyrics.isNotBlank() && lyrics != "null") {
                                    lyricsContent = lyrics.trim()
                                    isSynced = lyricsContent?.contains("[0") == true || lyricsContent?.contains("[1") == true
                                    Log.d(TAG, "Fetched secondary fallback lyrics from Lyrist for $cleanTitle")
                                }
                            }
                        }
                    }

                    if (lyricsContent == null && effectiveArtist.isNotBlank() && cleanTitle.isNotBlank()) {
                        runCatching {
                            val encodedArtist = java.net.URLEncoder.encode(effectiveArtist, "UTF-8")
                            val encodedTitle  = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                            val ovhUrl = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
                            val conn = java.net.URL(ovhUrl).openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 4000
                            conn.readTimeout = 4000
                            if (conn.responseCode == 200) {
                                val jsonStr = conn.inputStream.bufferedReader().readText()
                                val rawLyrics = org.json.JSONObject(jsonStr).optString("lyrics")
                                if (rawLyrics.isNotBlank() && rawLyrics != "null") {
                                    lyricsContent = rawLyrics.trim()
                                    isSynced = false
                                    Log.d(TAG, "Fetched secondary fallback lyrics from lyrics.ovh for $cleanTitle")
                                }
                            }
                        }
                    }
                }

                if (lyricsContent != null) {
                    if (currentTrackUri != songIdOnStart) {
                        _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false)) }
                        return@launch
                    }
                    val synced = parseLrc(lyricsContent)
                    _uiState.update {
                        it.copy(lyricsState = it.lyricsState.copy(
                            lyrics       = lyricsContent,
                            isLoading    = false,
                            syncedLyrics = synced,
                            isSynced     = isSynced
                        ))
                    }
                    rebuildPhoneticCache(synced)
                    musicRepository.updateTrackAiData(uri, lyrics = lyricsContent)
                } else {
                    if (currentTrackUri != songIdOnStart) {
                        _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false)) }
                        return@launch
                    }
                    _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false), error = if (offline) null else "Lyrics not found.") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "fetchLyrics error", e)
                if (currentTrackUri != songIdOnStart) {
                    _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false)) }
                    return@launch
                }
                _uiState.update { it.copy(lyricsState = it.lyricsState.copy(isLoading = false), error = "Lyrics not found.") }
            }
        }
    }

    private suspend fun preFetchAllLyrics() {
        val tracksList = musicRepository.allTracks.first()
        tracksList.filter { it.aiLyrics == null }.forEach { track ->
            try {
                val cleanTitle = track.title
                    .replace(Regex("\\(.*?\\)"), "")
                    .replace(Regex("\\[.*?\\]"), "")
                    .replace(Regex("(?i)-?\\s*(Official|Lyric|Music|Audio)\\s*(Video)?.*"), "")
                    .replace(Regex("(?i)(feat|ft)\\..*"), "")
                    .trim()

                val response = runCatching {
                    lrcLibService.getLyrics(
                        trackName = cleanTitle,
                        artistName = track.artist ?: "",
                        albumName = track.album,
                        durationInSeconds = (track.duration / 1000).toInt()
                    )
                }.getOrNull() ?: runCatching {
                    lrcLibService.searchLyrics(cleanTitle, track.artist ?: "").firstOrNull()
                }.getOrNull()

                val lyrics = response?.syncedLyrics ?: response?.plainLyrics
                if (lyrics != null) {
                    musicRepository.updateTrackAiData(track.uri, lyrics = lyrics)
                    Log.d(TAG, "Pre-fetched lyrics for: ${track.title}")
                }
                delay(2000) // Respect API limits
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Pre-fetch failed for ${track.title}", e)
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

        moreInfoJob?.cancel()
        moreInfoJob = viewModelScope.launch {
            if (settingsRepository.offlineModeEnabled.first()) return@launch
            val key = getGroqKey()
            if (key.isBlank()) {
                _uiState.update { it.copy(isGroqKeyMissing = true) }
                return@launch
            }
            _uiState.update { it.copy(moreInfoState = it.moreInfoState.copy(isLoading = true, artistVitals = "", songMeaning = ""), error = null, isGroqKeyMissing = false) }
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

                if (currentTrackUri != uri) return@launch
                _uiState.update {
                    it.copy(moreInfoState = it.moreInfoState.copy(artistVitals = vitals, songMeaning = meaning, isLoading = false))
                }
                musicRepository.updateTrackAiData(uri, vitals = vitals, meaning = meaning)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (currentTrackUri != uri) return@launch
                _uiState.update { it.copy(moreInfoState = it.moreInfoState.copy(isLoading = false), error = "AI Insight failed.") }
            }
        }
    }

    private fun fetchAiRecommendations() {
        val uri  = currentTrackUri ?: return
        val song = _uiState.value.currentSong ?: return

        recommendationsJob?.cancel()
        recommendationsJob = viewModelScope.launch {
            if (settingsRepository.offlineModeEnabled.first()) return@launch
            val key = getGroqKey()
            if (key.isBlank()) {
                _uiState.update { it.copy(isGroqKeyMissing = true) }
                return@launch
            }

            _uiState.update {
                it.copy(tasteState = it.tasteState.copy(
                    isLoadingCurated = true, isLoadingArtist = true,
                    curatedRecommendations = emptyList(), artistRecommendations = emptyList()
                ), error = null, isGroqKeyMissing = false)
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
                    if (currentTrackUri != uri) return@launch
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(curatedRecommendations = enriched, isLoadingCurated = false)) }
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    if (currentTrackUri != uri) return@onFailure
                    _uiState.update { s -> s.copy(tasteState = s.tasteState.copy(isLoadingCurated = false)) }
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
                    if (currentTrackUri != uri) return@launch
                    _uiState.update { it.copy(tasteState = it.tasteState.copy(artistRecommendations = enriched, isLoadingArtist = false)) }
                }.onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    if (currentTrackUri != uri) return@onFailure
                    _uiState.update { s -> s.copy(tasteState = s.tasteState.copy(isLoadingArtist = false)) }
                }
            }
        }
    }

    // ── Sing Confidently ──────────────────────────────────────────────────────

    private fun scoreInstrumentalCandidate(result: CatalogTrack, targetDurationMs: Long): Double {
        val lowTitle = result.title.lowercase()
        val durationDeltaMs = abs(result.duration - targetDurationMs)

        val hasInstrumentalSignal = lowTitle.contains("instrumental") || lowTitle.contains("karaoke")
        if (!hasInstrumentalSignal) return Double.NEGATIVE_INFINITY
        if (durationDeltaMs > 30_000L) return Double.NEGATIVE_INFINITY

        var score = 100.0
        score += (1.0 - (durationDeltaMs.toDouble() / 30_000.0).coerceIn(0.0, 1.0)) * 60.0

        if (lowTitle.contains("instrumental")) score += 15.0
        if (lowTitle.contains("official")) score += 5.0

        if (lowTitle.contains("reaction")) score -= 40.0
        if (lowTitle.contains("cover") && !lowTitle.contains("instrumental")) score -= 25.0
        if (lowTitle.contains("live")) score -= 10.0

        return score
    }

    private fun searchInstrumental(track: MusicTrack) {
        instrumentalSearchJob?.cancel()
        instrumentalSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSearchingInstrumental = true, instrumentalMatch = null) }
            try {
                val query = "${track.title} ${track.artist} instrumental"
                val (results, _) = catalogRepository.search(query)

                Log.d(TAG, "Instrumental search for '$query' found ${results.size} results")

                val scoredResults = results
                    .map { it to scoreInstrumentalCandidate(it, track.duration) }
                    .filter { (_, score) -> score.isFinite() }
                    .sortedByDescending { (_, score) -> score }

                val best = scoredResults.firstOrNull()?.first
                val top10 = scoredResults.take(10).map { it.first }

                _uiState.update { 
                    it.copy(
                        isSearchingInstrumental = false, 
                        instrumentalMatch = best,
                        instrumentalTopResults = top10
                    ) 
                }
                if (best != null) {
                    Log.d(TAG, "Best instrumental match: ${best.title} (${best.duration}ms)")
                    
                    // NEW: Pre-resolve the stream URL
                    launch {
                        try {
                            val streamUrl = catalogRepository.resolveAudioStream(best.sourceUrl)
                            if (streamUrl != null) {
                                _uiState.update { it.copy(instrumentalStreamUrl = streamUrl) }
                                Log.d(TAG, "Pre-resolved instrumental stream: $streamUrl")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to pre-resolve instrumental stream", e)
                        }
                    }

                    // Pre-warm the SpeechRecognizer now, while the user is
                    // still looking at the match — building/binding to the
                    // recognition service is what actually takes a noticeable
                    // moment, not startListening() itself. Doing it here
                    // means that by the time the user hits play, the only
                    // remaining step is startListening(), which is instant.
                    if (_uiState.value.karaokeSpeechCorrectionEnabled) {
                        prewarmRecognizer()
                    }
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
            currentInstrumentalQuery = query
            _uiState.update { it.copy(isSearchingInstrumental = true, instrumentalSearchResults = emptyList()) }
            try {
                val (results, nextPage) = catalogRepository.search(query)
                nextInstrumentalSearchPage = nextPage
                _uiState.update { it.copy(isSearchingInstrumental = false, instrumentalSearchResults = results) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Custom instrumental search failed", e)
                _uiState.update { it.copy(isSearchingInstrumental = false) }
            }
        }
    }

    fun loadMoreInstrumentalSearch() {
        val query = currentInstrumentalQuery ?: return
        val page = nextInstrumentalSearchPage ?: return
        if (_uiState.value.isSearchingInstrumental) return
        if (_uiState.value.instrumentalSearchResults.size >= 50) return // Hard limit 50

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSearchingInstrumental = true) }
            try {
                val (newResults, nextPage) = catalogRepository.search(query, page)
                nextInstrumentalSearchPage = nextPage
                _uiState.update { it.copy(
                    isSearchingInstrumental = false, 
                    instrumentalSearchResults = (it.instrumentalSearchResults + newResults).take(50)
                ) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Load more instrumental search failed", e)
                _uiState.update { it.copy(isSearchingInstrumental = false) }
            }
        }
    }

    fun setInstrumentalMatch(track: CatalogTrack) {
        _uiState.update { it.copy(instrumentalMatch = track, instrumentalStreamUrl = null) }
    }

    fun setSingConfidentlyMode(mode: SingConfidentlyMode) {
        viewModelScope.launch {
            settingsRepository.setKaraokeSingConfidentlyMode(mode.name)
            if (mode == SingConfidentlyMode.OFF) {
                stopSingConfidently()
            }
        }
    }

    // Legacy method for older UI components
    fun setSingConfidentlyEnabled(enabled: Boolean) {
        setSingConfidentlyMode(if (enabled) SingConfidentlyMode.AUTO else SingConfidentlyMode.OFF)
    }

    /**
     * Kicks off karaoke recording immediately (in parallel with resolving the
     * instrumental stream) so the mic is already listening by the time
     * playback actually starts. Combined with the pre-warm in
     * searchInstrumental(), this is now a true "instant start": the
     * recognizer object already exists, so this only needs to call
     * startListening() on it.
     */
    private fun startRecordingForKaraokeIfNeeded() {
        if (_uiState.value.karaokeSpeechCorrectionEnabled && !_uiState.value.isKaraokeRecording) {
            startKaraokeRecording()
        }
    }

    fun toggleSingConfidentlyActive(active: Boolean, onSync: (Long) -> Unit) {
        Log.d(TAG, "toggleSingConfidentlyActive: active=$active")
        if (active) {
            val match = _uiState.value.instrumentalMatch ?: run {
                Log.w(TAG, "No instrumental match found")
                return
            }

            // Start listening right away — don't wait for the stream to
            // resolve first. This is what makes recording feel instant.
            startRecordingForKaraokeIfNeeded()

            if (_uiState.value.instrumentalStreamUrl != null) {
                val streamUrl = _uiState.value.instrumentalStreamUrl!!
                if (_uiState.value.isSingConfidentlyActive) {
                    viewModelScope.launch(Dispatchers.Main.immediate) {
                        onSync(_playbackPositionMs.value)
                        instrumentalPlayer.seekTo(_playbackPositionMs.value)
                        instrumentalPlayer.play()
                        onSetMutedByAi?.invoke(true)
                        onPauseOriginal?.invoke(false)
                    }
                    Log.d(TAG, "Instrumental player resumed, original muted")
                    return
                }

                viewModelScope.launch(Dispatchers.Main.immediate) {
                    _uiState.update { it.copy(isSingConfidentlyActive = true, isResolvingInstrumental = false) }
                    
                    instrumentalPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                    instrumentalPlayer.prepare()

                    onSync(_playbackPositionMs.value)
                    instrumentalPlayer.seekTo(_playbackPositionMs.value)

                    onSetMutedByAi?.invoke(true)
                    onPauseOriginal?.invoke(false)
                    instrumentalPlayer.play()
                    Log.d(TAG, "Sing Confidently active: Instrumental playing (pre-resolved), original muted")
                }
                return
            }

            _uiState.update { it.copy(isResolvingInstrumental = true) }
            onPauseOriginal?.invoke(true)
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

                        onSetMutedByAi?.invoke(true)
                        onPauseOriginal?.invoke(false)
                        instrumentalPlayer.play()

                        Log.d(TAG, "Sing Confidently active: Instrumental playing, original muted")
                    } else {
                        Log.w(TAG, "Failed to resolve instrumental stream, falling back to original")
                        _uiState.update { it.copy(isSingConfidentlyActive = false, isResolvingInstrumental = false) }
                        onSetMutedByAi?.invoke(false)
                        onPauseOriginal?.invoke(false)
                        stopKaraokeRecording()
                    }
                }
            }
        } else {
            Log.d(TAG, "Pausing instrumental player, restoring original")
            val currentPos = _instrumentalPlayer?.currentPosition ?: _playbackPositionMs.value
            viewModelScope.launch(Dispatchers.Main.immediate) {
                if (_instrumentalPlayer != null) {
                    instrumentalPlayer.pause()
                }
                onSync(currentPos)
                onSetMutedByAi?.invoke(false)
                onPauseOriginal?.invoke(false)
            }
            _uiState.update { it.copy(isSingConfidentlyActive = false, isResolvingInstrumental = false) }
            stopKaraokeRecording()
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
        _uiState.update { it.copy(isSingConfidentlyActive = false, isInstrumentalPlaying = false, instrumentalStreamUrl = null) }
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (_instrumentalPlayer != null) {
                instrumentalPlayer.pause()
                instrumentalPlayer.stop()
                instrumentalPlayer.clearMediaItems()
            }
            onSetMutedByAi?.invoke(false)
        }
        stopKaraokeRecording()
    }

    fun pauseInstrumental() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            _instrumentalPlayer?.pause()
        }
    }

    private suspend fun <T> runGroqRequest(initialKey: String, requestBlock: suspend (String) -> T): T {
        return requestBlock(initialKey)
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
        if (!enabled) {
            stopKaraokeRecording()
        }
    }

    fun setQuickSingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKaraokeQuickSingEnabled(enabled) }
    }

    fun setAutoRecordEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKaraokeAutoRecordEnabled(enabled) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Speech recognition – hardened session management
    // ─────────────────────────────────────────────────────────────────────────
    //
    // Rewritten strategy (fixes both "speech correction doesn't work" and the
    // mic visibly flickering on/off):
    //
    //  1. RECOGNIZER TUNING: the old intent set
    //     EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS = 20_000L. That forced
    //     every single-utterance session to stay open for a *minimum* of 20
    //     real seconds, which most recognition services either silently sit
    //     on (no callbacks at all — looks "dead") or reject outright. Values
    //     are now in the 1-2s range, appropriate for short lyric phrases.
    //
    //  2. NO UNNECESSARY REBUILD/DESTROY: previously almost every restart
    //     path (including the extremely common ERROR_RECOGNIZER_BUSY, which
    //     mostly just means *our own* restart raced the service tearing down
    //     the previous session) counted toward a shared failure counter that,
    //     after 3 hits, destroyed and recreated the whole SpeechRecognizer.
    //     Recreating means unbinding from the recognition service and
    //     rebinding — which is the actual mic "turning off and on" the user
    //     sees/hears. Now:
    //       - onResults / NO_MATCH / SPEECH_TIMEOUT: cancel() + immediate
    //         restart on the SAME recognizer instance. No rebuild, no
    //         backoff, no UI change.
    //       - ERROR_RECOGNIZER_BUSY: same instance, short fixed retry, only
    //         escalates to a rebuild after MAX_BUSY_RETRIES in a row.
    //       - Real failures (network/server/too many requests/unknown):
    //         exponential backoff, rebuild only after REBUILD_AFTER_N_FAILURES.
    //
    //  3. UI "isListening" NO LONGER MIRRORS EVERY INTERNAL RESTART: it now
    //     reflects "karaoke recording is conceptually active", not "is a
    //     startListening() session open right this millisecond" (that
    //     internal detail lives in `sessionActive` instead). Since a fresh
    //     utterance session opens every time you finish speaking a phrase,
    //     mirroring that in the UI is exactly what produced the on/off
    //     flicker the user was seeing.
    //
    //  4. INSTANT START: searchInstrumental() pre-warms (builds, but does not
    //     start listening on) a SpeechRecognizer as soon as a match is found,
    //     well before the user taps play. startKaraokeRecording() then only
    //     has to call startListening() on an already-built recognizer.
    //
    //  5. Watchdog still guards against the recognizer going fully silent
    //     with no callbacks at all (a distinct failure mode from any of the
    //     above), forcing a rebuild only in that case.
    //
    // The recognizer runs on the MAIN thread (mandatory for SpeechRecognizer).

    fun startKaraokeRecording() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer not available on this device")
            return
        }
        if (_uiState.value.isKaraokeRecording) return

        // Count words once at session start. This becomes the stable denominator
        // for score computation throughout the session — we don't recount on
        // every update because mid-session counts include lines not yet reached,
        // which inflates/deflates the score unpredictably.
        val allLyrics = _uiState.value.lyricsState.syncedLyrics
        val totalWordsCount = allLyrics.sumOf { it.words.size }.coerceAtLeast(1)
        val totalLinesCount = allLyrics.count { it.words.isNotEmpty() }
        val newSessionId = _uiState.value.karaokeSessionId + 1

        consecutiveFailures = 0
        busyRetryCount = 0
        lastMissedCheckMs = 0L
        _uiState.update {
            it.copy(
                isKaraokeRecording      = true,
                isListening             = true,
                isReconnecting          = false,
                karaokeScore            = 0,
                karaokeCorrectWords     = 0,
                karaokeTotalWords       = totalWordsCount,
                karaokeCorrectLines     = 0,
                karaokeTotalLines       = totalLinesCount,
                karaokeMostAccurateLine = null,
                karaokeStreak           = 0,
                karaokeMaxStreak        = 0,
                karaokeMissedStreak     = 0,
                karaokeSessionId        = newSessionId,
                lyricsState             = it.lyricsState.copy(
                    syncedLyrics = it.lyricsState.syncedLyrics.map { line ->
                        line.copy(words = line.words.map { w -> w.copy(karaokeStatus = KaraokeWordStatus.PENDING) })
                    }
                )
            )
        }

        viewModelScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Starting Karaoke Recording: sessionId=$newSessionId totalWords=$totalWordsCount")
            // If searchInstrumental() already pre-warmed a recognizer for
            // this track, this is a no-op and startListening() fires
            // immediately below — this is what makes recording feel instant.
            if (speechRecognizer == null) {
                buildRecognizer()
            }
            beginListening()
            startWatchdog()
        }
    }

    fun stopKaraokeRecording() {
        if (!_uiState.value.isKaraokeRecording) return
        _uiState.update { it.copy(isKaraokeRecording = false, isListening = false, isReconnecting = false, micRms = 0f) }
        restartJob?.cancel()
        restartJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        sessionActive = false
        viewModelScope.launch(Dispatchers.Main) {
            destroyRecognizer()
        }
    }

    fun pauseKaraokeListening() {
        if (!_uiState.value.isKaraokeRecording) return
        _uiState.update { it.copy(isListening = false, isReconnecting = false, micRms = 0f) }
        restartJob?.cancel()
        watchdogJob?.cancel()
        sessionActive = false
        viewModelScope.launch(Dispatchers.Main) {
            destroyRecognizer()
        }
    }

    fun resumeKaraokeListening() {
        if (!_uiState.value.isKaraokeRecording || sessionActive) return
        _uiState.update { it.copy(isListening = true) }
        viewModelScope.launch(Dispatchers.Main) {
            if (speechRecognizer == null) buildRecognizer()
            beginListening()
            startWatchdog()
        }
    }

    /**
     * Builds (but does not start) a SpeechRecognizer ahead of time so the
     * actual startListening() call later is instant. Safe to call even if
     * karaoke recording never ends up starting for this track — the leaked
     * instance is always cleaned up via destroyRecognizer() in updateSong().
     * Must be called on the MAIN thread.
     */
    private fun prewarmRecognizer() {
        viewModelScope.launch(Dispatchers.Main) {
            if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
                buildRecognizer()
                Log.d(TAG, "Pre-warmed SpeechRecognizer ahead of Sing Confidently start")
            }
        }
    }

    /** Must be called on the MAIN thread. */
    private fun buildRecognizer() {
        // Always use the standard (cloud) SpeechRecognizer.
        // createOnDeviceSpeechRecognizer() causes ERROR_CLIENT (5) immediately on most devices
        // even when isOnDeviceRecognitionAvailable() returns true, because the on-device
        // model may not be downloaded or may be incompatible with the device's ROM.
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        Log.d(TAG, "SpeechRecognizer created (standard cloud recognizer)")

        recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // No EXTRA_AUDIO_SOURCE, no silence timeouts — let the recognizer use its own defaults.
            // These extras cause ERROR_CLIENT on many OEM ROMs (Samsung, Xiaomi, etc.).
        }
        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    /** Must be called on the MAIN thread. Safe to call regardless of current state. */
    private fun destroyRecognizer() {
        sessionActive = false
        val sr = speechRecognizer
        speechRecognizer = null
        recognitionIntent = null
        
        runCatching { sr?.stopListening() }
        runCatching { sr?.cancel() }
        runCatching { sr?.destroy() }
    }

    /**
     * Opens a new listening session on the existing recognizer instance.
     * Always cancel()s first — this is what actually prevents
     * ERROR_RECOGNIZER_BUSY: cancel() resets the recognizer's internal state
     * to idle synchronously instead of relying on a fixed delay and hoping
     * the previous session finished tearing down in time. On an idle
     * recognizer, cancel() is a harmless no-op.
     *
     * IMPORTANT: This is now a suspend function called from within a
     * coroutine. The previous implementation launched an *inner* coroutine
     * inside a runCatching block, which meant exceptions from startListening()
     * escaped the catch completely. Making it suspend means the entire call
     * site — delay + startListening — is covered by the caller's error handling.
     *
     * Must be called on the MAIN thread (via Dispatchers.Main coroutine).
     */
    private suspend fun beginListening() {
        val recognizer = speechRecognizer ?: run {
            Log.w(TAG, "beginListening: speechRecognizer is null")
            return
        }
        val intent = recognitionIntent ?: run {
            Log.w(TAG, "beginListening: recognitionIntent is null")
            return
        }
        if (!_uiState.value.isKaraokeRecording) return

        // CRITICAL: Only call stopListening/cancel if a session was previously
        // active. Calling stopListening() on a FRESH recognizer that has never
        // started listening fires onError(ERROR_CLIENT=5) on many devices,
        // which triggers rebuild loops and causes the mic to flicker.
        if (sessionActive) {
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.cancel() }
            sessionActive = false
            // Give the service time to release the previous session.
            // Increased to 500ms to prevent ERROR_RECOGNIZER_BUSY on slower devices
            delay(500L)
        }

        if (!_uiState.value.isKaraokeRecording || !_uiState.value.isListening) return

        Log.d(TAG, "beginListening: calling startListening")
        runCatching {
            recognizer.startListening(intent)
            sessionActive = true
            lastCallbackAtMs = System.currentTimeMillis()
            _uiState.update { it.copy(isReconnecting = false) }
        }.onFailure {
            Log.e(TAG, "beginListening: startListening threw", it)
            sessionActive = false
        }
    }

    /** Requeues a fresh listening session after `delayMs`. Used for every
     *  routine restart (successful result, NO_MATCH, SPEECH_TIMEOUT, a
     *  single BUSY retry) — none of these touch consecutiveFailures or flip
     *  isReconnecting, because they are expected, frequent events during
     *  normal singing, not failures.
     *
     *  beginListening() is now suspend, so it is called from within the
     *  launched coroutine rather than launching another inner one. */
    private fun requeueListening(delayMs: Long) {
        if (!_uiState.value.isKaraokeRecording) return
        restartJob?.cancel()
        restartJob = viewModelScope.launch(Dispatchers.Main) {
            if (delayMs > 0) delay(delayMs)
            if (!_uiState.value.isKaraokeRecording || !_uiState.value.isListening) return@launch
            beginListening()
        }
    }

    /** Full teardown + rebuild, used only for the watchdog and for repeated
     *  BUSY errors that didn't resolve with plain retries.
     *
     *  beginListening() is now suspend — called inline in the launched coroutine. */
    private fun rebuildAndRestart(preDelayMs: Long) {
        if (!_uiState.value.isKaraokeRecording) return
        restartJob?.cancel()
        restartJob = viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(isReconnecting = true) }
            destroyRecognizer()
            // Wait at least 500ms after destroying to let the speech service
            // fully release its resources. Shorter delays cause ERROR_CLIENT.
            delay(preDelayMs.coerceAtLeast(500L))
            if (!_uiState.value.isKaraokeRecording || !_uiState.value.isListening) return@launch
            buildRecognizer()
            beginListening()
        }
    }

    /** Schedules a recognizer restart with exponential backoff for GENUINE
     *  failures only (network/server/too-many-requests/unknown). Escalates
     *  to a full rebuild after too many consecutive failures.
     *
     *  beginListening() is now suspend — called inline in the launched coroutine. */
    private fun scheduleBackoffRestart(error: Int) {
        if (!_uiState.value.isKaraokeRecording) return

        consecutiveFailures++
        val baseDelayMs = ERROR_BASE_DELAY_MS[error] ?: ERROR_BASE_DELAY_MS[0]!!
        val backoffMultiplier = 1 shl (consecutiveFailures - 1).coerceIn(0, 4) // 1,2,4,8,16x
        val delayMs = min(baseDelayMs * backoffMultiplier, MAX_BACKOFF_MS)
        val forceRebuild = consecutiveFailures >= REBUILD_AFTER_N_FAILURES

        restartJob?.cancel()
        restartJob = viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(isReconnecting = true) }
            delay(delayMs)
            if (!_uiState.value.isKaraokeRecording || !_uiState.value.isListening) return@launch

            if (forceRebuild) {
                Log.d(TAG, "Rebuilding recognizer from scratch after $consecutiveFailures consecutive failures")
                destroyRecognizer()
                delay(150)
                buildRecognizer()
                // We keep the failure count intact so that if the rebuild still fails,
                // the backoff continues to grow instead of resetting.
            }
            Log.d(TAG, "Executing scheduled restart (delay=${delayMs}ms, rebuilt=$forceRebuild)")
            beginListening()
        }
    }

    /** Watches for the recognizer going silent (no callback at all) while
     *  recording is supposedly active, and forces a rebuild if so. */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch(Dispatchers.Main) {
            while (_uiState.value.isKaraokeRecording && _uiState.value.isListening) {
                delay(2_000L)
                val silentFor = System.currentTimeMillis() - lastCallbackAtMs
                if (_uiState.value.isKaraokeRecording && _uiState.value.isListening && silentFor > WATCHDOG_SILENCE_TIMEOUT_MS) {
                    Log.w(TAG, "Watchdog: recognizer silent for ${silentFor}ms, forcing rebuild")
                    sessionActive = false
                    rebuildAndRestart(preDelayMs = 100L)
                    lastCallbackAtMs = System.currentTimeMillis()
                }
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            lastCallbackAtMs = System.currentTimeMillis()
            consecutiveFailures = 0
            busyRetryCount = 0
            Log.v(TAG, "Recognizer ready")
        }
        override fun onBeginningOfSpeech() {
            lastCallbackAtMs = System.currentTimeMillis()
            Log.v(TAG, "Speech beginning")
        }
        override fun onEndOfSpeech() {
            lastCallbackAtMs = System.currentTimeMillis()
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            if (_uiState.value.isKaraokeRecording) {
                lastCallbackAtMs = System.currentTimeMillis()
                _uiState.update { it.copy(micRms = rmsdB) }
            }
        }

        override fun onError(error: Int) {
            sessionActive = false
            lastCallbackAtMs = System.currentTimeMillis()

            if (!_uiState.value.isKaraokeRecording) return

            if (error in FATAL_ERRORS) {
                Log.e(TAG, "Fatal SpeechRecognizer error: $error – stopping session")
                _uiState.update { it.copy(micRms = 0f) }
                stopKaraokeRecording()
                return
            }

            Log.w(TAG, "SpeechRecognizer onError: $error (consecutive=$consecutiveFailures, busy=$busyRetryCount)")

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal during karaoke — user was silent or paused.
                    // Just silently restart, no backoff, no UI change.
                    busyRetryCount = 0
                    consecutiveFailures = 0
                    requeueListening(ROUTINE_GAP_RESTART_DELAY_MS)
                }
                SpeechRecognizer.ERROR_AUDIO -> {
                    // Mic access conflict. Rebuild the recognizer to re-acquire the mic.
                    consecutiveFailures++
                    if (consecutiveFailures >= 5) {
                        // Circuit breaker: give up after 5 consecutive audio errors
                        Log.e(TAG, "Circuit breaker: 5 consecutive ERROR_AUDIO — stopping karaoke recognition")
                        runCatching {
                            android.widget.Toast.makeText(
                                context,
                                "Microphone unavailable for speech correction",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        stopKaraokeRecording()
                    } else {
                        rebuildAndRestart(preDelayMs = (500L * consecutiveFailures))
                    }
                }
                SpeechRecognizer.ERROR_CLIENT -> {
                    // ERROR_CLIENT (5): The speech recognition service disconnected.
                    // This happens when the recognizer service crashes or the session
                    // is initiated incorrectly. Always do a full destroy+rebuild.
                    consecutiveFailures++
                    if (consecutiveFailures >= 5) {
                        // Circuit breaker: give up after 5 consecutive client errors
                        Log.e(TAG, "Circuit breaker: 5 consecutive ERROR_CLIENT — stopping karaoke recognition")
                        runCatching {
                            android.widget.Toast.makeText(
                                context,
                                "Speech service unavailable. Try restarting the app.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                        stopKaraokeRecording()
                    } else {
                        val backoffMs = (1000L * consecutiveFailures).coerceAtMost(5000L)
                        Log.w(TAG, "ERROR_CLIENT: rebuild attempt $consecutiveFailures, delay=${backoffMs}ms")
                        rebuildAndRestart(preDelayMs = backoffMs)
                    }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    busyRetryCount++
                    if (busyRetryCount >= MAX_BUSY_RETRIES) {
                        Log.w(TAG, "Recognizer stayed busy after $busyRetryCount retries – rebuilding")
                        busyRetryCount = 0
                        rebuildAndRestart(preDelayMs = 300L)
                    } else {
                        requeueListening(BUSY_RETRY_DELAY_MS)
                    }
                }
                else -> {
                    busyRetryCount = 0
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
                        else -> "Unknown error $error"
                    }
                    Log.d(TAG, "SpeechRecognizer error: $errorMsg – scheduling backoff restart")
                    scheduleBackoffRestart(error)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            sessionActive = false
            lastCallbackAtMs = System.currentTimeMillis()
            // Successful result: fully reset all failure counters
            consecutiveFailures = 0
            busyRetryCount = 0

            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

            if (!texts.isNullOrEmpty()) {
                Log.d(TAG, "Speech Results: $texts")
                // When the recognizer reports confidence scores, drop
                // alternatives it isn't confident about — this cuts down on
                // false-positive word matches caused by the instrumental
                // track bleeding into the mic.
                val filtered = if (confidences != null && confidences.size == texts.size) {
                    texts.filterIndexed { i, _ -> confidences[i] >= MIN_RESULT_CONFIDENCE }
                        .ifEmpty { texts }
                } else texts
                processRecognizedTexts(filtered, isPartial = false)
            }

            // Quick, non-backoff requeue — this is the expected end of every
            // phrase, not a failure. The UI mic indicator is not touched.
            requeueListening(QUICK_RESTART_DELAY_MS)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            lastCallbackAtMs = System.currentTimeMillis()
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!texts.isNullOrEmpty()) {
                processRecognizedTexts(texts, isPartial = true)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phonetic-key word scoring
    // ─────────────────────────────────────────────────────────────────────────

    private fun rebuildPhoneticCache(lyrics: List<LyricsLine>) {
        targetPhoneticCache.clear()
        lyrics.forEach { line ->
            line.words.forEach { word ->
                val clean = normalizeAgnostic(word.word)
                if (clean.isNotBlank() && clean !in targetPhoneticCache) {
                    targetPhoneticCache[clean] = phoneticKey(clean)
                }
            }
        }
    }

    private fun phoneticKeyFor(normalizedWord: String): String =
        targetPhoneticCache.getOrPut(normalizedWord) { phoneticKey(normalizedWord) }

    private fun phoneticKey(text: String): String {
        val vowelSkeleton = getVowelSkeleton(text)
        val consonants = text.filter { it !in "aeiouy" }
        val consonantSig = when {
            consonants.isEmpty() -> ""
            consonants.length <= 3 -> consonants
            else -> "${consonants.take(2)}${consonants.last()}"
        }
        return "$vowelSkeleton|$consonantSig"
    }

    /**
     * Detects CORRECT matches only. Missed-word detection is handled
     * exclusively by checkMissedWords (driven by playback progress) so there
     * is a single, consistent grace period instead of two disagreeing ones.
     */
    private fun processRecognizedTexts(texts: List<String>, isPartial: Boolean = false) {
        val sessionId = _uiState.value.karaokeSessionId

        viewModelScope.launch(Dispatchers.Default) {
            val currentTime = _playbackPositionMs.value

            val newTokens = texts
                .asSequence()
                .flatMap { it.split(Regex("\\s+")) }
                .map { normalizeAgnostic(it) }
                .filter { it.isNotBlank() }
                .map { TokenEvent(it, phoneticKey(it), currentTime) }
                .toList()

            synchronized(recognitionBuffer) {
                if (!isPartial) {
                    // Deduplicate within RECOGNITION_DEDUP_WINDOW_MS (3s).
                    // We intentionally do NOT deduplicate across the whole buffer —
                    // repeated words in a lyric line ("na na na") must each score.
                    val recentTokens = recognitionBuffer
                        .filter { currentTime - it.timestamp < RECOGNITION_DEDUP_WINDOW_MS }
                        .map { it.token }.toSet()
                    val filteredNew = newTokens.filter { it.token !in recentTokens }
                    recognitionBuffer.addAll(filteredNew)
                }
                // Keep a reasonable rolling window of recognized tokens.
                recognitionBuffer.removeAll { currentTime - it.timestamp > 12_000L }
            }

            // For partial results, combine buffer with newTokens but don't persist yet.
            val effectiveTokens = if (isPartial) {
                synchronized(recognitionBuffer) { (recognitionBuffer + newTokens).distinctBy { it.token } }
            } else {
                synchronized(recognitionBuffer) { recognitionBuffer.toList().distinctBy { it.token } }
            }

            if (effectiveTokens.isEmpty()) return@launch

            val bufferedByKey: Map<String, List<TokenEvent>> = effectiveTokens.groupBy { it.phoneticKey }
            val bufferedTextSet = effectiveTokens.map { it.token }.toSet()

            // Serialize with lyricsMutex so this never races with checkMissedWords
            lyricsMutex.withLock {
                // Guard: bail if the session changed while waiting for the lock
                if (_uiState.value.karaokeSessionId != sessionId) return@withLock

                _uiState.update { state ->
                    if (state.karaokeSessionId != sessionId) return@update state

                    val currentIndex = state.lyricsState.syncedLyrics
                        .indexOfLast { it.timeMs <= currentTime }
                        .coerceAtLeast(0)

                    var newWordsCounted = 0
                    var partialMatchFound = false
                    var anyChange = false

                    val updatedLyrics = state.lyricsState.syncedLyrics.mapIndexed { index, line ->
                        // Only score current line (allow 1 line behind for recognition latency)
                        if (index < currentIndex - 1 || index > currentIndex) return@mapIndexed line

                        val updatedWords = line.words.map { word ->
                            val cleanTarget = normalizeAgnostic(word.word)
                            if (cleanTarget.isBlank()) return@map word
                            if (word.karaokeStatus != KaraokeWordStatus.PENDING) return@map word

                            // Wide backward window: recognition latency on real devices
                            // can be several seconds. Wide forward window: partial results
                            // arrive before the word is expected.
                            val windowStart = word.startTimeMs - 4_000L
                            val windowEnd   = word.startTimeMs + word.durationMs + MISSED_WORD_GRACE_MS

                            if (currentTime in windowStart..windowEnd) {
                                val matched = isWordMatchFast(cleanTarget, bufferedTextSet, bufferedByKey)
                                if (matched) {
                                    if (!isPartial) newWordsCounted++
                                    else partialMatchFound = true
                                    anyChange = true
                                    word.copy(karaokeStatus = KaraokeWordStatus.CORRECT)
                                } else word
                            } else word
                        }

                        if (updatedWords === line.words) return@mapIndexed line

                        // "Snap" partial lines: if enough significant words are correct,
                        // mark remaining pending ones correct too (generous marking for
                        // lines where the singer clearly sang most of it).
                        val significantWords    = updatedWords.filter { it.word.lowercase() !in STOP_WORDS }
                        val correctSignificant  = significantWords.count { it.karaokeStatus == KaraokeWordStatus.CORRECT }

                        val threshold = when {
                            significantWords.size <= 2 -> 0.6f
                            significantWords.size >= 5 -> 0.55f
                            else                       -> 0.65f
                        }

                        if (significantWords.isNotEmpty() &&
                            (correctSignificant.toFloat() / significantWords.size >= threshold)
                        ) {
                            line.copy(words = updatedWords.map {
                                if (it.karaokeStatus == KaraokeWordStatus.PENDING)
                                    it.copy(karaokeStatus = KaraokeWordStatus.CORRECT)
                                else it
                            })
                        } else {
                            line.copy(words = updatedWords)
                        }
                    }

                    if (!anyChange) return@update state

                    // Use stable totalWords from session start as denominator.
                    val totalWords   = state.karaokeTotalWords.coerceAtLeast(1)
                    val correctWords = updatedLyrics.sumOf { l -> l.words.count { it.karaokeStatus == KaraokeWordStatus.CORRECT } }
                    val score        = (correctWords * 100 / totalWords).coerceIn(0, 100)

                    // "Most accurate line" = line with highest correct-word ratio
                    // among lines where ≥60% of words have been evaluated.
                    val bestLine = updatedLyrics
                        .filter { l ->
                            l.words.isNotEmpty() &&
                            l.words.count { it.karaokeStatus != KaraokeWordStatus.PENDING }.toFloat() / l.words.size >= 0.6f
                        }
                        .maxByOrNull { l ->
                            l.words.count { it.karaokeStatus == KaraokeWordStatus.CORRECT }.toFloat() / l.words.size
                        }?.content

                    val nextMissedStreak = if (newWordsCounted > 0 || (isPartial && partialMatchFound)) 0 else state.karaokeMissedStreak
                    val currentStreak    = if (newWordsCounted > 0) state.karaokeStreak + newWordsCounted else state.karaokeStreak

                    // Lines metric: a line counts as correct if ≥60% of its
                    // significant words are CORRECT.
                    val linesWithWords = updatedLyrics.filter { it.words.isNotEmpty() }
                    val correctLines = linesWithWords.count { l ->
                        val sig = l.words.filter { it.word.lowercase() !in STOP_WORDS }
                        sig.isNotEmpty() && sig.count { it.karaokeStatus == KaraokeWordStatus.CORRECT }.toFloat() / sig.size >= 0.6f
                    }

                    state.copy(
                        lyricsState             = state.lyricsState.copy(syncedLyrics = updatedLyrics),
                        karaokeScore            = score,
                        karaokeCorrectWords     = correctWords,
                        karaokeMostAccurateLine = bestLine,
                        karaokeStreak           = currentStreak,
                        karaokeMaxStreak        = max(state.karaokeMaxStreak, currentStreak),
                        karaokeMissedStreak     = nextMissedStreak,
                        karaokeCorrectLines     = correctLines
                    )
                }
            }
        }
    }

    private fun isWordMatchFast(
        target: String,
        bufferedTextSet: Set<String>,
        bufferedByKey: Map<String, List<TokenEvent>>
    ): Boolean {
        if (target in bufferedTextSet) return true

        val targetKey = phoneticKeyFor(target)
        if (bufferedByKey.containsKey(targetKey)) return true

        val candidates = bufferedByKey.values.asSequence()
            .flatten()
            .filter { abs(it.token.length - target.length) <= 2 }
            .take(8)

        for (candidate in candidates) {
            if (jaroWinklerSimilarity(candidate.token, target) >= 0.85) return true
        }

        val targetSkeleton = getVowelSkeleton(target)
        if (targetSkeleton.length >= 2) {
            for (candidate in candidates) {
                if (getVowelSkeleton(candidate.token) == targetSkeleton &&
                    jaroWinklerSimilarity(candidate.token, target) >= 0.7
                ) return true
            }
        }

        return false
    }

    private fun normalizeAgnostic(text: String): String {
        val normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{M}"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }

    private fun getVowelSkeleton(text: String): String {
        val vowels = "aeiouy"
        return text.lowercase()
            .filter { it in vowels }
            .replace(Regex("([aeiouy])\\1+"), "$1")
    }

    private fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val matchDistance = (max(len1, len2) / 2) - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)
        var matches = 0

        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)
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

        val p = 0.1
        var l = 0
        while (l < min(4, min(len1, len2)) && s1[l] == s2[l]) {
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
        watchdogJob?.cancel()
        lyricsJob?.cancel()
        moreInfoJob?.cancel()
        recommendationsJob?.cancel()
        prefetchJob?.cancel()
        instrumentalSearchJob?.cancel()
        progressPollJob?.cancel()
        val sr = speechRecognizer
        speechRecognizer = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { sr?.stopListening() }
            runCatching { sr?.cancel() }
            runCatching { sr?.destroy() }
            runCatching { _instrumentalPlayer?.release() }
        }
    }
}