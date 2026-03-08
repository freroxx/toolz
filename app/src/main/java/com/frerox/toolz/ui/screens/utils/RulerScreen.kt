package com.frerox.toolz.ui.screens.utils

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulerScreen(
    onBack: () -> Unit
) {
    var touchY by remember { mutableStateOf(0f) }
    var isFlipped by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val displayMetrics = context.resources.displayMetrics
    
    // Physical constants using actual screen DPI for better accuracy
    val xdpi = displayMetrics.xdpi
    val ydpi = displayMetrics.ydpi
    
    // Use ydpi for vertical ruler
    val mmPx = ydpi / 25.4f
    val inchPx = ydpi

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val errorColor = MaterialTheme.colorScheme.error
    
    val strokeWidthMm = with(density) { 1.dp.toPx() }
    val strokeWidthInch = with(density) { 1.5.dp.toPx() }
    val strokeWidthSubInch = with(density) { 1.dp.toPx() }
    val strokeWidthMeasure = with(density) { 2.dp.toPx() }
    
    val majorLen = with(density) { 40.dp.toPx() }
    val halfLen = with(density) { 25.dp.toPx() }
    val minorLen = with(density) { 15.dp.toPx() }
    
    val inchMajorLen = with(density) { 40.dp.toPx() }
    val inchHalfLen = with(density) { 30.dp.toPx() }
    val inchQuarterLen = with(density) { 22.dp.toPx() }
    val inchEighthLen = with(density) { 15.dp.toPx() }
    
    val mmTextSize = with(density) { 12.dp.toPx() }
    val inchTextSize = with(density) { 14.dp.toPx() }
    val textOffset = with(density) { 5.dp.toPx() }
    val mmLabelOffset = with(density) { 8.dp.toPx() }
    val inchLabelOffset = with(density) { 48.dp.toPx() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ruler") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isFlipped = !isFlipped }) {
                        Icon(Icons.Rounded.Flip, contentDescription = "Flip Ruler")
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
                
                // CM / MM Side
                var currentY = 0f
                var mmCount = 0
                while (currentY < height) {
                    val isMajor = mmCount % 10 == 0
                    val isHalf = mmCount % 5 == 0
                    val lineLength = when {
                        isMajor -> majorLen
                        isHalf -> halfLen
                        else -> minorLen
                    }
                    
                    val startX = if (isFlipped) width else 0f
                    val endX = if (isFlipped) width - lineLength else lineLength
                    
                    drawLine(
                        color = Color(onSurfaceColor).copy(alpha = 0.6f),
                        start = Offset(startX, currentY),
                        end = Offset(endX, currentY),
                        strokeWidth = strokeWidthMm
                    )

                    if (isMajor) {
                        val text = (mmCount / 10).toString()
                        val textX = if (isFlipped) width - lineLength - mmLabelOffset else lineLength + mmLabelOffset
                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            textX,
                            currentY + textOffset,
                            Paint().apply {
                                color = onSurfaceColor
                                textSize = mmTextSize
                                textAlign = if (isFlipped) Paint.Align.RIGHT else Paint.Align.LEFT
                            }
                        )
                    }
                    
                    currentY += mmPx
                    mmCount++
                }
                
                // Inch Side
                currentY = 0f
                var inchCount = 0
                val eighth = inchPx / 8
                while (currentY < height) {
                    val startX = if (isFlipped) 0f else width
                    val endX = if (isFlipped) inchMajorLen else width - inchMajorLen
                    
                    // Full inch
                    drawLine(
                        color = Color(primaryColor).copy(alpha = 0.6f),
                        start = Offset(startX, currentY),
                        end = Offset(endX, currentY),
                        strokeWidth = strokeWidthInch
                    )

                    val textX = if (isFlipped) inchLabelOffset else width - inchLabelOffset
                    drawContext.canvas.nativeCanvas.drawText(
                        inchCount.toString(),
                        textX,
                        currentY + textOffset,
                        Paint().apply {
                            color = primaryColor
                            textSize = inchTextSize
                            textAlign = if (isFlipped) Paint.Align.LEFT else Paint.Align.RIGHT
                            isFakeBoldText = true
                        }
                    )
                    
                    for (i in 1..7) {
                        val subY = currentY + i * eighth
                        if (subY >= height) break
                        val subLen = when {
                            i % 4 == 0 -> inchHalfLen
                            i % 2 == 0 -> inchQuarterLen
                            else -> inchEighthLen
                        }
                        val subEndX = if (isFlipped) subLen else width - subLen
                        drawLine(
                            color = Color(primaryColor).copy(alpha = 0.4f),
                            start = Offset(startX, subY),
                            end = Offset(subEndX, subY),
                            strokeWidth = strokeWidthSubInch
                        )
                    }
                    
                    currentY += inchPx
                    inchCount++
                }

                // Measurement line
                if (touchY > 0) {
                    drawLine(
                        color = errorColor,
                        start = Offset(0f, touchY),
                        end = Offset(width, touchY),
                        strokeWidth = strokeWidthMeasure
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
