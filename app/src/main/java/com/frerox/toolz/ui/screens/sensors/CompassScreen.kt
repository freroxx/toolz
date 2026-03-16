package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val view = LocalView.current

    val animatedAzimuth by animateFloatAsState(
        targetValue = state.azimuth,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        ),
        label = "Azimuth"
    )

    LaunchedEffect(state.azimuth.toInt()) {
        if (state.azimuth.toInt() == 0 || state.azimuth.toInt() == 360) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PRECISION COMPASS", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val config = LocalConfiguration.current
        val dialSize = (config.screenWidthDp.dp * 0.92f).coerceAtMost(480.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 40.dp)
            ) {
                Text(
                    text = "${state.azimuth.toInt()}°",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 100.sp, letterSpacing = (-4).sp),
                    fontWeight = FontWeight.Black,
                    color = onSurface
                )
                
                Surface(
                    color = primaryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = getDirectionLabel(state.azimuth).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp),
                        letterSpacing = 2.5.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(dialSize)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = onSurface.copy(alpha = 0.08f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(-animatedAzimuth)
                ) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2

                    for (i in 0 until 360 step 2) {
                        val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
                        val isMain = i % 30 == 0
                        val isCardinal = i % 90 == 0
                        
                        val tickLength = if (isCardinal) 32.dp.toPx() else if (isMain) 20.dp.toPx() else 10.dp.toPx()
                        val strokeWidth = if (isCardinal) 4.dp.toPx() else if (isMain) 2.5.dp.toPx() else 1.5.dp.toPx()
                        val alpha = if (isMain) 0.9f else 0.35f
                        
                        val start = Offset(
                            center.x + (radius - tickLength) * cos(angleRad),
                            center.y + (radius - tickLength) * sin(angleRad)
                        )
                        val end = Offset(
                            center.x + radius * cos(angleRad),
                            center.y + radius * sin(angleRad)
                        )
                        
                        drawLine(
                            color = if (i == 0) Color.Red else onSurface.copy(alpha = alpha),
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }

                    val needlePath = Path().apply {
                        moveTo(center.x, center.y - radius + 40.dp.toPx())
                        lineTo(center.x - 14.dp.toPx(), center.y - radius + 75.dp.toPx())
                        lineTo(center.x + 14.dp.toPx(), center.y - radius + 75.dp.toPx())
                        close()
                    }
                    drawPath(needlePath, Color.Red)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).alpha(0.4f),
                        tint = primaryColor
                    )
                    
                    val bubbleX by animateFloatAsState(targetValue = (state.roll / 45f) * 45f, label = "levelX")
                    val bubbleY by animateFloatAsState(targetValue = (state.pitch / 45f) * 45f, label = "levelY")
                    
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(surfaceVariant.copy(alpha = 0.6f))
                            .border(1.5.dp, onSurface.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(onSurface.copy(0.12f), Offset(size.width/2, 0f), Offset(size.width/2, size.height), 1.5.dp.toPx())
                            drawLine(onSurface.copy(0.12f), Offset(0f, size.height/2), Offset(size.width, size.height/2), 1.5.dp.toPx())
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .offset(bubbleX.dp, bubbleY.dp)
                                .clip(CircleShape)
                                .background(if (state.isLevel) Color(0xFF4CAF50) else primaryColor)
                                .shadow(4.dp, CircleShape)
                        )
                    }
                }
            }

            Spacer(Modifier.height(64.dp))

            Surface(
                color = surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, onSurface.copy(alpha = 0.1f)),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    StatusItem(
                        label = "ACCURACY",
                        value = when (state.accuracy) {
                            3 -> "HIGH"
                            2 -> "MID"
                            else -> "LOW"
                        },
                        color = when (state.accuracy) {
                            3 -> Color(0xFF4CAF50)
                            2 -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    )
                    
                    VerticalDivider(modifier = Modifier.height(40.dp), thickness = 1.dp, color = onSurface.copy(alpha = 0.1f))
                    
                    StatusItem(
                        label = "STABILITY",
                        value = if (state.isLevel) "FLAT" else "${abs(state.pitch).toInt()}°",
                        color = if (state.isLevel) Color(0xFF4CAF50) else primaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun StatusItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            letterSpacing = 1.5.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = color
        )
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
