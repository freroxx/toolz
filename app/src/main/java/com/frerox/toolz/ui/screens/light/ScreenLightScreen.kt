package com.frerox.toolz.ui.screens.light

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.frerox.toolz.ui.components.bouncyClick

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ScreenLightScreen(
    viewModel: ScreenLightViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var showControls by remember { mutableStateOf(true) }
    
    val contentColor = if (state.color.luminance() > 0.5f) Color.Black else Color.White

    val presets = listOf(
        Color.White, 
        Color(0xFFFFEB3B), // Soft Yellow
        Color(0xFFFF9800), // Orange
        Color(0xFFF44336), // Red
        Color(0xFFE91E63), // Pink
        Color(0xFF9C27B0), // Purple
        Color(0xFF2196F3), // Blue
        Color(0xFF00BCD4), // Cyan
        Color(0xFF4CAF50), // Green
    )

    // Handle Brightness and System Bars
    DisposableEffect(state.brightness) {
        val window = activity?.window
        if (window != null) {
            val params = window.attributes
            params.screenBrightness = state.brightness.coerceIn(0.01f, 1.0f)
            window.attributes = params
        }
        onDispose {}
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            onDispose {
                val params = window.attributes
                params.screenBrightness = -1f // Restore system default
                window.attributes = params
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            onDispose {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(state.color)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (!state.isLocked) showControls = !showControls },
                onLongClick = { if (state.isLocked) viewModel.toggleLock() }
            )
    ) {
        // Main Display Info (when controls are hidden)
        AnimatedVisibility(
            visible = !showControls && !state.isLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = contentColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "TAP TO CONFIGURE",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }

        // Lock Status Indicator
        AnimatedVisibility(
            visible = state.isLocked,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(40.dp),
                    color = contentColor.copy(alpha = 0.15f),
                    border = BorderStroke(2.dp, contentColor.copy(alpha = 0.2f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = contentColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "SCREEN LOCKED",
                    style = MaterialTheme.typography.headlineSmall,
                    color = contentColor,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    "LONG PRESS TO UNLOCK",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls && !state.isLocked,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(contentColor.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = contentColor)
                    }
                    
                    Surface(
                        color = contentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "SCREEN LIGHT",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = contentColor,
                            letterSpacing = 2.sp
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleLock() },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(contentColor.copy(alpha = 0.15f))
                    ) {
                        Icon(Icons.Rounded.LockOpen, "Lock", tint = contentColor)
                    }
                }

                // Bottom Control Panel
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(40.dp),
                    color = (if (state.color.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Intensity Slider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LightMode, null, tint = contentColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Slider(
                                value = state.brightness,
                                onValueChange = { viewModel.setBrightness(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = contentColor,
                                    activeTrackColor = contentColor,
                                    inactiveTrackColor = contentColor.copy(alpha = 0.2f)
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${(state.brightness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = contentColor,
                                modifier = Modifier.width(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Presets
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(presets) { color ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (state.color == color) 3.dp else 1.dp,
                                            color = if (state.color == color) contentColor else contentColor.copy(alpha = 0.2f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setColor(color) }
                                ) {
                                    if (state.color == color) {
                                        Icon(
                                            Icons.Rounded.Check, 
                                            null, 
                                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                                            modifier = Modifier.align(Alignment.Center).size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Custom Color Hue Slider
                        var hue by remember { mutableFloatStateOf(0f) }
                        val hueColors = remember {
                            listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Brush.horizontalGradient(hueColors))
                        )
                        
                        Slider(
                            value = hue,
                            onValueChange = { 
                                hue = it
                                val hsv = floatArrayOf(it * 360f, 1f, 1f)
                                viewModel.setColor(Color(android.graphics.Color.HSVToColor(hsv)))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = contentColor,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}
