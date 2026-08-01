/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.utils

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SoundMeterScreen(
    viewModel: SoundMeterViewModel,
    onBack: () -> Unit
) {
    val db by viewModel.decibels.collectAsState()
    val maxDb by viewModel.maxDecibels.collectAsState()
    val history by viewModel.decibelHistory.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    
    SoundMeterContent(
        db = db,
        maxDb = maxDb,
        history = history,
        isRecording = isRecording,
        onStartStop = { if (isRecording) viewModel.stopRecording() else viewModel.startRecording() },
        onResetMax = { viewModel.resetMax() },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoundMeterContent(
    db: Float,
    maxDb: Float,
    history: List<Float>,
    isRecording: Boolean,
    onStartStop: () -> Unit,
    onResetMax: () -> Unit,
    onBack: () -> Unit
) {
    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val locale = LocalConfiguration.current.locales[0]
    
    val animatedDb by animateFloatAsState(
        targetValue = db,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "DbAnim"
    )

    val activeColor = if (db > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "SOUND METER",
                subtitle = if (isRecording) "Monitoring active environment" else "Ready to measure sound levels",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                    verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)
                ) {
                    StaggeredEntrance(index = 0) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                            ToolzWavyCircularProgressIndicator(
                                progress = { (animatedDb / 120f).coerceIn(0f, 1f) },
                                color = activeColor,
                                modifier = Modifier.fillMaxSize(),
                                trackColor = activeColor.copy(alpha = 0.1f)
                            )
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(locale, "%.1f", animatedDb),
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
                    }

                    StaggeredEntrance(index = 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "PEAK LEVEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = String.format(locale, "%.1f dB", maxDb),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            
                            ExpressiveStatePill(
                                text = if (isRecording) "Monitoring" else "Idle",
                                icon = if (isRecording) Icons.Rounded.GraphicEq else Icons.Rounded.MicNone,
                                color = if (isRecording) activeColor else MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    StaggeredEntrance(index = 2) {
                        ExpressiveWaveform(
                            history = history,
                            color = activeColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(MediumExpressiveShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(16.dp)
                        )
                    }

                    StaggeredEntrance(index = 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolzExpressiveButton(
                                onClick = onStartStop,
                                modifier = Modifier.weight(1f).height(64.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = LargeExpressiveShape
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isRecording) "STOP MEASURING" else "START MEASURING", fontWeight = FontWeight.Black)
                            }

                            ToolzOutlinedExpressiveButton(
                                onClick = onResetMax,
                                modifier = Modifier.height(64.dp),
                                enabled = maxDb > 0,
                                shape = LargeExpressiveShape
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Reset Max")
                            }
                        }
                    }
                }
            } else {
                PermissionRequestState { permissionState.launchPermissionRequest() }
            }
        }
    }
}

@Composable
fun ExpressiveWaveform(
    history: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Canvas(modifier = modifier) {
        if (history.isEmpty()) return@Canvas
        
        val width = size.width
        val height = size.height
        val maxHistory = 50
        val barSpacing = 6.dp.toPx()
        val barWidth = (width - ((maxHistory - 1) * barSpacing)) / maxHistory
        
        // Pad history with zeros if it's less than maxHistory to keep layout stable
        val paddedHistory = if (history.size < maxHistory) {
            List(maxHistory - history.size) { 0f } + history
        } else {
            history.takeLast(maxHistory)
        }
        
        paddedHistory.forEachIndexed { index, db ->
            val progress = (db / 120f).coerceIn(0.02f, 1f)
            // Subtle animated undulation for "smoothness"
            val wave = sin((index.toFloat() / maxHistory + waveOffset) * 2 * PI.toFloat()).toFloat()
            val animatedHeightMultiplier = 1f + 0.1f * wave
            val barHeight = progress * height * animatedHeightMultiplier
            
            val x = index * (barWidth + barSpacing)
            
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.3f), color, color.copy(alpha = 0.3f)),
                    startY = (height - barHeight) / 2,
                    endY = (height + barHeight) / 2
                ),
                topLeft = Offset(x, (height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
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
            shape = ExtraLargeExpressiveShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Mic, 
                    null, 
                    modifier = Modifier.size(64.dp), 
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "AUDIO ACCESS REQUIRED", 
            style = MaterialTheme.typography.headlineSmall, 
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            "Sound Meter needs microphone access to measure real-time decibel levels. Your audio is never recorded or stored.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
        )
        ToolzExpressiveButton(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = LargeExpressiveShape
        ) {
            Text("GRANT PERMISSION", fontWeight = FontWeight.Black)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SoundMeterScreenPreview() {
    ToolzTheme {
        SoundMeterContent(
            db = 65f,
            maxDb = 82f,
            history = List(50) { 40f + it % 40f },
            isRecording = true,
            onStartStop = {},
            onResetMax = {},
            onBack = {}
        )
    }
}
