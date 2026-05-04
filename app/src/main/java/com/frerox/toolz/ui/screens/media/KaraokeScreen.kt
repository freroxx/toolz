package com.frerox.toolz.ui.screens.media

import android.Manifest
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.ui.screens.media.ai.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.components.bouncyClick
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// Phase
// ─────────────────────────────────────────────────────────────────────────────

private enum class KaraokePhase { SPLASH, COUNTDOWN, ACTIVE, EVALUATION }

// ─────────────────────────────────────────────────────────────────────────────
// Root – KaraokeView
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun KaraokeView(
    state: MusicUiState,
    aiState: NowPlayingAiUiState,
    aiViewModel: NowPlayingAiViewModel,
    onToggleKaraoke: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onNextSongConfirmed: () -> Unit,
    playbackPosition: Long,
    visualizerData: FloatArray
) {
    // Keep previous track visible during transitions so nothing flashes black.
    var activeTrack by remember { mutableStateOf(state.currentTrack) }
    LaunchedEffect(state.currentTrack) {
        if (state.currentTrack != null) activeTrack = state.currentTrack
    }
    val track = activeTrack ?: return

    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()
    val performanceMode = LocalPerformanceMode.current

    // ── State ─────────────────────────────────────────────────────────────────
    var phase               by remember(state.currentTrack?.uri) { mutableStateOf(KaraokePhase.SPLASH) }
    var countdownTick       by remember { mutableIntStateOf(3) }
    var showSettings        by remember { mutableStateOf(false) }
    var showSingConfidentlyDialog by remember { mutableStateOf(false) }
    var showInstrumentalSearch by remember { mutableStateOf(false) }
    var mediaRecorder       by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile       by remember { mutableStateOf<File?>(null) }
    var isRecorderStarting  by remember { mutableStateOf(false) }
    var hasStartedOnce      by remember { mutableStateOf(false) }
    var isPaused            by remember(state.isPlaying) { mutableStateOf(!state.isPlaying && hasStartedOnce) }
    var minSplashTimeElapsed by remember { mutableStateOf(false) }
    var isSkipRequested      by remember { mutableStateOf(false) }

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // ── Current lyric index ───────────────────────────────────────────────────
    val listState = rememberLazyListState()
    val currentLineIndex by remember(playbackPosition, aiState.lyricsState.syncedLyrics) {
        derivedStateOf {
            val lyrics = aiState.lyricsState.syncedLyrics
            if (lyrics.isEmpty()) 0
            else lyrics.indexOfLast { it.timeMs <= playbackPosition }.coerceAtLeast(0)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    val startRecording: () -> Unit = {
        if (micPermission.status.isGranted && !isRecorderStarting) {
            // Always try to start media recording for saving audio
            if (mediaRecorder == null) {
                isRecorderStarting = true
                startMediaRecording(
                    context       = context,
                    trackTitle    = track.title,
                    onRecorderReady = { rec, file ->
                        mediaRecorder      = rec
                        recordingFile      = file
                        isRecorderStarting = false
                        
                        // If scoring is enabled, also start that
                        if (aiState.karaokeSpeechCorrectionEnabled) {
                            aiViewModel.startKaraokeRecording()
                        }
                    },
                    onError = { 
                        isRecorderStarting = false
                        // Fallback: if media recorder fails, try starting scoring anyway
                        if (aiState.karaokeSpeechCorrectionEnabled) {
                            aiViewModel.startKaraokeRecording()
                        }
                    }
                )
            } else if (aiState.karaokeSpeechCorrectionEnabled && !aiState.isKaraokeRecording) {
                // Media recorder already running, just start scoring
                aiViewModel.startKaraokeRecording()
            }
        }
    }

    val toggleRecording: () -> Unit = {
        if (aiState.isKaraokeRecording || mediaRecorder != null) {
            stopMediaRecording(mediaRecorder) { mediaRecorder = null }
            aiViewModel.stopKaraokeRecording()
        } else {
            startRecording()
        }
    }

    val togglePause: () -> Unit = {
        if (state.isPlaying) onPause() else onPlay()
    }

    val finishSession: () -> Unit = {
        if (phase != KaraokePhase.EVALUATION) {
            onPause()
            phase = KaraokePhase.EVALUATION
            stopMediaRecording(mediaRecorder) { mediaRecorder = null }
            aiViewModel.stopKaraokeRecording()
            aiViewModel.toggleSingConfidentlyActive(false) {}
        }
    }

    val skipWithEvaluation: () -> Unit = {
        if (phase == KaraokePhase.ACTIVE) {
            isSkipRequested = true
            finishSession()
        } else {
            onSkipNext()
            aiViewModel.toggleSingConfidentlyActive(false) {}
        }
    }

    // ── Effects ───────────────────────────────────────────────────────────────
    LaunchedEffect(state.currentTrack?.uri) {
        if (!micPermission.status.isGranted) micPermission.launchPermissionRequest()
        minSplashTimeElapsed = false
        delay(1400)
        minSplashTimeElapsed = true
    }

    LaunchedEffect(
        minSplashTimeElapsed,
        aiState.isSearchingInstrumental,
        showSingConfidentlyDialog,
        phase,
        hasStartedOnce
    ) {
        if (phase == KaraokePhase.SPLASH && minSplashTimeElapsed && !aiState.isSearchingInstrumental && !showSingConfidentlyDialog) {
            if (hasStartedOnce) {
                phase = KaraokePhase.ACTIVE
                onPlay()
            } else {
                phase = KaraokePhase.COUNTDOWN
            }
        }
    }

    LaunchedEffect(phase) {
        if (phase == KaraokePhase.COUNTDOWN) {
            for (i in 3 downTo 1) {
                countdownTick = i
                delay(1000L)
            }
            onPlay()
            phase = KaraokePhase.ACTIVE
            hasStartedOnce = true
        }
    }

    // Auto-end at song finish
    LaunchedEffect(playbackPosition, state.duration) {
        if (state.duration > 0
            && playbackPosition >= state.duration - 500
            && phase == KaraokePhase.ACTIVE
        ) {
            delay(500)
            if (phase == KaraokePhase.ACTIVE) {
                finishSession()
            }
        }
    }

    // Quick Sing
    LaunchedEffect(playbackPosition) {
        if (phase == KaraokePhase.ACTIVE && aiState.quickSingEnabled) {
            aiViewModel.checkQuickSing { onSeek(it) }
        }
    }

    // Auto-start recording once active - REMOVED, now manual
    // LaunchedEffect(phase, micPermission.status.isGranted) {
    //    if (phase == KaraokePhase.ACTIVE && micPermission.status.isGranted) startRecording()
    // }

    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying && aiState.isKaraokeRecording) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
            }
        } else if (state.isPlaying && aiState.isKaraokeRecording && !isPaused) {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
            }
        }
    }
    LaunchedEffect(currentLineIndex) {
        if (aiState.lyricsState.syncedLyrics.isNotEmpty() && phase == KaraokePhase.ACTIVE) {
            listState.animateScrollToItem(
                index        = currentLineIndex.coerceAtMost(aiState.lyricsState.syncedLyrics.size - 1),
                scrollOffset = -200
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopMediaRecording(mediaRecorder) { mediaRecorder = null }
            aiViewModel.stopKaraokeRecording()
            recordingFile?.delete()
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .border(
                BorderStroke(
                    2.dp, 
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                        )
                    )
                ),
                RoundedCornerShape(32.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
    ) {

        // Blurred album art background
        if (!performanceMode) {
            AsyncImage(
                model          = track.thumbnailUri,
                contentDescription = null,
                contentScale   = ContentScale.Crop,
                modifier       = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = android.graphics.RenderEffect
                            .createBlurEffect(80f, 80f, android.graphics.Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                        alpha = 0.22f
                    }
            )
        }
        // Dark gradient scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.60f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // ── SPLASH ────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = phase == KaraokePhase.SPLASH,
            enter    = fadeIn(tween(400)) + scaleIn(spring(Spring.DampingRatioLowBouncy), 0.82f),
            exit     = fadeOut(tween(280)) + scaleOut(targetScale = 1.12f),
            modifier = Modifier.fillMaxSize().zIndex(10f)
        ) {
            KaraokeSplash(trackTitle = track.title, trackUri = track.thumbnailUri)
        }

        // ── COUNTDOWN ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = phase == KaraokePhase.COUNTDOWN,
            enter    = fadeIn(tween(180)),
            exit     = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize().zIndex(15f)
        ) {
            KaraokeCountdown(tick = countdownTick)
        }

        // ── ACTIVE / EVALUATION main column ──────────────────────────────────
        AnimatedVisibility(
            visible  = phase == KaraokePhase.ACTIVE || phase == KaraokePhase.EVALUATION,
            enter    = fadeIn(tween(500)),
            exit     = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                KaraokeHeader(
                    title      = track.title,
                    onClose    = onToggleKaraoke,
                    onSettings = { showSettings = true }
                )

                KaraokeProgressBar(
                    position = playbackPosition,
                    duration = state.duration,
                    onSeek   = onSeek
                )

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        aiState.lyricsState.syncedLyrics.isEmpty() ->
                            KaraokeLoadingLyrics()
                        else ->
                            KaraokeLyricsPane(
                                lyrics                   = aiState.lyricsState.syncedLyrics,
                                currentIndex             = currentLineIndex,
                                playbackPosition         = playbackPosition,
                                speechCorrectionEnabled  = aiState.karaokeSpeechCorrectionEnabled,
                                onSeek                   = onSeek,
                                listState                = listState,
                                performanceMode          = performanceMode
                            )
                    }
                }

                KaraokeBottomBar(
                    isPlaying               = state.isPlaying,
                    isRecording             = aiState.isKaraokeRecording || mediaRecorder != null,
                    isPaused                = isPaused,
                    score                   = aiState.karaokeScore,
                    correctWords            = aiState.karaokeCorrectWords,
                    totalWords              = aiState.karaokeTotalWords,
                    streak                  = aiState.karaokeStreak,
                    speechCorrectionEnabled = aiState.karaokeSpeechCorrectionEnabled,
                    micRms                  = aiState.micRms,
                    onStart                 = { 
                        if (hasStartedOnce) {
                            phase = KaraokePhase.ACTIVE
                            onPlay()
                        } else {
                            phase = KaraokePhase.COUNTDOWN 
                        }
                    },
                    onPause                 = togglePause,
                    onToggleRecording       = toggleRecording,
                    onSkipNext              = skipWithEvaluation,
                    onStop                  = finishSession,
                    performanceMode         = performanceMode
                )
            }
        }

        AnimatedVisibility(
            visible  = phase == KaraokePhase.EVALUATION,
            enter    = fadeIn(tween(450)) + slideInVertically(
                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)
            ) { it / 4 },
            exit     = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize().zIndex(20f)
        ) {
            KaraokeEvaluation(
                track           = track,
                score           = aiState.karaokeScore,
                correctWords    = aiState.karaokeCorrectWords,
                totalWords      = aiState.karaokeTotalWords,
                mostAccurateLine= aiState.karaokeMostAccurateLine,
                recordingFile   = recordingFile,
                onDone          = {
                    recordingFile?.delete()
                    recordingFile = null
                    phase = KaraokePhase.ACTIVE
                    if (isSkipRequested) {
                        onNextSongConfirmed()
                    } else {
                        onStop()
                    }
                },
                onAudioSaved    = { 
                    recordingFile?.let { file ->
                        try {
                            val resolver = context.contentResolver
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                                put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                                put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Karaoke")
                            }
                            val uri = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                resolver.openOutputStream(uri)?.use { outStream ->
                                    file.inputStream().use { inStream ->
                                        inStream.copyTo(outStream)
                                    }
                                }
                                Toast.makeText(context, "Saved to Music/Karaoke", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("Karaoke", "Failed to save audio via MediaStore", e)
                            Toast.makeText(context, "Failed to save audio", Toast.LENGTH_SHORT).show()
                        }
                    }
                    recordingFile = null 
                }
            )
        }
    }

    // Settings bottom-sheet
    if (showSettings) {
        KaraokeSettingsModal(
            speechCorrectionEnabled = aiState.karaokeSpeechCorrectionEnabled,
            onSpeechCorrectionToggle = { aiViewModel.setKaraokeSpeechCorrectionEnabled(it) },
            quickSingEnabled         = aiState.quickSingEnabled,
            onQuickSingToggle        = { aiViewModel.setQuickSingEnabled(it) },
            singConfidentlyEnabled   = aiState.karaokeSingConfidentlyEnabled,
            onSingConfidentlyToggle  = { aiViewModel.setSingConfidentlyEnabled(it) },
            onDismiss                = { showSettings = false }
        )
    }

    // Sing Confidently Dialog

    LaunchedEffect(aiState.instrumentalMatch, aiState.karaokeSingConfidentlyEnabled, phase) {
        if (aiState.instrumentalMatch != null 
            && aiState.karaokeSingConfidentlyEnabled 
            && !aiState.isSingConfidentlyActive
            && (phase == KaraokePhase.SPLASH || phase == KaraokePhase.COUNTDOWN)
        ) {
            showSingConfidentlyDialog = true
        }
    }

    if (showSingConfidentlyDialog && aiState.instrumentalMatch != null) {
        SingConfidentlyDialog(
            match = aiState.instrumentalMatch,
            onSwitch = {
                showSingConfidentlyDialog = false
                aiViewModel.toggleSingConfidentlyActive(true) { onSeek(it) }
            },
            onKeep = { showSingConfidentlyDialog = false },
            onEdit = { 
                showSingConfidentlyDialog = false
                showInstrumentalSearch = true
            }
        )
    }

    if (showInstrumentalSearch) {
        InstrumentalSearchSheet(
            onDismiss = { showInstrumentalSearch = false },
            onSearch = { query -> 
                aiViewModel.searchInstrumentalCustom(query)
                showInstrumentalSearch = false
                showSingConfidentlyDialog = true // Bring dialog back after search
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Splash
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KaraokeSplash(trackTitle: String, trackUri: String?) {
    val inf = rememberInfiniteTransition(label = "splash")
    val pulse by inf.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Ambient glow ring
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .scale(pulse)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                Surface(
                    modifier = Modifier.size(68.dp),
                    shape    = CircleShape,
                    color    = MaterialTheme.colorScheme.primary,
                    shadowElevation = 12.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = null,
                            tint     = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            Text(
                "KARAOKE",
                style       = MaterialTheme.typography.displaySmall,
                fontWeight  = FontWeight.Black,
                color       = MaterialTheme.colorScheme.primary,
                letterSpacing = 8.sp
            )
            Text(
                trackTitle,
                style       = MaterialTheme.typography.bodyLarge,
                fontWeight  = FontWeight.Medium,
                color       = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign   = TextAlign.Center,
                modifier    = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Countdown
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KaraokeCountdown(tick: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            AnimatedContent(
                targetState  = tick,
                transitionSpec = {
                    (scaleIn(spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow), 0.25f) +
                            fadeIn(tween(180)))
                        .togetherWith(scaleOut(targetScale = 2.2f) + fadeOut(tween(180)))
                },
                label = "countdownNum"
            ) { t ->
                Text(
                    text       = "$t",
                    style      = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    fontSize   = 148.sp,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "GET READY",
                style       = MaterialTheme.typography.labelLarge,
                fontWeight  = FontWeight.Black,
                letterSpacing = 6.sp,
                color       = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KaraokeHeader(
    title     : String,
    onClose   : () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        HeaderButton(icon = Icons.Rounded.Close, onClick = onClose)

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                "KARAOKE MODE",
                style       = MaterialTheme.typography.labelSmall,
                fontWeight  = FontWeight.Black,
                letterSpacing = 2.sp,
                color       = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                title,
                style   = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color   = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        HeaderButton(icon = Icons.Rounded.Settings, onClick = onSettings)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000L
    return "%d:%02d".format(s / 60, s % 60)
}

// ─────────────────────────────────────────────────────────────────────────────
// Lyrics pane
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun KaraokeLyricsPane(
    lyrics                 : List<LyricsLine>,
    currentIndex           : Int,
    playbackPosition       : Long,
    speechCorrectionEnabled: Boolean,
    onSeek                 : (Long) -> Unit,
    listState              : LazyListState,
    performanceMode        : Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state             = listState,
            modifier          = Modifier.fillMaxSize(),
            contentPadding    = PaddingValues(vertical = 110.dp, horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lyrics, key = { idx, line -> "${idx}_${line.timeMs}" }) { idx, line ->
                val isCurrent = idx == currentIndex
                val isPast    = idx < currentIndex

                KaraokeLine(
                    line                    = line,
                    isCurrent               = isCurrent,
                    isPast                  = isPast,
                    playbackPosition        = playbackPosition,
                    speechCorrectionEnabled = speechCorrectionEnabled,
                    performanceMode         = performanceMode,
                    onLongClick             = { onSeek(line.timeMs) },
                    modifier                = Modifier.animateItem()
                )
            }
        }

        // Fade edges so lyrics blend into background
        FadeEdge(Modifier.align(Alignment.TopCenter), topToBottom = true)
        FadeEdge(Modifier.align(Alignment.BottomCenter), topToBottom = false)
    }
}

@Composable
private fun FadeEdge(modifier: Modifier, topToBottom: Boolean) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                Brush.verticalGradient(
                    if (topToBottom) listOf(surface, Color.Transparent)
                    else             listOf(Color.Transparent, surface)
                )
            )
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun KaraokeLine(
    line                   : LyricsLine,
    isCurrent              : Boolean,
    isPast                 : Boolean,
    playbackPosition       : Long,
    speechCorrectionEnabled: Boolean,
    performanceMode        : Boolean,
    onLongClick            : () -> Unit,
    modifier               : Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1f
            isPast    -> 0.88f
            else      -> 0.84f
        },
        animationSpec = if (performanceMode) snap()
        else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        label         = "lineScale"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1f
            isPast    -> 0.32f
            else      -> 0.16f
        },
        animationSpec = if (performanceMode) snap() else tween(350),
        label         = "lineAlpha"
    )

    // STOP_INDICATOR – render animated dots during instrumental breaks
    if (line.content == "[STOP_INDICATOR]") {
        Row(
            modifier              = modifier
                .fillMaxWidth()
                .scale(scale)
                .alpha(alpha)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                BreathingDot(delayMs = i * 220, isActive = isCurrent)
                if (i < 2) Spacer(Modifier.width(14.dp))
            }
        }
        return
    }

    Box(
        modifier          = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        contentAlignment  = Alignment.Center
    ) {
        if (line.words.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement   = Arrangement.spacedBy(2.dp)
            ) {
                line.words.forEach { word ->
                    KaraokeWord(
                        word                    = word,
                        isCurrent               = isCurrent,
                        playbackPosition        = playbackPosition,
                        speechCorrectionEnabled = speechCorrectionEnabled,
                        performanceMode         = performanceMode
                    )
                }
            }
        } else {
            Text(
                text       = line.content,
                style      = if (isCurrent) MaterialTheme.typography.headlineMedium
                else           MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
                color      = if (isCurrent) MaterialTheme.colorScheme.onSurface
                else           MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun BreathingDot(delayMs: Int, isActive: Boolean) {
    val inf = rememberInfiniteTransition(label = "dot$delayMs")
    val dy by if (isActive) {
        inf.animateFloat(
            0f, -12f,
            infiniteRepeatable(
                tween(550, delayMillis = delayMs, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "dy"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Surface(
        modifier  = Modifier
            .size(10.dp)
            .graphicsLayer { translationY = dy }
            .alpha(if (isActive) 1f else 0.28f),
        shape     = CircleShape,
        color     = if (isActive) MaterialTheme.colorScheme.primary
        else          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    ) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// Single word with karaoke status coloring
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KaraokeWord(
    word                   : LyricsWord,
    isCurrent              : Boolean,
    playbackPosition       : Long,
    speechCorrectionEnabled: Boolean,
    performanceMode        : Boolean
) {
    val isActive = playbackPosition >= word.startTimeMs &&
            playbackPosition <  word.startTimeMs + word.durationMs
    val isPast   = playbackPosition >= word.startTimeMs + word.durationMs

    val animFrac by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = if (performanceMode) snap() else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        label = "wordAnim"
    )

    // Ping animation when correct
    val isCorrect = word.karaokeStatus == KaraokeWordStatus.CORRECT
    val pingScale by animateFloatAsState(
        targetValue = if (isCorrect) 1.15f else 1f,
        animationSpec = if (isCorrect) spring(Spring.DampingRatioHighBouncy) else snap()
    )

    // Word coloring
    val highlightColor = Color(0xFFFFD700) // Vibrant Gold
    val missedColor    = Color(0xFFFF5252).copy(alpha = 0.7f) // Subdued Red
    val activeColor    = MaterialTheme.colorScheme.primary
    val pastColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val pendingColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val farColor       = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)

    val color = when {
        isCorrect                                                                    ->
            highlightColor
        word.karaokeStatus == KaraokeWordStatus.MISSED && speechCorrectionEnabled    ->
            missedColor
        isActive                                                                     ->
            activeColor
        isCurrent && isPast                                                          ->
            pastColor
        isCurrent                                                                    ->
            pendingColor
        else                                                                         ->
            farColor
    }

    val wordScale = (1f + (0.08f * animFrac)) * pingScale

    Text(
        text       = word.word,
        style      = if (isCurrent) MaterialTheme.typography.headlineMedium
        else           MaterialTheme.typography.bodyLarge,
        fontWeight = if (isActive || isCorrect) FontWeight.Black
        else if (isCurrent) FontWeight.ExtraBold
        else FontWeight.Medium,
        color      = color,
        modifier   = Modifier
            .graphicsLayer {
                scaleX = wordScale
                scaleY = wordScale
                if (!performanceMode) {
                    translationY = -2.dp.toPx() * animFrac
                }
            }
            .padding(horizontal = 3.dp, vertical = 2.dp),
        textAlign  = TextAlign.Center
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KaraokeLoadingLyrics() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier    = Modifier.size(36.dp),
                color       = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Text(
                "Loading lyrics…",
                style   = MaterialTheme.typography.bodyMedium,
                color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KaraokeBottomBar(
    isPlaying              : Boolean,
    isRecording            : Boolean,
    isPaused               : Boolean,
    score                  : Int,
    correctWords           : Int,
    totalWords             : Int,
    streak                 : Int,
    speechCorrectionEnabled: Boolean,
    micRms                 : Float,
    onStart                : () -> Unit,
    onPause                : () -> Unit,
    onToggleRecording      : () -> Unit,
    onSkipNext             : () -> Unit,
    onStop                 : () -> Unit,
    performanceMode        : Boolean
) {
    Column(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(14.dp)
    ) {
        // Live score chip – only shown once we have words to evaluate
        AnimatedVisibility(
            visible = speechCorrectionEnabled && totalWords > 0,
            enter   = fadeIn(tween(300)) + expandVertically(),
            exit    = fadeOut(tween(200)) + shrinkVertically()
        ) {
            LiveScoreChip(score = score, correct = correctWords, total = totalWords, streak = streak)
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mic Toggle (Recording)
            MicToggleButton(
                isRecording = isRecording,
                micRms      = micRms,
                onClick     = onToggleRecording
            )

            // Play/Pause Master
            PlayPauseMasterButton(
                isPlaying       = isPlaying,
                isPaused        = isPaused,
                onPause         = onPause,
                onStart         = onStart,
                performanceMode = performanceMode
            )

            // Skip Next
            SkipNextButton(onClick = onSkipNext)

            // Finish/Stop session
            FinishSessionButton(onClick = onStop)
        }
    }
}

// ── Live score chip ───────────────────────────────────────────────────────────

@Composable
private fun LiveScoreChip(score: Int, correct: Int, total: Int, streak: Int) {
    val tint = when {
        score >= 80 -> Color(0xFF43A047)
        score >= 50 -> Color(0xFFFB8C00)
        else        -> Color(0xFFE53935)
    }

    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(500),
        label = "animatedScore"
    )

    val popScale by animateFloatAsState(
        targetValue = 1f + (correct % 5 * 0.01f).coerceAtMost(0.05f), // Slight pop on correct word
        animationSpec = spring(Spring.DampingRatioMediumBouncy)
    )

    Surface(
        shape  = CircleShape,
        color  = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
        modifier = Modifier.scale(popScale)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Star, null,
                modifier = Modifier.size(13.dp), tint = tint)
            Text(
                "$correct / $total  ·  $animatedScore%",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color      = tint
            )

            if (streak > 2) {
                VerticalDivider(modifier = Modifier.height(12.dp).alpha(0.2f), color = tint)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Whatshot, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF7043))
                    Text(
                        "$streak",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF7043)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Progress Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KaraokeProgressBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    val progress = if (duration > 0) position.toFloat() / duration else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Slider(
            value = progress,
            onValueChange = { onSeek((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(position),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MicToggleButton(
    isRecording: Boolean,
    micRms     : Float,
    onClick    : () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "micPulse")

    // Pulse faster and larger if recording and based on RMS (volume)
    val targetScale = if (isRecording) 1.15f + (micRms / 100f).coerceIn(0f, 0.15f) else 1f
    val pulse by inf.animateFloat(
        1f, targetScale,
        infiniteRepeatable(tween(if (isRecording) 600 else 1000), RepeatMode.Reverse),
        label = "pulse"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.size(56.dp).scale(pulse),
        border = BorderStroke(
            1.5.dp,
            if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isRecording) Icons.Rounded.Mic else Icons.Rounded.MicNone,
                contentDescription = null,
                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun PlayPauseMasterButton(
    isPlaying: Boolean,
    isPaused: Boolean,
    onPause: () -> Unit,
    onStart: () -> Unit,
    performanceMode: Boolean
) {
    Surface(
        onClick = { if (isPlaying) onPause() else onStart() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp),
        shadowElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun SkipNextButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
    ) {
        Icon(Icons.Rounded.SkipNext, null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FinishSessionButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), CircleShape)
    ) {
        Icon(Icons.Rounded.Stop, null, tint = MaterialTheme.colorScheme.error)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Evaluation screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun KaraokeEvaluationCard(
    track            : com.frerox.toolz.data.music.MusicTrack,
    score            : Int,
    correctWords     : Int,
    totalWords       : Int,
    mostAccurateLine : String?,
    dynamicColors    : DynamicColors,
    performanceMode  : Boolean,
    recordingFile    : File?,
    onDismiss        : () -> Unit,
    onAudioSaved     : () -> Unit
) = KaraokeEvaluation(
    track            = track,
    score            = score,
    correctWords     = correctWords,
    totalWords       = totalWords,
    mostAccurateLine = mostAccurateLine,
    recordingFile    = recordingFile,
    onDone           = onDismiss,
    onAudioSaved     = onAudioSaved
)


@Composable
private fun KaraokeEvaluation(
    track            : com.frerox.toolz.data.music.MusicTrack,
    score            : Int,
    correctWords     : Int,
    totalWords       : Int,
    mostAccurateLine : String?,
    recordingFile    : File?,
    onDone           : () -> Unit,
    onAudioSaved     : () -> Unit
) {
    val (grade, gradeColor, message) = remember(score) {
        when {
            score >= 95 -> Triple("S", Color(0xFFFFD700), "Legendary Performance! 👑")
            score >= 85 -> Triple("A", Color(0xFF42A5F5), "Superb Vocals! 🌟")
            score >= 70 -> Triple("B", Color(0xFF66BB6A), "Solid Performance! 🎵")
            score >= 50 -> Triple("C", Color(0xFFFFA726), "Nice Try! 💪")
            else        -> Triple("D", Color(0xFFEF5350), "Don't Give Up! 🎤")
        }
    }

    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "animatedScore"
    )

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            )
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(28.dp)
        ) {
            Text(
                "SESSION RESULTS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            // Grade ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(240.dp).background(
                        Brush.radialGradient(listOf(gradeColor.copy(alpha=0.3f), Color.Transparent)), CircleShape
                    )
                )
                CircularProgressIndicator(
                    progress    = { animatedScore / 100f },
                    modifier    = Modifier.size(210.dp),
                    strokeWidth = 14.dp,
                    color       = gradeColor,
                    trackColor  = gradeColor.copy(alpha = 0.08f),
                    strokeCap   = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        grade,
                        style      = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        fontSize   = 104.sp,
                        color      = gradeColor
                    )
                    Text(
                        "$animatedScore%",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Text(
                message,
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color      = gradeColor,
                textAlign  = TextAlign.Center
            )

            // Stats card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(28.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border   = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            ) {
                Column(
                    modifier            = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EvalStat("CORRECT", "$correctWords", gradeColor)
                        EvalStat("ACCURACY", "$score%", gradeColor)
                        EvalStat("TOTAL",   "$totalWords",
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }

                    if (mostAccurateLine != null) {
                        HorizontalDivider(modifier = Modifier.alpha(0.06f))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "PERFECT LINE",
                                style       = MaterialTheme.typography.labelSmall,
                                fontWeight  = FontWeight.Black,
                                color       = gradeColor.copy(alpha = 0.7f),
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                "\"$mostAccurateLine\"",
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                fontStyle  = FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // Actions
            Column(
                modifier            = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (recordingFile != null) {
                    OutlinedButton(
                        onClick  = onAudioSaved,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape    = RoundedCornerShape(18.dp),
                        border   = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Rounded.Save, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "SAVE RECORDING",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Button(
                    onClick  = onDone,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape    = RoundedCornerShape(18.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = gradeColor),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        "DONE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        fontSize      = 18.sp,
                        letterSpacing = 2.sp,
                        color         = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) = EvalStat(label, value,
    MaterialTheme.colorScheme.onSurface)

@Composable
private fun EvalStat(label: String, value: String, valueColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color      = valueColor)
        Text(label,
            style       = MaterialTheme.typography.labelSmall,
            fontWeight  = FontWeight.Bold,
            color       = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
            letterSpacing = 1.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings modal  (unchanged from original – kept intact)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SingConfidentlyDialog(
    match: CatalogTrack,
    onSwitch: () -> Unit,
    onKeep: () -> Unit,
    onEdit: () -> Unit
) {
    Dialog(
        onDismissRequest = onKeep,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 32.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            modifier = Modifier.padding(horizontal = 24.dp).graphicsLayer {
                renderEffect = android.graphics.RenderEffect.createBlurEffect(40f, 40f, android.graphics.Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoFixHigh,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    "Sing Confidently",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "For your maximum karaoke experience, we recommend choosing the instrumental version of this song.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AsyncImage(
                            model = match.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                match.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                match.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)) {
                            Icon(
                                Icons.Rounded.Search,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onKeep,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(54.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text("Keep Original", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onSwitch,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Switch", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentalSearchSheet(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Search Instrumental", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Someone Like You Karaoke", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch(query) }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Button(
                onClick = { if (query.isNotBlank()) onSearch(query) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Search Catalog", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeSettingsModal(
    speechCorrectionEnabled : Boolean,
    onSpeechCorrectionToggle: (Boolean) -> Unit,
    quickSingEnabled        : Boolean,
    onQuickSingToggle       : (Boolean) -> Unit,
    singConfidentlyEnabled  : Boolean,
    onSingConfidentlyToggle : (Boolean) -> Unit,
    onDismiss               : () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        containerColor   = MaterialTheme.colorScheme.surface,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp, bottom = 10.dp)
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier.padding(top = 6.dp)
            ) {
                Surface(
                    shape   = RoundedCornerShape(14.dp),
                    color   = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Tune, null,
                            tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp))
                    }
                }
                Column {
                    Text("Karaoke Settings",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black)
                    Text("Customize your experience",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            }

            HorizontalDivider(modifier = Modifier.alpha(0.08f))

            SettingsToggleRow(
                icon           = Icons.Rounded.RecordVoiceOver,
                title          = "Speech Correction",
                subtitle       = if (speechCorrectionEnabled)
                    "Score evaluated at end of song"
                else
                    "Free singing — no scoring",
                checked        = speechCorrectionEnabled,
                onCheckedChange = onSpeechCorrectionToggle
            )

            SettingsToggleRow(
                icon           = Icons.Rounded.FastForward,
                title          = "Quick Sing",
                subtitle       = "Skips long intros and pauses between lyrics",
                checked        = quickSingEnabled,
                onCheckedChange = onQuickSingToggle
            )

            SettingsToggleRow(
                icon           = Icons.Rounded.AutoFixHigh,
                title          = "Sing Confidently",
                subtitle       = "Recommends instrumental version when available",
                checked        = singConfidentlyEnabled,
                onCheckedChange = onSingConfidentlyToggle
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Icon(Icons.Rounded.Info, null,
                    modifier = Modifier.size(14.dp).alpha(0.35f),
                    tint     = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Press the record button to start recording. You can save the audio after singing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon           : androidx.compose.ui.graphics.vector.ImageVector,
    title          : String,
    subtitle       : String,
    checked        : Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val surfaceColor by animateColorAsState(
        targetValue   = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else         MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        animationSpec = tween(300),
        label         = "toggleSurface"
    )
    Surface(
        shape   = RoundedCornerShape(20.dp),
        color   = surfaceColor,
        border  = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            else         MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier.weight(1f)
            ) {
                val iconBg by animateColorAsState(
                    targetValue   = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else         MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = tween(300),
                    label         = "iconBg"
                )
                Surface(shape = CircleShape, color = iconBg, modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon, null,
                            tint     = if (checked) MaterialTheme.colorScheme.primary
                            else         MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(title,   fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f))
                }
            }
            Switch(
                checked         = checked,
                onCheckedChange = onCheckedChange,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MediaRecorder helpers  (unchanged)
// ─────────────────────────────────────────────────────────────────────────────

private fun startMediaRecording(
    context        : android.content.Context,
    trackTitle     : String,
    onRecorderReady: (MediaRecorder, File) -> Unit,
    onError        : () -> Unit
) {
    val folder    = context.getExternalFilesDir(null) ?: return
    val safeTitle = trackTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val count     = folder.listFiles { f ->
        f.name.startsWith("$safeTitle recording")
    }?.size ?: 0

    val file = File(folder, "$safeTitle recording ${count + 1}.m4a")

    try {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        onRecorderReady(recorder, file)
    } catch (e: Exception) {
        android.util.Log.e("KaraokeRecorder", "Failed to start recording", e)
        onError()
    }
}

private fun stopMediaRecording(recorder: MediaRecorder?, onDone: () -> Unit) {
    try {
        recorder?.stop()
        recorder?.release()
    } catch (_: Exception) {}
    onDone()
}