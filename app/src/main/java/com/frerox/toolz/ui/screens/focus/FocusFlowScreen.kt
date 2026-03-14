package com.frerox.toolz.ui.screens.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.rememberAsyncImagePainter
import com.frerox.toolz.data.focus.AppCategory
import com.frerox.toolz.data.focus.AppUsageInfo
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.SquigglySlider
import com.frerox.toolz.ui.components.fadingEdge
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusFlowScreen(
    onNavigateBack: () -> Unit,
    viewModel: FocusFlowViewModel = hiltViewModel()
) {
    val usageStats by viewModel.combinedUsageStats.collectAsState(initial = emptyList())
    val productivityScore by viewModel.productivityScore.collectAsState()
    val isWeekly by viewModel.isWeekly.collectAsState()
    var selectedAppForSettings by remember { mutableStateOf<AppUsageInfo?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FOCUS FLOW", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
                        Text(
                            if (isWeekly) "WEEKLY PERFORMANCE" else "TODAY'S USAGE", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .fadingEdge(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.05f to Color.Black,
                            0.95f to Color.Black,
                            1f to Color.Transparent
                        ),
                        length = 24.dp
                    ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    EnhancedProductivityHeader(productivityScore)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "ANALYTICS",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.height(40.dp)
                        ) {
                            SegmentedButton(
                                selected = !isWeekly,
                                onClick = { viewModel.toggleWeekly(false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Daily", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            SegmentedButton(
                                selected = isWeekly,
                                onClick = { viewModel.toggleWeekly(true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Weekly", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (isWeekly) {
                    item {
                        WeeklySummaryCard(usageStats)
                    }
                }

                if (usageStats.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    modifier = Modifier.size(80.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Icon(Icons.Rounded.Assessment, null, modifier = Modifier.padding(20.dp), tint = MaterialTheme.colorScheme.outline)
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("No usage data collected yet.", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                items(usageStats, key = { it.packageName }) { info ->
                    EnhancedUsageItem(info, onClick = { selectedAppForSettings = info })
                }
                
                item {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        selectedAppForSettings?.let { app ->
            FocusAppSettingsSheet(
                app = app,
                onDismiss = { selectedAppForSettings = null },
                onSaveLimit = { minutes ->
                    if (minutes > 0) {
                        viewModel.setAppLimit(app.packageName, minutes)
                    } else {
                        viewModel.removeAppLimit(app.packageName)
                    }
                },
                onUpdateCategory = { isProductive ->
                    viewModel.updateAppCategory(app.packageName, isProductive)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusAppSettingsSheet(
    app: AppUsageInfo,
    onDismiss: () -> Unit,
    onSaveLimit: (Long) -> Unit,
    onUpdateCategory: (Boolean) -> Unit
) {
    var limitInput by remember { mutableStateOf((app.limitMillis?.div(60000) ?: 0L).toString()) }
    val isCurrentlyProductive = app.category == AppCategory.TOOLZ

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 48.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(64.dp), 
                    shape = RoundedCornerShape(18.dp), 
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = app.icon), 
                        contentDescription = null, 
                        modifier = Modifier.padding(10.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(app.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(32.dp))
            Text("CLASSIFICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(true to "Productive", false to "Distraction").forEach { (isProd, label) ->
                    val isSelected = isCurrentlyProductive == isProd
                    val activeColor = if (isProd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    
                    Surface(
                        onClick = { onUpdateCategory(isProd) },
                        modifier = Modifier.weight(1f).height(60.dp).bouncyClick {},
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(
                            2.dp, 
                            if (isSelected) activeColor else Color.Transparent
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isProd) Icons.Rounded.AddModerator else Icons.Rounded.Block, 
                                    null, 
                                    modifier = Modifier.size(20.dp), 
                                    tint = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    label, 
                                    fontWeight = FontWeight.Black, 
                                    color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("DAILY TIME LIMIT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            
            OutlinedTextField(
                value = limitInput,
                onValueChange = { if (it.all { char -> char.isDigit() }) limitInput = it },
                label = { Text("Minutes") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                placeholder = { Text("0 to disable limit") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                trailingIcon = {
                    if (limitInput.isNotEmpty()) {
                        IconButton(onClick = { limitInput = "" }) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
            
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { 
                    onSaveLimit(limitInput.toLongOrNull() ?: 0L)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("APPLY SETTINGS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun WeeklySummaryCard(stats: List<AppUsageInfo>) {
    val totalTime = stats.sumOf { it.usageTimeMillis }
    val hours = totalTime / 3600000
    val minutes = (totalTime % 3600000) / 60000
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timeline, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("WEEKLY INVESTMENT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            ).toString() // Just to avoid unused warning if needed, but Text is correct
            Text(
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))
            
            val toolzTime = stats.filter { it.category == AppCategory.TOOLZ }.sumOf { it.usageTimeMillis }
            val distractionTime = stats.filter { it.category == AppCategory.DISTRACTION }.sumOf { it.usageTimeMillis }
            
            Row(modifier = Modifier.fillMaxWidth().height(16.dp).clip(CircleShape).background(surfaceVariant.copy(alpha = 0.5f))) {
                if (totalTime > 0) {
                    val toolzWeight = (toolzTime.toFloat() / totalTime).coerceIn(0f, 1f)
                    val distractionWeight = (distractionTime.toFloat() / totalTime).coerceIn(0f, 1f)
                    
                    if (toolzWeight > 0) {
                        Box(modifier = Modifier.fillMaxHeight().weight(toolzWeight).background(MaterialTheme.colorScheme.primary))
                    }
                    if (distractionWeight > 0) {
                        Box(modifier = Modifier.fillMaxHeight().weight(distractionWeight).background(MaterialTheme.colorScheme.error))
                    }
                    val otherWeight = (1f - toolzWeight - distractionWeight).coerceAtLeast(0f)
                    if (otherWeight > 0) {
                        Box(modifier = Modifier.fillMaxHeight().weight(otherWeight).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem("Productive", MaterialTheme.colorScheme.primary)
                LegendItem("Distraction", MaterialTheme.colorScheme.error)
                LegendItem("Other", MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EnhancedProductivityHeader(score: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(40.dp), spotColor = primary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(40.dp),
        color = container.copy(alpha = 0.1f),
        border = BorderStroke(2.dp, Brush.verticalGradient(listOf(primary.copy(alpha = 0.5f), Color.Transparent)))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "FOCUS EFFICIENCY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = primary,
                letterSpacing = 2.sp
            )
            
            Row(verticalAlignment = Alignment.Bottom) {
                val animatedScore by animateIntAsState(targetValue = score, animationSpec = tween(1500, easing = FastOutSlowInEasing), label = "")
                Text(
                    "$animatedScore",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "%",
                    modifier = Modifier.padding(bottom = 18.dp, start = 4.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = primary
                )
            }

            Spacer(Modifier.height(12.dp))
            
            SquigglySlider(
                value = score.toFloat(),
                onValueChange = {},
                onValueChangeFinished = {},
                valueRange = 0f..100f,
                isPlaying = true
            )
            
            Spacer(Modifier.height(20.dp))
            
            Surface(
                color = (if (score > 50) primary else MaterialTheme.colorScheme.error).copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    when {
                        score > 85 -> "ELITE FLOW STATE"
                        score > 70 -> "HIGH PRODUCTIVITY"
                        score > 50 -> "BALANCED FOCUS"
                        score > 30 -> "MODERATE DISTRACTION"
                        else -> "CRITICAL REFOCUS NEEDED"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = if (score > 50) primary else MaterialTheme.colorScheme.error,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun EnhancedUsageItem(info: AppUsageInfo, onClick: () -> Unit) {
    val hours = info.usageTimeMillis / 3600000
    val minutes = (info.usageTimeMillis % 3600000) / 60000
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    val isOverLimit = info.limitMillis != null && info.usageTimeMillis >= info.limitMillis

    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.dp, 
            if (isOverLimit) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        shadowElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = info.icon),
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        info.appName, 
                        fontWeight = FontWeight.Black, 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (info.limitMillis != null) {
                            Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Limit: ${info.limitMillis / 60000}m", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                info.packageName.lowercase(), 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        timeStr, 
                        fontWeight = FontWeight.Black, 
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = when(info.category) {
                            AppCategory.TOOLZ -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            AppCategory.DISTRACTION -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            when(info.category) {
                                AppCategory.TOOLZ -> "PRODUCTIVE"
                                AppCategory.DISTRACTION -> "DISTRACTION"
                                else -> "UNCATEGORIZED"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = when(info.category) {
                                AppCategory.TOOLZ -> MaterialTheme.colorScheme.primary
                                AppCategory.DISTRACTION -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
            
            // Progress bar if limit exists
            if (info.limitMillis != null) {
                val progress = (info.usageTimeMillis.toFloat() / info.limitMillis).coerceIn(0f, 1.2f)
                val barProgress = progress.coerceIn(0f, 1f)
                val color = when {
                    progress >= 1f -> MaterialTheme.colorScheme.error
                    progress > 0.8f -> Color(0xFFFFA000) // Warning orange
                    else -> MaterialTheme.colorScheme.primary
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barProgress)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(color.copy(alpha = 0.7f), color)
                                )
                            )
                    )
                }
            }
        }
    }
}
