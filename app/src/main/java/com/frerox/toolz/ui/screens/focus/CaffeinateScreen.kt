package com.frerox.toolz.ui.screens.focus

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.focus.CaffeinateApp
import com.frerox.toolz.ui.components.AppIcon
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalVibrationManager
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeinateScreen(
    onNavigateBack: () -> Unit,
    viewModel: CaffeinateViewModel = hiltViewModel()
) {
    val isRunning by viewModel.isServiceRunning.collectAsState()
    val allApps by viewModel.allApps.collectAsState()
    val interval by viewModel.reminderInterval.collectAsState()
    val isInfinite by viewModel.isInfinite.collectAsState()
    val isCategorizing by viewModel.isCategorizing.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val hasPermission by viewModel.hasNotificationPermission.collectAsState()
    
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val primaryColorInt = MaterialTheme.colorScheme.primary.toArgb()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.checkServiceStatus() }

    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CAFFEINATE", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
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
                onToggleApp = { viewModel.toggleAppAutoEnable(it) }
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
    onClick: () -> Unit
) {
    val transition = updateTransition(isRunning, label = "liquid_toggle")
    val liquidLevel by transition.animateFloat(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy) },
        label = "level"
    ) { if (it) 1f else 0f }

    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_offset"
    )
    val waveOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_offset_2"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val infiniteGlow = rememberInfiniteTransition(label = "glow_inf")
    val infiniteAlpha by infiniteGlow.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    val glowAlpha = if (isRunning) infiniteAlpha else 0f

    Box(
        modifier = Modifier
            .size(240.dp)
            .bouncyClick(onClick = onClick)
            .aspectRatio(1f)
            .drawBehind {
                if (isRunning) {
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(primaryColor.copy(glowAlpha), Color.Transparent),
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
                    brush = Brush.sweepGradient(
                        colors = if (isRunning) listOf(primaryColor, secondaryColor, primaryColor)
                        else listOf(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.outlineVariant)
                    )
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

            // 1. Deepest wave (Tertiary)
            val pathDeep = Path().apply {
                moveTo(0f, fillHeight)
                for (x in 0..width.toInt()) {
                    val y = fillHeight + (sin((x.toFloat() / width * 2f * PI.toFloat()) + waveOffset2 * 0.8f) * 12f * liquidLevel)
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            // 2. Middle wave (Secondary)
            val pathRear = Path().apply {
                moveTo(0f, fillHeight)
                for (x in 0..width.toInt()) {
                    val y = fillHeight + (sin((x.toFloat() / width * 2.5f * PI.toFloat()) + waveOffset2) * 10f * liquidLevel)
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            // 3. Top wave (Primary)
            val pathFront = Path().apply {
                moveTo(0f, fillHeight)
                for (x in 0..width.toInt()) {
                    val y = fillHeight + (sin((x.toFloat() / width * 1.8f * PI.toFloat()) + waveOffset) * 16f * liquidLevel)
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            clipPath(Path().apply { addOval(Rect(0f, 0f, width, height)) }) {
                drawPath(
                    path = pathDeep,
                    brush = Brush.verticalGradient(
                        colors = listOf(tertiaryColor.copy(0.3f), tertiaryColor.copy(0.1f))
                    )
                )
                drawPath(
                    path = pathRear,
                    brush = Brush.verticalGradient(
                        colors = listOf(secondaryColor.copy(0.5f), primaryColor.copy(0.3f))
                    )
                )
                drawPath(
                    path = pathFront,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(0.9f), secondaryColor)
                    )
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Rounded.Coffee else Icons.Rounded.CoffeeMaker,
                contentDescription = null,
                modifier = Modifier.size(64.dp).graphicsLayer {
                    if (isRunning) {
                        scaleX = 1f + (glowAlpha * 0.2f)
                        scaleY = 1f + (glowAlpha * 0.2f)
                    }
                },
                tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isRunning) "ACTIVE" else "START",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
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
                    Text("REMINDER ALERT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(
                        if (isInfinite) "Running indefinitely" else "Alert every $interval mins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = !isInfinite,
                    onCheckedChange = { onInfiniteToggle(!it) },
                    thumbContent = {
                        Icon(
                            if (!isInfinite) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                            null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }

            AnimatedVisibility(visible = !isInfinite) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("5m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        Text("120m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                    }
                    Slider(
                        value = interval.toFloat(),
                        onValueChange = onIntervalChange,
                        valueRange = 5f..120f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(0.1f)
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
    onToggleApp: (CaffeinateApp) -> Unit
) {
    val categories = apps.groupBy { it.category }
    
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "AUTO-COFFEE CATEGORIES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
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
        }
        
        categories.forEach { (category, categoryApps) ->
            var expanded by remember { mutableStateOf(false) }
            
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.secondary.copy(0.1f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                when(category.uppercase()) {
                                    "WORK", "PRODUCTIVITY" -> Icons.Rounded.Work
                                    "ENTERTAINMENT" -> Icons.Rounded.Movie
                                    "SOCIAL" -> Icons.Rounded.Public
                                    "GAMES" -> Icons.Rounded.SportsEsports
                                    else -> Icons.Rounded.Category
                                },
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(category, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
    }
}
