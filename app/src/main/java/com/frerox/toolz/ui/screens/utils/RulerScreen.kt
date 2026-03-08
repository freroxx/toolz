package com.frerox.toolz.ui.screens.utils

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulerScreen(
    onBack: () -> Unit
) {
    var touchY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    
    // Physical constants (approximate based on standard screen DPI)
    val ppi = 160f * density.density 
    val mmPx = ppi / 25.4f
    val inchPx = ppi

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ruler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> touchY = offset.y },
                        onDrag = { change, _ -> touchY = change.position.y }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // CM / MM Side (Left)
                var currentY = 0f
                var mmCount = 0
                while (currentY < height) {
                    val isMajor = mmCount % 10 == 0
                    val isHalf = mmCount % 5 == 0
                    val lineLength = when {
                        isMajor -> 40.dp.toPx()
                        isHalf -> 25.dp.toPx()
                        else -> 15.dp.toPx()
                    }
                    
                    drawLine(
                        color = Color(onSurfaceColor).copy(alpha = 0.6f),
                        start = Offset(0f, currentY),
                        end = Offset(lineLength, currentY),
                        strokeWidth = 1.dp.toPx()
                    )

                    if (isMajor) {
                        val text = (mmCount / 10).toString()
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            lineLength + 8.dp.toPx(),
                            currentY + 5.dp.toPx(),
                            Paint().apply {
                                color = onSurfaceColor
                                textSize = 12.dp.toPx()
                                textAlign = Paint.Align.LEFT
                            }
                        )
                    }
                    
                    currentY += mmPx
                    mmCount++
                }
                
                // Inch Side (Right)
                currentY = 0f
                var inchCount = 0
                val eighth = inchPx / 8
                while (currentY < height) {
                    // Full inch
                    drawLine(
                        color = Color(primaryColor).copy(alpha = 0.6f),
                        start = Offset(width, currentY),
                        end = Offset(width - 40.dp.toPx(), currentY),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        inchCount.toString(),
                        width - 48.dp.toPx(),
                        currentY + 5.dp.toPx(),
                        Paint().apply {
                            color = primaryColor
                            textSize = 14.dp.toPx()
                            textAlign = Paint.Align.RIGHT
                            isFakeBoldText = true
                        }
                    )
                    
                    for (i in 1..7) {
                        val subY = currentY + i * eighth
                        if (subY >= height) break
                        val subLen = when {
                            i % 4 == 0 -> 30.dp.toPx() // Half
                            i % 2 == 0 -> 22.dp.toPx() // Quarter
                            else -> 15.dp.toPx() // Eighth
                        }
                        drawLine(
                            color = Color(primaryColor).copy(alpha = 0.4f),
                            start = Offset(width, subY),
                            end = Offset(width - subLen, subY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    
                    currentY += inchPx
                    inchCount++
                }

                // Measurement line
                if (touchY > 0) {
                    drawLine(
                        color = Color.Red,
                        start = Offset(0f, touchY),
                        end = Offset(width, touchY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            
            // Real-time measurement display
            if (touchY > 0) {
                val cm = touchY / mmPx / 10f
                val inches = touchY / inchPx
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f cm", cm),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f in", inches),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
