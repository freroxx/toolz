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
        targetValue = if (isFlipping) 2160f else 0f,
        animationSpec = if (isFlipping) {
            tween(durationMillis = 1200, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f))
        } else {
            snap()
        },
        label = "CoinFlip"
    )

    val scale = animateFloatAsState(
        targetValue = if (isFlipping) 1.4f else 1f,
        animationSpec = if (isFlipping) {
            keyframes {
                durationMillis = 1200
                1.0f at 0 with FastOutSlowInEasing
                1.8f at 600 with FastOutSlowInEasing
                1.4f at 1200 with FastOutSlowInEasing
            }
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "CoinScale"
    )

    fun flipCoin() {
        if (isFlipping) return
        scope.launch {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            isFlipping = true
            val nextResult = Random.nextBoolean()

            // Wait for half the animation to change the visual result
            delay(600)
            isHeads = nextResult

            delay(600)
            isFlipping = false
            history = (listOf(nextResult) + history).take(12)

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
                title = {
                    Text(
                        "COIN FLIP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { history = emptyList() },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // History Row
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "RECENT FLIPS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (history.isEmpty()) {
                        Text("No history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    } else {
                        history.forEachIndexed { index, heads ->
                            val itemScale by animateFloatAsState(1f)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (index == 0) 32.dp else 24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (heads) Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFB8860B)))
                                        else Brush.linearGradient(listOf(Color(0xFFC0C0C0), Color(0xFF708090)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (heads) "H" else "T",
                                    fontSize = if (index == 0) 14.sp else 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Coin Display
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .scale(scale.value)
                    .graphicsLayer {
                        rotationY = rotation.value
                        cameraDistance = 15f * density
                    }
                    .bouncyClick(enabled = !isFlipping) { flipCoin() },
                contentAlignment = Alignment.Center
            ) {
                // Coin Design
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = if (isFlipping) 24.dp else 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    if (isHeads) listOf(Color(0xFFFFE082), Color(0xFFFFA000), Color(0xFFB8860B))
                                    else listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF616161))
                                )
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Inner ring
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.05f))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isHeads) "H" else "T",
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Black,
                                fontSize = 120.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )

                            // Edge details
                            repeat(12) { i ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { rotationZ = i * 30f },
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(Modifier.size(4.dp, 12.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
                                }
                            }
                        }
                    }
                }
            }

            // Result Text and Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = if (isFlipping) "FLIPPING..." else if (isHeads) "HEADS" else "TAILS",
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                    },
                    label = "ResultAnim"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = if (isFlipping) MaterialTheme.colorScheme.outline
                                else if (isHeads) Color(0xFFFFA000) else Color(0xFF757575),
                        letterSpacing = 2.sp
                    )
                }

                Spacer(Modifier.height(48.dp))

                Button(
                    onClick = { flipCoin() },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !isFlipping,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(Icons.Rounded.Casino, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("FLIP COIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}
