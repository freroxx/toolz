package com.frerox.toolz.ui.screens.focus

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import coil3.compose.rememberAsyncImagePainter
import com.frerox.toolz.data.focus.AppCategory
import com.frerox.toolz.data.focus.AppUsageInfo
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.SquigglySlider
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.rememberLifecycleEvent
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
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
    val performanceMode = LocalPerformanceMode.current
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    val context = LocalContext.current
    
    val canDrawOverlays = remember { Settings.canDrawOverlays(context) }
    
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var selectedAppForSettings by remember { mutableStateOf<AppUsageInfo?>(null) }
    var appToRename by remember { mutableStateOf<AppUsageInfo?>(null) }
    
    val lifecycleEvent = rememberLifecycleEvent()
    
    LaunchedEffect(lifecycleEvent) {
        if (lifecycleEvent == Lifecycle.Event.ON_RESUME) {
            val accessibilityEnabled = try {
                val setting = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
                if (setting == 1) {
                    val service = "${context.packageName}/com.frerox.toolz.service.FocusFlowAccessibilityService"
                    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    enabledServices?.contains(service) == true
                } else false
            } catch (e: Exception) { false }
            isAccessibilityEnabled = accessibilityEnabled
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FOCUS FLOW", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp)
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
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.refreshStats() 
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp)),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!canDrawOverlays || !isAccessibilityEnabled) {
                    item {
                        Surface(
                            onClick = {
                                if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (!canDrawOverlays) {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().bouncyClick { },
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.error, RoundedCornerShape(18.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (!canDrawOverlays) Icons.Rounded.Layers else Icons.Rounded.AccessibilityNew, 
                                            null, 
                                            tint = MaterialTheme.colorScheme.onError,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            if (!canDrawOverlays) "OVERLAY REQUIRED" else "ACCESSIBILITY REQUIRED", 
                                            fontWeight = FontWeight.Black, 
                                            color = MaterialTheme.colorScheme.error, 
                                            style = MaterialTheme.typography.labelSmall,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            if (!canDrawOverlays) "CRITICAL FOR LIMITS" else "CRITICAL FOR ENGINE", 
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    if (!canDrawOverlays) "Grant overlay permission so Toolz can interrupt distracting apps effectively." 
                                    else "The focus engine requires accessibility access to analyze app changes and enforce your limits.", 
                                    style = MaterialTheme.typography.bodyMedium, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp
                                )
                                Spacer(Modifier.height(24.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("RESOLVE PERMISSION", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    EnhancedProductivityHeader(productivityScore, performanceMode)
                }

                item {
                    val totalTime = usageStats.sumOf { it.usageTimeMillis }
                    val hours = totalTime / 3600000
                    val minutes = (totalTime % 3600000) / 60000
                    val appCount = usageStats.size
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Text("SCREEN TIME", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "$appCount",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Text("APPS USED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ANALYTICS",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.height(44.dp)
                        ) {
                            SegmentedButton(
                                selected = !isWeekly,
                                onClick = { 
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.toggleWeekly(false) 
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Daily", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            SegmentedButton(
                                selected = isWeekly,
                                onClick = { 
                                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.toggleWeekly(true) 
                                },
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
                                    modifier = Modifier.size(100.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Icon(Icons.Rounded.Assessment, null, modifier = Modifier.padding(24.dp), tint = MaterialTheme.colorScheme.outline)
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("No usage data collected yet.", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                items(usageStats, key = { it.packageName }) { info ->
                    EnhancedUsageItem(
                        info = info, 
                        performanceMode = performanceMode,
                        onClick = { 
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedAppForSettings = info 
                        },
                        onLongClick = { 
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            appToRename = info 
                        },
                        onQuickLimit = { 
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.setAppLimit(info.packageName, it) 
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = if (performanceMode) snap() else spring(),
                            fadeOutSpec = if (performanceMode) snap() else spring(),
                            placementSpec = if (performanceMode) snap() else spring()
                        )
                    )
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

        val currentAppToRename = appToRename
        if (currentAppToRename != null) {
            var nameInput by remember(currentAppToRename.packageName) { 
                mutableStateOf(currentAppToRename.appName) 
            }
            
            AlertDialog(
                onDismissRequest = { appToRename = null },
                confirmButton = {
                    TextButton(onClick = {
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (nameInput.isNotBlank()) {
                            viewModel.renameApp(currentAppToRename.packageName, nameInput.trim())
                        }
                        appToRename = null
                    }) {
                        Text("SAVE", fontWeight = FontWeight.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        appToRename = null 
                    }) {
                        Text("CANCEL")
                    }
                },
                title = { Text("Rename App", fontWeight = FontWeight.Black) },
                text = {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { newVal -> nameInput = newVal },
                        label = { Text("Custom Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp)
                    )
                },
                shape = RoundedCornerShape(32.dp)
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
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    val initialMinutes = app.limitMillis?.div(60000) ?: 0L
    var selectedHours by remember { mutableStateOf((initialMinutes / 60).toInt()) }
    var selectedMins by remember { mutableStateOf((initialMinutes % 60).toInt()) }
    
    val isCurrentlyProductive = app.category == AppCategory.TOOLZ

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
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
                    Text(app.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
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
                        onClick = { 
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onUpdateCategory(isProd) 
                        },
                        modifier = Modifier.weight(1f).height(64.dp).bouncyClick {},
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
                                    modifier = Modifier.size(24.dp), 
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

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(15, 30, 60, 120).forEach { mins ->
                    val isSelected = (selectedHours * 60 + selectedMins) == mins
                    val activeColor = MaterialTheme.colorScheme.primary
                    
                    Surface(
                        onClick = {
                            if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedHours = mins / 60
                            selectedMins = mins % 60
                        },
                        modifier = Modifier.weight(1f).height(40.dp).bouncyClick { },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) activeColor else activeColor.copy(alpha = 0.1f),
                        border = if (isSelected) null else BorderStroke(1.dp, activeColor.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (mins < 60) "${mins}m" else "${mins / 60}h" , 
                                style = MaterialTheme.typography.labelSmall, 
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else activeColor
                            )
                        }
                    }
                }
                
                Surface(
                    onClick = {
                        if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedHours = 0
                        selectedMins = 0
                    },
                    modifier = Modifier.weight(1f).height(40.dp).bouncyClick { },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        @Suppress("DEPRECATION")
                        Text("RESET", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CUSTOM DURATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                if (selectedHours == 0 && selectedMins == 0) {
                    Text("No Limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(0.6f).height(48.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {}

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScrollableNumberPicker(
                            range = 0..23,
                            selectedItem = selectedHours,
                            onItemSelected = { selectedHours = it },
                            label = "h"
                        )
                        
                        Spacer(Modifier.width(24.dp))
                        Text(":", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(0.5f))
                        Spacer(Modifier.width(24.dp))
                        
                        ScrollableNumberPicker(
                            range = 0..59,
                            selectedItem = selectedMins,
                            onItemSelected = { selectedMins = it },
                            label = "m",
                            formatTwoDigits = true
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { 
                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val totalMins = (selectedHours * 60L) + selectedMins
                    onSaveLimit(totalMins)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
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
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timeline, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("WEEKLY INVESTMENT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                style = MaterialTheme.typography.headlineLarge,
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
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LegendItem("Productive", MaterialTheme.colorScheme.primary)
                LegendItem("Distraction", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EnhancedProductivityHeader(score: Int, performanceMode: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    
    val isElite = score > 85
    val glowTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by if (performanceMode) remember { mutableFloatStateOf(0.4f) } else glowTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (performanceMode) Modifier else Modifier.shadow(
                elevation = if (isElite) 24.dp else 12.dp, 
                shape = RoundedCornerShape(40.dp), 
                spotColor = if (isElite) primary else primary.copy(alpha = 0.3f)
            )),
        shape = RoundedCornerShape(40.dp),
        color = container.copy(alpha = 0.1f),
        border = BorderStroke(
            2.dp, 
            if (performanceMode) SolidColor(primary.copy(alpha = 0.3f)) 
            else Brush.verticalGradient(listOf(primary.copy(alpha = if (isElite) glowAlpha else 0.5f), Color.Transparent))
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 40.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                val infiniteTransition = rememberInfiniteTransition(label = "arc")
                val rotation by if (performanceMode) remember { mutableFloatStateOf(0f) } else infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
                    label = "rotation"
                )

                Canvas(modifier = Modifier.size(200.dp).rotate(rotation)) {
                    drawArc(
                        color = primary.copy(alpha = 0.1f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = (score / 100f) * 360f,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isElite) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            "FLOW STATE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = primary,
                            letterSpacing = 2.sp
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.Bottom) {
                        val animatedScore by animateIntAsState(targetValue = score, animationSpec = if (performanceMode) snap() else tween(1500, easing = FastOutSlowInEasing), label = "")
                        Text(
                            "$animatedScore",
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer {
                                if (isElite && !performanceMode) {
                                    val pulse = glowAlpha * 0.04f
                                    scaleX = 1f + pulse
                                    scaleY = 1f + pulse
                                }
                            }
                        )
                        Text(
                            "%",
                            modifier = Modifier.padding(bottom = 18.dp, start = 4.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            
            SquigglySlider(
                value = score.toFloat(),
                onValueChange = {},
                valueRange = 0f..100f,
                isPlaying = !performanceMode
            )
            
            Spacer(Modifier.height(32.dp))
            
            Surface(
                color = (if (score > 50) primary else MaterialTheme.colorScheme.error).copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, (if (score > 50) primary else MaterialTheme.colorScheme.error).copy(alpha = 0.2f))
            ) {
                Text(
                    when {
                        score > 85 -> "ELITE FLOW STATE"
                        score > 70 -> "HIGH PRODUCTIVITY"
                        score > 50 -> "BALANCED FOCUS"
                        score > 30 -> "MODERATE DISTRACTION"
                        else -> "CRITICAL REFOCUS"
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = if (score > 50) primary else MaterialTheme.colorScheme.error,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedUsageItem(
    info: AppUsageInfo, 
    performanceMode: Boolean,
    onClick: () -> Unit, 
    onLongClick: () -> Unit,
    onQuickLimit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(if (visible || performanceMode) 1f else 0.9f, if (performanceMode) snap() else spring(Spring.DampingRatioMediumBouncy), label = "")
    val alpha by animateFloatAsState(if (visible || performanceMode) 1f else 0f, if (performanceMode) snap() else tween(500), label = "")

    val hours = info.usageTimeMillis / 3600000
    val minutes = (info.usageTimeMillis % 3600000) / 60000
    val timeStr = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    val isOverLimit = info.limitMillis != null && info.usageTimeMillis >= info.limitMillis

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.5.dp, 
            if (isOverLimit) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = if (performanceMode) 0.dp else 1.dp
                ) {
                    if (info.icon != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = info.icon),
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Android, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        }
                    }
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                                else -> "OTHER"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
                            color = when(info.category) {
                                AppCategory.TOOLZ -> MaterialTheme.colorScheme.primary
                                AppCategory.DISTRACTION -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }
            
            if (info.limitMillis != null) {
                val progress = (info.usageTimeMillis.toFloat() / info.limitMillis).coerceIn(0f, 1.2f)
                val barProgress = progress.coerceIn(0f, 1f)
                val color = when {
                    progress >= 1f -> MaterialTheme.colorScheme.error
                    progress > 0.8f -> Color(0xFFFFA000)
                    else -> MaterialTheme.colorScheme.primary
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(barProgress)
                            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = if (barProgress >= 1f) 28.dp else 0.dp))
                            .background(color)
                    )
                }
            } else if (info.category == AppCategory.DISTRACTION) {
                Surface(
                    onClick = { onQuickLimit(30) },
                    modifier = Modifier.padding(bottom = 12.dp, start = 16.dp, end = 16.dp).height(32.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Limit to 30m", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun ScrollableNumberPicker(
    range: IntRange,
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    label: String,
    formatTwoDigits: Boolean = false,
    visibleItemsCount: Int = 3
) {
    val hapticEnabled = LocalHapticEnabled.current
    val view = LocalView.current
    val items = range.toList()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(selectedItem).coerceAtLeast(0))
    val itemHeight = 44.dp
    
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
            val centerOffset = listState.layoutInfo.viewportEndOffset / 2
            val closestItem = listState.layoutInfo.visibleItemsInfo.minByOrNull {
                kotlin.math.abs((it.offset + it.size / 2) - centerOffset)
            }
            if (closestItem != null) {
                val index = closestItem.index
                if (index in items.indices) {
                    onItemSelected(items[index])
                    listState.animateScrollToItem(index)
                    if (hapticEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }

    LaunchedEffect(selectedItem) {
        val index = items.indexOf(selectedItem)
        if (index >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(index)
        }
    }

    Box(
        modifier = Modifier.height(itemHeight * visibleItemsCount).width(64.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2))
        ) {
            items(items) { item ->
                val isSelected = item == selectedItem
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.25f else 0.75f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.35f,
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (formatTwoDigits) String.format("%02d", item) else item.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.scale(scale).alpha(alpha)
                        )
                        if (isSelected) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp).alpha(alpha)
                            )
                        }
                    }
                }
            }
        }
    }
}
