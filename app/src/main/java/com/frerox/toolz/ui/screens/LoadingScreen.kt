package com.frerox.toolz.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import kotlinx.coroutines.delay

import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoadingScreen(
    viewModel: LoadingViewModel = hiltViewModel(),
    onLoadingComplete: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val isInitialized by viewModel.isInitialized.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    LaunchedEffect(isInitialized) {
        if (isInitialized) {
            onLoadingComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background elements
        if (!performanceMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.03f)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 1.dp.toPx()
                    val step = 40.dp.toPx()
                    for (i in 0..(size.width / step).toInt()) {
                        drawLine(
                            color = Color.Gray,
                            start = androidx.compose.ui.geometry.Offset(i * step, 0f),
                            end = androidx.compose.ui.geometry.Offset(i * step, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
                    for (i in 0..(size.height / step).toInt()) {
                        drawLine(
                            color = Color.Gray,
                            start = androidx.compose.ui.geometry.Offset(0f, i * step),
                            end = androidx.compose.ui.geometry.Offset(size.width, i * step),
                            strokeWidth = strokeWidth
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Outer ring
                if (!performanceMode) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation }) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    primaryColor.copy(alpha = 0.1f),
                                    primaryColor
                                )
                            ),
                            startAngle = 0f,
                            sweepAngle = 280f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    )
                }

                // Inner Logo Circle
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .graphicsLayer { translationY = if (performanceMode) 0f else bounce }
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "T",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.offset(y = (-2).dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "TOOLZ",
                style = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.alpha(alpha)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = loadingMessage,
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Version info at bottom
        Text(
            text = "v1.0.6",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
