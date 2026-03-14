package com.frerox.toolz.ui.screens.light

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
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
            CenterAlignedTopAppBar(
                title = { Text("FLASHLIGHT", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdge(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.05f to Color.Black,
                            0.95f to Color.Black,
                            1f to Color.Transparent
                        ),
                        length = 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (cameraPermissionState.status.isGranted) {
                    Spacer(modifier = Modifier.weight(0.6f))
                    
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                        val infiniteTransition = rememberInfiniteTransition(label = "FlashlightPulse")
                        val glowScale by infiniteTransition.animateFloat(
                            initialValue = 0.9f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "GlowScale"
                        )

                        if (state.isOn) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(glowScale)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFFFFEE58).copy(alpha = 0.3f * state.brightness), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                            )
                        }
                        
                        Surface(
                            modifier = Modifier
                                .size(180.dp)
                                .shadow(if (state.isOn) 24.dp else 4.dp, CircleShape, spotColor = Color(0xFFFFEE58))
                                .bouncyClick { viewModel.toggleFlashlight() },
                            shape = CircleShape,
                            color = if (state.isOn) Color(0xFFFFEE58) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (state.isOn) BorderStroke(4.dp, Color.White.copy(alpha = 0.5f)) else BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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

                    Surface(
                        color = (if (state.isOn) Color(0xFFFFEE58) else MaterialTheme.colorScheme.outline).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (state.isOn) "BEAM ACTIVE" else "STANDBY",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Black,
                            color = if (state.isOn) Color(0xFFFBC02D) else MaterialTheme.colorScheme.outline
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))

                    // Expressive Controls
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        shape = RoundedCornerShape(40.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
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
                                        Spacer(Modifier.width(10.dp))
                                        Text("INTENSITY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    }
                                    if (!state.isBrightnessSupported) {
                                        Text("HW RESTRICTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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
                                    "SIGNAL MODES", 
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
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
                                            Text(mode.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                            Surface(
                                modifier = Modifier.size(120.dp),
                                shape = RoundedCornerShape(40.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Rounded.FlashlightOn, null, modifier = Modifier.padding(32.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            }
                            Spacer(Modifier.height(32.dp))
                            Text("HARDWARE ACCESS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                            Text(
                                "Camera permission is required to engage the device flashlight. Your privacy is maintained.", 
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)
                            )
                            Button(
                                onClick = { cameraPermissionState.launchPermissionRequest() },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                            ) {
                                Text("GRANT PERMISSION", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
