package com.frerox.toolz.ui.screens.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlipCoinScreen(
    onBack: () -> Unit
) {
    var isHeads by remember { mutableStateOf(true) }
    var isFlipping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val rotation = animateFloatAsState(
        targetValue = if (isFlipping) 1080f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "CoinFlip"
    )

    fun flipCoin() {
        if (isFlipping) return
        scope.launch {
            isFlipping = true
            delay(400) // Halfway through rotation
            isHeads = Random.nextBoolean()
            delay(400)
            isFlipping = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flip a Coin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        rotationY = rotation.value
                        cameraDistance = 12f * density
                    }
                    .clip(CircleShape)
                    .background(
                        if (isHeads) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isHeads) "HEADS" else "TAILS",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = if (isHeads) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.height(64.dp))

            Button(
                onClick = { flipCoin() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = MaterialTheme.shapes.large,
                enabled = !isFlipping
            ) {
                Text("FLIP COIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            
            if (!isFlipping) {
                Text(
                    text = "Result: ${if (isHeads) "Heads" else "Tails"}",
                    modifier = Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
