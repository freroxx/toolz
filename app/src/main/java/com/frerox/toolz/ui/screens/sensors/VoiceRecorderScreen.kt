package com.frerox.toolz.ui.screens.sensors

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val context = LocalContext.current
    var showSettingsSheet by remember { mutableStateOf(false) }

    val archiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { } // We just want to open the picker to the folder, no specific file selection needed

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
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            showSettingsSheet = true
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                        modifier = Modifier.size(64.dp),
                        shape = MediumExpressiveShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (uiState.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic, 
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
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
                        clickableItem(
                            onClick = {
                                vibrationManager?.vibrateTick()
                                viewModel.addMark()
                            },
                            icon = { Icon(Icons.Rounded.BookmarkBorder, null) },
                            label = "MARK"
                        )
                    }
                    clickableItem(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            try {
                                val uri = uiState.customOutputPath?.toUri() ?: context.getExternalFilesDir("recordings")?.toUri()
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = uri
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback to generic picker if direct view fails
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
                        },
                        icon = { Icon(Icons.Rounded.FolderCopy, null) },
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
                    shape = ExtraLargeExpressiveShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 40.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                            // Dynamic background glow responding to state
                            if (!performanceMode) {
                                val infiniteTransition = rememberInfiniteTransition(label = "GlowPulse")
                                val glowScale by infiniteTransition.animateFloat(
                                    initialValue = 0.8f,
                                    targetValue = 1.2f,
                                    animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                                    label = "Scale"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(glowScale)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    (if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                                                    Color.Transparent
                                                )
                                            ),
                                            CircleShape
                                        )
                                )
                            }

                            // Pulsing Mic Icon for recording feedback
                            if (uiState.isRecording && !uiState.isPaused) {
                                LiveMicIcon(
                                    isRecording = true,
                                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-20).dp)
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
                                        fontSize = 84.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = (-4.5).sp
                                    ),
                                    color = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                
                                AnimatedVisibility(
                                    visible = uiState.isRecording,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut()
                                ) {
                                    ExpressiveStatePill(
                                        text = if (uiState.isPaused) "SESSION PAUSED" else "LIVE CAPTURE",
                                        icon = if (uiState.isPaused) Icons.Rounded.Pause else Icons.Rounded.GraphicEq,
                                        color = if (uiState.isPaused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                            }
                        }

                        if (uiState.marks.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.marks.takeLast(3).forEach { mark ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            formatDuration(mark),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
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
                            itemsIndexed(uiState.recordings, key = { _, it -> it.file.absolutePath }) { index, recordingItem ->
                                StaggeredEntrance(index = index) {
                                    RecordingCardExpressive(
                                        file = recordingItem.file,
                                        isPlaying = uiState.playingFile == recordingItem.file && uiState.isPlaying,
                                        playbackPosition = if (uiState.playingFile == recordingItem.file) uiState.playbackPosition else 0,
                                        playbackDuration = if (uiState.playingFile == recordingItem.file) uiState.playbackDuration else 0,
                                        marks = recordingItem.marks,
                                        onTogglePlay = { 
                                            vibrationManager?.vibrateClick()
                                            viewModel.togglePlayback(recordingItem.file) 
                                        },
                                        onDelete = { 
                                            vibrationManager?.vibrateClick()
                                            viewModel.deleteRecording(recordingItem.file) 
                                        },
                                        onRename = { 
                                            vibrationManager?.vibrateSuccess()
                                            viewModel.renameRecording(recordingItem.file, it) 
                                        }
                                    )
                                }
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
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidSphere")
    
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "BaseRotation"
    )
    
    val normAmplitude = (amplitude.toFloat() / 32768f).coerceIn(0f, 1f)
    val animatedAmplitude by animateFloatAsState(
        targetValue = normAmplitude,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "Amplitude"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = (size.minDimension / 3f) + (animatedAmplitude * size.minDimension / 6f)
            
            // Outer fluid layer
            rotate(baseRotation) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Red.copy(alpha = 0.1f),
                            Color.Red.copy(alpha = 0.4f),
                            Color.Red.copy(alpha = 0.1f)
                        )
                    ),
                    radius = radius * 1.4f,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            
            // Core liquid body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Red.copy(alpha = 0.8f),
                        Color.Red.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.2f
                ),
                radius = radius,
                center = center
            )
            
            // Animated "waves" inside core
            repeat(3) { i ->
                val waveRotation = baseRotation * (1f + i * 0.2f) * (if (i % 2 == 0) 1f else -1f)
                rotate(waveRotation) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.3f),
                        startAngle = 0f,
                        sweepAngle = 90f + (animatedAmplitude * 40f),
                        useCenter = false,
                        style = Stroke(width = (2 + i).dp.toPx(), cap = StrokeCap.Round),
                        size = Size(radius * 1.8f, radius * 1.8f),
                        topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f)
                    )
                }
            }
        }
        
        // Inner "breathing" core
        Box(
            modifier = Modifier
                .size((80 + animatedAmplitude * 40).dp)
                .graphicsLayer {
                    alpha = 0.4f + animatedAmplitude * 0.6f
                    scaleX = 0.9f + animatedAmplitude * 0.2f
                    scaleY = 0.9f + animatedAmplitude * 0.2f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White, Color.Transparent)
                    ),
                    CircleShape
                )
        )
    }
}

