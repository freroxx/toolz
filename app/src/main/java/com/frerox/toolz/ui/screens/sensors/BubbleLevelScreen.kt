package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleLevelScreen(
    viewModel: BubbleLevelViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.bubbleState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    val animX by animateFloatAsState(targetValue = state.x, animationSpec = spring(stiffness = 500f))
    val animY by animateFloatAsState(targetValue = state.y, animationSpec = spring(stiffness = 500f))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bubble Level", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val isLevel = Math.abs(state.x) < 0.3f && Math.abs(state.y) < 0.3f
            
            Surface(
                color = if (isLevel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.padding(bottom = 48.dp)
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "X: %.1f°  Y: %.1f°", state.x, state.y),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    color = if (isLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .size(320.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                // Background Grid
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    
                    // Circles
                    drawCircle(Color.Gray.copy(alpha = 0.2f), radius = 40.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                    drawCircle(Color.Gray.copy(alpha = 0.1f), radius = 100.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                    
                    // Lines
                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                    drawLine(Color.Gray.copy(alpha = 0.2f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                }

                // The Bubble
                // Scale factor to map gravity (-9.8 to 9.8) to pixels
                val bubbleX = (animX * 12).dp
                val bubbleY = (animY * 12).dp
                
                Box(
                    modifier = Modifier
                        .offset(x = -bubbleX, y = bubbleY)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLevel) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner highlight for 3D effect
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .offset(x = (-8).dp, y = (-8).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            Text(
                text = if (isLevel) "PERFECTLY LEVEL" else "ADJUST DEVICE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (isLevel) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
            )
        }
    }
}
