package com.frerox.toolz.ui.screens.utils

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
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
    var history by remember { mutableStateOf(listOf<Boolean>()) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val rotation = animateFloatAsState(
        targetValue = if (isFlipping) 1800f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)),
        label = "CoinFlip"
    )

    val scale = animateFloatAsState(
        targetValue = if (isFlipping) 1.5f else 1f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "CoinScale"
    )

    fun flipCoin() {
        if (isFlipping) return
        scope.launch {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            isFlipping = true
            val nextResult = Random.nextBoolean()
            delay(500)
            isHeads = nextResult
            delay(500)
            isFlipping = false
            history = (listOf(nextResult) + history).take(10)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("COIN FLIP", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { history = emptyList() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset History")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // History Row
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (history.isEmpty()) {
                    Text("No flips yet", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                } else {
                    history.forEach { heads ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (heads) Color(0xFFFFD700) else Color(0xFFC0C0C0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (heads) "H" else "T", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }

            // Coin Display
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(scale.value)
                    .graphicsLayer {
                        rotationX = rotation.value
                        cameraDistance = 12f * density
                    }
                    .bouncyClick { flipCoin() },
                contentAlignment = Alignment.Center
            ) {
                // Outer Ring
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = if (isHeads) Color(0xFFFFD700) else Color(0xFFC0C0C0),
                    shadowElevation = 12.dp,
                    tonalElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.3f),
                                        Color.Black.copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(0.85f),
                            shape = CircleShape,
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black.copy(alpha = 0.1f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isHeads) "H" else "T",
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 100.sp,
                                    color = Color.Black.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // Result Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = if (isFlipping) "Flipping..." else if (isHeads) "HEADS" else "TAILS",
                    transitionSpec = {
                        slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
                    },
                    label = "ResultAnim"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = if (isFlipping) MaterialTheme.colorScheme.outline 
                                else if (isHeads) Color(0xFFB8860B) else Color(0xFF708090)
                    )
                }
                
                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { flipCoin() },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(24.dp),
                    enabled = !isFlipping,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Rounded.Casino, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("FLIP COIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
