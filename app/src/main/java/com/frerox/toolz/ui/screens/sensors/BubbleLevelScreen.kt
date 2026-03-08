package com.frerox.toolz.ui.screens.sensors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleLevelScreen(
    viewModel: BubbleLevelViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.bubbleState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bubble Level") },
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
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "X: ${String.format("%.1f", state.x)}  Y: ${String.format("%.1f", state.y)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(300.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                // Background Grid
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    drawCircle(Color.Gray.copy(alpha = 0.3f), radius = 20.dp.toPx(), center = center)
                    drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
                    drawLine(Color.Gray.copy(alpha = 0.3f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height))
                }

                // The Bubble
                val bubbleX = (state.x * 10).dp
                val bubbleY = (state.y * 10).dp
                
                Box(
                    modifier = Modifier
                        .offset(x = -bubbleX, y = bubbleY)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (Math.abs(state.x) < 0.5f && Math.abs(state.y) < 0.5f) 
                                Color.Green 
                            else 
                                MaterialTheme.colorScheme.primary
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = if (Math.abs(state.x) < 0.5f && Math.abs(state.y) < 0.5f) "Level" else "Adjust",
                style = MaterialTheme.typography.headlineSmall,
                color = if (Math.abs(state.x) < 0.5f && Math.abs(state.y) < 0.5f) Color.Green else MaterialTheme.colorScheme.secondary
            )
        }
    }
}
