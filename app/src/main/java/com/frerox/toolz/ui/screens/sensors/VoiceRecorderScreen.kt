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

package com.frerox.toolz.ui.screens.sensors

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.sin
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceRecorderScreen(
    onBack: () -> Unit,
    viewModel: VoiceRecorderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    val context = LocalContext.current
    var showSettingsSheet by remember { mutableStateOf(false) }

    val title = stringResource(R.string.st_VoiceRecorderScreen_v1r2)
    val backDesc = stringResource(R.string.st_VoiceRecorderScreen_b3a4)
    val settingsDesc = stringResource(R.string.st_VoiceRecorderScreen_s5e6)

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backDesc)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            showSettingsSheet = true
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = settingsDesc)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
                titleHorizontalAlignment = Alignment.Start
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            RecorderFloatingToolbar(
                uiState = uiState,
                onRecord = {
                    vibrationManager?.vibrateClick()
                    if (uiState.isRecording) viewModel.stopRecording()
                    else viewModel.startRecording()
                },
                onPauseResume = {
                    vibrationManager?.vibrateClick()
                    if (uiState.isPaused) viewModel.resumeRecording()
                    else viewModel.pauseRecording()
                },
                onMark = {
                    vibrationManager?.vibrateTick()
                    viewModel.addMark()
                },
                onOpenFolder = {
                    vibrationManager?.vibrateClick()
                    try {
                        val uri = uiState.customOutputPath?.toUri()
                            ?: context.getExternalFilesDir("recordings")?.toUri()
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = uri
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "audio/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            e2.printStackTrace()
                        }
                    }
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ─── Recording status card ───────────────────────────────────
                RecordingStatusCard(
                    uiState = uiState,
                    performanceMode = performanceMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp)
                )

                Spacer(Modifier.height(8.dp))

                // ─── Recordings list ─────────────────────────────────────────
                RecordingsSection(
                    uiState = uiState,
                    performanceMode = performanceMode,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    onTogglePlay = { file ->
                        vibrationManager?.vibrateClick()
                        viewModel.togglePlayback(file)
                    },
                    onDelete = { file ->
                        vibrationManager?.vibrateClick()
                        viewModel.deleteRecording(file)
                    },
                    onRename = { file, name ->
                        vibrationManager?.vibrateSuccess()
                        viewModel.renameRecording(file, name)
                    }
                )
            }
        }

        if (showSettingsSheet) {
            VoiceRecorderSettingsSheet(
                state = uiState,
                onDismiss = { showSettingsSheet = false },
                onGainChange = { viewModel.setGainLevel(it) },
                onDeviceSelect = { viewModel.setSelectedDevice(it) },
                onBackgroundToggle = { viewModel.setBackgroundEnabled(it) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating Toolbar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecorderFloatingToolbar(
    uiState: RecordingState,
    onRecord: () -> Unit,
    onPauseResume: () -> Unit,
    onMark: () -> Unit,
    onOpenFolder: () -> Unit
) {
    val stopDesc = stringResource(R.string.st_VoiceRecorderScreen_s7t8)
    val recordDesc = stringResource(R.string.st_VoiceRecorderScreen_r9e0)
    val resumeLabel = stringResource(R.string.st_VoiceRecorderScreen_r1e2)
    val pauseLabel = stringResource(R.string.st_VoiceRecorderScreen_p3a4)
    val markLabel = stringResource(R.string.st_VoiceRecorderScreen_m5a6)
    val folderLabel = stringResource(R.string.st_VoiceRecorderScreen_f7o8)

    ToolzHorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier.padding(bottom = 16.dp),
        content = {
            // Primary record / stop button
            FilledIconButton(
                onClick = onRecord,
                modifier = Modifier.size(56.dp),
                shape = MediumExpressiveShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (uiState.isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (uiState.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (uiState.isRecording) stopDesc else recordDesc,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        trailingContent = {
            if (uiState.isRecording) {
                clickableItem(
                    onClick = onPauseResume,
                    icon = {
                        Icon(
                            imageVector = if (uiState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = null
                        )
                    },
                    label = if (uiState.isPaused) resumeLabel else pauseLabel
                )
                clickableItem(
                    onClick = onMark,
                    icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
                    label = markLabel
                )
            }
            clickableItem(
                onClick = onOpenFolder,
                icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                label = folderLabel
            )
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Recording Status Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordingStatusCard(
    uiState: RecordingState,
    performanceMode: Boolean,
    modifier: Modifier = Modifier
) {
    val recordingColor = if (uiState.isRecording)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary

    ElevatedCard(
        modifier = modifier,
        shape = LargeExpressiveShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Timer
            Text(
                text = formatDuration(uiState.durationMillis),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp
                ),
                color = if (uiState.isRecording)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            // Waveform visualization
            AnimatedContent(
                targetState = uiState.isRecording,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "WaveformSwitch"
            ) { isRecording ->
                if (isRecording) {
                    RecorderWaveform(
                        amplitude = uiState.maxAmplitude,
                        isPaused = uiState.isPaused,
                        color = recordingColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                } else {
                    IdleWaveform(
                        performanceMode = performanceMode,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // State chip
            AnimatedVisibility(
                visible = uiState.isRecording,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = if (uiState.isPaused) stringResource(R.string.st_VoiceRecorderScreen_p9a0) else stringResource(R.string.st_VoiceRecorderScreen_r1e3),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (uiState.isPaused)
                                Icons.Rounded.Pause
                            else
                                Icons.Rounded.FiberManualRecord,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = recordingColor
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = recordingColor.copy(alpha = 0.1f),
                        labelColor = recordingColor,
                        leadingIconContentColor = recordingColor
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = recordingColor.copy(alpha = 0.25f)
                    )
                )
            }

            // Mark chips
            AnimatedVisibility(
                visible = uiState.marks.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.marks.takeLast(5).forEach { mark ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = formatDuration(mark),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                        if (uiState.marks.size > 5) {
                            Text(
                                "+${uiState.marks.size - 5}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Waveforms
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecorderWaveform(
    amplitude: Int,
    isPaused: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 28
    val normAmplitude = (amplitude.toFloat() / 32768f).coerceIn(0f, 1f)
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPaused) 0f else normAmplitude,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "amp"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
    val phase by if (!isPaused) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 2f - 1f)
        val maxBarHeight = size.height

        for (i in 0 until barCount) {
            val sinVal = sin(phase + i * 0.45f)
            val barFrac = (0.08f + animatedAmplitude * 0.65f + sinVal * 0.15f * animatedAmplitude)
                .coerceIn(0.06f, 1f)
            val barHeight = maxBarHeight * barFrac
            val left = i * barWidth * 2f
            val top = (size.height - barHeight) / 2f

            drawRoundRect(
                color = color.copy(alpha = 0.3f + animatedAmplitude * 0.5f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Composable
private fun IdleWaveform(
    performanceMode: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (performanceMode) {
        // Static gentle bars in performance mode
        Canvas(modifier = modifier) {
            val barCount = 28
            val barWidth = size.width / (barCount * 2f - 1f)
            val heights = listOf(0.15f, 0.25f, 0.18f, 0.3f, 0.2f, 0.12f, 0.22f)
            for (i in 0 until barCount) {
                val frac = heights[i % heights.size]
                val barHeight = size.height * frac
                val left = i * barWidth * 2f
                val top = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = color.copy(alpha = 0.2f),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f)
                )
            }
        }
        return
    }

    val barCount = 28
    val infiniteTransition = rememberInfiniteTransition(label = "idleWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "idlePhase"
    )

    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 2f - 1f)
        for (i in 0 until barCount) {
            val sinVal = sin(phase.toDouble() + i * 0.4).toFloat()
            val frac = (0.08f + sinVal * 0.12f + 0.06f).coerceIn(0.04f, 0.35f)
            val barHeight = size.height * frac
            val left = i * barWidth * 2f
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                color = color.copy(alpha = 0.2f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recordings Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordingsSection(
    uiState: RecordingState,
    performanceMode: Boolean,
    modifier: Modifier = Modifier,
    onTogglePlay: (File) -> Unit,
    onDelete: (File) -> Unit,
    onRename: (File, String) -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.st_VoiceRecorderScreen_r3e4),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${uiState.recordings.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.recordings.isEmpty()) {
            EmptyRecordingsView()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (performanceMode) Modifier
                        else Modifier.fadingEdges(top = 8.dp, bottom = 100.dp)
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                itemsIndexed(
                    uiState.recordings,
                    key = { _, item -> item.file.absolutePath }
                ) { index, item ->
                    StaggeredEntrance(index = index) {
                        RecordingCard(
                            file = item.file,
                            isPlaying = uiState.playingFile == item.file && uiState.isPlaying,
                            playbackPosition = if (uiState.playingFile == item.file) uiState.playbackPosition else 0,
                            playbackDuration = if (uiState.playingFile == item.file) uiState.playbackDuration else 0,
                            marks = item.marks,
                            onTogglePlay = { onTogglePlay(item.file) },
                            onDelete = { onDelete(item.file) },
                            onRename = { onRename(item.file, it) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Recording Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordingCard(
    file: File,
    isPlaying: Boolean,
    playbackPosition: Int,
    playbackDuration: Int,
    marks: List<Long>,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember(file) { mutableStateOf(file.nameWithoutExtension) }

    ExpressiveCard(
        onClick = onTogglePlay,
        onLongClick = { showRenameDialog = true },
        shape = SquircleShape,
        containerColor = if (isPlaying)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isPlaying)
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Play/Stop icon
                FilledTonalIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(48.dp),
                    shape = SmallExpressiveShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isPlaying)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isPlaying)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                        },
                        label = "PlayIcon"
                    ) { playing ->
                        if (playing) {
                            Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.st_VoiceRecorderScreen_p3a4), modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.st_VoiceRecorderScreen_p5l6), modifier = Modifier.size(22.dp))
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.nameWithoutExtension,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
                            .format(Date(file.lastModified())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.st_VoiceRecorderScreen_d7e8),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Playback progress (only shown when this card is active)
            AnimatedVisibility(
                visible = playbackDuration > 0 && (isPlaying || playbackPosition > 0),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    PlaybackProgressWithMarks(
                        progress = if (playbackDuration > 0) playbackPosition.toFloat() / playbackDuration.toFloat() else 0f,
                        marks = marks,
                        playbackDuration = playbackDuration,
                        isPlaying = isPlaying
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(playbackPosition.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatDuration(playbackDuration.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.st_VoiceRecorderScreen_d9r0)) },
            text = { Text(stringResource(R.string.st_VoiceRecorderScreen_confirm_delete, file.nameWithoutExtension)) },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = { onDelete(); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.st_VoiceRecorderScreen_d7e8))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.st_VoiceRecorderScreen_c1a2)) }
            },
            shape = SquircleShape
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.st_VoiceRecorderScreen_r3e5)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.st_VoiceRecorderScreen_r5n6)) },
                    shape = SmallExpressiveShape,
                    singleLine = true
                )
            },
            confirmButton = {
                ToolzExpressiveButton(onClick = { onRename(newName); showRenameDialog = false }) {
                    Text(stringResource(R.string.st_VoiceRecorderScreen_s7a8))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.st_VoiceRecorderScreen_c1a2)) }
            },
            shape = SquircleShape
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playback Progress with Mark indicators
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaybackProgressWithMarks(
    progress: Float,
    marks: List<Long>,
    playbackDuration: Int,
    isPlaying: Boolean
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Progress bar
        ToolzWavyLinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        )

        // Mark notches drawn on top
        if (marks.isNotEmpty() && playbackDuration > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                marks.forEach { mark ->
                    val ratio = (mark.toFloat() / playbackDuration.toFloat()).coerceIn(0f, 1f)
                    val x = ratio * size.width
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(x, size.height * 0.1f),
                        end = Offset(x, size.height * 0.9f),
                        strokeWidth = with(density) { 2.dp.toPx() },
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyRecordingsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = LargeExpressiveShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MicNone,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                text = stringResource(R.string.st_VoiceRecorderScreen_n9o0),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.st_VoiceRecorderScreen_t1m2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderSettingsSheet(
    state: RecordingState,
    onDismiss: () -> Unit,
    onGainChange: (Float) -> Unit,
    onDeviceSelect: (String) -> Unit,
    onBackgroundToggle: (Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ExtraLargeExpressiveShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.st_VoiceRecorderScreen_s5e6),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Gain
            ElevatedCard(shape = SquircleShape) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.st_VoiceRecorderScreen_i3g4), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${String.format("%.1f", state.gainLevel)}×",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    ExpressiveSlider(
                        value = state.gainLevel,
                        onValueChange = onGainChange,
                        valueRange = 0.5f..3.0f,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Background recording
            ElevatedCard(shape = SquircleShape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.st_VoiceRecorderScreen_b5r6),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            stringResource(R.string.st_VoiceRecorderScreen_k7b8),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    ExpressiveSwitch(
                        checked = state.isBackgroundEnabled,
                        onCheckedChange = onBackgroundToggle
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
