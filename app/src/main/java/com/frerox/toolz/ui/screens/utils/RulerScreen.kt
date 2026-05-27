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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Settings
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
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RulerScreen(
    onBack: () -> Unit
) {
    var touchY by remember { mutableFloatStateOf(0f) }
    var isFlipped by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
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
    val strokeWidthMeasure = with(density) { 4.dp.toPx() }
    
    val majorLen = with(density) { 48.dp.toPx() }
    val halfLen = with(density) { 32.dp.toPx() }
    val minorLen = with(density) { 18.dp.toPx() }
    
    val inchMajorLen = with(density) { 48.dp.toPx() }
    val inchHalfLen = with(density) { 36.dp.toPx() }
    val inchQuarterLen = with(density) { 28.dp.toPx() }
    val inchEighthLen = with(density) { 18.dp.toPx() }
    
    val mmTextSize = with(density) { 14.sp.toPx() }
    val inchTextSize = with(density) { 16.sp.toPx() }
    val textOffset = with(density) { 5.dp.toPx() }
    val mmLabelOffset = with(density) { 12.dp.toPx() }
    val inchLabelOffset = with(density) { 56.dp.toPx() }

    // Fluid bouncy spring for the measurement line
    val animatedTouchY by animateFloatAsState(
        targetValue = touchY,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "MeasureLine"
    )

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "RULER",
                subtitle = "Precision Metric & Imperial",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Recalibrate", Icons.Rounded.Sync, { vibrationManager?.vibrateClick() }),
                            Triple("Settings", Icons.Rounded.Settings, { vibrationManager?.vibrateClick() })
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ToolzHorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(bottom = 16.dp),
                content = {
                    FilledIconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            isFlipped = !isFlipped
                        },
                        modifier = Modifier.size(48.dp),
                        shape = SmallExpressiveShape
                    ) {
                        Icon(Icons.Rounded.Flip, contentDescription = "Flip Orientation")
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = { vibrationManager?.vibrateClick() },
                        icon = { Icon(Icons.Rounded.Info, null) },
                        label = "SPECS"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
                .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 32.dp, bottom = 32.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> 
                            vibrationManager?.vibrateTick()
                            touchY = offset.y 
                        },
                        onDrag = { change, _ -> 
                            change.consume()
                            if (Math.abs(change.position.y - touchY) > 5) {
                                vibrationManager?.vibrateTick()
                            }
                            touchY = change.position.y 
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // CM / MM Side with expressive markings
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
                        color = if (isMajor) onSurface else onSurface.copy(alpha = 0.25f),
                        start = Offset(startX, currentY),
                        end = Offset(endX, currentY),
                        strokeWidth = if (isMajor) strokeWidthMm * 1.8f else strokeWidthMm,
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
                                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
                            }
                        )
                    }
                    
                    currentY += mmPx
                    mmCount++
                }
                
                // Inch Side with energetic primary colors
                currentY = 0f
                var inchCount = 0
                val eighth = inchPx / 8
                while (currentY < height) {
                    val startX = if (isFlipped) 0f else width
                    val endX = if (isFlipped) inchMajorLen else width - inchMajorLen
                    
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
                            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
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
                            color = primaryColor.copy(alpha = if (i % 4 == 0) 0.6f else 0.2f),
                            start = Offset(startX, subY),
                            end = Offset(subEndX, subY),
                            strokeWidth = if (i % 4 == 0) strokeWidthSubInch * 2f else strokeWidthSubInch,
                            cap = StrokeCap.Round
                        )
                    }
                    
                    currentY += inchPx
                    inchCount++
                }

                // Measurement line with high visibility
                if (animatedTouchY > 0) {
                    drawLine(
                        color = errorColor,
                        start = Offset(0f, animatedTouchY),
                        end = Offset(width, animatedTouchY),
                        strokeWidth = strokeWidthMeasure,
                        cap = StrokeCap.Round
                    )
                    
                    drawCircle(
                        color = errorColor,
                        radius = 8.dp.toPx(),
                        center = Offset(if (isFlipped) width - 8.dp.toPx() else 8.dp.toPx(), animatedTouchY)
                    )
                }
            }
            
            // High-fidelity Floating Measurement Hub in Squircle Container
            AnimatedVisibility(
                visible = touchY > 0,
                enter = fadeIn() + scaleIn(animationSpec = spring(Spring.DampingRatioMediumBouncy)),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val cm = touchY / mmPx / 10f
                val inches = touchY / inchPx
                
                Surface(
                    modifier = Modifier
                        .padding(24.dp)
                        .shadow(elevation = if (performanceMode) 0.dp else 48.dp, shape = SquircleShape, spotColor = primaryColor.copy(alpha = 0.4f)),
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(36.dp), 
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = primaryColor.copy(alpha = 0.12f),
                            shape = BouncyShape
                        ) {
                            Text(
                                "PRECISE SCAN",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = primaryColor,
                                letterSpacing = 2.sp
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%.2f CM", cm),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 64.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = (-2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                            shape = SmallExpressiveShape
                        ) {
                            Text(
                                text = String.format(Locale.getDefault(), "%.2f IN", inches),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
            
            if (touchY <= 0) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        shape = BouncyShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "DRAG SURFACE TO MEASURE",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
