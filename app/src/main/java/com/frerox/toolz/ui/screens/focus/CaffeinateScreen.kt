package com.frerox.toolz.ui.screens.focus

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.focus.CaffeinateApp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalVibrationManager
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
    
    val vibrationManager = LocalVibrationManager.current

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
                    IconButton(onClick = { viewModel.refreshAppCategories() }) {
                        Icon(
                            Icons.Rounded.AutoAwesome, 
                            contentDescription = "AI Categorize",
                            tint = if (isCategorizing) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Liquid Toggle Button
            LiquidToggleButton(
                isRunning = isRunning,
                onClick = {
                    vibrationManager?.vibrateClick()
                    viewModel.toggleService()
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
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "wave_offset"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .bouncyClick(onClick = onClick)
            .clip(CircleShape)
            .border(4.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val fillHeight = height * (1f - liquidLevel)

            val path = Path().apply {
                moveTo(0f, fillHeight)
                for (x in 0..width.toInt()) {
                    val y = fillHeight + (sin(x.toFloat() / width * 2 * Math.PI.toFloat() + waveOffset) * 15f * liquidLevel)
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            clipPath(Path().apply { addOval(Rect(0f, 0f, width, height)) }) {
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFD2691E).copy(0.8f), // Coffee/Amber
                            Color(0xFF8B4513)
                        )
                    )
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isRunning) Icons.Rounded.Coffee else Icons.Rounded.CoffeeMaker,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (isRunning) "ACTIVE" else "START",
                fontWeight = FontWeight.Black,
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("REMINDER ALERT", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Switch(checked = !isInfinite, onCheckedChange = { onInfiniteToggle(!it) })
            }

            if (!isInfinite) {
                Text("Interval: $interval minutes", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = interval.toFloat(),
                    onValueChange = onIntervalChange,
                    valueRange = 5f..120f,
                    steps = 23
                )
            } else {
                Text("Screen will stay on until manually stopped.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("AUTO-COFFEE CATEGORIES", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        
        categories.forEach { (category, categoryApps) ->
            var expanded by remember { mutableStateOf(false) }
            
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .clickable { expanded = !expanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${categoryApps.count { it.isAutoEnabled }}/${categoryApps.size}", style = MaterialTheme.typography.labelSmall)
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    
                    AnimatedVisibility(visible = expanded) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            categoryApps.forEach { app ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    AppIcon(packageName = app.packageName, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(app.appName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Checkbox(checked = app.isAutoEnabled, onCheckedChange = { onToggleApp(app) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
