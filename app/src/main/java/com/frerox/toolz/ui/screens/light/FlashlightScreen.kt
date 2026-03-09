package com.frerox.toolz.ui.screens.light

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FlashlightScreen(
    viewModel: FlashlightViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flashlight", fontWeight = FontWeight.ExtraBold) },
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
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                        )
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (cameraPermissionState.status.isGranted) {
                Spacer(modifier = Modifier.weight(0.5f))
                
                // Animated Beam Effect using Box instead of Canvas to avoid import issues
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
                    if (state.isOn) {
                        val infiniteTransition = rememberInfiniteTransition(label = "FlashlightPulse")
                        val beamAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "BeamAlpha"
                        )
                        val beamScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "BeamScale"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(beamScale)
                                .alpha(beamAlpha * state.brightness)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color.Yellow, Color.Transparent)
                                    ),
                                    CircleShape
                                )
                        )
                    }
                    
                    Surface(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .bouncyClick { viewModel.toggleFlashlight() },
                        color = if (state.isOn) Color.Yellow else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 12.dp,
                        shadowElevation = 8.dp,
                        border = if (state.isOn) null else androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = if (state.isOn) Color.Black else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(32.dp))

                Text(
                    text = if (state.isOn) "ACTIVE" else "READY",
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Black,
                    color = if (state.isOn) Color.Yellow.copy(green = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.weight(1f))

                // Modern Expressive Controls
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Brightness Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Intensity", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                                if (!state.isBrightnessSupported) {
                                    Text("Not supported", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = state.brightness,
                                onValueChange = { viewModel.setBrightness(it) },
                                enabled = state.isBrightnessSupported,
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                )
                            )
                        }

                        // Mode Selector
                        Column {
                            Text(
                                "MODES", 
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                FlashlightMode.entries.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = state.mode == mode,
                                        onClick = { viewModel.setMode(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = FlashlightMode.entries.size)
                                    ) {
                                        Text(mode.name, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Rounded.FlashlightOn, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Camera permission is required to use the flashlight.", textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}
