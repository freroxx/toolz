package com.frerox.toolz.ui.screens.light

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenLightScreen(
    viewModel: ScreenLightViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var showControls by remember { mutableStateOf(true) }

    val colors = listOf(
        Color.White, Color.Red, Color.Green, Color.Blue, 
        Color.Yellow, Color.Cyan, Color.Magenta, Color(0xFFFFA500) // Orange
    )

    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val params = window.attributes
            val originalBrightness = params.screenBrightness
            params.screenBrightness = 1.0f
            window.attributes = params

            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            onDispose {
                params.screenBrightness = originalBrightness
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
            .clickable { showControls = !showControls }
    ) {
        if (showControls) {
            TopAppBar(
                title = { Text("Screen Light", color = if (state.color == Color.White) Color.Black else Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack, 
                            contentDescription = "Back",
                            tint = if (state.color == Color.White) Color.Black else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Select Color", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(colors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { viewModel.setColor(color) }
                                    .then(
                                        if (state.color == color) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else Modifier
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
