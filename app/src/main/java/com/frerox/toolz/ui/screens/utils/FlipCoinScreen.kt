/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.ui.screens.utils

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
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
import androidx.compose.ui.tooling.preview.Preview
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.frerox.toolz.data.settings.SettingsRepository
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.screens.dashboard.DashboardViewModel
import androidx.compose.ui.graphics.TransformOrigin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
fun FlipCoinScreenPreview() {
    ToolzTheme {
        FlipCoinScreen(onBack = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlipCoinScreen(
    onBack: () -> Unit,
    settingsRepository: SettingsRepository? = null
) {
    var isHeads by remember { mutableStateOf(true) }
    var isFlipping by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<Boolean>()) }
    var showStats by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    val headsImageUri by settingsRepository?.flipCoinHeadsImageUri?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }
    val tailsImageUri by settingsRepository?.flipCoinTailsImageUri?.collectAsState(initial = null) ?: remember { mutableStateOf(null) }
    
    val scope = rememberCoroutineScope()
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current

    val headsCount = history.count { it }
    val tailsCount = history.count { !it }
    val totalFlips = history.size

    // Enhanced physical flip animation
    val rotation = animateFloatAsState(
        targetValue = if (isFlipping) 4320f else 0f, // More rotations for speed
        animationSpec = if (isFlipping) {
            tween(durationMillis = 1400, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f))
        } else {
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
        },
        label = "FlipRotation"
    )

    // Vertical travel for a realistic flip
    val translationY = animateFloatAsState(
        targetValue = if (isFlipping) -400f else 0f,
        animationSpec = if (isFlipping) {
            keyframes {
                durationMillis = 1400
                0f at 0 with FastOutSlowInEasing
                -400f at 700 with LinearOutSlowInEasing
                0f at 1400
            }
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "FlipTranslation"
    )

    // Scale dynamics
    val scale = animateFloatAsState(
        targetValue = if (isFlipping) 1.1f else 1f,
        animationSpec = if (isFlipping) {
            keyframes {
                durationMillis = 1400
                1.0f at 0 with FastOutSlowInEasing
                1.3f at 700 with FastOutSlowInEasing
                1.1f at 1400 with FastOutSlowInEasing
            }
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        },
        label = "FlipScale"
    )

    fun flipCoin() {
        if (isFlipping) return
        scope.launch {
            vibrationManager?.vibrateLongClick()
            isFlipping = true
            val nextResult = Random.nextBoolean()

            // Time result swap to midpoint of flip (highest point)
            delay(700)
            isHeads = nextResult

            delay(700)
            isFlipping = false
            history = (listOf(nextResult) + history).take(20)

            vibrationManager?.vibrateSuccess()
        }
    }

    if (showSettings && settingsRepository != null) {
        FlipCoinSettingsSheet(
            onDismiss = { showSettings = false },
            repository = settingsRepository
        )
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_FlipCoinScreen_a1b2),
                subtitle = stringResource(R.string.st_FlipCoinScreen_c3d4),
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_FlipCoinScreen_e5f6))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            history = emptyList()
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.st_FlipCoinScreen_g7h8))
                    }
                    IconButton(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            showSettings = true
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.st_FlipCoinScreen_i9j0))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            ToolzHorizontalFloatingToolbar(
                expanded = true,
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
                    val flipLabel = stringResource(R.string.st_FlipCoinScreen_k1l2)
                    val statsLabel = stringResource(R.string.st_FlipCoinScreen_m3n4)
                    clickableItem(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            showStats = !showStats
                        },
                        icon = { Icon(if (showStats) Icons.Rounded.Casino else Icons.Rounded.History, null) },
                        label = if (showStats) flipLabel else statsLabel
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
                verticalArrangement = Arrangement.Top
            ) {
                // Stats or History Hub
                StaggeredEntrance(index = 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (showStats) stringResource(R.string.st_FlipCoinScreen_o5p6) else stringResource(R.string.st_FlipCoinScreen_q7r8),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        ExpressiveCard(
                            onClick = { showStats = !showStats },
                            shape = SquircleShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                            elevation = 0.dp
                        ) {
                            AnimatedContent(
                                targetState = showStats,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                    scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)) togetherWith
                                    fadeOut(animationSpec = tween(90))
                                },
                                label = "StatsAnim"
                            ) { isStats ->
                                if (isStats) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.st_FlipCoinScreen_s9t0), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                                            Text("$headsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(stringResource(R.string.st_FlipCoinScreen_u1v2), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF757575))
                                            Text("$tailsCount", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                        }
                                        Box(
                                            modifier = Modifier.size(1.dp, 32.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val winRate = if (totalFlips > 0) (headsCount.toFloat() / totalFlips * 100).toInt() else 0
                                            Text(stringResource(R.string.st_FlipCoinScreen_w3x4), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text("$winRate%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (history.isEmpty()) {
                                            Text(stringResource(R.string.st_FlipCoinScreen_y5z6), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                        } else {
                                            history.take(8).forEachIndexed { index, heads ->
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
                    }
                }

                Spacer(Modifier.height(48.dp))

                // High-fidelity Coin Display with fluid rotation and 3D depth
                Box(
                    modifier = Modifier.size(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(scale.value)
                            .graphicsLayer {
                                rotationY = rotation.value
                                this.translationY = translationY.value
                                cameraDistance = 12f * density
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
                            val isBackVisible = (rotation.value % 360f) in 90f..270f
                            
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
                                    val currentImageUri = if (isHeads) headsImageUri else tailsImageUri
                                    
                                    if (currentImageUri != null) {
                                        AsyncImage(
                                            model = currentImageUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = if (isHeads) "H" else "T",
                                            style = MaterialTheme.typography.displayLarge,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 130.sp,
                                            color = Color.White.copy(alpha = 0.95f)
                                        )
                                    }

                                    // Decorative coin ridges
                                    repeat(12) { i ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer { rotationZ = i * 30f },
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Box(Modifier.size(if (i % 3 == 0) 6.dp else 4.dp, 16.dp).background(Color.White.copy(alpha = 0.4f), CircleShape))
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
                        targetState = if (isFlipping) stringResource(R.string.st_FlipCoinScreen_a7b8) else if (isHeads) stringResource(R.string.st_FlipCoinScreen_s9t0) else stringResource(R.string.st_FlipCoinScreen_u1v2),
                        transitionSpec = {
                            (slideInVertically(initialOffsetY = { 40 }) + fadeIn(animationSpec = tween(200))) togetherWith
                            (slideOutVertically(targetOffsetY = { -40 }) + fadeOut(animationSpec = tween(200)))
                        },
                        label = "ResultAnim"
                    ) { text ->
                        Surface(
                            color = if (isFlipping) Color.Transparent else (if (isHeads) Color(0xFFFFD700) else Color(0xFF9E9E9E)).copy(alpha = 0.15f),
                            shape = BouncyShape,
                            border = if (!isFlipping) BorderStroke(1.dp, (if (isHeads) Color(0xFFFFD700) else Color(0xFF9E9E9E)).copy(alpha = 0.3f)) else null
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = if (isFlipping) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else if (isHeads) Color(0xFFFFA000) else Color(0xFF757575),
                                letterSpacing = 4.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(48.dp))

                    ToolzExpressiveButton(
                        onClick = { flipCoin() },
                        modifier = Modifier.fillMaxWidth().height(84.dp),
                        shape = LargeExpressiveShape,
                        enabled = !isFlipping,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHeads) Color(0xFFFFD700) else Color(0xFF9E9E9E),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.Casino, null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.st_FlipCoinScreen_a1b2), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                }
                
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlipCoinSettingsSheet(
    onDismiss: () -> Unit,
    repository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val headsImageUri by repository.flipCoinHeadsImageUri.collectAsState(initial = null)
    val tailsImageUri by repository.flipCoinTailsImageUri.collectAsState(initial = null)
    
    val headsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { repository.setFlipCoinHeadsImageUri(it.toString()) } }
    }
    
    val tailsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { scope.launch { repository.setFlipCoinTailsImageUri(it.toString()) } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.st_FlipCoinScreen_c9d0),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                stringResource(R.string.st_FlipCoinScreen_e1f2),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CoinSidePicker(
                    label = stringResource(R.string.st_FlipCoinScreen_s9t0),
                    imageUri = headsImageUri,
                    onPick = { headsLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDelete = { scope.launch { repository.setFlipCoinHeadsImageUri(null) } },
                    modifier = Modifier.weight(1f)
                )
                CoinSidePicker(
                    label = stringResource(R.string.st_FlipCoinScreen_u1v2),
                    imageUri = tailsImageUri,
                    onPick = { tailsLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDelete = { scope.launch { repository.setFlipCoinTailsImageUri(null) } },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            ToolzOutlinedExpressiveButton(
                onClick = {
                    scope.launch {
                        repository.setFlipCoinHeadsImageUri(null)
                        repository.setFlipCoinTailsImageUri(null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.st_FlipCoinScreen_g3h4), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CoinSidePicker(
    label: String,
    imageUri: String?,
    onPick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .bouncyClick { onPick() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            } else {
                Icon(Icons.Rounded.Image, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}
