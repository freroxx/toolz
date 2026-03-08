package com.frerox.toolz.ui.screens.sensors

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel,
    onBack: () -> Unit
) {
    val azimuth by viewModel.azimuth.collectAsState()
    val animatedAzimuth by animateFloatAsState(targetValue = -azimuth)

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Digital Compass") },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${azimuth.toInt()}°",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = getDirectionLabel(azimuth),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier.size(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CompassDial(rotation = animatedAzimuth)
                
                // Needle
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    drawLine(
                        color = Color.Red,
                        start = center,
                        end = Offset(size.width / 2, size.height / 2 - 120.dp.toPx()),
                        strokeWidth = 4.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
fun CompassDial(rotation: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        rotate(rotation) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2
            
            drawCircle(
                color = Color.Gray.copy(alpha = 0.2f),
                radius = radius,
                center = center
            )

            for (i in 0 until 360 step 30) {
                val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
                val lineStart = Offset(
                    center.x + (radius - 10.dp.toPx()) * Math.cos(angleRad.toDouble()).toFloat(),
                    center.y + (radius - 10.dp.toPx()) * Math.sin(angleRad.toDouble()).toFloat()
                )
                val lineEnd = Offset(
                    center.x + radius * Math.cos(angleRad.toDouble()).toFloat(),
                    center.y + radius * Math.sin(angleRad.toDouble()).toFloat()
                )
                drawLine(Color.Gray, lineStart, lineEnd, 2.dp.toPx())
            }
            
            // Cardinal Points
            val points = listOf("N", "E", "S", "W")
            points.forEachIndexed { index, label ->
                val angleRad = Math.toRadians((index * 90).toDouble() - 90).toFloat()
                // Labels are complex in Canvas, skipping text for now to keep it simple or use AndroidView
            }
        }
    }
}

private fun getDirectionLabel(azimuth: Float): String {
    return when (azimuth) {
        in 337.5..360.0, in 0.0..22.5 -> "North"
        in 22.5..67.5 -> "North East"
        in 67.5..112.5 -> "East"
        in 112.5..157.5 -> "South East"
        in 157.5..202.5 -> "South"
        in 202.5..247.5 -> "South West"
        in 247.5..292.5 -> "West"
        in 292.5..337.5 -> "North West"
        else -> "North"
    }
}
