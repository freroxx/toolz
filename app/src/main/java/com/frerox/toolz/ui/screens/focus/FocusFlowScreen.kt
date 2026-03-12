package com.frerox.toolz.ui.screens.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
    var selectedAppForLimit by remember { mutableStateOf<AppUsageInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Focus Flow", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (isWeekly) "Weekly Performance" else "Today's Usage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    EnhancedProductivityHeader(productivityScore)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SCREEN TIME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = !isWeekly,
                                onClick = { viewModel.toggleWeekly(false) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Today", style = MaterialTheme.typography.labelSmall)
                            }
                            SegmentedButton(
                                selected = isWeekly,
                                onClick = { viewModel.toggleWeekly(true) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Weekly", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (isWeekly) {
                    item {
                        WeeklySummaryCard(usageStats)
                    }
                }

                items(usageStats, key = { it.packageName }) { info ->
                    EnhancedUsageItem(info, onClick = { selectedAppForLimit = info })
                }
                
                item {
                    Spacer(Modifier.height(100.dp))
                }
            }
        }

        selectedAppForLimit?.let { app ->
            AppLimitDialog(
                app = app,
                onDismiss = { selectedAppForLimit = null },
                onSave = { minutes ->
                    if (minutes > 0) {
                        viewModel.setAppLimit(app.packageName, minutes)
                    } else {
                        viewModel.removeAppLimit(app.packageName)
                    }
                    selectedAppForLimit = null
                }
            )
        }
    }
}

@Composable
fun WeeklySummaryCard(stats: List<AppUsageInfo>) {
    val totalTime = stats.sumOf { it.usageTimeMillis }
    val hours = totalTime / 3600000
    val minutes = (totalTime % 3600000) / 60000
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Weekly Total", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            
            // Distribution bar
            val toolzTime = stats.filter { it.category == AppCategory.TOOLZ }.sumOf { it.usageTimeMillis }
            val distractionTime = stats.filter { it.category == AppCategory.DISTRACTION }.sumOf { it.usageTimeMillis }
            
            Row(modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (totalTime > 0) {
                    Box(modifier = Modifier.fillMaxHeight().weight((toolzTime.toFloat() / totalTime).coerceAtLeast(0.01f)).background(MaterialTheme.colorScheme.primary))
                    Box(modifier = Modifier.fillMaxHeight().weight((distractionTime.toFloat() / totalTime).coerceAtLeast(0.01f)).background(MaterialTheme.colorScheme.error))
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem("Productive", MaterialTheme.colorScheme.primary)
                LegendItem("Distraction", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EnhancedProductivityHeader(score: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(40.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "FOCUS EFFICIENCY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$score",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "%",
                    modifier = Modifier.padding(bottom = 14.dp, start = 4.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(8.dp))
            
            SquigglySlider(
                value = score.toFloat(),
                onValueChange = {},
                onValueChangeFinished = {},
                valueRange = 0f..100f,
                isPlaying = true
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                when {
                    score > 80 -> "Peak Performance Active"
                    score > 60 -> "Strong Focus Maintained"
                    score > 40 -> "Balanced Activity"
                    else -> "Focus Improvement Required"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = if (score > 50) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun EnhancedUsageItem(info: AppUsageInfo, onClick: () -> Unit) {
    val hours = info.usageTimeMillis / 3600000
    val minutes = (info.usageTimeMillis % 3600000) / 60000
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = info.icon),
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info.appName, 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (info.limitMillis != null) "Limit: ${info.limitMillis / 60000}m" else info.packageName.lowercase(), 
                    style = MaterialTheme.typography.labelSmall, 
                    color = if (info.limitMillis != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    timeStr, 
                    fontWeight = FontWeight.Black, 
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (info.category == AppCategory.DISTRACTION) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when(info.category) {
                        AppCategory.TOOLZ -> "Productive"
                        AppCategory.DISTRACTION -> "Distraction"
                        else -> "Other"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = when(info.category) {
                        AppCategory.TOOLZ -> MaterialTheme.colorScheme.primary
                        AppCategory.DISTRACTION -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }
        }
    }
}

@Composable
fun AppLimitDialog(
    app: AppUsageInfo,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var limitInput by remember { mutableStateOf((app.limitMillis?.div(60000) ?: 0L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Limit", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Image(
                            painter = rememberAsyncImagePainter(model = app.icon), 
                            contentDescription = null, 
                            modifier = Modifier.padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(app.appName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(16.dp))
                Text("Lock app after specified minutes of daily use.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) limitInput = it },
                    label = { Text("Limit in minutes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("0 to disable") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(limitInput.toLongOrNull() ?: 0L) }) {
                Text("SAVE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
