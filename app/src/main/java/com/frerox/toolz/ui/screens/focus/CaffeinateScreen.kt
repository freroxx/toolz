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

package com.frerox.toolz.ui.screens.focus

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.frerox.toolz.R
import com.frerox.toolz.data.focus.InstalledAppInfo
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.round
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeinateScreen(
    onNavigateBack: () -> Unit,
    viewModel: CaffeinateViewModel = hiltViewModel()
) {
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val isAutoRunning by viewModel.isAutoRunning.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()

    val caffeinateNotificationsEnabled by viewModel.caffeinateNotificationsEnabled.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderMins by viewModel.reminderMins.collectAsState()
    val autoStopEnabled by viewModel.autoStopEnabled.collectAsState()
    val autoStopMins by viewModel.autoStopMins.collectAsState()
    val caffeinateEverything by viewModel.caffeinateEverything.collectAsState()
    val autoPkgs by viewModel.autoPkgs.collectAsState()
    val autoEnabledAppsCount by viewModel.autoEnabledAppsCount.collectAsState()

    val installedApps by viewModel.installedApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current

    var showAppsSheet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.checkServiceStatus() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkServiceStatus()
        viewModel.refreshAccessibilityStatus()
    }

    // Blocking Accessibility Permission Dialog
    if (!isAccessibilityEnabled) {
        AlertDialog(
            onDismissRequest = { /* Blocking */ },
            icon = {
                Icon(
                    Icons.Rounded.AccessibilityNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Accessibility Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Caffeinate requires Accessibility access to detect which app you are currently using. This ensures the screen stays awake ONLY while you are inside an app, and automatically turns off on your home screen or lock screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        onNavigateBack()
                    }
                ) {
                    Text("Exit")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_CaffeinateScreen_8f1a),
                subtitle = if (isAutoRunning) "Auto-caffeinate active" else if (isRunning) "Active (Infinite)" else "Keep screen awake",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Inline Notification Permission Warning (non-blocking)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    ExpressiveCard(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        },
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.error)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Notifications Required",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "Needed to show service status and reminders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // 1. Polished Liquid Coffee Hero Button
                LiquidCoffeeHeroButton(
                    isRunning = isRunning,
                    isAutoRunning = isAutoRunning,
                    elapsedTimeMillis = elapsedTime,
                    onClick = {
                        vibrationManager?.vibrateClick()
                        viewModel.toggleService()
                    }
                )

                // 2. Primary Start / Stop Button
                PrimaryActionButton(
                    isRunning = isRunning && !isAutoRunning,
                    isAutoRunning = isAutoRunning,
                    onClick = {
                        vibrationManager?.vibrateClick()
                        if (isRunning || isAutoRunning) {
                            viewModel.stopCaffeinate()
                        } else {
                            viewModel.startInfinite()
                        }
                    }
                )

                Spacer(Modifier.height(4.dp))

                // 3. Selective Reminder Alert Card (Hidden if master notifications are disabled in Settings)
                if (caffeinateNotificationsEnabled) {
                    ReminderAlertCard(
                        enabled = reminderEnabled,
                        minutes = reminderMins,
                        onToggle = {
                            vibrationManager?.vibrateClick()
                            viewModel.setReminderEnabled(it)
                        },
                        onMinutesChange = { viewModel.setReminderMins(it) }
                    )
                }

                // 4. Selective Auto-Stop Card
                AutoStopCard(
                    enabled = autoStopEnabled,
                    minutes = autoStopMins,
                    onToggle = {
                        vibrationManager?.vibrateClick()
                        viewModel.setAutoStopEnabled(it)
                    },
                    onMinutesChange = { viewModel.setAutoStopMins(it) }
                )

                Spacer(Modifier.height(8.dp))

                // 5. Auto Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUTO-CAFFEINATE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // "Auto-coffee apps" Card
                ExpressiveCard(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        viewModel.loadInstalledApps()
                        showAppsSheet = true
                    },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-coffee apps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$autoEnabledAppsCount selected • Keeps screen awake forever inside selected apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // "Caffeinate everything" Card
                ExpressiveCard(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        viewModel.toggleEverything()
                    },
                    shape = RoundedCornerShape(24.dp),
                    containerColor = if (caffeinateEverything) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (caffeinateEverything) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (caffeinateEverything) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.AllInclusive,
                                null,
                                tint = if (caffeinateEverything) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Caffeinate everything",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Keeps screen on in any app, never on home or lock screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ExpressiveSwitch(
                            checked = caffeinateEverything,
                            onCheckedChange = {
                                vibrationManager?.vibrateClick()
                                viewModel.toggleEverything()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Modal Bottom Sheet: Auto-coffee installed apps
    if (showAppsSheet) {
        AutoCoffeeAppsBottomSheet(
            installedApps = installedApps,
            isLoading = isLoadingApps,
            initiallySelectedPkgs = autoPkgs,
            onSave = { selected ->
                viewModel.saveAutoPkgs(selected)
                showAppsSheet = false
            },
            onDismiss = { showAppsSheet = false }
        )
    }
}

/**
 * Clean, polished Liquid Coffee Hero Button.
 * Animates a smooth liquid fill with gentle rolling waves and subtle rising bubbles.
 * Simple, elegant, zero AI slop, zero lag!
 */
@Composable
private fun LiquidCoffeeHeroButton(
    isRunning: Boolean,
    isAutoRunning: Boolean,
    elapsedTimeMillis: Long,
    onClick: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val transition = updateTransition(isRunning || isAutoRunning, label = "liquid_transition")
    val liquidLevel by transition.animateFloat(
        transitionSpec = {
            spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy)
        },
        label = "level"
    ) { if (it) 1f else 0f }

    val infiniteTransition = rememberInfiniteTransition(label = "liquid_waves")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wave1"
    )
    val waveOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "wave2"
    )
    val waveOffset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3300, easing = LinearEasing), RepeatMode.Restart),
        label = "wave3"
    )

    // Gentle bubble system
    val bubbleCount = 6
    val bubbles = remember {
        List(bubbleCount) { index ->
            val startX = (30..190).random().toFloat()
            val speed = (2200..3800).random()
            val delay = (index * 450) % 2000
            Triple(startX, speed, delay)
        }
    }

    val bubbleProgresses = bubbles.mapIndexed { index, (startX, speed, delay) ->
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(speed, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bubble_$index"
        )
        startX to progress
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val infiniteGlow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteGlow.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glow_alpha"
    )

    val activeGlow = if (isRunning || isAutoRunning) glowAlpha else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .bouncyClick(onClick = onClick)
                .drawBehind {
                    if (activeGlow > 0f && !performanceMode) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(primaryColor.copy(alpha = activeGlow), Color.Transparent),
                                center = center,
                                radius = size.width * 0.75f
                            )
                        )
                    }
                }
                .clip(CircleShape)
                .border(
                    BorderStroke(
                        width = 5.dp,
                        brush = if (isRunning || isAutoRunning) {
                            Brush.sweepGradient(listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor))
                        } else {
                            Brush.sweepGradient(listOf(outlineVariant.copy(0.5f), outlineVariant.copy(0.5f)))
                        }
                    ),
                    CircleShape
                )
                .background(surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val fillHeight = height * (1f - liquidLevel)

                if (liquidLevel > 0.005f) {
                    val pathDeep = Path().apply {
                        moveTo(0f, fillHeight)
                        for (x in 0..width.toInt()) {
                            val y = fillHeight + (sin((x.toFloat() / width * 2f * PI.toFloat()) + waveOffset3) * 12f * liquidLevel)
                            lineTo(x.toFloat(), y)
                        }
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }

                    val pathMid = Path().apply {
                        moveTo(0f, fillHeight)
                        for (x in 0..width.toInt()) {
                            val y = fillHeight + (sin((x.toFloat() / width * 2.4f * PI.toFloat()) + waveOffset2) * 9f * liquidLevel)
                            lineTo(x.toFloat(), y)
                        }
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }

                    val pathFront = Path().apply {
                        moveTo(0f, fillHeight)
                        for (x in 0..width.toInt()) {
                            val y = fillHeight + (sin((x.toFloat() / width * 1.8f * PI.toFloat()) + waveOffset) * 15f * liquidLevel)
                            lineTo(x.toFloat(), y)
                        }
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }

                    clipPath(Path().apply { addOval(Rect(0f, 0f, width, height)) }) {
                        // Deep wave
                        drawPath(
                            path = pathDeep,
                            brush = Brush.verticalGradient(
                                colors = listOf(tertiaryColor.copy(alpha = 0.45f), tertiaryColor.copy(alpha = 0.2f))
                            )
                        )

                        // Bubbles
                        if (!performanceMode) {
                            bubbleProgresses.forEachIndexed { i, (startX, progress) ->
                                val bubbleY = height - (progress * height * liquidLevel)
                                if (bubbleY > fillHeight - 15f) {
                                    val bubbleX = startX.dp.toPx() + sin(progress * 8f + i) * 8f
                                    val bubbleAlpha = (1f - progress).coerceIn(0f, 0.5f) * liquidLevel
                                    drawCircle(
                                        color = Color.White,
                                        radius = (4.dp.toPx() + (i % 3).dp.toPx()) * (1f - progress * 0.4f),
                                        center = Offset(bubbleX, bubbleY),
                                        alpha = bubbleAlpha
                                    )
                                }
                            }
                        }

                        // Mid wave
                        drawPath(
                            path = pathMid,
                            brush = Brush.verticalGradient(
                                colors = listOf(secondaryColor.copy(alpha = 0.65f), primaryColor.copy(alpha = 0.4f))
                            )
                        )

                        // Front wave
                        drawPath(
                            path = pathFront,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor, secondaryColor)
                            )
                        )
                    }
                }
            }

            // Foreground Coffee Icon & Label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Coffee,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer {
                            if (isRunning || isAutoRunning) {
                                scaleX = 1f + (glowAlpha * 0.15f)
                                scaleY = 1f + (glowAlpha * 0.15f)
                            }
                        },
                    tint = if (isRunning || isAutoRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isRunning) "ACTIVE" else if (isAutoRunning) "AUTO" else "START",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = if (isRunning || isAutoRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Monospace timer / status description below cup
        val formattedTime = remember(elapsedTimeMillis) {
            val hours = TimeUnit.MILLISECONDS.toHours(elapsedTimeMillis)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTimeMillis) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTimeMillis) % 60
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }

        if (isRunning) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap cup to stop caffeinate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isAutoRunning) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Auto-Caffeinate Active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Screen stays awake while inside target app",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Tap cup to start infinite mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    isRunning: Boolean,
    isAutoRunning: Boolean = false,
    onClick: () -> Unit
) {
    val active = isRunning || isAutoRunning
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (active) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (active) Icons.Rounded.Stop else Icons.Rounded.Coffee,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = if (isAutoRunning) "Stop Auto-Caffeinate" else if (isRunning) "Stop Caffeinate" else "Start Caffeinate (Infinite)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Selective stepped time selector.
 * Snaps to discrete options (e.g. 10m, 15m, 30m, 1h, 2h).
 * Completely lag-free: uses stepped slider + quick-select chips.
 */
@Composable
private fun SelectiveTimeSelector(
    options: List<Int>,
    currentValue: Int,
    formatLabel: (Int) -> String,
    onValueSelected: (Int) -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val vibrationManager = LocalVibrationManager.current

    val currentIndex = remember(currentValue, options) {
        val idx = options.indexOf(currentValue)
        if (idx != -1) idx else {
            options.indices.minByOrNull { kotlin.math.abs(options[it] - currentValue) } ?: 0
        }
    }

    var sliderIndex by remember(currentIndex) { mutableFloatStateOf(currentIndex.toFloat()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Top label showing min, selected, and max
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatLabel(options.first()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = activeColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, activeColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = formatLabel(options[currentIndex]),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = activeColor
                )
            }
            Text(
                text = formatLabel(options.last()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Stepped slider: snaps precisely, zero lag
        Slider(
            value = sliderIndex,
            onValueChange = { newIdx ->
                sliderIndex = newIdx
                val intIdx = round(newIdx).toInt().coerceIn(0, options.size - 1)
                if (intIdx != currentIndex) {
                    vibrationManager?.vibrateClick()
                    onValueSelected(options[intIdx])
                }
            },
            onValueChangeFinished = {
                val finalIdx = round(sliderIndex).toInt().coerceIn(0, options.size - 1)
                sliderIndex = finalIdx.toFloat()
                onValueSelected(options[finalIdx])
            },
            valueRange = 0f..(options.size - 1).toFloat(),
            steps = options.size - 2,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = activeColor.copy(alpha = 0.15f),
                activeTickColor = Color.White,
                inactiveTickColor = activeColor.copy(alpha = 0.4f)
            )
        )
    }
}

private val REMINDER_OPTIONS = listOf(10, 15, 30, 45, 60, 90, 120)
private val AUTOSTOP_OPTIONS = listOf(15, 30, 45, 60, 120, 240, 480)

private fun formatReminderLabel(mins: Int): String {
    return when {
        mins < 60 -> "${mins}m"
        mins % 60 == 0 -> "${mins / 60}h"
        else -> "${mins / 60}.${(mins % 60) * 10 / 60}h"
    }
}

private fun formatAutoStopLabel(mins: Int): String {
    return when {
        mins < 60 -> "${mins}m"
        mins % 60 == 0 -> "${mins / 60}h"
        else -> "${mins / 60}.${(mins % 60) * 10 / 60}h"
    }
}

@Composable
private fun ReminderAlertCard(
    enabled: Boolean,
    minutes: Int,
    onToggle: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    ExpressiveCard(
        onClick = { onToggle(!enabled) },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reminder Alert",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (enabled) "Alerts after ${formatReminderLabel(minutes)} of use" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveSwitch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }

            AnimatedVisibility(visible = enabled) {
                SelectiveTimeSelector(
                    options = REMINDER_OPTIONS,
                    currentValue = minutes,
                    formatLabel = ::formatReminderLabel,
                    onValueSelected = onMinutesChange,
                    activeColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AutoStopCard(
    enabled: Boolean,
    minutes: Int,
    onToggle: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    ExpressiveCard(
        onClick = { onToggle(!enabled) },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.HourglassBottom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Stop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (enabled) "Disables caffeinate after ${formatAutoStopLabel(minutes)}" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveSwitch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }

            AnimatedVisibility(visible = enabled) {
                SelectiveTimeSelector(
                    options = AUTOSTOP_OPTIONS,
                    currentValue = minutes,
                    formatLabel = ::formatAutoStopLabel,
                    onValueSelected = onMinutesChange,
                    activeColor = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoCoffeeAppsBottomSheet(
    installedApps: List<InstalledAppInfo>,
    isLoading: Boolean,
    initiallySelectedPkgs: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPkgs by remember { mutableStateOf(initiallySelectedPkgs) }
    var searchQuery by remember { mutableStateOf("") }
    val vibrationManager = LocalVibrationManager.current

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            val q = searchQuery.trim().lowercase()
            installedApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Auto-Coffee Apps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${selectedPkgs.size} apps selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            selectedPkgs = if (selectedPkgs.size == filteredApps.size) emptySet()
                            else filteredApps.map { it.packageName }.toSet()
                        }
                    ) {
                        Text(if (selectedPkgs.size == filteredApps.size) "Clear" else "Select All")
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search installed apps…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )

            // App List
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No apps matching \"$searchQuery\"" else "No apps found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPkgs.contains(app.packageName)
                        Surface(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                selectedPkgs = if (isSelected) {
                                    selectedPkgs - app.packageName
                                } else {
                                    selectedPkgs + app.packageName
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                AppIcon(
                                    packageName = app.packageName,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        vibrationManager?.vibrateClick()
                                        selectedPkgs = if (checked) {
                                            selectedPkgs + app.packageName
                                        } else {
                                            selectedPkgs - app.packageName
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onSave(selectedPkgs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = "Save Changes (${selectedPkgs.size} apps)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
