package com.frerox.toolz.ui.screens.time

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

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
                        text = "STOPWATCH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )

                    IconButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Dynamic Rotating Ring
                val infiniteTransition = rememberInfiniteTransition(label = "StopwatchRing")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "Rotation"
                )

                val primaryColor = MaterialTheme.colorScheme.primary
                
                Canvas(modifier = Modifier.size(310.dp)) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.08f),
                        style = Stroke(width = 16.dp.toPx())
                    )
                    
                    if (state.isRunning) {
                        drawArc(
                            color = primaryColor,
                            startAngle = rotation - 90f,
                            sweepAngle = 75f,
                            useCenter = false,
                            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatTime(state.elapsedTime),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val lapTime = if (state.laps.isEmpty()) state.elapsedTime else state.elapsedTime - state.laps.first()
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "+ ${formatTime(lapTime)}",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset", modifier = Modifier.size(32.dp))
                }
                
                Surface(
                    onClick = { viewModel.toggleStartStop() },
                    modifier = Modifier
                        .size(100.dp)
                        .bouncyClick {},
                    shape = RoundedCornerShape(32.dp),
                    color = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (state.isRunning) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isRunning) "Pause" else "Start",
                            modifier = Modifier.size(52.dp),
                            tint = if (state.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                IconButton(
                    onClick = { if (state.isRunning) viewModel.lap() },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isRunning) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f) 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                    enabled = state.isRunning
                ) {
                    Icon(
                        Icons.Rounded.Flag, 
                        contentDescription = "Lap", 
                        modifier = Modifier.size(32.dp),
                        tint = if (state.isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
            
            Column(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LAP SESSIONS", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                    if (state.laps.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "${state.laps.size} Records",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                if (state.laps.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Flag, null, modifier = Modifier.size(80.dp).alpha(0.08f), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("NO LAPS RECORDED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), letterSpacing = 1.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().fadingEdge(
                            brush = Brush.verticalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent),
                            length = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        itemsIndexed(state.laps) { index, lapTime ->
                            val duration = if (index == state.laps.size - 1) {
                                lapTime
                            } else {
                                lapTime - state.laps[index + 1]
                            }
                            LapCard(
                                lapNumber = state.laps.size - index, 
                                totalTime = lapTime,
                                lapDuration = duration
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LapCard(lapNumber: Int, totalTime: Long, lapDuration: Long) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(48.dp),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = lapNumber.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        text = "DURATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = formatTime(lapDuration),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "CUMULATIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = formatTime(totalTime),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun formatTime(timeMillis: Long): String {
    val totalSeconds = timeMillis / 1000
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    val millis = (timeMillis % 1000) / 10
    
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
}
