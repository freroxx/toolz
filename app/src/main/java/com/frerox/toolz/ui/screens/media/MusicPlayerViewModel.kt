package com.frerox.toolz.ui.screens.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.lifecycle.asFlow
import androidx.work.WorkManager
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.data.music.MusicRepository
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.Playlist
import com.frerox.toolz.data.settings.SettingsRepository
import com.frerox.toolz.service.MusicPlayerService
import com.frerox.toolz.util.OfflineManager
import com.frerox.toolz.util.OfflineState
import com.frerox.toolz.util.VibrationManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SortOrder {
    TITLE, ARTIST, RECENT
}

data class MusicUiState(
    val tracks: List<MusicTrack> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val favoriteTracks: List<MusicTrack> = emptyList(),
    val recentlyPlayed: List<MusicTrack> = emptyList(),
    val mostPlayed: List<MusicTrack> = emptyList(),
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val isShuffleOn: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sortOrder: SortOrder = SortOrder.RECENT,
    val isLoading: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val folders: Map<String, List<MusicTrack>> = emptyMap(),
    val selectedTracks: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val showVisualizer: Boolean = false,
    val artShape: String = "CIRCLE",
    val rotationEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val hapticIntensity: Float = 0.5f,
    val pipEnabled: Boolean = false,
    val sleepTimerActive: Boolean = false,
    val sleepTimerRemaining: Long? = null,
    val queue: List<QueueEntry> = emptyList(),
    val performanceMode: Boolean = false,
    val playbackPosition: Long = 0L,
    val duration: Long = 0L,
    val isOnline: Boolean = false,
    val isResolvingCatalog: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val equalizerPreset: String = "Normal",
    val equalizerPresets: List<String> = listOf("Normal", "Pop", "Rock", "Jazz", "Classical", "Dance", "Heavy Metal", "Hip Hop", "Flat", "Custom"),
    val customEqualizerGains: List<Float> = List(5) { 0f },
    val visualizerSensitivity: Float = 1.0f,
    val showMusicSettings: Boolean = false,
    val fastSeeking: Boolean = true,
    val alwaysSync: Boolean = true,
    val catalogResults: List<CatalogTrack> = emptyList()
)

data class QueueEntry(
    val id: String,
    val track: MusicTrack
)

