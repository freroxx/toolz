package com.frerox.toolz.ui.screens.utils

import android.graphics.Paint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulerScreen(
    onBack: () -> Unit
) {
    var touchY by remember { mutableFloatStateOf(0f) }
    var isFlipped by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val displayMetrics = context.resources.displayMetrics
    
    val ydpi = displayMetrics.ydpi
    
    val mmPx = ydpi / 25.4f
    val inchPx = ydpi

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryColorArgb = primaryColor.toArgb()
    val errorColor = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    
    val strokeWidthMm = with(density) { 1.2.dp.toPx() }
    val strokeWidthInch = with(density) { 2.dp.toPx() }
    val strokeWidthSubInch = with(density) { 1.dp.toPx() }
    val strokeWidthMeasure = with(density) { 3.dp.toPx() }
    
    val majorLen = with(density) { 44.dp.toPx() }
    val halfLen = with(density) { 28.dp.toPx() }
    val minorLen = with(density) { 16.dp.toPx() }
    
    val inchMajorLen = with(density) { 44.dp.toPx() }
    val inchHalfLen = with(density) { 32.dp.toPx() }
    val inchQuarterLen = with(density) { 24.dp.toPx() }
    val inchEighthLen = with(density) { 16.dp.toPx() }
    
    val mmTextSize = with(density) { 12.sp.toPx() }
    val inchTextSize = with(density) { 14.sp.toPx() }
    val textOffset = with(density) { 4.dp.toPx() }
    val mmLabelOffset = with(density) { 10.dp.toPx() }
    val inchLabelOffset = with(density) { 52.dp.toPx() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PRECISION RULER", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isFlipped = !isFlipped },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Flip, contentDescription = "Flip Ruler", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(padding)
                .then(if (performanceMode) Modifier else Modifier.fadingEdge(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.05f to Color.Black,
                        0.95f to Color.Black,
                        1f to Color.Transparent
                    ),
                    length = 32.dp
                ))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> touchY = offset.y },
                        onDrag = { change, _ -> 
                            change.consume()
                            touchY = change.position.y 
                        }
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
                        color = if (isMajor) onSurface else onSurface.copy(alpha = 0.3f),
                        start = Offset(startX, currentY),
                        end = Offset(endX, currentY),
                        strokeWidth = if (isMajor) strokeWidthMm * 1.5f else strokeWidthMm,
                        cap = StrokeCap.Round
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
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
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
                        color = primaryColor,
                        start = Offset(startX, currentY),
                        end = Offset(endX, currentY),
                        strokeWidth = strokeWidthInch,
                        cap = StrokeCap.Round
                    )

                    val textX = if (isFlipped) inchLabelOffset else width - inchLabelOffset
                    drawContext.canvas.nativeCanvas.drawText(
                        inchCount.toString(),
                        textX,
                        currentY + textOffset,
                        Paint().apply {
                            color = primaryColorArgb
                            textSize = inchTextSize
                            textAlign = if (isFlipped) Paint.Align.LEFT else Paint.Align.RIGHT
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
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
                            color = primaryColor.copy(alpha = if (i % 4 == 0) 0.6f else 0.3f),
                            start = Offset(startX, subY),
                            end = Offset(subEndX, subY),
                            strokeWidth = if (i % 4 == 0) strokeWidthSubInch * 1.5f else strokeWidthSubInch,
                            cap = StrokeCap.Round
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
                        strokeWidth = strokeWidthMeasure,
                        cap = StrokeCap.Round
                    )
                    
                    drawCircle(
                        color = errorColor,
                        radius = 6.dp.toPx(),
                        center = Offset(if (isFlipped) width - 4.dp.toPx() else 4.dp.toPx(), touchY)
                    )
                }
            }
            
            // Real-time measurement display
            AnimatedVisibility(
                visible = touchY > 0,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val cm = touchY / mmPx / 10f
                val inches = touchY / inchPx
                
                Surface(
                    modifier = Modifier
                        .padding(24.dp)
                        .shadow(24.dp, RoundedCornerShape(32.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "CURRENT MEASUREMENT",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.5.sp
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f cm", cm),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        @Suppress("DEPRECATION")
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f inches", inches),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            if (touchY <= 0) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "DRAG TO MEASURE",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}
