package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    
                    Text(
                        text = "VOICE RECORDER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    IconButton(
                        onClick = { /* Settings */ },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
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
                tonalElevation = 4.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
                        if (uiState.isRecording) {
                            RecordingRippleAnimation()
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatDuration(uiState.durationMillis),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 84.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = (-4).sp
                                ),
                                color = if (uiState.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            AnimatedVisibility(
                                visible = uiState.isRecording,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text(
                                        "LIVE RECORDING",
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.error,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(48.dp))
                    
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
            Column(
                modifier = Modifier
                    .weight(1.5f)
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AUDIO LIBRARY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${uiState.recordings.size} FILES",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize().fadingEdge(
                        brush = Brush.verticalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent),
                        length = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    items(uiState.recordings) { recording ->
                        RecordingCard(
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
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Rounded.MicNone, 
                                        null, 
                                        modifier = Modifier.size(80.dp).alpha(0.08f),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "READY TO CAPTURE", 
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        letterSpacing = 1.sp
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
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer(scaleX = scale1, scaleY = scale1)
                .background(MaterialTheme.colorScheme.error.copy(alpha = alpha1), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer(scaleX = scale1 * 0.75f, scaleY = scale1 * 0.75f)
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
        modifier = Modifier.size(110.dp).bouncyClick {},
        shape = CircleShape,
        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(4.dp, Color.White.copy(alpha = 0.3f))
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
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
fun RecordingCard(
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
        shape = RoundedCornerShape(32.dp),
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp, 
            if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        tonalElevation = if (isPlaying) 4.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.name.removeSuffix(".m4a").removeSuffix(".3gp"), 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Black, 
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(file.lastModified())).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
            
            if (playbackDuration > 0 && (isPlaying || (playbackPosition > 0 && !isPlaying))) {
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { playbackPosition.toFloat() / playbackDuration.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(playbackPosition.toLong()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text(formatDuration(playbackDuration.toLong()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("REMOVE RECORDING?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = { Text("This action will permanently delete the audio file from your device storage.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("DELETE", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(36.dp)
        )
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