@UnstableApi
@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    val repository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    private val offlineManager: OfflineManager,
    val vibrationManager: VibrationManager,
    val player: ExoPlayer,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState = _uiState.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _sliderPosition = MutableStateFlow<Long?>(null)
    val sliderPosition = _sliderPosition.asStateFlow()

    private val _showSleepTimer = MutableStateFlow(false)
    val showSleepTimer = _showSleepTimer.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: (() -> Unit)? = null
    
    private var equalizer: Equalizer? = null
    private var visualizer: Visualizer? = null
    private var shakeDetector: ShakeDetector? = null
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _visualizerData = MutableStateFlow(FloatArray(0))
    val visualizerData = _visualizerData.asStateFlow()

    private val _equalizerPresets = MutableStateFlow<List<String>>(emptyList())
    val equalizerPresets = _equalizerPresets.asStateFlow()

    private var currentQueueUris: List<String> = emptyList()

    @UnstableApi
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressUpdate()
                startPlayerService()
                startVisualizer()
                initEqualizer()
                vibrationManager.vibrateClick()
            } else {
                stopProgressUpdate()
                stopVisualizer()
                vibrationManager.vibrateClick()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uri = mediaItem?.mediaId ?: mediaItem?.requestMetadata?.mediaUri?.toString()
            val metadata = mediaItem?.mediaMetadata
            val sourceUrl = metadata?.extras?.getString("source_url")
            
            viewModelScope.launch {
                var track = repository.getTrackByUri(uri ?: "")
                
                if (track == null && sourceUrl != null) {
                    track = repository.getTrackBySourceUrl(sourceUrl)
                }

                // Handle external tracks not in the main library
                if (track == null && mediaItem != null) {
                    track = MusicTrack(
                        uri = uri ?: "",
                        title = metadata?.title?.toString() ?: "External Audio",
                        artist = metadata?.artist?.toString() ?: "Unknown",
                        album = metadata?.albumTitle?.toString() ?: "Unknown",
                        duration = player.duration.coerceAtLeast(0L),
                        thumbnailUri = metadata?.artworkUri?.toString() ?: metadata?.albumTitle?.let { "" }, // Fallback check
                        sourceUrl = sourceUrl,
                        lastPlayed = System.currentTimeMillis()
                    )
                    
                    // Persist the external track to database
                    repository.insertTrack(track)
                } else if (track != null) {
                    // Update lastPlayed for recently played tracking
                    val updatedTrack = track.copy(
                        lastPlayed = System.currentTimeMillis(),
                        duration = if (player.duration > 0 && track.duration <= 0) player.duration else track.duration
                    )
                    repository.updateTrack(updatedTrack)
                    track = updatedTrack
                }

                val dur = player.duration.coerceAtLeast(0L)
                _uiState.update { it.copy(currentTrack = track, duration = dur) }
                _duration.value = dur
                
                if (track != null) {
                    repository.incrementPlayCount(track)
                }
                
                updateQueue()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val dur = player.duration.coerceAtLeast(0L)
                _uiState.update { it.copy(duration = dur) }
                _duration.value = dur
                
                // Also update database if duration was previously unknown
                val currentTrack = _uiState.value.currentTrack
                if (currentTrack != null && dur > 0 && currentTrack.duration <= 0) {
                    val updatedTrack = currentTrack.copy(duration = dur)
                    viewModelScope.launch {
                        repository.updateTrack(updatedTrack)
                    }
                    _uiState.update { it.copy(currentTrack = updatedTrack) }
                }
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
            vibrationManager.vibrateClick()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(isShuffleOn = shuffleModeEnabled) }
            vibrationManager.vibrateClick()
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            super.onPlayerError(error)
            player.prepare()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            updateQueue()
        }
    }

    private fun updateQueue() {
        viewModelScope.launch(Dispatchers.Main) {
            val p: Player = controller ?: player
            val currentTracks = _uiState.value.tracks
            
            val trackMap = currentTracks.associateBy { it.uri }
            val queueEntries = mutableListOf<QueueEntry>()
            val uris = mutableListOf<String>()
            
            val timeline = p.currentTimeline
            val window = androidx.media3.common.Timeline.Window()
            
            for (i in 0 until p.mediaItemCount) {
                val mediaItem = p.getMediaItemAt(i)
                uris.add(mediaItem.mediaId)
                
                // Get a stable UID from the timeline window if possible
                val stableId = if (!timeline.isEmpty && i < timeline.windowCount) {
                    timeline.getWindow(i, window).uid.toString()
                } else {
                    "${mediaItem.mediaId}_$i"
                }

                val track = trackMap[mediaItem.mediaId] ?: run {
                    val meta = mediaItem.mediaMetadata
                    MusicTrack(
                        uri = mediaItem.mediaId,
                        title = meta.title?.toString() ?: "External Audio",
                        artist = meta.artist?.toString() ?: "Unknown",
                        album = meta.albumTitle?.toString() ?: "Unknown",
                        duration = 0L
                    )
                }
                queueEntries.add(QueueEntry(id = stableId, track = track))
            }
            
            currentQueueUris = uris
            _uiState.update { it.copy(queue = queueEntries) }
        }
    }

    private data class MusicData(
        val tracks: List<MusicTrack>,
        val playlists: List<Playlist>,
        val favorites: List<MusicTrack>,
        val recent: List<MusicTrack>,
        val most: List<MusicTrack>
    )

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        
        player.setAudioAttributes(audioAttributes, true)
        player.setHandleAudioBecomingNoisy(true)
        player.addListener(playerListener)

        connectToMediaController()

        viewModelScope.launch {
            settingsRepository.showMusicVisualizer.collect { show ->
                _uiState.update { it.copy(showVisualizer = show) }
                if (show && player.isPlaying) {
                    startVisualizer()
                } else if (!show) {
                    stopVisualizer()
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.performanceMode.collect { perf ->
                _uiState.update { it.copy(performanceMode = perf) }
            }
        }

        viewModelScope.launch {
            settingsRepository.musicArtShape.collect { shape ->
                _uiState.update { it.copy(artShape = shape) }
            }
        }

        viewModelScope.launch {
            settingsRepository.musicRotationEnabled.collect { enabled ->
                _uiState.update { it.copy(rotationEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            settingsRepository.musicPlaybackSpeed.collect { speed ->
                player.playbackParameters = PlaybackParameters(speed)
                _uiState.update { it.copy(playbackSpeed = speed) }
            }
        }

        viewModelScope.launch {
            settingsRepository.musicShakeToSkip.collect { enabled ->
                if (enabled) {
                    startShakeDetection()
                } else {
                    stopShakeDetection()
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.musicEqualizerPreset.collect { preset ->
                _uiState.update { it.copy(equalizerPreset = preset) }
                applyEqualizerPreset(preset)
            }
        }

        viewModelScope.launch {
            settingsRepository.musicCustomEqualizer.collect { data ->
                if (data.isNotBlank()) {
                    val gains = data.split(",").mapNotNull { it.toFloatOrNull() }
                    if (gains.size >= 5) {
                        _uiState.update { it.copy(customEqualizerGains = gains) }
                        if (_uiState.value.equalizerPreset == "Custom") {
                            applyCustomEqualizer(gains)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.musicVisualizerSensitivity.collect { sens ->
                _uiState.update { it.copy(visualizerSensitivity = sens) }
            }
        }

        viewModelScope.launch {
            settingsRepository.hapticFeedback.collect { enabled ->
                _uiState.update { it.copy(hapticEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            settingsRepository.hapticIntensity.collect { intensity ->
                _uiState.update { it.copy(hapticIntensity = intensity) }
            }
        }
        
        viewModelScope.launch {
            settingsRepository.musicPipEnabled.collect { enabled ->
                _uiState.update { it.copy(pipEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            offlineManager.offlineState.collect { state ->
                _uiState.update { it.copy(isOnline = state == OfflineState.ONLINE) }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            val combinedFlow = combine(
                repository.allTracks,
                repository.allPlaylists,
                repository.favoriteTracks,
                repository.recentlyPlayed,
                repository.mostPlayed
            ) { tracks, playlists, favorites, recent, most ->
                MusicData(tracks, playlists, favorites, recent, most)
            }

            combinedFlow.combine(
                combine(
                    _uiState.map { it.sortOrder }.distinctUntilChanged(),
                    _uiState.map { it.isOnline }.distinctUntilChanged()
                ) { sortOrder, isOnline -> sortOrder to isOnline }
            ) { data, (sortOrder, isOnline) ->
                // Deduplicate tracks: prioritize offline (those with a path)
                val deduplicatedTracks = data.tracks
                    .groupBy { "${it.title}|${it.artist}" }
                    .map { (_, tracks) ->
                        tracks.find { it.path != null } ?: tracks.first()
                    }

                // Filter online tracks if offline
                val visibleTracks = if (!isOnline) {
                    deduplicatedTracks.filter { it.path != null }
                } else {
                    deduplicatedTracks
                }

                val sortedBase = when (sortOrder) {
                    SortOrder.TITLE -> visibleTracks.sortedBy { it.title }
                    SortOrder.ARTIST -> visibleTracks.sortedBy { it.artist ?: "Unknown" }
                    SortOrder.RECENT -> visibleTracks.reversed()
                }

                val prioritizedTracks = sortedBase.sortedWith(
                    compareByDescending<MusicTrack> { it.thumbnailUri != null }
                        .thenByDescending { it.artist != null && it.artist != "Unknown Artist" && it.artist != "<unknown>" }
                )
                
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val toolzDirPath = File(downloadsDir, "Toolz").absolutePath

                val folders = prioritizedTracks.groupBy { track ->
                    if (track.album == "Toolz Downloads" || (track.path != null && track.path.startsWith(toolzDirPath))) {
                        "Toolz Downloads"
                    } else {
                        val pathStr = track.path ?: track.uri.toUri().path ?: ""
                        if (pathStr.contains("/")) {
                            val folder = pathStr.substringBeforeLast("/").substringAfterLast("/")
                            folder.ifEmpty { "Internal Storage" }
                        } else {
                            "Internal Storage"
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            tracks = prioritizedTracks, 
                            playlists = data.playlists.sortedByDescending { it.createdAt }, 
                            favoriteTracks = data.favorites,
                            recentlyPlayed = data.recent,
                            mostPlayed = data.most,
                            folders = folders,
                            currentTrack = prioritizedTracks.find { t -> t.uri == (player.currentMediaItem?.mediaId ?: state.currentTrack?.uri) } ?: state.currentTrack,
                            isPlaying = player.isPlaying,
                            isShuffleOn = player.shuffleModeEnabled,
                            repeatMode = player.repeatMode,
                            duration = player.duration.coerceAtLeast(0L)
                        ) 
                    }
                    _duration.value = player.duration.coerceAtLeast(0L)
                }
            }.collect()
        }
        
        if (player.isPlaying) {
            startProgressUpdate()
        }
    }

    @UnstableApi
    private fun connectToMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(playerListener)
                val dur = controller?.duration?.coerceAtLeast(0L) ?: 0L
                _uiState.update { it.copy(
                    isPlaying = controller?.isPlaying ?: false,
                    isShuffleOn = controller?.shuffleModeEnabled ?: false,
                    repeatMode = controller?.repeatMode ?: Player.REPEAT_MODE_OFF,
                    currentTrack = _uiState.value.tracks.find { t -> t.uri == controller?.currentMediaItem?.mediaId } ?: it.currentTrack,
                    duration = dur
                )}
                _duration.value = dur
                updateQueue()
                pendingAction?.invoke()
                pendingAction = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startShakeDetection() {
        if (shakeDetector == null) {
            shakeDetector = ShakeDetector {
                if (player.isPlaying) {
                    skipNext()
                }
            }
        }
        sensorManager.registerListener(
            shakeDetector,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI
        )
    }

    private fun stopShakeDetection() {
        shakeDetector?.let { sensorManager.unregisterListener(it) }
    }

    @UnstableApi
    private fun initEqualizer() {
        if (equalizer == null && player.audioSessionId != 0) {
            try {
                equalizer = Equalizer(0, player.audioSessionId).apply {
                    enabled = true
                }
                
                val presets = mutableListOf<String>()
                // Add standard Android presets
                for (i in 0 until (equalizer?.numberOfPresets?.toInt() ?: 0)) {
                    presets.add(equalizer?.getPresetName(i.toShort()) ?: "")
                }
                
                // Add our custom enhanced presets if not present
                val customPresets = listOf("Bass Boost", "Vocal Booster", "Treble Booster", "Electronic", "Classical", "Pop", "Rock")
                customPresets.forEach { if (!presets.contains(it)) presets.add(it) }
                
                _equalizerPresets.value = presets.distinct()

                viewModelScope.launch {
                    applyEqualizerPreset(settingsRepository.musicEqualizerPreset.first())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startVisualizer() {
        if (visualizer != null || !_uiState.value.showVisualizer) return
        val sessionId = player.audioSessionId
        if (sessionId == 0) return
        
        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        fft?.let {
                            val n = it.size / 2
                            val magnitudes = FloatArray(64)
                            
                            // Group FFT bins into 64 bars with logarithmic spacing for better visual representation
                            // Low frequencies (bass) are usually more interesting to watch
                            for (i in 0 until 64) {
                                // Simple logarithmic mapping from 64 bars to FFT bins
                                val startBin = (Math.pow(2.0, i / 10.6) - 1).toInt().coerceIn(0, n - 1)
                                val endBin = (Math.pow(2.0, (i + 1) / 10.6) - 1).toInt().coerceIn(startBin + 1, n)
                                
                                var sum = 0f
                                for (j in startBin until endBin) {
                                    val r = it[j * 2].toInt()
                                    val im = it[j * 2 + 1].toInt()
                                    val magnitude = Math.hypot(r.toDouble(), im.toDouble()).toFloat()
                                    sum += magnitude
                                }
                                
                                val avg = if (endBin > startBin) sum / (endBin - startBin) else 0f
                                // Apply sensitivity boost and scaling
                                magnitudes[i] = (avg * (1f + i * 0.05f) * 2.5f).coerceIn(0f, 100f)
                            }
                            _visualizerData.value = magnitudes
                        }
                    }
                }, Visualizer.getMaxCaptureRate(), false, true)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {}
        visualizer = null
        _visualizerData.value = FloatArray(0)
    }

    // Duplicate removed: setEqualizerPreset(preset: String)


    fun setCustomEqualizerGain(band: Int, gain: Float) {
        val currentGains = _uiState.value.customEqualizerGains.toMutableList()
        if (band in currentGains.indices) {
            currentGains[band] = gain
            _uiState.update { it.copy(customEqualizerGains = currentGains) }
            viewModelScope.launch {
                settingsRepository.setMusicCustomEqualizer(currentGains.joinToString(","))
            }
            if (_uiState.value.equalizerPreset == "Custom") {
                applyCustomEqualizer(currentGains)
            }
        }
    }

    private fun applyCustomEqualizer(gains: List<Float>) {
        val eq = equalizer ?: return
        try {
            gains.forEachIndexed { index, gain ->
                if (index < eq.numberOfBands) {
                    val level = (gain * 1500).toInt().coerceIn(-1500, 1500)
                    eq.setBandLevel(index.toShort(), level.toShort())
                }
            }
        } catch (e: Exception) {}
    }

    fun setVisualizerSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            settingsRepository.setMusicVisualizerSensitivity(sensitivity)
            _uiState.update { it.copy(visualizerSensitivity = sensitivity) }
        }
    }

    private fun applyEqualizerPreset(preset: String) {
        if (preset == "Custom") {
            applyCustomEqualizer(_uiState.value.customEqualizerGains)
            return
        }
        val eq = equalizer ?: return
        try {
            val numPresets = eq.numberOfPresets
            if (numPresets > 0) {
                for (i in 0 until numPresets.toInt()) {
                    try {
                        val presetName = eq.getPresetName(i.toShort())
                        if (presetName.equals(preset, ignoreCase = true)) {
                            eq.usePreset(i.toShort())
                            return
                        }
                    } catch (e: Exception) {
                    }
                }
            }
            
            // Custom Equalizer logic for non-system presets
            val numBands = eq.numberOfBands.toInt()
            when (preset) {
                "Bass Boost" -> {
                    for (i in 0 until numBands) {
                        val freq = eq.getCenterFreq(i.toShort())
                        if (freq < 500000) eq.setBandLevel(i.toShort(), 1000.toShort())
                        else eq.setBandLevel(i.toShort(), 0.toShort())
                    }
                }
                "Vocal Booster" -> {
                    for (i in 0 until numBands) {
                        val freq = eq.getCenterFreq(i.toShort())
                        if (freq in 500000..3000000) eq.setBandLevel(i.toShort(), 800.toShort())
                        else eq.setBandLevel(i.toShort(), (-200).toShort())
                    }
                }
                "Treble Booster" -> {
                    for (i in 0 until numBands) {
                        val freq = eq.getCenterFreq(i.toShort())
                        if (freq > 3000000) eq.setBandLevel(i.toShort(), 1000.toShort())
                        else eq.setBandLevel(i.toShort(), 0.toShort())
                    }
                }
                "Electronic" -> {
                    for (i in 0 until numBands) {
                        val freq = eq.getCenterFreq(i.toShort())
                        when {
                            freq < 250000 -> eq.setBandLevel(i.toShort(), 800.toShort())
                            freq in 250000..1000000 -> eq.setBandLevel(i.toShort(), 0.toShort())
                            freq > 4000000 -> eq.setBandLevel(i.toShort(), 600.toShort())
                            else -> eq.setBandLevel(i.toShort(), 200.toShort())
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    @UnstableApi
    private fun startPlayerService() {
        val intent = Intent(context, MusicPlayerService::class.java)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                if (_sliderPosition.value == null) {
                    val p: Player = controller ?: player
                    val currentPos = p.currentPosition.coerceAtLeast(0)
                    _uiState.update { it.copy(playbackPosition = currentPos) }
                    _playbackPosition.value = currentPos
                }
                val isSyncedLyricsVisible = _uiState.value.currentTrack?.aiLyrics?.contains("[0") == true
                val interval = when {
                    _uiState.value.performanceMode -> 500L
                    _uiState.value.fastSeeking || isSyncedLyricsVisible -> 16L
                    else -> 100L
                }
                delay(interval)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        _visualizerData.value = FloatArray(0)
    }

    fun onSliderChange(position: Long) {
        _sliderPosition.value = position
    }

    fun onSliderChangeFinished() {
        _sliderPosition.value?.let { pos ->
            val p: Player = controller ?: player
            p.seekTo(pos)
            _uiState.update { it.copy(playbackPosition = pos) }
            _playbackPosition.value = pos
            vibrationManager.vibrateClick()
        }
        _sliderPosition.value = null
    }

    fun scanMusic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.scanDeviceForMusic()
            _uiState.update { it.copy(isLoading = false) }
            vibrationManager.vibrateSuccess()
        }
    }

    fun addCustomFolder(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.scanCustomFolder(uri)
            _uiState.update { it.copy(isLoading = false) }
            vibrationManager.vibrateSuccess()
        }
    }

    fun toggleTrackSelection(uri: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedTracks.contains(uri)) {
                state.selectedTracks - uri
            } else {
                state.selectedTracks + uri
            }
            state.copy(
                selectedTracks = newSelection,
                isSelectionMode = newSelection.isNotEmpty()
            )
        }
        vibrationManager.vibrateClick()
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedTracks = emptySet(), isSelectionMode = false) }
        vibrationManager.vibrateClick()
    }

    fun playTrack(track: MusicTrack, tracks: List<MusicTrack> = _uiState.value.tracks) {
        startPlayerService()
        
        // Auto-load lyrics when song is played
        viewModelScope.launch {
            // We need a reference to the NowPlayingAiViewModel or a way to trigger it.
            // Since we're in MusicPlayerViewModel, we might not have direct access.
            // But we can check if lyrics already exist in the track.
            if (track.aiLyrics.isNullOrBlank()) {
                // If the track is from the repository, we could fetch them here or 
                // trust that the UI will trigger it.
            }
        }

        if (controller == null) {
            pendingAction = { playTrack(track, tracks) }
            connectToMediaController()
            return
        }

        vibrationManager.vibrateClick()
        
        viewModelScope.launch(Dispatchers.Default) {
            val trackUris = tracks.map { it.uri }
            val isSameQueue = trackUris == currentQueueUris
            
            val mediaItems = tracks.map { t -> t.toMediaItem() }
            val startIndex = tracks.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
            
            withContext(Dispatchers.Main) {
                val p: Player = controller ?: player
                if (isSameQueue) {
                    val index = trackUris.indexOf(track.uri)
                    if (index != -1) {
                        p.seekTo(index, 0L)
                        p.play()
                        return@withContext
                    }
                }
                
                p.stop()
                p.setMediaItems(mediaItems, startIndex, 0L)
                p.prepare()
                p.play()
            }
        }
    }

    fun playUri(uri: Uri, title: String? = null, artist: String? = null, thumbUrl: String? = null, sourceUrl: String? = null) {
        startPlayerService()
        
        if (controller == null) {
            pendingAction = { playUri(uri, title, artist, thumbUrl, sourceUrl) }
            connectToMediaController()
            return
        }

        vibrationManager.vibrateClick()
        
        var displayTitle = title ?: "External Audio"
        if (title == null) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        displayTitle = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setDisplayTitle(displayTitle)
            .setArtist(artist ?: "Unknown Artist")
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
        
        thumbUrl?.let {
            metadataBuilder.setArtworkUri(Uri.parse(it))
        }
        
        sourceUrl?.let {
            metadataBuilder.setExtras(android.os.Bundle().apply {
                putString("source_url", it)
            })
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        val p: Player = controller ?: player
        p.stop()
        p.setMediaItem(mediaItem)
        p.prepare()
        p.play()
    }

    fun addToQueue(track: MusicTrack) {
        val p: Player = controller ?: player
        p.addMediaItem(track.toMediaItem())
        vibrationManager.vibrateClick()
    }

    fun playNext(track: MusicTrack) {
        val p: Player = controller ?: player
        val nextIndex = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
        p.addMediaItem(nextIndex, track.toMediaItem())
        vibrationManager.vibrateClick()
    }

    fun playCatalogTracks(tracks: List<CatalogTrack>, startIndex: Int = 0) {
        startPlayerService()
        
        if (controller == null) {
            pendingAction = { playCatalogTracks(tracks, startIndex) }
            connectToMediaController()
            return
        }

        vibrationManager.vibrateClick()
        
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingCatalog = true) }
            try {
                val mediaItems = tracks.map { track ->
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle("YouTube Catalog")
                        .setDisplayTitle(track.title)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setIsPlayable(true)
                        .setArtworkUri(track.thumbnailUrl?.toUri())
                        .setExtras(android.os.Bundle().apply {
                            putString("source_url", track.sourceUrl)
                            putBoolean("is_catalog", true)
                        })
                        .build()

                    MediaItem.Builder()
                        .setMediaId(track.sourceUrl)
                        .setUri(track.sourceUrl.toUri())
                        .setMediaMetadata(metadata)
                        .build()
                }

                withContext(Dispatchers.Main) {
                    val p: Player = controller ?: player
                    p.stop()
                    p.setMediaItems(mediaItems, startIndex, 0L)
                    p.prepare()
                    p.play()
                }
            } finally {
                _uiState.update { it.copy(isResolvingCatalog = false) }
            }
        }
    }

    fun addToQueue(track: CatalogTrack) {
        val p: Player = controller ?: player
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle("YouTube Catalog")
            .setDisplayTitle(track.title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setArtworkUri(track.thumbnailUrl?.toUri())
            .setExtras(android.os.Bundle().apply {
                putString("source_url", track.sourceUrl)
                putBoolean("is_catalog", true)
            })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.sourceUrl)
            .setUri(track.sourceUrl.toUri())
            .setMediaMetadata(metadata)
            .build()
            
        p.addMediaItem(mediaItem)
        vibrationManager.vibrateClick()
    }

    fun playNext(track: CatalogTrack) {
        val p: Player = controller ?: player
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle("YouTube Catalog")
            .setDisplayTitle(track.title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setArtworkUri(track.thumbnailUrl?.toUri())
            .setExtras(android.os.Bundle().apply {
                putString("source_url", track.sourceUrl)
                putBoolean("is_catalog", true)
            })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.sourceUrl)
            .setUri(track.sourceUrl.toUri())
            .setMediaMetadata(metadata)
            .build()

        val nextIndex = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
        p.addMediaItem(nextIndex, mediaItem)
        vibrationManager.vibrateClick()
    }

    private fun MusicTrack.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist ?: "Unknown Artist")
            .setAlbumTitle(album ?: "Unknown Album")
            .setDisplayTitle(title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setArtworkUri(thumbnailUri?.toUri())
            .apply {
                sourceUrl?.let {
                    setExtras(android.os.Bundle().apply {
                        putString("source_url", it)
                    })
                }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(uri.toUri())
            .setMediaMetadata(metadata)
            .build()
    }

    fun playPlaylist(playlist: Playlist) {
        val tracks = playlist.trackUris.mapNotNull { uri -> 
            _uiState.value.tracks.find { it.uri == uri }
        }
        if (tracks.isEmpty()) return
        
        val p: Player = controller ?: player
        p.shuffleModeEnabled = true
        _uiState.update { it.copy(isShuffleOn = true) }
        
        playTrack(tracks.random(), tracks)
        vibrationManager.vibrateSuccess()
    }

    fun togglePlayPause() {
        startPlayerService()
        val p: Player = controller ?: player
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
        vibrationManager.vibrateClick()
    }

    fun stop() {
        val p: Player = controller ?: player
        p.stop()
        p.clearMediaItems()
        _uiState.update { it.copy(currentTrack = null, isPlaying = false, playbackPosition = 0L, duration = 0L) }
        _playbackPosition.value = 0L
        _duration.value = 0L
        vibrationManager.vibrateClick()
    }

    fun seekTo(position: Long) {
        val p: Player = controller ?: player
        p.seekTo(position)
        _uiState.update { it.copy(playbackPosition = position) }
        _playbackPosition.value = position
        vibrationManager.vibrateClick()
    }

    fun skipNext() {
        val p: Player = controller ?: player
        if (p.hasNextMediaItem()) {
            p.seekToNext()
        } else if (p.repeatMode == Player.REPEAT_MODE_ALL) {
            p.seekTo(0, 0)
        }
        vibrationManager.vibrateClick()
    }
    
    fun skipPrevious() {
        val p: Player = controller ?: player
        if (p.currentPosition > 3000) {
            p.seekTo(0)
        } else if (p.hasPreviousMediaItem()) {
            p.seekToPrevious()
        }
        vibrationManager.vibrateClick()
    }

    fun toggleShuffle() {
        val p: Player = controller ?: player
        val newState = !p.shuffleModeEnabled
        p.shuffleModeEnabled = newState
        _uiState.update { it.copy(isShuffleOn = newState) }
        
        if (newState) {
            vibrationManager.vibrateSuccess()
        } else {
            vibrationManager.vibrateClick()
        }
    }

    fun toggleRepeat() {
        val p: Player = controller ?: player
        val newMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        p.repeatMode = newMode
        _uiState.update { it.copy(repeatMode = newMode) }
        
        if (newMode != Player.REPEAT_MODE_OFF) {
            vibrationManager.vibrateSuccess()
        } else {
            vibrationManager.vibrateClick()
        }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        vibrationManager.vibrateClick()
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(
            sleepTimerMinutes = minutes, 
            sleepTimerActive = minutes != null,
            sleepTimerRemaining = minutes?.let { it * 60 * 1000L }
        ) }
        
        if (minutes != null) {
            val endTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
            sleepTimerJob = viewModelScope.launch {
                while (System.currentTimeMillis() < endTime) {
                    val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0)
                    _uiState.update { it.copy(sleepTimerRemaining = remaining) }
                    delay(1000)
                }
                fadeOutAndStop()
            }
        }
        vibrationManager.vibrateClick()
    }

    fun toggleSleepTimerDialog() {
        _showSleepTimer.update { !it }
        vibrationManager.vibrateClick()
    }

    private suspend fun fadeOutAndStop() {
        var volume = 1.0f
        while (volume > 0) {
            volume -= 0.05f
            player.volume = volume.coerceAtLeast(0f)
            delay(100)
        }
        val p: Player = controller ?: player
        p.pause()
        player.volume = 1.0f
        _uiState.update { it.copy(sleepTimerMinutes = null, sleepTimerActive = false, sleepTimerRemaining = null) }
        vibrationManager.vibrateLongClick()
    }

    fun toggleShowVisualizer() {
        viewModelScope.launch {
            settingsRepository.setShowMusicVisualizer(!_uiState.value.showVisualizer)
            vibrationManager.vibrateClick()
        }
    }

    fun setArtShape(shape: String) {
        viewModelScope.launch {
            settingsRepository.setMusicArtShape(shape)
            vibrationManager.vibrateClick()
        }
    }

    fun toggleRotation() {
        viewModelScope.launch {
            settingsRepository.setMusicRotationEnabled(!_uiState.value.rotationEnabled)
            vibrationManager.vibrateClick()
        }
    }
    
    fun togglePipEnabled() {
        viewModelScope.launch {
            settingsRepository.setMusicPipEnabled(!_uiState.value.pipEnabled)
            vibrationManager.vibrateClick()
        }
    }

    fun createPlaylist(name: String, thumbnailUri: String? = null) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name, thumbnailUri = thumbnailUri))
            vibrationManager.vibrateSuccess()
        }
    }

    fun addTrackToPlaylist(playlist: Playlist, track: MusicTrack) {
        viewModelScope.launch {
            val newList = (playlist.trackUris + track.uri).distinct()
            repository.updatePlaylist(playlist.copy(trackUris = newList))
            vibrationManager.vibrateClick()
        }
    }

    fun addSelectedTracksToPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val newList = (playlist.trackUris + _uiState.value.selectedTracks).distinct()
            repository.updatePlaylist(playlist.copy(trackUris = newList))
            clearSelection()
            vibrationManager.vibrateSuccess()
        }
    }

    fun removeTrackFromPlaylist(playlist: Playlist, trackUri: String) {
        viewModelScope.launch {
            val newList = playlist.trackUris - trackUri
            repository.updatePlaylist(playlist.copy(trackUris = newList))
            vibrationManager.vibrateClick()
        }
    }
    
    fun updatePlaylistThumbnail(playlist: Playlist, uri: Uri) {
        viewModelScope.launch {
            repository.updatePlaylist(playlist.copy(thumbnailUri = uri.toString()))
            vibrationManager.vibrateClick()
        }
    }

    fun createPlaylistWithTracks(name: String, trackUris: List<String>) {
        viewModelScope.launch {
            repository.createPlaylist(Playlist(name = name, trackUris = trackUris))
            vibrationManager.vibrateSuccess()
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            vibrationManager.vibrateClick()
        }
    }

    fun deleteTrack(track: MusicTrack) {
        viewModelScope.launch {
            repository.deleteTrack(track)
            vibrationManager.vibrateClick()
        }
    }
    
    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.setMusicPlaybackSpeed(speed)
            val p: Player = controller ?: player
            p.playbackParameters = PlaybackParameters(speed)
            _uiState.update { it.copy(playbackSpeed = speed) }
            vibrationManager.vibrateClick()
        }
    }

    fun setEqualizerPreset(preset: String) {
        viewModelScope.launch {
            settingsRepository.setMusicEqualizerPreset(preset)
            _uiState.update { it.copy(equalizerPreset = preset) }
            applyEqualizerPreset(preset)
            vibrationManager.vibrateClick()
        }
    }

    fun toggleMusicSettings() {
        _uiState.update { it.copy(showMusicSettings = !it.showMusicSettings) }
        vibrationManager.vibrateClick()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val p: Player = controller ?: player
        if (fromIndex in 0 until p.mediaItemCount && toIndex in 0 until p.mediaItemCount) {
            p.moveMediaItem(fromIndex, toIndex)
            vibrationManager.vibrateTick()
        }
    }

    fun removeQueueItem(index: Int) {
        val p: Player = controller ?: player
        if (index in 0 until p.mediaItemCount) {
            p.removeMediaItem(index)
            vibrationManager.vibrateClick()
        }
    }

    fun clearQueue() {
        val p: Player = controller ?: player
        p.clearMediaItems()
        vibrationManager.vibrateClick()
    }

    fun toggleFavorite(track: MusicTrack) {
        viewModelScope.launch {
            repository.toggleFavorite(track)
            vibrationManager.vibrateClick()
        }
    }

    fun setResolvingCatalog(resolving: Boolean) {
        _uiState.update { it.copy(isResolvingCatalog = resolving) }
    }

    fun enqueueCatalogTrack(track: CatalogTrack, playNext: Boolean) {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setDisplayTitle(track.title)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setArtworkUri(Uri.parse(track.thumbnailUrl))
            .setExtras(android.os.Bundle().apply {
                putString("source_url", track.sourceUrl)
                putBoolean("is_catalog", true)
            })
            .build()
            
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.sourceUrl)
            .setUri(Uri.parse(track.sourceUrl)) // Use sourceUrl as fallback/ID
            .setMediaMetadata(metadata)
            .build()
            
        val p: Player = controller ?: player
        if (playNext) {
            val nextIndex = if (p.mediaItemCount > 0) p.currentMediaItemIndex + 1 else 0
            p.addMediaItem(nextIndex, mediaItem)
        } else {
            p.addMediaItem(mediaItem)
        }
        
        if (!p.isPlaying && !p.playWhenReady) {
            p.prepare()
        }
        
        vibrationManager.vibrateClick()
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        stopShakeDetection()
        stopVisualizer()
        sleepTimerJob?.cancel()
        equalizer?.release()
        player.removeListener(playerListener)
        controller?.removeListener(playerListener)
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        controller = null
    }
}
