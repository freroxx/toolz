package com.frerox.toolz.ui.screens.utils

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulerScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ruler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val density = LocalDensity.current
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Drawing MM marks on the left side
                val mmStepPx = with(density) { 1.mm.toPx() }
                var currentY = 0f
                var mmCount = 0
                
                while (currentY < height) {
                    val lineLength = when {
                        mmCount % 10 == 0 -> 40.dp.toPx()
                        mmCount % 5 == 0 -> 25.dp.toPx()
                        else -> 15.dp.toPx()
                    }
                    
                    drawLine(
                        color = Color.Gray,
                        start = Offset(0f, currentY),
                        end = Offset(lineLength, currentY),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    if (mmCount % 10 == 0) {
                        // Draw label for CM
                    }
                    
                    currentY += mmStepPx
                    mmCount++
                }
                
                // Drawing Inch marks on the right side
                val inchStepPx = with(density) { 1.inches.toPx() }
                val halfInchStepPx = inchStepPx / 2
                val quarterInchStepPx = inchStepPx / 4
                val eighthInchStepPx = inchStepPx / 8
                
                currentY = 0f
                var inchCount = 0
                while (currentY < height) {
                    // Full inch
                    drawLine(
                        color = Color.Gray,
                        start = Offset(width, currentY),
                        end = Offset(width - 40.dp.toPx(), currentY),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    // Divisions
                    if (currentY + eighthInchStepPx < height) {
                         drawLine(Color.Gray, Offset(width, currentY + eighthInchStepPx), Offset(width - 15.dp.toPx(), currentY + eighthInchStepPx), 1.dp.toPx())
                    }
                    if (currentY + quarterInchStepPx < height) {
                         drawLine(Color.Gray, Offset(width, currentY + quarterInchStepPx), Offset(width - 20.dp.toPx(), currentY + quarterInchStepPx), 1.dp.toPx())
                    }
                    if (currentY + halfInchStepPx < height) {
                         drawLine(Color.Gray, Offset(width, currentY + halfInchStepPx), Offset(width - 30.dp.toPx(), currentY + halfInchStepPx), 1.dp.toPx())
                    }
                    
                    currentY += inchStepPx
                    inchCount++
                }
            }
            
            // Labels (simplified)
            Column(modifier = Modifier.align(Alignment.Center)) {
                Text("CM", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.weight(1f))
                Text("INCH", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// Density extensions for physical measurements
val Int.mm get() = (this / 1.0).dp * (160f / 25.4f) / (160f / 160f) // Very rough approximation
val Int.inches get() = (this * 160).dp // Based on 160dpi standard
