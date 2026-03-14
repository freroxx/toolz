package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.fadingEdge
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

    val animX by animateFloatAsState(targetValue = state.x, animationSpec = spring(stiffness = 500f), label = "animX")
    val animY by animateFloatAsState(targetValue = state.y, animationSpec = spring(stiffness = 500f), label = "animY")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BUBBLE LEVEL", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                color = if (isLevel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.padding(bottom = 48.dp),
                border = BorderStroke(1.5.dp, if (isLevel) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.CenterFocusStrong,
                        null,
                        tint = if (isLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "X: %.1f°  Y: %.1f°", state.x, state.y),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "glow")
                val glowScale by infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                    label = "glow"
                )

                if (isLevel) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(glowScale)
                            .background(
                                Brush.radialGradient(
                                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), Color.Transparent)
                                )
                            )
                    )
                }

                // Background Grid
                Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val primaryColor = Color.Gray.copy(alpha = 0.2f)
                    
                    // Circles
                    drawCircle(primaryColor, radius = 40.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
                    drawCircle(primaryColor.copy(alpha = 0.1f), radius = 100.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                    
                    // Lines
                    drawLine(primaryColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                    drawLine(primaryColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
                }

                // The Bubble
                val bubbleX = (animX * 12).dp
                val bubbleY = (animY * 12).dp
                
                Surface(
                    modifier = Modifier
                        .offset(x = -bubbleX, y = bubbleY)
                        .size(64.dp)
                        .shadow(if (isLevel) 16.dp else 4.dp, CircleShape, spotColor = if (isLevel) MaterialTheme.colorScheme.primary else Color.Black),
                    shape = CircleShape,
                    color = if (isLevel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Inner highlight for 3D effect
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = (-8).dp, y = (-8).dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(64.dp))
            
            AnimatedContent(
                targetState = isLevel,
                transitionSpec = { fadeIn() togetherWith fadeOut() }, label = ""
            ) { level ->
                Surface(
                    color = if (level) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (level) "PERFECTLY LEVEL" else "ALIGN DEVICE",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = if (level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
