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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import com.frerox.toolz.data.catalog.CatalogTrack
import com.frerox.toolz.ui.screens.media.ai.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.AlbumArtImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Phase
// ─────────────────────────────────────────────────────────────────────────────

private enum class KaraokePhase { SPLASH, COUNTDOWN, ACTIVE, EVALUATION, IDLE }

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
    onSetVolume: (Float) -> Unit,
    onSetMutedByAi: (Boolean) -> Unit,
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

    val prsMsg = stringResource(R.string.st_KaraokeScreen_prs1)
    val rfefMsg = stringResource(R.string.st_KaraokeScreen_rfef2)
    val stmkMsg = stringResource(R.string.st_KaraokeScreen_stmk3)
    val ftsrMsg = stringResource(R.string.st_KaraokeScreen_ftsr4)

    // ── State ─────────────────────────────────────────────────────────────────
    var phase               by remember(track.uri) { mutableStateOf(KaraokePhase.SPLASH) }
    var countdownTick       by remember { mutableIntStateOf(3) }
    var showSettings        by remember { mutableStateOf(false) }
    var showSingConfidentlyDialog by remember { mutableStateOf(false) }
    var showManualPickSheet by remember { mutableStateOf(false) }
    var showInstrumentalSearch by remember { mutableStateOf(false) }
    var mediaRecorder       by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile       by remember { mutableStateOf<File?>(null) }
    var isRecorderStarting  by remember { mutableStateOf(false) }
    var hasStartedOnce      by remember { mutableStateOf(false) }
    
    // Track if the user wants to save their audio. Disabled if Speech Correction is enabled.
    var isAudioSavingEnabled by remember { mutableStateOf(!aiState.karaokeSpeechCorrectionEnabled) }
    // Track whether the MediaRecorder was fully started so stopMediaRecording
    // never calls stop() on a recorder that was never started.
    var isMediaRecorderStarted by remember { mutableStateOf(false) }
    
    // Logical playing state accounts for both the main player and the AI instrumental player
    val isLogicalPlaying = state.isPlaying || aiState.isInstrumentalPlaying
    
    var minSplashTimeElapsed by remember { mutableStateOf(false) }
    var isSkipRequested      by remember { mutableStateOf(false) }
    var wasSingConfidentlyHandled by remember(track.uri) { mutableStateOf(false) }

    var pendingSpeechCorrectionToggle by remember { mutableStateOf<Boolean?>(null) }
    var pendingSingConfidentlyMode by remember { mutableStateOf<com.frerox.toolz.ui.screens.media.ai.SingConfidentlyMode?>(null) }

    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // ── Current lyric index ───────────────────────────────────────────────────
    val listState = rememberLazyListState()
    val currentLineIndex by remember(playbackPosition, aiState.lyricsState.syncedLyrics) {
        derivedStateOf {
            val lyrics = aiState.lyricsState.syncedLyrics
            if (lyrics.isEmpty()) -1
            else lyrics.indexOfLast { it.timeMs <= playbackPosition }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    var isNaturalFinish by remember { mutableStateOf(false) }

    val finishSession: (Boolean) -> Unit = { isNatural ->
        if (phase != KaraokePhase.EVALUATION) {
            isNaturalFinish = isNatural
            onPause()
            phase = KaraokePhase.EVALUATION
            stopMediaRecording(scope, mediaRecorder, isMediaRecorderStarted) { 
                mediaRecorder = null
                isMediaRecorderStarted = false
            }
            aiViewModel.stopKaraokeRecording()
            aiViewModel.toggleSingConfidentlyActive(false) {}
            isAudioSavingEnabled = false
        }
    }

    val startAudioRecording: () -> Unit = {
        if (micPermission.status.isGranted && !isRecorderStarting && mediaRecorder == null
                && !aiState.karaokeSpeechCorrectionEnabled) {
            isRecorderStarting = true
            startMediaRecording(
                scope           = scope,
                context         = context,
                trackTitle      = track.title,
                trackArtist     = track.artist ?: "Unknown Artist",
                thumbnailUrl    = track.thumbnailUri ?: "",
                preferMicSource = false,
                onRecorderReady = { rec, file ->
                    mediaRecorder          = rec
                    recordingFile          = file
                    isMediaRecorderStarted = true
                    isRecorderStarting     = false
                },
                onError = {
                    isMediaRecorderStarted = false
                    isRecorderStarting     = false
                }
            )
        }
    }

    // Mic button: toggles audio saving (MediaRecorder) only.
    // When speech correction is active the button is disabled in the UI so
    // this lambda will never fire in that mode, but we keep the guard here
    // as a safety net.
    var isManualRecordingMode by remember { mutableStateOf(false) }

    val toggleRecording: () -> Unit = {
        if (!aiState.karaokeSpeechCorrectionEnabled) {
            if (mediaRecorder != null || isMediaRecorderStarted) {
                stopMediaRecording(scope, mediaRecorder, isMediaRecorderStarted) {
                    mediaRecorder = null
                    isMediaRecorderStarted = false
                    isAudioSavingEnabled = false
                    isManualRecordingMode = true
                }
            } else {
                recordingFile?.let { file ->
                    if (file.exists() && file.length() > 0L) {
                        saveKaraokeRecording(
                            context      = context,
                            file         = file,
                            displayName  = file.name,
                            trackTitle   = track.title,
                            thumbnailUrl = track.thumbnailUri ?: "",
                            onDone       = { success ->
                                if (success) {
                                    (context as? android.app.Activity)?.runOnUiThread {
                                        Toast.makeText(context, prsMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                runCatching { File(file.absolutePath.replace(".m4a", ".json")).delete() }
                                runCatching { file.delete() }
                            }
                        )
                    }
                }
                recordingFile = null
                isAudioSavingEnabled = true
                isManualRecordingMode = false
                startAudioRecording()
            }
        }
    }

    val togglePause: () -> Unit = {
        if (isLogicalPlaying) onPause() else onPlay()
    }

    val skipWithEvaluation: () -> Unit = {
        if (phase == KaraokePhase.ACTIVE) {
            isSkipRequested = true
            finishSession(false)
        } else {
            onSkipNext()
            aiViewModel.toggleSingConfidentlyActive(false) {}
        }
    }

    // ── Effects ───────────────────────────────────────────────────────────────
    LaunchedEffect(state.currentTrack?.uri, phase) {
        if (phase == KaraokePhase.SPLASH) {
            if (!micPermission.status.isGranted) {
                // Only request once per track change; system suppresses repeated permanent denials
                micPermission.launchPermissionRequest()
            }
            minSplashTimeElapsed = false
            delay(1400)
            minSplashTimeElapsed = true
        }
    }

    LaunchedEffect(
        minSplashTimeElapsed,
        aiState.isSearchingInstrumental,
        showSingConfidentlyDialog,
        showManualPickSheet,
        phase,
        hasStartedOnce,
        state.currentTrack?.uri,
        aiState.instrumentalMatch,
        aiState.singConfidentlyMode,
        aiState.isSingConfidentlyActive,
        wasSingConfidentlyHandled
    ) {
        if (phase == KaraokePhase.SPLASH && minSplashTimeElapsed) {

            // Manual Mode: show the manual pick sheet immediately, don't wait for search
            if (aiState.singConfidentlyMode == com.frerox.toolz.ui.screens.media.ai.SingConfidentlyMode.MANUAL
                && !aiState.isSingConfidentlyActive
                && !showManualPickSheet
                && !hasStartedOnce
                && !wasSingConfidentlyHandled
            ) {
                onPause()
                showManualPickSheet = true
                return@LaunchedEffect
            }

            if (!aiState.isSearchingInstrumental) {
                // Auto-Proceed Mode: silently switch to instrumental as soon as a match is found
                if (aiState.instrumentalMatch != null
                    && aiState.singConfidentlyMode == com.frerox.toolz.ui.screens.media.ai.SingConfidentlyMode.AUTO_PROCEED
                    && !aiState.isSingConfidentlyActive
                    && !hasStartedOnce
                    && !wasSingConfidentlyHandled
                ) {
                    wasSingConfidentlyHandled = true
                    onPause() // Mute until countdown is done
                    aiViewModel.toggleSingConfidentlyActive(true) { onSeek(it) }
                    // Fall through to countdown
                }

                // Auto Mode: show the recommendation dialog
                if (aiState.instrumentalMatch != null
                    && aiState.singConfidentlyMode == com.frerox.toolz.ui.screens.media.ai.SingConfidentlyMode.AUTO
                    && !aiState.isSingConfidentlyActive
                    && !showSingConfidentlyDialog
                    && !hasStartedOnce
                    && !wasSingConfidentlyHandled
                ) {
                    onPause()
                    showSingConfidentlyDialog = true
                    return@LaunchedEffect
                }

                if (!showSingConfidentlyDialog && !showManualPickSheet) {
                    if (hasStartedOnce) {
                        phase = KaraokePhase.ACTIVE
                        onPlay()
                    } else {
                        onPause() // Ensure music is paused before countdown starts
                        phase = KaraokePhase.COUNTDOWN
                    }
                }
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
        if (state.duration > 3000
            && playbackPosition > 0
            && playbackPosition >= state.duration - 800
            && phase == KaraokePhase.ACTIVE
        ) {
            finishSession(true)
        }
    }

    // Quick Sing
    LaunchedEffect(playbackPosition) {
        if (phase == KaraokePhase.ACTIVE && aiState.quickSingEnabled) {
            aiViewModel.checkQuickSing { onSeek(it) }
        }
    }

    // Auto-start Speech Correct or Audio Saving when phase is ACTIVE.
    // We strictly enforce exclusive mic ownership.
    LaunchedEffect(phase, micPermission.status.isGranted, aiState.karaokeSpeechCorrectionEnabled, isLogicalPlaying) {
        if (phase == KaraokePhase.ACTIVE && micPermission.status.isGranted && isLogicalPlaying) {
            // Add a stability delay to prevent mic flickering at the very start of the session
            // while the media player is still buffering or transitioning.
            delay(400)
            if (aiState.karaokeSpeechCorrectionEnabled) {
                // EXCLUSIVE: Speech Correction owns the mic.
                if (isAudioSavingEnabled || mediaRecorder != null) {
                    isAudioSavingEnabled = false
                    stopMediaRecording(scope, mediaRecorder, isMediaRecorderStarted) {
                        mediaRecorder = null
                        isMediaRecorderStarted = false
                        // Only start recognition AFTER recorder is fully released
                        aiViewModel.startKaraokeRecording()
                    }
                } else {
                    aiViewModel.startKaraokeRecording()
                }
            } else if (isAudioSavingEnabled && mediaRecorder == null && !isRecorderStarting) {
                startAudioRecording()
            } else if (aiState.autoRecordEnabled && !isAudioSavingEnabled && !isManualRecordingMode && mediaRecorder == null && !isRecorderStarting) {
                isAudioSavingEnabled = true
                startAudioRecording()
            }
        }
    }

    LaunchedEffect(isLogicalPlaying, mediaRecorder, aiState.karaokeSpeechCorrectionEnabled) {
        if (aiState.karaokeSpeechCorrectionEnabled) {
            if (!isLogicalPlaying) aiViewModel.pauseKaraokeListening()
            else aiViewModel.resumeKaraokeListening()
        }
        
        // Only pause/resume the MediaRecorder if it was fully started.
        val isAudioRecording = mediaRecorder != null && isMediaRecorderStarted
        if (!isLogicalPlaying && isAudioRecording) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mediaRecorder?.pause()
                    Log.d("Karaoke", "MediaRecorder paused (isLogicalPlaying=false)")
                }
                // Pre-N: MediaRecorder.pause() is unavailable. The recording
                // continues through the pause — this is acceptable behaviour
                // (the user chose to save the whole performance) and is clearly
                // preferable to crashing or recording corrupted audio.
            }
        } else if (isLogicalPlaying && isAudioRecording) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    mediaRecorder?.resume()
                    Log.d("Karaoke", "MediaRecorder resumed (isLogicalPlaying=true)")
                }
            }
        }
    }
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && aiState.lyricsState.syncedLyrics.isNotEmpty() && phase == KaraokePhase.ACTIVE) {
            listState.animateScrollToItem(
                index        = currentLineIndex.coerceAtMost(aiState.lyricsState.syncedLyrics.size - 1),
                scrollOffset = -200
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopMediaRecording(scope, mediaRecorder, isMediaRecorderStarted) { 
                mediaRecorder = null
                isMediaRecorderStarted = false
            }
            aiViewModel.stopKaraokeRecording()
            // Ensure Sing Confidently is cleaned up and main player state is restored
            aiViewModel.toggleSingConfidentlyActive(false) { onSeek(it) }
            isAudioSavingEnabled = false
            
            // Clean up any temporary unsaved recordings if the screen is disposed
            recordingFile?.let { file ->
                val metaFile = File(file.absolutePath.replace(".m4a", ".json"))
                metaFile.delete()
                file.delete()
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
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
        if (!performanceMode && !track.thumbnailUri.isNullOrBlank()) {
            AsyncImage(
                model          = track.thumbnailUri,
                contentDescription = null,
                contentScale   = ContentScale.Crop,
                modifier       = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = android.graphics.RenderEffect
                            .createBlurEffect(48f, 48f, android.graphics.Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                        alpha = 0.14f
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

        // No mic-conflict dialog needed: when Speech Correction is active the
        // mic button is disabled in the UI, so the conflict can never be triggered.

        // ── SPLASH ────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = phase == KaraokePhase.SPLASH,
            enter    = fadeIn(tween(400)) + scaleIn(spring(Spring.DampingRatioLowBouncy), 0.82f),
            exit     = fadeOut(tween(280)) + scaleOut(targetScale = 1.12f),
            modifier = Modifier.fillMaxSize().zIndex(10f)
        ) {
            KaraokeSplash(
                trackTitle = track.title, 
                trackUri = track.thumbnailUri,
                onSkip = { phase = KaraokePhase.COUNTDOWN }
            )
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
            ) {
                KaraokeHeader(
                    title      = track.title,
                    isReconnecting = aiState.isReconnecting,
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
                                performanceMode          = performanceMode,
                                isWordSyncEnabled        = aiState.lyricsState.isKaraokeWordSyncEnabled
                            )
                    }
                }

                KaraokeBottomBar(
                    isPlaying               = isLogicalPlaying,
                    isRecording             = isAudioSavingEnabled || mediaRecorder != null,
                    isListening             = aiState.isListening,
                    isReconnecting          = aiState.isReconnecting,
                    score                   = aiState.karaokeScore,
                    correctWords            = aiState.karaokeCorrectWords,
                    totalWords              = aiState.karaokeTotalWords,
                    streak                  = aiState.karaokeStreak,
                    maxStreak               = aiState.karaokeMaxStreak,
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
                    onSkipNext              = { finishSession(true) },
                    onStop                  = { finishSession(false) },
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
                track            = track,
                score            = aiState.karaokeScore,
                correctWords     = aiState.karaokeCorrectWords,
                totalWords       = aiState.karaokeTotalWords,
                correctLines     = aiState.karaokeCorrectLines,
                totalLines       = aiState.karaokeTotalLines,
                maxStreak        = aiState.karaokeMaxStreak,
                mostAccurateLine = aiState.karaokeMostAccurateLine,
                recordingFile    = recordingFile,
                onDone           = {
                    recordingFile?.let { file ->
                        val metaFile = File(file.absolutePath.replace(".m4a", ".json"))
                        metaFile.delete()
                        file.delete()
                    }
                    recordingFile = null
                    phase = KaraokePhase.IDLE
                    if (isSkipRequested || isNaturalFinish) {
                        onNextSongConfirmed()
                    } else {
                        onStop()
                    }
                },
                onAudioSaved    = {
                    recordingFile?.let { file ->
                        if (!file.exists() || file.length() == 0L) {
                            Log.e("Karaoke", "Recording file is empty or missing")
                            Toast.makeText(context, rfefMsg, Toast.LENGTH_SHORT).show()
                            return@let
                        }

                        Log.d("Karaoke", "Saving recording: ${file.absolutePath}, size: ${file.length()}")

                        saveKaraokeRecording(
                            context      = context,
                            file         = file,
                            displayName  = file.name,
                            trackTitle   = track.title,
                            thumbnailUrl = track.thumbnailUri ?: "",
                            onDone       = { success ->
                                (context as? android.app.Activity)?.runOnUiThread {
                                    if (success) {
                                        Toast.makeText(context, stmkMsg, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, ftsrMsg, Toast.LENGTH_SHORT).show()
                                    }
                                    // Clean up temp files
                                    runCatching { File(file.absolutePath.replace(".m4a", ".json")).delete() }
                                    runCatching { file.delete() }
                                }
                            }
                        )
                    }
                    recordingFile = null
                    phase = KaraokePhase.IDLE
                    if (isSkipRequested || isNaturalFinish) {
                        onNextSongConfirmed()
                    } else {
                        onStop()
                    }
                }
            )
        }
    }

    // Settings bottom-sheet
    if (showSettings) {
        KaraokeSettingsModal(
            speechCorrectionEnabled = aiState.karaokeSpeechCorrectionEnabled,
            onSpeechCorrectionToggle = { enabled ->
                if (enabled) {
                    isAudioSavingEnabled = false
                    if (isMediaRecorderStarted && mediaRecorder != null) {
                        stopMediaRecording(scope, mediaRecorder, isMediaRecorderStarted) {
                            mediaRecorder = null
                            isMediaRecorderStarted = false
                            recordingFile = null
                        }
                    }
                }
                if (phase == KaraokePhase.ACTIVE) {
                    pendingSpeechCorrectionToggle = enabled
                } else {
                    aiViewModel.setKaraokeSpeechCorrectionEnabled(enabled)
                }
            },
            quickSingEnabled         = aiState.quickSingEnabled,
            onQuickSingToggle        = { aiViewModel.setQuickSingEnabled(it) },
            autoRecordEnabled        = aiState.autoRecordEnabled,
            onAutoRecordToggle       = { aiViewModel.setAutoRecordEnabled(it) },
            singConfidentlyMode      = aiState.singConfidentlyMode,
            onSingConfidentlyModeChange = { mode ->
                if (phase == KaraokePhase.ACTIVE) {
                    pendingSingConfidentlyMode = mode
                } else {
                    aiViewModel.setSingConfidentlyMode(mode)
                }
            },
            wordSyncEnabled          = aiState.lyricsState.isKaraokeWordSyncEnabled,
            onWordSyncToggle         = { aiViewModel.toggleKaraokeWordSyncEnabled() },
            onDismiss                = { showSettings = false }
        )
    }

    if (pendingSpeechCorrectionToggle != null || pendingSingConfidentlyMode != null) {
        val isSpeechToggle = pendingSpeechCorrectionToggle != null
        AlertDialog(
            onDismissRequest = { 
                pendingSpeechCorrectionToggle = null
                pendingSingConfidentlyMode = null
            },
            icon = { Icon(if (isSpeechToggle) Icons.Rounded.RecordVoiceOver else Icons.Rounded.Mic, null) },
            title = { Text(stringResource(R.string.st_KaraokeScreen_rr5)) },
            text = { Text(stringResource(R.string.st_KaraokeScreen_rr_desc6)) },
            confirmButton = {
                TextButton(onClick = {
                    val newSpeechState = pendingSpeechCorrectionToggle
                    val newModeState = pendingSingConfidentlyMode
                    pendingSpeechCorrectionToggle = null
                    pendingSingConfidentlyMode = null
                    
                    // Stop any ongoing recording WITHOUT saving
                    if (isMediaRecorderStarted && mediaRecorder != null) {
                        stopMediaRecording(scope, mediaRecorder, isMediaRecorderStarted) {
                            mediaRecorder = null
                            isMediaRecorderStarted = false
                            runCatching { recordingFile?.delete() }
                            recordingFile = null
                        }
                    }
                    aiViewModel.stopKaraokeRecording()
                    
                    newSpeechState?.let { aiViewModel.setKaraokeSpeechCorrectionEnabled(it) }
                    newModeState?.let { aiViewModel.setSingConfidentlyMode(it) }
                    
                    onSeek(0)
                    phase = KaraokePhase.SPLASH
                    hasStartedOnce = false
                    wasSingConfidentlyHandled = false
                    countdownTick = 3
                    minSplashTimeElapsed = false
                    showSettings = false
                }) {
                    Text(stringResource(R.string.st_KaraokeScreen_rs7))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    pendingSpeechCorrectionToggle = null
                    pendingSingConfidentlyMode = null
                }) {
                    Text(stringResource(R.string.st_KaraokeScreen_c8))
                }
            }
        )
    }

    // Sing Confidently Dialog

    // Sync Sing Confidently Active with Main Player Muting
    LaunchedEffect(aiState.isSingConfidentlyActive) {
        if (aiState.isSingConfidentlyActive) {
            aiViewModel.setInstrumentalPlayerVolume(1f)
            onSetMutedByAi(true)
        } else {
            aiViewModel.setInstrumentalPlayerVolume(0f)
            onSetMutedByAi(false)
        }
    }

    if (showSingConfidentlyDialog && aiState.instrumentalMatch != null) {
        SingConfidentlyBottomSheet(
            match = aiState.instrumentalMatch,
            isResolving = aiState.instrumentalStreamUrl == null,
            onSwitch = {
                showSingConfidentlyDialog = false
                wasSingConfidentlyHandled = true
                aiViewModel.toggleSingConfidentlyActive(true) { onSeek(it) }
            },
            onKeep = { 
                showSingConfidentlyDialog = false 
                wasSingConfidentlyHandled = true
            }
        )
    }

    if (showManualPickSheet) {
        // Auto-trigger initial search as soon as the sheet opens so results are populated
        LaunchedEffect(Unit) {
            val initialQuery = "${track.title} ${track.artist ?: ""} instrumental karaoke"
            aiViewModel.searchInstrumentalCustom(initialQuery)
        }
        ManualPickSheet(
            track = track,
            initialTopResults = aiState.instrumentalTopResults,
            searchResults = aiState.instrumentalSearchResults,
            isSearching = aiState.isSearchingInstrumental,
            onSearch = { query -> aiViewModel.searchInstrumentalCustom(query) },
            onLoadMore = { aiViewModel.loadMoreInstrumentalSearch() },
            onPick = { pickedTrack ->
                showManualPickSheet = false
                wasSingConfidentlyHandled = true
                aiViewModel.setInstrumentalMatch(pickedTrack)
                aiViewModel.toggleSingConfidentlyActive(true) { onSeek(it) }
            },
            onDismiss = { 
                showManualPickSheet = false
                wasSingConfidentlyHandled = true
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Splash
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun KaraokeSplash(trackTitle: String, trackUri: String?, onSkip: () -> Unit) {
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
                stringResource(R.string.st_KaraokeScreen_k31),
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

            Spacer(Modifier.height(8.dp))
            
            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Text(stringResource(R.string.st_KaraokeScreen_rtts32), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
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
                stringResource(R.string.st_KaraokeScreen_gr33),
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
    isReconnecting: Boolean,
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
            AnimatedContent(targetState = isReconnecting, label = "karaokeHeaderStatus") { reconnecting ->
                if (reconnecting) {
                    Text(
                        stringResource(R.string.st_KaraokeScreen_rm34),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        stringResource(R.string.st_KaraokeScreen_km35),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }
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
    performanceMode        : Boolean,
    isWordSyncEnabled      : Boolean = true
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
                    isWordSyncEnabled       = isWordSyncEnabled,
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
    isWordSyncEnabled      : Boolean = true,
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
        BreathingDotsIndicator(
            isActive = isCurrent,
            modifier = modifier
                .fillMaxWidth()
                .scale(scale)
                .alpha(alpha)
                .padding(vertical = 10.dp)
        )
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
        if (line.words.isNotEmpty() && isWordSyncEnabled) {
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
            val isLineCorrect = line.words.any { it.karaokeStatus == KaraokeWordStatus.CORRECT }
            val isLineMissed  = speechCorrectionEnabled && line.words.any { it.karaokeStatus == KaraokeWordStatus.MISSED }

            val textColor = when {
                isLineCorrect -> Color(0xFFFFD700) // Gold
                isLineMissed  -> Color(0xFFFF5252).copy(alpha = 0.7f) // Subdued Red
                isCurrent     -> MaterialTheme.colorScheme.primary
                else          -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }

            Text(
                text       = line.content,
                style      = if (isCurrent) MaterialTheme.typography.headlineMedium
                else           MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent || isLineCorrect) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
                color      = textColor
            )
        }
    }
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
        targetValue = if (isCorrect) 1.08f else 1f,
        animationSpec = if (isCorrect) spring(Spring.DampingRatioMediumBouncy) else snap()
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

    val wordScale = (1f + (0.05f * animFrac)) * pingScale

    val glowAlpha by animateFloatAsState(
        targetValue = if ((isActive || isCorrect) && !performanceMode) 0.4f else 0f,
        animationSpec = tween(400),
        label = "glowAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        if (glowAlpha > 0f) {
            Text(
                text       = word.word,
                style      = if (isCurrent) MaterialTheme.typography.headlineMedium
                else           MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Black,
                color      = color.copy(alpha = glowAlpha),
                modifier   = Modifier
                    .graphicsLayer {
                        scaleX = wordScale * 1.15f
                        scaleY = wordScale * 1.15f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    }
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                textAlign  = TextAlign.Center
            )
        }

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
                stringResource(R.string.st_KaraokeScreen_ll36),
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
    isListening            : Boolean = true,
    isReconnecting         : Boolean = false,
    score                  : Int,
    correctWords           : Int,
    totalWords             : Int,
    streak                 : Int,
    maxStreak              : Int = 0,
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
        AnimatedVisibility(
            visible = !speechCorrectionEnabled,
            enter   = fadeIn(tween(300)) + expandVertically(),
            exit    = fadeOut(tween(200)) + shrinkVertically()
        ) {
            RecordingStatusPill(
                isRecording = isRecording,
                isPaused = !isPlaying
            )
        }

        // Live score chip – only shown once we have words to evaluate
        AnimatedVisibility(
            visible = speechCorrectionEnabled && totalWords > 0,
            enter   = fadeIn(tween(300)) + expandVertically(),
            exit    = fadeOut(tween(200)) + shrinkVertically()
        ) {
            LiveScoreChip(
                score    = score,
                correct  = correctWords,
                total    = totalWords,
                streak   = streak,
                maxStreak = maxStreak
            )
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mic button: disabled (display-only listening indicator) when
            // Speech Correction owns the mic; interactive recording toggle otherwise.
            MicToggleButton(
                isRecording             = isRecording,
                isListening             = isListening,
                isReconnecting          = isReconnecting,
                speechCorrectionEnabled = speechCorrectionEnabled,
                micRms                  = micRms,
                onClick                 = onToggleRecording
            )

            // Play/Pause Master
            PlayPauseMasterButton(
                isPlaying       = isPlaying,
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

@Composable
private fun RecordingStatusPill(
    isRecording: Boolean,
    isPaused: Boolean
) {
    val pillColor = when {
        isRecording && !isPaused -> Color(0xFF4CAF50) // Green REC
        isPaused && isRecording -> Color(0xFFFFA000) // Amber PAUSED
        else -> Color.Gray // NOT RECORDING
    }
    
    val text = when {
        isRecording && !isPaused -> stringResource(R.string.st_KaraokeScreen_rec37)
        isPaused && isRecording -> stringResource(R.string.st_KaraokeScreen_p38)
        else -> stringResource(R.string.st_KaraokeScreen_nr39)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = pillColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, pillColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = pillColor
            )
        }
    }
}

// ── Live score chip ───────────────────────────────────────────────────────────

@Composable
private fun LiveScoreChip(
    score    : Int,
    correct  : Int,
    total    : Int,
    streak   : Int,
    maxStreak: Int = 0
) {
    val tint = when {
        score >= 80 -> Color(0xFF43A047)
        score >= 50 -> Color(0xFFFB8C00)
        else        -> Color(0xFFE53935)
    }

    // Live grade tier shown inside the chip
    val liveTier = when {
        score >= 95 -> "S"
        score >= 85 -> "A"
        score >= 70 -> "B"
        score >= 50 -> "C"
        else        -> "D"
    }

    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(600),
        label = "animatedScore"
    )

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.5.dp, tint.copy(alpha = 0.25f))
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live tier badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = tint.copy(alpha = 0.18f)
            ) {
                Text(
                    liveTier,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color      = tint,
                    modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Icon(Icons.Rounded.Stars, null,
                modifier = Modifier.size(16.dp), tint = tint)
            
            Text(
                "$animatedScore%",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color      = tint
            )

            VerticalDivider(modifier = Modifier.height(14.dp).alpha(0.15f), color = tint)

            Text(
                "$correct/$total",
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = tint.copy(alpha = 0.8f)
            )

            if (streak > 2) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color(0xFFFF7043).copy(alpha = 0.1f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Rounded.Whatshot, null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF7043))
                    Text(
                        "🔥$streak",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF7043)
                    )
                    // Show max streak if meaningfully larger
                    if (maxStreak > streak + 2) {
                        Text(
                            "/ $maxStreak",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF7043).copy(alpha = 0.55f)
                        )
                    }
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
    isRecording            : Boolean,
    isListening            : Boolean = true,
    isReconnecting         : Boolean = false,
    speechCorrectionEnabled: Boolean = false,
    micRms                 : Float,
    onClick                : () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "micPulse")

    // When Speech Correction owns the mic, pulse with the RMS level to give
    // live feedback. For normal recording, the same pulse applies.
    val isActiveAudio = (isRecording && isListening) || (speechCorrectionEnabled && isListening)
    val targetScale = if (isActiveAudio) 1.15f + (micRms / 100f).coerceIn(0f, 0.15f) else 1f
    val pulse by inf.animateFloat(
        1f, targetScale,
        infiniteRepeatable(tween(if (isActiveAudio) 600 else 1000), RepeatMode.Reverse),
        label = "pulse"
    )

    val iconAlpha by animateFloatAsState(
        if (speechCorrectionEnabled || isListening || !isRecording || isReconnecting) 1f else 0.4f
    )

    // Speech Correction mode: display-only listening indicator using primary color.
    // Recording mode: interactive toggle using error (red) color.
    val containerColor = when {
        speechCorrectionEnabled -> MaterialTheme.colorScheme.primary.copy(
            alpha = if (isListening && !isReconnecting) 0.18f else 0.08f
        )
        isRecording -> MaterialTheme.colorScheme.error.copy(
            alpha = if (isListening && !isReconnecting) 0.18f else 0.08f
        )
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val borderColor = when {
        speechCorrectionEnabled -> MaterialTheme.colorScheme.primary.copy(
            alpha = if (isListening && !isReconnecting) 0.6f else 0.2f
        )
        isRecording -> MaterialTheme.colorScheme.error.copy(
            alpha = if (isListening && !isReconnecting) 0.6f else 0.2f
        )
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }
    val iconTint = when {
        speechCorrectionEnabled -> MaterialTheme.colorScheme.primary
        isRecording             -> MaterialTheme.colorScheme.error
        else                    -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        // Disabled when Speech Correction is active — it owns the mic exclusively.
        onClick  = { if (!speechCorrectionEnabled) onClick() },
        enabled  = !speechCorrectionEnabled,
        shape    = CircleShape,
        color    = containerColor,
        modifier = Modifier.size(56.dp).scale(pulse),
        border   = BorderStroke(1.5.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isReconnecting -> {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color       = iconTint
                    )
                }
                speechCorrectionEnabled -> {
                    // Show a speech-recognition icon to distinguish from plain
                    // recording and make it obvious the mic is in AI-listen mode.
                    Icon(
                        imageVector        = Icons.Rounded.RecordVoiceOver,
                        contentDescription = stringResource(R.string.st_KaraokeScreen_sca40),
                        tint               = iconTint,
                        modifier           = Modifier.size(24.dp).alpha(iconAlpha)
                    )
                }
                else -> {
                    Icon(
                        imageVector        = if (isRecording) Icons.Rounded.Mic else Icons.Rounded.MicNone,
                        contentDescription = null,
                        tint               = iconTint,
                        modifier           = Modifier.size(24.dp).alpha(iconAlpha)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseMasterButton(
    isPlaying: Boolean,
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
    correctLines     : Int = 0,
    totalLines       : Int = 0,
    maxStreak        : Int = 0,
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
    correctLines     = correctLines,
    totalLines       = totalLines,
    maxStreak        = maxStreak,
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
    correctLines     : Int = 0,
    totalLines       : Int = 0,
    maxStreak        : Int = 0,
    mostAccurateLine : String?,
    recordingFile    : File?,
    onDone           : () -> Unit,
    onAudioSaved     : () -> Unit
) {
    val gSMsg = stringResource(R.string.st_KaraokeScreen_gS42)
    val gAMsg = stringResource(R.string.st_KaraokeScreen_gA43)
    val gBMsg = stringResource(R.string.st_KaraokeScreen_gB44)
    val gCMsg = stringResource(R.string.st_KaraokeScreen_gC45)
    val gDMsg = stringResource(R.string.st_KaraokeScreen_gD46)

    val (grade, gradeColor, message) = remember(score) {
        when {
            score >= 95 -> Triple("S", Color(0xFFFFD700), gSMsg)
            score >= 85 -> Triple("A", Color(0xFF42A5F5), gAMsg)
            score >= 70 -> Triple("B", Color(0xFF66BB6A), gBMsg)
            score >= 50 -> Triple("C", Color(0xFFFFA726), gCMsg)
            else        -> Triple("D", Color(0xFFEF5350), gDMsg)
        }
    }

    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "animatedScore"
    )

    val showContent = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showContent.value = true }

    val accuracyRate = remember(correctWords, totalWords) {
        if (totalWords > 0) correctWords.toFloat() / totalWords else 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        gradeColor.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .clickable(enabled = false) {}
    ) {
        if (score >= 85) {
            ConfettiEffect(
                color = gradeColor,
                particleCount = if (score >= 95) 45 else 25
            )
        }

        AnimatedVisibility(
            visible = showContent.value,
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 4 },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.st_KaraokeScreen_pr41),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(32.dp))

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.size(280.dp).background(
                            Brush.radialGradient(listOf(gradeColor.copy(alpha = 0.35f), Color.Transparent)), CircleShape
                        )
                    )
                    Surface(
                        modifier = Modifier.size(220.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, gradeColor.copy(alpha = 0.2f)),
                        tonalElevation = 4.dp
                    ) {}
                    CircularProgressIndicator(
                        progress = { animatedScore / 100f },
                        modifier = Modifier.size(220.dp),
                        strokeWidth = 14.dp,
                        color = gradeColor,
                        trackColor = gradeColor.copy(alpha = 0.1f),
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            grade,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            fontSize = 110.sp,
                            color = gradeColor,
                            modifier = Modifier.graphicsLayer {
                                val s = 0.9f + (animatedScore / 100f) * 0.15f
                                scaleX = s; scaleY = s
                            }
                        )
                        Text(
                            "$animatedScore%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    message,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = gradeColor,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            EvalStat(stringResource(R.string.st_KaraokeScreen_c47), "$correctWords", Icons.Rounded.DoneAll, gradeColor)
                            EvalStat(stringResource(R.string.st_KaraokeScreen_a48), "$score%", Icons.Rounded.TrackChanges, gradeColor)
                            EvalStat(stringResource(R.string.st_KaraokeScreen_t49),   "$totalWords", Icons.Rounded.List,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }

                        if (totalLines > 0 || maxStreak > 0) {
                            HorizontalDivider(modifier = Modifier.alpha(0.08f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                if (totalLines > 0) {
                                    EvalStat(
                                        stringResource(R.string.st_KaraokeScreen_l50),
                                        "$correctLines/$totalLines",
                                        Icons.Rounded.Lyrics,
                                        gradeColor.copy(alpha = 0.85f)
                                    )
                                }
                                if (maxStreak > 0) {
                                    EvalStat(
                                        stringResource(R.string.st_KaraokeScreen_bs51),
                                        "$maxStreak",
                                        Icons.Rounded.Whatshot,
                                        Color(0xFFFF7043)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.alpha(0.08f))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.st_KaraokeScreen_acc52),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "${(accuracyRate * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = gradeColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = { accuracyRate },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = gradeColor,
                                trackColor = gradeColor.copy(alpha = 0.1f),
                            )
                        }

                        if (mostAccurateLine != null) {
                            HorizontalDivider(modifier = Modifier.alpha(0.08f))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = gradeColor, modifier = Modifier.size(16.dp))
                                    Text(
                                        stringResource(R.string.st_KaraokeScreen_pl53),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = gradeColor.copy(alpha = 0.7f),
                                        letterSpacing = 1.2.sp
                                    )
                                }
                                Text(
                                    "\"$mostAccurateLine\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (recordingFile != null) {
                        Button(
                            onClick  = onAudioSaved,
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape    = RoundedCornerShape(24.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Rounded.Save, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.st_KaraokeScreen_s54),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Button(
                        onClick  = onDone,
                        modifier = Modifier.weight(if (recordingFile != null) 1.2f else 1f).height(64.dp),
                        shape    = RoundedCornerShape(24.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = gradeColor),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.st_KaraokeScreen_cont55),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            fontSize      = 18.sp,
                            letterSpacing = 2.sp,
                            color         = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String) = EvalStat(label, value, Icons.Rounded.Star,
    MaterialTheme.colorScheme.onSurface)

@Composable
private fun EvalStat(label: String, value: String, icon: ImageVector, valueColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = valueColor.copy(alpha = 0.6f))
        Text(value,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color      = valueColor)
        Text(label,
            style       = MaterialTheme.typography.labelSmall,
            fontWeight  = FontWeight.Bold,
            color       = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            letterSpacing = 0.5.sp)
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val delayMs: Long
)

@Composable
private fun ConfettiEffect(color: Color, particleCount: Int = 30) {
    val particles = remember {
        List(particleCount) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = -Random.nextFloat() * 0.2f,
                vx = (Random.nextFloat() - 0.5f) * 0.6f,
                vy = Random.nextFloat() * 0.5f + 0.3f,
                size = Random.nextFloat() * 8f + 4f,
                delayMs = (Random.nextLong() % 600).coerceAtLeast(0)
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(3000, easing = LinearEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val elapsed = t * 3000f

        particles.forEach { p ->
            val dt = (elapsed - p.delayMs).coerceAtLeast(0f) / 3000f
            if (dt <= 0f) return@forEach
            val alpha = (1f - dt).coerceIn(0f, 1f)
            val px = (p.x + p.vx * dt * 5f) * size.width
            val py = (p.y + p.vy * dt * 5f + 0.5f * 1.5f * dt * dt) * size.height

            drawCircle(
                color = color.copy(alpha = alpha * 0.8f),
                radius = p.size,
                center = Offset(px, py)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings modal  (unchanged from original – kept intact)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingConfidentlyBottomSheet(
    match: com.frerox.toolz.data.catalog.CatalogTrack,
    isResolving: Boolean,
    onSwitch: () -> Unit,
    onKeep: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onKeep,
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
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
            modifier = Modifier.padding(28.dp).padding(bottom = 24.dp),
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
                stringResource(R.string.st_KaraokeScreen_sc56),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                stringResource(R.string.st_KaraokeScreen_sc_desc57),
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
                    AlbumArtImage(
                        url = match.thumbnailUrl,
                        seed = match.title,
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
                    if (isResolving) {
                        com.frerox.toolz.ui.components.ExpressiveLoadingWheel(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.height(40.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    String.format(java.util.Locale.US, "%d:%02d", match.duration / 60000, (match.duration % 60000) / 1000),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton(
                    onClick = onKeep,
                    modifier = Modifier.weight(1f).height(54.dp),
                ) {
                    Text(stringResource(R.string.st_KaraokeScreen_ko58), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.frerox.toolz.ui.components.ToolzExpressiveButton(
                    onClick = onSwitch,
                    enabled = !isResolving,
                    modifier = Modifier.weight(1f).height(54.dp)
                ) {
                    Text(stringResource(R.string.st_KaraokeScreen_s59), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPickSheet(
    track: com.frerox.toolz.data.music.MusicTrack,
    initialTopResults: List<com.frerox.toolz.data.catalog.CatalogTrack>,
    searchResults: List<com.frerox.toolz.data.catalog.CatalogTrack>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPick: (com.frerox.toolz.data.catalog.CatalogTrack) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("${track.title} ${track.artist ?: ""} instrumental karaoke") }
    // Show search results if available, fall back to the pre-fetched top results
    val displayList = searchResults.ifEmpty { initialTopResults }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(bottom = 16.dp),
        ) {
            androidx.compose.material3.SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = { if (query.isNotBlank()) onSearch(query) },
                active = false,
                onActiveChange = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.st_KaraokeScreen_si_hint9)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { 
                    if (isSearching) {
                        com.frerox.toolz.ui.components.ExpressiveLoadingWheel(modifier = Modifier.size(24.dp))
                    }
                }
            ) {}

            Spacer(Modifier.height(16.dp))

            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayList) { item ->
                    Surface(
                        onClick = { onPick(item) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AlbumArtImage(
                                url = item.thumbnailUrl,
                                seed = item.title,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.st_KaraokeScreen_u10),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
                
                if (searchResults.isNotEmpty() && searchResults.size < 50) {
                    item {
                        com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            enabled = !isSearching
                        ) {
                            Text(stringResource(R.string.st_KaraokeScreen_lm11))
                        }
                    }
                }
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
    autoRecordEnabled       : Boolean,
    onAutoRecordToggle      : (Boolean) -> Unit,
    singConfidentlyMode     : com.frerox.toolz.ui.screens.media.ai.SingConfidentlyMode,
    onSingConfidentlyModeChange : (com.frerox.toolz.ui.screens.media.ai.SingConfidentlyMode) -> Unit,
    wordSyncEnabled         : Boolean,
    onWordSyncToggle        : (Boolean) -> Unit,
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
                    Text(stringResource(R.string.st_KaraokeScreen_ks12),
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.st_KaraokeScreen_cye13),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            }

            HorizontalDivider(modifier = Modifier.alpha(0.08f))

            SettingsToggleRow(
                icon           = Icons.Rounded.RecordVoiceOver,
                title          = stringResource(R.string.st_KaraokeScreen_sc14),
                subtitle       = if (speechCorrectionEnabled)
                    stringResource(R.string.st_KaraokeScreen_seaos15)
                else
                    stringResource(R.string.st_KaraokeScreen_fsns16),
                checked        = speechCorrectionEnabled,
                onCheckedChange = onSpeechCorrectionToggle
            )

            SettingsToggleRow(
                icon           = Icons.Rounded.Mic,
                title          = stringResource(R.string.st_KaraokeScreen_ar17),
                subtitle       = stringResource(R.string.st_KaraokeScreen_ar_desc18),
                checked        = autoRecordEnabled,
                onCheckedChange = onAutoRecordToggle
            )

            SettingsToggleRow(
                icon           = Icons.Rounded.FastForward,
                title          = stringResource(R.string.st_KaraokeScreen_qs19),
                subtitle       = stringResource(R.string.st_KaraokeScreen_qs_desc20),
                checked        = quickSingEnabled,
                onCheckedChange = onQuickSingToggle
            )

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(stringResource(R.string.st_KaraokeScreen_sc21), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.st_KaraokeScreen_im22), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    val modes = com.frerox.toolz.ui.screens.media.ai.SingConfidentlyMode.values()
                    val labels = listOf(
                        stringResource(R.string.st_KaraokeScreen_off23),
                        stringResource(R.string.st_KaraokeScreen_auto24),
                        stringResource(R.string.st_KaraokeScreen_autoplay25),
                        stringResource(R.string.st_KaraokeScreen_manual26)
                    )
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            onClick = { onSingConfidentlyModeChange(mode) },
                            selected = mode == singConfidentlyMode,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(labels[index], style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            SettingsToggleRow(
                icon           = Icons.Rounded.Spellcheck,
                title          = stringResource(R.string.st_KaraokeScreen_sw27),
                subtitle       = stringResource(R.string.st_KaraokeScreen_sw_desc28),
                checked        = wordSyncEnabled,
                onCheckedChange = onWordSyncToggle
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Icon(Icons.Rounded.Info, null,
                    modifier = Modifier.size(14.dp).alpha(0.35f),
                    tint     = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (speechCorrectionEnabled)
                        stringResource(R.string.st_KaraokeScreen_sc_info29)
                    else
                        stringResource(R.string.st_KaraokeScreen_ptr_info30),
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
    scope              : kotlinx.coroutines.CoroutineScope,
    context            : android.content.Context,
    trackTitle         : String,
    trackArtist        : String,
    thumbnailUrl       : String,
    preferMicSource    : Boolean = false,
    onRecorderReady    : (MediaRecorder, File) -> Unit,
    onError            : () -> Unit
) {
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val folder    = context.getExternalFilesDir(null) ?: return@launch

        // Garbage collection: clear any orphaned files older than 1 hour to prevent storage leaks
        try {
            val oneHourAgo = System.currentTimeMillis() - 3600_000L
            folder.listFiles()?.forEach { f ->
                if ((f.extension == "m4a" || f.extension == "json") && f.lastModified() < oneHourAgo) {
                    f.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("KaraokeRecorder", "Failed to garbage collect old recordings", e)
        }

        val safeTitle = trackTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(50)
        val count     = folder.listFiles { f ->
            f.name.startsWith("Karaoke - $safeTitle") && f.extension == "m4a"
        }?.size ?: 0

        val baseName = "Karaoke - $safeTitle${if (count > 0) " (${count + 1})" else ""}"
        val file = File(folder, "$baseName.m4a")
        val metaFile = File(folder, "$baseName.json")

        // Save metadata sidecar (used by evaluation screen for display)
        try {
            val metaJson = """
                {
                    "title": "$trackTitle",
                    "artist": "Toolz Karaoke",
                    "thumbnailUrl": "$thumbnailUrl",
                    "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent()
            metaFile.writeText(metaJson)
        } catch (e: Exception) {
            android.util.Log.e("KaraokeRecorder", "Failed to save metadata", e)
        }

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            val audioSource = if (preferMicSource) {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            } else {
                MediaRecorder.AudioSource.MIC
            }
            recorder.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onRecorderReady(recorder, file)
            }
        } catch (e: Exception) {
            android.util.Log.e("KaraokeRecorder", "Failed to start recording", e)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onError()
            }
        }
    }
}

private fun stopMediaRecording(
    scope     : kotlinx.coroutines.CoroutineScope,
    recorder  : MediaRecorder?,
    wasStarted: Boolean = true,
    onDone    : () -> Unit
) {
    if (recorder == null) {
        onDone()
        return
    }
    if (!wasStarted) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { recorder.release() } catch (_: Exception) {}
            withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        }
        return
    }
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            recorder.stop()
            Log.d("Karaoke", "MediaRecorder stopped")
        } catch (e: Exception) {
            Log.e("Karaoke", "Failed to stop MediaRecorder", e)
        } finally {
            try {
                recorder.release()
                Log.d("Karaoke", "MediaRecorder released")
            } catch (e: Exception) {
                Log.e("Karaoke", "Failed to release MediaRecorder", e)
            }
        }
        withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
    }
}

@Composable
fun BreathingDotsIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    dotCount: Int = 3,
    dotSize: Dp = 10.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { i ->
            val delayMs = i * 220
            val inf = rememberInfiniteTransition(label = "dots$i")
            val dy by if (isActive) {
                inf.animateFloat(
                    0f, -12f,
                    infiniteRepeatable(
                        tween(550, delayMillis = delayMs, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse
                    ),
                    label = "dy$i"
                )
            } else {
                remember { mutableFloatStateOf(0f) }
            }
            Surface(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer { translationY = dy }
                    .alpha(if (isActive) 1f else 0.28f),
                shape = CircleShape,
                color = if (isActive) activeColor else inactiveColor
            ) {}
            if (i < dotCount - 1) Spacer(Modifier.width(14.dp))
        }
    }
}