@Composable
private fun RecordingRippleAnimationExpressive(performanceMode: Boolean) {
    if (performanceMode) return
    val infiniteTransition = rememberInfiniteTransition(label = "OrganicRipple")
    
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        repeat(3) { index ->
            val duration = 2500 + (index * 600)
            val delay = index * 800
            
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 2.2f,
                animationSpec = infiniteRepeatable(
                    tween(duration, delayMillis = delay, easing = FastOutSlowInEasing),
                    RepeatMode.Restart
                ),
                label = "RippleScale$index"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    tween(duration, delayMillis = delay, easing = LinearOutSlowInEasing),
                    RepeatMode.Restart
                ),
                label = "RippleAlpha$index"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
            )
        }
        
        // Central pulse
        val centerScale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                tween(1500, easing = EaseInOutSine),
                RepeatMode.Reverse
            ),
            label = "CenterPulse"
        )
        
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(centerScale)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    CircleShape
                )
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
        )
    }
}

@Composable
private fun RecordingCardExpressive(
    file: File,
    isPlaying: Boolean,
    playbackPosition: Int,
    playbackDuration: Int,
    marks: List<Long> = emptyList(),
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
        containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
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
                        if (isPlaying) {
                            ExpressiveLoadingIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
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
                // Official Linear Wavy Progress Indicator with Marks
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    val width = maxWidth
                    ToolzWavyLinearProgressIndicator(
                        progress = { playbackPosition.toFloat() / playbackDuration.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                    
                    // Marks Visualization on Progress Bar
                    marks.forEach { mark ->
                        val ratio = mark.toFloat() / playbackDuration.toFloat()
                        if (ratio in 0f..1f) {
                            Box(
                                modifier = Modifier
                                    .offset(x = width * ratio - 2.dp)
                                    .size(4.dp, 16.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                }
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
    StaggeredEntrance(index = 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(160.dp),
                    shape = ExtraLargeExpressiveShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MicNone,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    "NO CAPTURES FOUND",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    letterSpacing = 2.sp
                )
                Text(
                    "START RECORDING TO POPULATE ARCHIVE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
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
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = ExtraLargeExpressiveShape
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp)) {
            Text("AUDIO CONFIGURATION", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(vertical = 16.dp))

            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = SquircleShape, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("SENSITIVITY (GAIN)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                    Text("${String.format("%.1f", state.gainLevel)}x", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    ExpressiveSlider(value = state.gainLevel, onValueChange = onGainChange, valueRange = 0.5f..3.0f, modifier = Modifier.padding(top = 12.dp))
                }
            }

            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = SquircleShape, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(modifier = Modifier.padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PERSISTENT RECORDING", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                        Text("Allow capture in background", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                    ExpressiveSwitch(checked = state.isBackgroundEnabled, onCheckedChange = onBackgroundToggle)
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
