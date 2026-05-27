package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VoiceRecorderScreen(
    onBack: () -> Unit,
    viewModel: VoiceRecorderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    var showSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "RECORDER",
                subtitle = "Precision Audio Capture",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Settings", Icons.Rounded.Tune, { showSettingsSheet = true }),
                            Triple("Audio Source", Icons.Rounded.Mic, { vibrationManager?.vibrateClick() })
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ToolzHorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(bottom = 16.dp),
                content = {
                    FilledIconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            if (uiState.isRecording) viewModel.stopRecording()
                            else viewModel.startRecording()
                        },
                        modifier = Modifier.size(56.dp),
                        shape = SmallExpressiveShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (uiState.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic, 
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                trailingContent = {
                    if (uiState.isRecording) {
                        clickableItem(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                if (uiState.isPaused) viewModel.resumeRecording()
                                else viewModel.pauseRecording()
                            },
                            icon = { Icon(if (uiState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null) },
                            label = if (uiState.isPaused) "RESUME" else "PAUSE"
                        )
                    }
                    clickableItem(
                        onClick = { vibrationManager?.vibrateClick() },
                        icon = { Icon(Icons.Rounded.Folder, null) },
                        label = "ARCHIVE"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Energetic Recording Visualization Area in Squircle Container
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 40.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
                            // Dynamic background glow responding to state
                            if (!performanceMode) {
                                val infiniteTransition = rememberInfiniteTransition(label = "GlowPulse")
                                val glowScale by infiniteTransition.animateFloat(
                                    initialValue = 0.9f,
                                    targetValue = 1.1f,
                                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                                    label = "Scale"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(glowScale)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    (if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f),
                                                    Color.Transparent
                                                )
                                            ),
                                            CircleShape
                                        )
                                )
                            }

                            // Waveform / Amplitude visualization
                            if (uiState.isRecording) {
                                WaveformAnimationExpressive(amplitude = uiState.maxAmplitude)
                            } else {
                                RecordingRippleAnimationExpressive(performanceMode)
                            }
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formatDuration(uiState.durationMillis),
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 72.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = (-4).sp
                                    ),
                                    color = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                
                                AnimatedVisibility(
                                    visible = uiState.isRecording,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    Surface(
                                        color = if (uiState.isPaused) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                        shape = BouncyShape,
                                        modifier = Modifier.padding(top = 16.dp)
                                    ) {
                                        Text(
                                            if (uiState.isPaused) "SESSION PAUSED" else "LIVE CAPTURE",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = if (uiState.isPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Audio Archive List with Staggered Entrance
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = SmallExpressiveShape
                        ) {
                            Text(
                                "AUDIO ARCHIVE",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 2.sp
                            )
                        }
                        Text(
                            "${uiState.recordings.size} CAPTURES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    
                    if (uiState.recordings.isEmpty()) {
                        EmptyArchiveView()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 100.dp)),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(uiState.recordings, key = { it.absolutePath }) { recording ->
                                RecordingCardExpressive(
                                    file = recording,
                                    isPlaying = uiState.playingFile == recording && uiState.isPlaying,
                                    playbackPosition = if (uiState.playingFile == recording) uiState.playbackPosition else 0,
                                    playbackDuration = if (uiState.playingFile == recording) uiState.playbackDuration else 0,
                                    onTogglePlay = { 
                                        vibrationManager?.vibrateClick()
                                        viewModel.togglePlayback(recording) 
                                    },
                                    onDelete = { 
                                        vibrationManager?.vibrateClick()
                                        viewModel.deleteRecording(recording) 
                                    },
                                    onRename = { 
                                        vibrationManager?.vibrateSuccess()
                                        viewModel.renameRecording(recording, it) 
                                    }
                                )
                            }
                        }
                    }
                }
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

@Composable
private fun WaveformAnimationExpressive(amplitude: Int) {
    val barCount = 12
    val heights = remember { List(barCount) { mutableStateOf(0.1f) } }
    
    LaunchedEffect(amplitude) {
        val norm = (amplitude.toFloat() / 32768f).coerceIn(0.1f, 1f)
        heights.forEach { h ->
            h.value = norm * (0.2f + Random.nextFloat() * 0.8f)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { h ->
            val animatedHeight by animateFloatAsState(
                targetValue = h.value * 140f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "WaveBar"
            )
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(animatedHeight.dp.coerceAtLeast(8.dp))
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun RecordingRippleAnimationExpressive(performanceMode: Boolean) {
    if (performanceMode) return
    val infiniteTransition = rememberInfiniteTransition(label = "Ripple")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "Scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "Alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        val s = scale - (index * 0.4f)
                        val a = if (s > 0) alpha else 0f
                        scaleX = s
                        scaleY = s
                        this.alpha = a
                    }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

@Composable
private fun RecordingCardExpressive(
    file: File,
    isPlaying: Boolean,
    playbackPosition: Int,
    playbackDuration: Int,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(file.nameWithoutExtension) }

    ExpressiveCard(
        onClick = { onTogglePlay() },
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectTapGestures(onLongPress = { showRenameDialog = true }, onTap = { onTogglePlay() })
        },
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = SmallExpressiveShape,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.nameWithoutExtension, 
                        style = MaterialTheme.typography.bodyLarge, 
                        fontWeight = FontWeight.Black, 
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(file.lastModified())).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                }
            }
            
            if (playbackDuration > 0 && (isPlaying || (playbackPosition > 0 && !isPlaying))) {
                Spacer(Modifier.height(20.dp))
                // Official Linear Wavy Progress Indicator
                ToolzWavyLinearProgressIndicator(
                    progress = { playbackPosition.toFloat() / playbackDuration.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(playbackPosition.toLong()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text(formatDuration(playbackDuration.toLong()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("PURGE CAPTURE?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = { Text("This audio data will be permanently removed from secure storage.") },
            confirmButton = {
                ToolzExpressiveButton(onClick = { onDelete(); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("DELETE", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("CANCEL", fontWeight = FontWeight.Bold) }
            },
            shape = SquircleShape,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("RENAME SESSION", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = BouncyShape,
                    singleLine = true
                )
            },
            confirmButton = {
                ToolzExpressiveButton(onClick = { onRename(newName); showRenameDialog = false }) {
                    Text("SAVE", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("CANCEL", fontWeight = FontWeight.Bold) }
            },
            shape = SquircleShape,
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun EmptyArchiveView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MicNone, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "NO CAPTURES FOUND", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
        }
    }
}

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
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp)) {
            Text("AUDIO CONFIGURATION", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 16.dp))

            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = SquircleShape, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("SENSITIVITY (GAIN)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    Text("${String.format("%.1f", state.gainLevel)}x", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Slider(value = state.gainLevel, onValueChange = onGainChange, valueRange = 0.5f..3.0f, modifier = Modifier.padding(top = 12.dp))
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = SquircleShape, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(modifier = Modifier.padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PERSISTENT RECORDING", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                        Text("Allow capture in background", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                    Switch(checked = state.isBackgroundEnabled, onCheckedChange = onBackgroundToggle)
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
