package com.frerox.toolz.ui.screens.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlipCoinScreen(
    onBack: () -> Unit
) {
    var isHeads by remember { mutableStateOf(true) }
    var isFlipping by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<Boolean>()) }
    val scope = rememberCoroutineScope()
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current

    // Energetic flip animation with many rotations
    val rotation = animateFloatAsState(
        targetValue = if (isFlipping) 2880f else 0f,
        animationSpec = if (isFlipping) {
            tween(durationMillis = 1500, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f))
        } else {
            snap()
        },
        label = "FlipRotation"
    )

    // Organic scale bounce during flip
    val scale = animateFloatAsState(
        targetValue = if (isFlipping) 1.5f else 1f,
        animationSpec = if (isFlipping) {
            keyframes {
                durationMillis = 1500
                1.0f at 0 with FastOutSlowInEasing
                2.0f at 750 with FastOutSlowInEasing
                1.5f at 1500 with FastOutSlowInEasing
            }
        } else {
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "FlipScale"
    )

    fun flipCoin() {
        if (isFlipping) return
        scope.launch {
            vibrationManager?.vibrateLongClick()
            isFlipping = true
            val nextResult = Random.nextBoolean()

            // Time result swap to midpoint of flip
            delay(750)
            isHeads = nextResult

            delay(750)
            isFlipping = false
            history = (listOf(nextResult) + history).take(15)

            vibrationManager?.vibrateSuccess()
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "COIN FLIP",
                subtitle = "Random Outcome Hub",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Clear History", Icons.Rounded.Refresh, { 
                                vibrationManager?.vibrateClick()
                                history = emptyList() 
                            }),
                            Triple("Settings", Icons.Rounded.Settings, { vibrationManager?.vibrateClick() })
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ToolzHorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.padding(bottom = 16.dp),
                content = {
                    FilledIconButton(
                        onClick = { flipCoin() },
                        modifier = Modifier.size(56.dp),
                        shape = SmallExpressiveShape,
                        enabled = !isFlipping
                    ) {
                        Icon(Icons.Rounded.Casino, contentDescription = "Flip")
                    }
                },
                trailingContent = {
                    clickableItem(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            history = emptyList() 
                        },
                        icon = { Icon(Icons.Rounded.History, null) },
                        label = "CLEAR"
                    )
                }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // History Hub in an organic Squircle Container
                StaggeredEntrance(index = 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "RECENT SEQUENCE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        ExpressiveCard(
                            onClick = {},
                            shape = SquircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (history.isEmpty()) {
                                    Text("NO HISTORY RECORDED", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                } else {
                                    history.forEachIndexed { index, heads ->
                                        Box(
                                            modifier = Modifier
                                                .size(if (index == 0) 36.dp else 28.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (heads) Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFB8860B)))
                                                    else Brush.linearGradient(listOf(Color(0xFFC0C0C0), Color(0xFF708090)))
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                if (heads) "H" else "T",
                                                fontSize = if (index == 0) 16.sp else 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(64.dp))

                // High-fidelity Coin Display with fluid rotation and 3D depth
                Box(
                    modifier = Modifier.size(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFlipping && !performanceMode) {
                        ExpressiveScanningIndicator(
                            modifier = Modifier.fillMaxSize().padding((-32).dp),
                            color = if (isHeads) Color(0xFFFFD700) else Color(0xFFC0C0C0)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scale.value)
                            .graphicsLayer {
                                rotationY = rotation.value
                                cameraDistance = 20f * density
                            }
                            .bouncyClick(enabled = !isFlipping) { flipCoin() },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = Color.Transparent,
                            shadowElevation = if (isFlipping) 48.dp else 12.dp,
                            tonalElevation = if (isFlipping) 16.dp else 0.dp
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
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.08f))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isHeads) "H" else "T",
                                        style = MaterialTheme.typography.displayLarge,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 130.sp,
                                        color = Color.White.copy(alpha = 0.95f)
                                    )

                                    // Decorative coin ridges
                                    repeat(12) { i ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { rotationZ = i * 30f },
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Box(Modifier.size(5.dp, 16.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(64.dp))

                // Energetic Result Presentation
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        targetState = if (isFlipping) "FLIPPING..." else if (isHeads) "HEADS" else "TAILS",
                        transitionSpec = {
                            (slideInVertically(initialOffsetY = { it }) + fadeIn()) togetherWith 
                            (slideOutVertically(targetOffsetY = { -it }) + fadeOut())
                        },
                        label = "ResultAnim"
                    ) { text ->
                        Surface(
                            color = if (isFlipping) Color.Transparent else (if (isHeads) Color(0xFFFFD700) else Color(0xFF9E9E9E)).copy(alpha = 0.15f),
                            shape = BouncyShape
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = if (isFlipping) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else if (isHeads) Color(0xFFFFA000) else Color(0xFF757575),
                                letterSpacing = 4.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(56.dp))

                    ToolzExpressiveButton(
                        onClick = { flipCoin() },
                        modifier = Modifier.fillMaxWidth().height(84.dp),
                        shape = LargeExpressiveShape,
                        enabled = !isFlipping
                    ) {
                        Icon(Icons.Rounded.Casino, null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("FLIP COIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                }
                
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}
