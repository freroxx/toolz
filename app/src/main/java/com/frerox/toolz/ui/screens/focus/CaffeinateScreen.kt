package com.frerox.toolz.ui.screens.focus

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.focus.CaffeinateApp
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeinateScreen(
    onNavigateBack: () -> Unit,
    viewModel: CaffeinateViewModel = hiltViewModel()
) {
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val isAutoRunning by viewModel.isAutoRunning.collectAsState()
    val isAutoAllAppsEnabled by viewModel.isAutoAllAppsEnabled.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val autoEnabledAppsCount by viewModel.autoEnabledAppsCount.collectAsState()
    val allApps by viewModel.allApps.collectAsState()
    val interval by viewModel.reminderInterval.collectAsState()
    val isInfinite by viewModel.isInfinite.collectAsState()
    val isCategorizing by viewModel.isCategorizing.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val hasPermission by viewModel.hasNotificationPermission.collectAsState()
    
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current
    val primaryColorInt = MaterialTheme.colorScheme.primary.toArgb()

    var isRainbowMode by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (true) {
                delay(50000L) // Wait 50s
                isRainbowMode = true
                delay(10000L) // Stay rainbow for 10s
                isRainbowMode = false
            }
        } else {
            isRainbowMode = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.checkServiceStatus() }

    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus()
        viewModel.refreshAccessibilityStatus()
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "CAFFEINATE",
                subtitle = if (isRunning) "KEEP AWAKE ACTIVE" else "System sleep prevention",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(onClick = { viewModel.refreshAppCategories() }, enabled = !isCategorizing) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = "AI Categorize",
                                tint = if (isCategorizing) MaterialTheme.colorScheme.primary.copy(0.3f) else MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isCategorizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                largeFlexible = true,
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 24.dp, bottom = 24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
            // Accessibility Bridge Indicator
            AccessibilityBridgeIndicator(
                isEnabled = isAccessibilityEnabled,
                onEnable = {
                    vibrationManager?.vibrateClick()
                    context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            // Permission Banner if needed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                PermissionBanner(
                    onAuthorize = {
                        vibrationManager?.vibrateClick()
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }

            // Liquid Toggle Button
            LiquidToggleButton(
                isRunning = isRunning,
                isAutoRunning = isAutoRunning,
                isRainbow = isRainbowMode,
                onClick = {
                    vibrationManager?.vibrateClick()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.toggleService(primaryColorInt)
                    }
                }
            )

            // Reminder Settings
            ReminderSettingsCard(
                interval = interval,
                isInfinite = isInfinite,
                onIntervalChange = { viewModel.setReminderInterval(it.toInt()) },
                onInfiniteToggle = { viewModel.setInfinite(it) }
            )

            // Category Manager
            CategoryManagerUI(
                apps = allApps,
                autoAllAppsEnabled = isAutoAllAppsEnabled,
                onToggleAutoAllApps = {
                    vibrationManager?.vibrateClick()
                    viewModel.toggleAutoAllApps()
                },
                onToggleApp = {
                    vibrationManager?.vibrateClick()
                    viewModel.toggleAppAutoEnable(it)
                }
            )

            Spacer(Modifier.height(40.dp))
            }

            // AI Status Overlay
            AnimatedVisibility(
                visible = aiStatus.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    tonalElevation = 12.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                            .animateContentSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        @Suppress("DEPRECATION")
                        Text(
                            aiStatus.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccessibilityBridgeIndicator(
    isEnabled: Boolean,
    onEnable: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    ExpressiveCard(
        onClick = if (isEnabled) ({}) else onEnable,
        shape = RoundedCornerShape(32.dp),
        containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(0.15f) 
                         else MaterialTheme.colorScheme.errorContainer.copy(0.15f),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp, 
            if (isEnabled) MaterialTheme.colorScheme.primary.copy(0.2f) 
            else MaterialTheme.colorScheme.error.copy(0.2f)
        ),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Pulsing dot
                Canvas(modifier = Modifier.size(12.dp)) {
                    drawCircle(
                        color = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFFF5252),
                        radius = size.minDimension / 2,
                        alpha = pulseAlpha
                    )
                }
                if (!isEnabled) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(
                            color = Color(0xFFFF5252),
                            radius = (size.minDimension / 2) * (1f + (1f - pulseAlpha)),
                            alpha = (1f - pulseAlpha) * 0.3f
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                @Suppress("DEPRECATION")
                Text(
                    if (isEnabled) "BRIDGE CONNECTED" else "BRIDGE DISCONNECTED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    if (isEnabled) "Accessibility automation active" else "Requires Accessibility Service for auto-triggering",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isEnabled) {
                ToolzExpressiveButton(
                    onClick = onEnable,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    @Suppress("DEPRECATION")
                    Text("FIX", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Icon(
                    Icons.Rounded.CheckCircle, 
                    null, 
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionBanner(onAuthorize: () -> Unit) {
    Surface(
        onClick = onAuthorize,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.error.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.error)
            }
            Column(modifier = Modifier.weight(1f)) {
                @Suppress("DEPRECATION")
                Text("NOTIFICATION REQUIRED", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Text("Needed to keep screen awake while active", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun LiquidToggleButton(
    isRunning: Boolean,
    isAutoRunning: Boolean = false,
    isRainbow: Boolean = false,
    onClick: () -> Unit
) {
    val transition = updateTransition(isRunning || isAutoRunning, label = "liquid_toggle")
    val liquidLevel by transition.animateFloat(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy) },
        label = "level"
    ) { if (it) 1f else 0f }

    val rainbowIntensity by animateFloatAsState(
        targetValue = if (isRainbow) 1f else 0f,
        animationSpec = tween(1500),
        label = "rainbow_intensity"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_offset"
    )
    val waveOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_offset_2"
    )
    val waveOffset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_offset_3"
    )

    // Bubble system
    val bubbleCount = 8
    val bubbles = List(bubbleCount) { index ->
        val duration = remember { (2000..4000).random() }
        val delay = remember { (0..2000).random() }
        val startX = remember { (20..220).random().toFloat() }
        
        val progress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bubble_$index"
        )
        Triple(startX, progress, index)
    }

    val primaryColor = if (isAutoRunning && !isRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val secondaryColor = if (isAutoRunning && !isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val tertiaryColor = if (isAutoRunning && !isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary

    val rainbowColors = listOf(
        Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFF4CAF50),
        Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFF5252)
    )

    val infiniteGlow = rememberInfiniteTransition(label = "glow_inf")
    val infiniteAlpha by infiniteGlow.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    val glowAlpha = if (isRunning || isAutoRunning) infiniteAlpha else 0f

    Box(
        modifier = Modifier
            .size(240.dp)
            .bouncyClick(onClick = onClick)
            .aspectRatio(1f)
            .drawBehind {
                if (isRunning || isAutoRunning) {
                    drawCircle(
                        Brush.radialGradient(
                            colors = if (isRainbow) listOf(Color.White.copy(glowAlpha * 0.5f), Color.Transparent)
                                     else listOf(primaryColor.copy(glowAlpha), Color.Transparent),
                            center = center,
                            radius = size.width * 0.8f
                        )
                    )
                }
            }
            .clip(CircleShape)
            .border(
                BorderStroke(
                    width = 6.dp,
                    brush = if (isRunning || isAutoRunning) {
                        if (rainbowIntensity > 0.01f) {
                            Brush.sweepGradient(
                                colors = rainbowColors.map { 
                                    colorLerp(primaryColor, it, rainbowIntensity)
                                }
                            )
                        }
                        else Brush.sweepGradient(colors = listOf(primaryColor, secondaryColor, primaryColor))
                    } else Brush.sweepGradient(colors = listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant))
                ),
                CircleShape
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val fillHeight = height * (1f - liquidLevel)

            // 1. Deepest wave (Tertiary/Rainbow)
            val pathDeep = Path().apply {
                moveTo(0f, fillHeight)
                for (x in 0..width.toInt()) {
                    val y = fillHeight + (sin((x.toFloat() / width * 2f * PI.toFloat()) + waveOffset3) * 14f * liquidLevel)
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            // 2. Middle wave (Secondary/Rainbow)
            val pathRear = Path().apply {
                moveTo(0f, fillHeight)
                for (x in 0..width.toInt()) {
                    val y = fillHeight + (sin((x.toFloat() / width * 2.5f * PI.toFloat()) + waveOffset2) * 11f * liquidLevel)
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            // 3. Top wave (Primary/Rainbow)
            val pathFront = Path().apply {
                moveTo(0f, fillHeight)
                for (x in 0..width.toInt()) {
                    val y = fillHeight + (sin((x.toFloat() / width * 1.8f * PI.toFloat()) + waveOffset) * 18f * liquidLevel)
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            clipPath(Path().apply { addOval(Rect(0f, 0f, width, height)) }) {
                drawPath(
                    path = pathDeep,
                    brush = if (rainbowIntensity > 0f) {
                        Brush.verticalGradient(listOf(
                            colorLerp(tertiaryColor.copy(0.3f), Color(0xFFFF5252).copy(0.3f), rainbowIntensity),
                            colorLerp(tertiaryColor.copy(0.1f), Color(0xFFFFEB3B).copy(0.1f), rainbowIntensity)
                        ))
                    } else Brush.verticalGradient(colors = listOf(tertiaryColor.copy(0.3f), tertiaryColor.copy(0.1f)))
                )
                
                // Bubbles (Drawn between waves for depth)
                if (isRunning || isAutoRunning) {
                    bubbles.forEach { (startX, progress, index) ->
                        val bubbleY = height - (progress * height * liquidLevel)
                        if (bubbleY > fillHeight - 20f) {
                            val bubbleX = startX.dp.toPx() + sin(progress * 10f + index) * 10f
                            val bubbleAlpha = (1f - progress).coerceIn(0f, 0.4f) * liquidLevel
                            drawCircle(
                                color = Color.White,
                                radius = (4.dp.toPx() + (index % 3).dp.toPx()) * (1f - progress * 0.5f),
                                center = Offset(bubbleX, bubbleY),
                                alpha = bubbleAlpha
                            )
                        }
                    }
                }

                drawPath(
                    path = pathRear,
                    brush = if (rainbowIntensity > 0f) {
                        Brush.verticalGradient(listOf(
                            colorLerp(secondaryColor.copy(0.5f), Color(0xFF4CAF50).copy(0.5f), rainbowIntensity),
                            colorLerp(primaryColor.copy(0.3f), Color(0xFF2196F3).copy(0.3f), rainbowIntensity)
                        ))
                    } else Brush.verticalGradient(colors = listOf(secondaryColor.copy(0.5f), primaryColor.copy(0.3f)))
                )
                drawPath(
                    path = pathFront,
                    brush = if (rainbowIntensity > 0f) {
                        Brush.verticalGradient(listOf(
                            colorLerp(primaryColor.copy(0.9f), Color(0xFF2196F3).copy(0.9f), rainbowIntensity),
                            colorLerp(secondaryColor, Color(0xFF9C27B0), rainbowIntensity)
                        ))
                    } else Brush.verticalGradient(colors = listOf(primaryColor.copy(0.9f), secondaryColor))
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isRunning || isAutoRunning) Icons.Rounded.Coffee else Icons.Rounded.CoffeeMaker,
                contentDescription = null,
                modifier = Modifier.size(64.dp).graphicsLayer {
                    if (isRunning || isAutoRunning) {
                        scaleX = 1f + (glowAlpha * 0.2f)
                        scaleY = 1f + (glowAlpha * 0.2f)
                    }
                },
                tint = if (isRunning || isAutoRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isRunning) "ACTIVE" else if (isAutoRunning) "AUTO" else "START",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = if (isRunning || isAutoRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReminderSettingsCard(
    interval: Int,
    isInfinite: Boolean,
    onIntervalChange: (Float) -> Unit,
    onInfiniteToggle: (Boolean) -> Unit
) {
    ExpressiveCard(
        onClick = {},
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    @Suppress("DEPRECATION")
                    Text("REMINDER ALERT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(
                        if (isInfinite) "Running indefinitely" else "Alert every $interval mins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                ExpressiveSwitch(
                    checked = !isInfinite,
                    onCheckedChange = { onInfiniteToggle(!it) }
                )
            }

            AnimatedVisibility(visible = !isInfinite) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("5m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Text("120m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    }
                    ExpressiveSlider(
                        value = interval.toFloat(),
                        onValueChange = onIntervalChange,
                        valueRange = 5f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryManagerUI(
    apps: List<CaffeinateApp>,
    autoAllAppsEnabled: Boolean,
    onToggleAutoAllApps: () -> Unit,
    onToggleApp: (CaffeinateApp) -> Unit
) {
    val categories = apps.groupBy { it.category }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Global Auto-Caffeinate Switch
        ExpressiveCard(
            onClick = onToggleAutoAllApps,
            shape = RoundedCornerShape(28.dp),
            containerColor = if (autoAllAppsEnabled) MaterialTheme.colorScheme.primaryContainer.copy(0.3f) 
                             else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, if (autoAllAppsEnabled) MaterialTheme.colorScheme.primary.copy(0.3f) 
                                       else MaterialTheme.colorScheme.outlineVariant.copy(0.1f)),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (autoAllAppsEnabled) MaterialTheme.colorScheme.primary.copy(0.1f) 
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AllInclusive, 
                        null, 
                        tint = if (autoAllAppsEnabled) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AUTO-CAFFEINATE ALL", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
                    Text(
                        "Enable for all non-system apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveSwitch(
                    checked = autoAllAppsEnabled,
                    onCheckedChange = { onToggleAutoAllApps() }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            @Suppress("DEPRECATION")
            Text(
                "AUTO-COFFEE CATEGORIES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            if (!autoAllAppsEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(0.1f),
                    shape = CircleShape
                ) {
                    Text(
                        "${apps.count { it.isAutoEnabled }}/${apps.size} ACTIVE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                ExpressiveStatePill(
                    text = "ALL ACTIVE",
                    icon = Icons.Rounded.CheckCircle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        AnimatedVisibility(
            visible = !autoAllAppsEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                categories.forEach { (category, categoryApps) ->
                    CategoryItem(category, categoryApps, onToggleApp)
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    category: String,
    categoryApps: List<CaffeinateApp>,
    onToggleApp: (CaffeinateApp) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExpressiveCard(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
        elevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when(category.uppercase()) {
                            "WORK", "PRODUCTIVITY" -> Icons.Rounded.Work
                            "VIDEO", "ENTERTAINMENT" -> Icons.Rounded.Movie
                            "SOCIAL" -> Icons.Rounded.Public
                            "GAMING", "GAMES" -> Icons.Rounded.SportsEsports
                            "READING" -> Icons.Rounded.MenuBook
                            "UTILITY" -> Icons.Rounded.Build
                            else -> Icons.Rounded.Category
                        },
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(category.uppercase(), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(0.2f))
                    categoryApps.forEach { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .bouncyClick { onToggleApp(app) }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(36.dp)
                            ) {
                                AppIcon(packageName = app.packageName, modifier = Modifier.padding(6.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(app.appName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                            Checkbox(
                                checked = app.isAutoEnabled,
                                onCheckedChange = { onToggleApp(app) },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}
