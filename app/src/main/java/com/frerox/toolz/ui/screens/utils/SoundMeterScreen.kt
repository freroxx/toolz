package com.frerox.toolz.ui.screens.utils

import android.Manifest
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SoundMeterScreen(
    viewModel: SoundMeterViewModel,
    onBack: () -> Unit
) {
    val db by viewModel.decibels.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val performanceMode = LocalPerformanceMode.current
    
    val animatedDb by animateFloatAsState(
        targetValue = db,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "DbAnim"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SOUND METER", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            if (permissionState.status.isGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Gauge Area
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val glowAlpha by if (performanceMode) remember { mutableFloatStateOf(0.08f) } else infiniteTransition.animateFloat(
                            initialValue = 0.05f,
                            targetValue = 0.15f,
                            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
                            label = "glow"
                        )
                        
                        val activeColor = if (db > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                        if (!performanceMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            listOf(activeColor.copy(alpha = glowAlpha), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                            )
                        }

                        DecibelCircularGauge(db = animatedDb, color = activeColor)
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.GraphicEq, 
                                null, 
                                tint = activeColor, 
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f", animatedDb),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = (-4).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "DECIBELS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = activeColor,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    // Linear visualization
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    ) {
                        DecibelLinearGauge(db = animatedDb)
                    }
                    
                    Spacer(modifier = Modifier.height(64.dp))
                    
                    // Control Button
                    Surface(
                        onClick = {
                            if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
                        },
                        modifier = Modifier.size(100.dp).bouncyClick {},
                        shape = CircleShape,
                        color = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                        shadowElevation = 12.dp,
                        border = BorderStroke(4.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isRecording) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        @Suppress("DEPRECATION")
                        Text(
                            text = if (isRecording) "MONITORING ACTIVE" else "READY TO MEASURE",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else {
                PermissionRequestState { permissionState.launchPermissionRequest() }
            }
        }
    }
}

@Composable
fun DecibelCircularGauge(db: Float, color: Color) {
    val progress = (db / 120f).coerceIn(0f, 1f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 16.dp.toPx()
        drawArc(
            color = Color.LightGray.copy(alpha = 0.1f),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 135f,
            sweepAngle = 270f * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun DecibelLinearGauge(db: Float) {
    val progress = (db / 120f).coerceIn(0f, 1f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        
        val activeColor = when {
            db > 90 -> Color(0xFFEF5350)
            db > 70 -> Color(0xFFFFA726)
            else -> Color(0xFF66BB6A)
        }
        
        drawRect(
            color = activeColor,
            size = size.copy(width = width * progress)
        )
    }
}

@Composable
fun PermissionRequestState(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(48.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Icon(Icons.Rounded.Mic, null, modifier = Modifier.padding(32.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(32.dp))
        Text("AUDIO ACCESS REQUIRED", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(
            "Sound Meter needs microphone access to measure real-time decibel levels. Your audio is never recorded or stored.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
        )
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("GRANT PERMISSION", fontWeight = FontWeight.Black)
        }
    }
}
