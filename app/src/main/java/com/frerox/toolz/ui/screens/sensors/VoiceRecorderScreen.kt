package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecorderScreen(
    onBack: () -> Unit,
    viewModel: VoiceRecorderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                TopAppBar(
                    title = { Text("VOICE RECORDER", fontWeight = FontWeight.Black, letterSpacing = 1.5.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Recording Visualization Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(bottomStart = 48.dp, bottomEnd = 48.dp),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                        if (uiState.isRecording) {
                            RecordingRippleAnimation()
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatDuration(uiState.durationMillis),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            AnimatedVisibility(
                                visible = uiState.isRecording,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Text(
                                    "RECORDING...",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.error,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    RecordButton(
                        isRecording = uiState.isRecording,
                        onClick = {
                            if (uiState.isRecording) viewModel.stopRecording()
                            else viewModel.startRecording()
                        }
                    )
                }
            }

            // Recordings List
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .fadingEdge(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.05f to Color.Black,
                            0.95f to Color.Black,
                            1f to Color.Transparent
                        ),
                        length = 16.dp
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "RECENT RECORDINGS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = CircleShape
                        ) {
                            Text(
                                uiState.recordings.size.toString(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(uiState.recordings) { recording ->
                            RecordingItem(
                                file = recording,
                                isPlaying = uiState.playingFile == recording && uiState.isPlaying,
                                playbackPosition = if (uiState.playingFile == recording) uiState.playbackPosition else 0,
                                playbackDuration = if (uiState.playingFile == recording) uiState.playbackDuration else 0,
                                onTogglePlay = { viewModel.togglePlayback(recording) },
                                onDelete = { viewModel.deleteRecording(recording) }
                            )
                        }
                        
                        if (uiState.recordings.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillParentMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.MicNone, 
                                        null, 
                                        modifier = Modifier.size(64.dp).alpha(0.1f)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "NO RECORDINGS FOUND", 
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingRippleAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer(scaleX = scale1, scaleY = scale1)
                .background(MaterialTheme.colorScheme.error.copy(alpha = alpha1), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer(scaleX = scale1 * 0.7f, scaleY = scale1 * 0.7f)
                .background(MaterialTheme.colorScheme.error.copy(alpha = alpha1), CircleShape)
        )
    }
}

@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(96.dp).shadow(16.dp, CircleShape),
        shape = CircleShape,
        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        tonalElevation = 8.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = isRecording,
                transitionSpec = {
                    scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                },
                label = "icon"
            ) { recording ->
                Icon(
                    imageVector = if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}

@Composable
fun RecordingItem(
    file: File,
    isPlaying: Boolean,
    playbackPosition: Int,
    playbackDuration: Int,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick { onTogglePlay() },
        shape = RoundedCornerShape(28.dp),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.AudioFile,
                            contentDescription = null,
                            tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.name.removeSuffix(".m4a").removeSuffix(".3gp"), 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Black, 
                        maxLines = 1
                    )
                    Text(
                        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(file.lastModified())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
            }
            
            if (playbackDuration > 0 && (isPlaying || playbackPosition > 0)) {
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = playbackPosition.toFloat(),
                    onValueChange = {}, // Playback control can be added to ViewModel
                    valueRange = 0f..playbackDuration.toFloat(),
                    modifier = Modifier.height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(playbackPosition.toLong()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(formatDuration(playbackDuration.toLong()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete recording?", fontWeight = FontWeight.Black) },
            text = { Text("Are you sure you want to permanently delete this voice recording?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
