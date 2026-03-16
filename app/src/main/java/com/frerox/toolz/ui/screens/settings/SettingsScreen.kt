package com.frerox.toolz.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onResetOnboarding: () -> Unit
) {
    val stepGoal by viewModel.stepGoal.collectAsState(initial = 10000)
    val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")
    val dynamicColor by viewModel.dynamicColor.collectAsState(initial = true)
    val customPrimaryInt by viewModel.customPrimaryColor.collectAsState(initial = null)
    val customSecondaryInt by viewModel.customSecondaryColor.collectAsState(initial = null)
    
    val dashboardView by viewModel.dashboardView.collectAsState(initial = "DEFAULT")

    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val stepNotifications by viewModel.stepNotifications.collectAsState(initial = true)
    val timerNotifications by viewModel.timerNotifications.collectAsState(initial = true)
    val musicNotifications by viewModel.musicNotifications.collectAsState(initial = true)

    val widgetBgColor by viewModel.widgetBackgroundColor.collectAsState(initial = 0xFFFFFFFF.toInt())
    val widgetOpacity by viewModel.widgetOpacity.collectAsState(initial = 0.9f)
    
    val hapticFeedback by viewModel.hapticFeedback.collectAsState(initial = true)
    val hapticIntensity by viewModel.hapticIntensity.collectAsState(initial = 0.5f)
    val unitSystem by viewModel.unitSystem.collectAsState(initial = "METRIC")
    val showQibla by viewModel.showQibla.collectAsState(initial = false)
    val stepCounterEnabled by viewModel.stepCounterEnabled.collectAsState(initial = true)
    val showToolzPill by viewModel.showToolzPill.collectAsState(initial = true)
    val userName by viewModel.userName.collectAsState(initial = "")
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState(initial = false)

    val musicShakeToSkip by viewModel.musicShakeToSkip.collectAsState(initial = false)
    val musicAudioFocus by viewModel.musicAudioFocus.collectAsState(initial = true)

    var showResetDialog by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState(initial = "")

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("PURGE ALL DATA?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = { Text("This will reset your profile, preferences, and the core engine to factory state.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetOnboarding()
                        showResetDialog = false
                        onResetOnboarding()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("FACTORY RESET", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(40.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SYSTEM SETTINGS", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fadingEdge(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), 24.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Spacer(Modifier.height(12.dp))
                
                if (matches(searchQuery, "update", "auto", "version", "engine")) {
                    SettingsSection(title = "CORE ENGINE") {
                        SettingsToggleItem(
                            title = "Auto-Update Infrastructure",
                            subtitle = "Automated background patches",
                            icon = Icons.Rounded.AutoFixHigh,
                            checked = autoUpdateEnabled,
                            onCheckedChange = { viewModel.setAutoUpdateEnabled(it) }
                        )
                        SettingsItem(
                            title = "Update Deployment",
                            subtitle = "Manual version check & install",
                            icon = Icons.Rounded.SystemUpdate,
                            onClick = onNavigateToUpdate
                        )
                    }
                }

                if (matches(searchQuery, "step goal", "health", "units", "qibla", "compass", "haptic", "vibration", "step counter", "tracker", "pill", "dashboard", "onboarding", "profile", "name", "view", "list")) {
                    SettingsSection(title = "USER INTERFACE") {
                        SettingsToggleItem(
                            title = "Toolz Smart Pill",
                            subtitle = "Dynamic interactive foreground overlay",
                            icon = Icons.Rounded.SmartButton,
                            checked = showToolzPill,
                            onCheckedChange = { viewModel.setShowToolzPill(it) }
                        )
                        
                        SettingsItem(
                            title = "Dashboard Viewport",
                            subtitle = "Selected: ${dashboardView.lowercase()}",
                            icon = Icons.Rounded.Dashboard
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("DEFAULT", "LIST").forEach { view ->
                                    val isSelected = dashboardView == view
                                    Surface(
                                        onClick = { viewModel.setDashboardView(view) },
                                        modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(view, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        SettingsToggleItem(
                            title = "Movement Engine",
                            subtitle = "Precision background activity tracking",
                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                            checked = stepCounterEnabled,
                            onCheckedChange = { viewModel.setStepCounterEnabled(it) }
                        )

                        SettingsItem(
                            title = "Identity Matrix",
                            subtitle = "Display: $userName",
                            icon = Icons.Rounded.AccountCircle
                        ) {
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { viewModel.setUserName(it) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                placeholder = { Text("Display Name") },
                                shape = RoundedCornerShape(20.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }

                        SettingsItem(
                            title = "Metric Configuration",
                            subtitle = "Standard: ${unitSystem.lowercase()}",
                            icon = Icons.Rounded.SquareFoot
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("METRIC", "IMPERIAL").forEach { unit ->
                                    val isSelected = unitSystem == unit
                                    Surface(
                                        onClick = { viewModel.setUnitSystem(unit) },
                                        modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(unit, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        
                        SettingsToggleItem(
                            title = "Haptic Feed Engine",
                            subtitle = "High-precision tactile feedback",
                            icon = Icons.Rounded.Vibration,
                            checked = hapticFeedback,
                            onCheckedChange = { viewModel.setHapticFeedback(it) }
                        )

                        if (hapticFeedback) {
                            SettingsItem(
                                title = "Vibration Intensity",
                                subtitle = "Amplitude: ${(hapticIntensity * 100).toInt()}%",
                                icon = Icons.Rounded.GraphicEq
                            ) {
                                Slider(
                                    value = hapticIntensity,
                                    onValueChange = { viewModel.setHapticIntensity(it) },
                                    valueRange = 0.1f..1f,
                                    modifier = Modifier.padding(top = 8.dp),
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        SettingsToggleItem(
                            title = "Advanced Orientation",
                            subtitle = "Integrate Qibla in Compass engine",
                            icon = Icons.Rounded.Explore,
                            checked = showQibla,
                            onCheckedChange = { viewModel.setShowQibla(it) }
                        )

                        SettingsItem(
                            title = "Daily Milestone",
                            subtitle = "Target: $stepGoal steps",
                            icon = Icons.Rounded.EmojiEvents
                        ) {
                            Slider(
                                value = stepGoal.toFloat(),
                                onValueChange = { viewModel.setStepGoal(it.toInt()) },
                                valueRange = 1000f..30000f,
                                steps = 29,
                                modifier = Modifier.padding(top = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                if (matches(searchQuery, "music", "shake", "skip", "audio focus")) {
                    SettingsSection(title = "MULTIMEDIA ENGINE") {
                        SettingsToggleItem(
                            title = "Physical Skip Gesture",
                            subtitle = "Shake device to advance track",
                            icon = Icons.Rounded.PhonelinkRing,
                            checked = musicShakeToSkip,
                            onCheckedChange = { viewModel.setMusicShakeToSkip(it) }
                        )
                        SettingsToggleItem(
                            title = "Audio Matrix Management",
                            subtitle = "Dynamic focus control for playback",
                            icon = Icons.Rounded.Hearing,
                            checked = musicAudioFocus,
                            onCheckedChange = { viewModel.setMusicAudioFocus(it) }
                        )
                    }
                }

                if (matches(searchQuery, "notifications", "tracker", "alerts", "music")) {
                    SettingsSection(title = "ALERT INFRASTRUCTURE") {
                        SettingsToggleItem(
                            title = "Global Notification Matrix",
                            subtitle = "Master switch for all system alerts",
                            icon = Icons.Rounded.NotificationsActive,
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                        
                        if (notificationsEnabled) {
                            SettingsToggleItem(
                                title = "Achievement Alerts",
                                subtitle = "Fitness milestone notifications",
                                icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                checked = stepNotifications,
                                onCheckedChange = { viewModel.setStepNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Countdown Protocols",
                                subtitle = "Timer & Pomodoro status alerts",
                                icon = Icons.Rounded.Timer,
                                checked = timerNotifications,
                                onCheckedChange = { viewModel.setTimerNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Media Payload Info",
                                subtitle = "Interactive playback alerts",
                                icon = Icons.Rounded.MusicNote,
                                checked = musicNotifications,
                                onCheckedChange = { viewModel.setMusicNotifications(it) }
                            )
                        }
                    }
                }

                if (matches(searchQuery, "appearance", "theme", "dark mode", "colors", "secondary")) {
                    SettingsSection(title = "VISUAL MATRIX") {
                        SettingsToggleItem(
                            title = "Dynamic Chromaticism",
                            subtitle = "Synchronize with system wallpaper palette",
                            icon = Icons.Rounded.Palette,
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                        
                        if (!dynamicColor) {
                            SettingsItem(
                                title = "Chroma Calibration",
                                subtitle = "Configure primary and secondary tones",
                                icon = Icons.Rounded.ColorLens
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("PRIMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
                                            IconButton(onClick = { viewModel.setCustomPrimaryColor(null) }, modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))) {
                                                Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        ColorPickerRow(
                                            selectedColor = customPrimaryInt ?: Color(0xFF2962FF).toArgb(),
                                            onColorSelected = { viewModel.setCustomPrimaryColor(it) }
                                        )
                                    }
                                    
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("SECONDARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.5.sp)
                                            IconButton(onClick = { viewModel.setCustomSecondaryColor(null) }, modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))) {
                                                Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        ColorPickerRow(
                                            selectedColor = customSecondaryInt ?: Color(0xFF00BFA5).toArgb(),
                                            onColorSelected = { viewModel.setCustomSecondaryColor(it) }
                                        )
                                    }
                                }
                            }
                        }
                        
                        SettingsItem(
                            title = "Interface Modality",
                            subtitle = "Active: ${themeMode.lowercase()}",
                            icon = Icons.Rounded.DarkMode
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                    val isSelected = themeMode == mode
                                    Surface(
                                        onClick = { viewModel.setThemeMode(mode) },
                                        modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(mode, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (matches(searchQuery, "widget", "color", "opacity")) {
                    SettingsSection(title = "WIDGET ARCHITECTURE") {
                        SettingsItem(
                            title = "Desktop Aesthetic",
                            subtitle = "Visual background & transparency configuration",
                            icon = Icons.Rounded.SettingsSuggest
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                ColorPickerRow(
                                    selectedColor = widgetBgColor,
                                    onColorSelected = { viewModel.setWidgetBackgroundColor(it) }
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Opacity, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(16.dp))
                                    Slider(
                                        value = widgetOpacity,
                                        onValueChange = { viewModel.setWidgetOpacity(it) },
                                        valueRange = 0.1f..1f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text("${(widgetOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.width(40.dp))
                                }
                            }
                        }
                    }
                }

                AboutSection(onCheckUpdate = onNavigateToUpdate)
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

private fun matches(query: String, vararg keywords: String): Boolean {
    if (query.isEmpty()) return true
    return keywords.any { it.contains(query, ignoreCase = true) }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { 
            Text(
                "Search engine parameters...", 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            ) 
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .shadow(
                elevation = 12.dp, 
                shape = RoundedCornerShape(24.dp), 
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ),
        leadingIcon = { 
            Icon(
                Icons.Rounded.Search, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            ) 
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(24.dp))
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun AboutSection(onCheckUpdate: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(48.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 16.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Bolt, 
                        contentDescription = null, 
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("TOOLZ PRO", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("ENGINE V1.0.0 (BETA)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "A precision instrument kit for Android hardware orchestration and productivity optimization. Designed with high-fidelity principles by frerox.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onCheckUpdate,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("CHECK FOR ENGINE UPDATES", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun ColorPickerRow(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val colors = listOf(
            Color(0xFF2962FF), Color(0xFF00BFA5), Color(0xFFFF6D00), 
            Color(0xFFD50000), Color(0xFFAA00FF), Color(0xFF00C853),
            Color(0xFFE91E63), Color(0xFF673AB7), Color(0xFF03A9F4),
            Color.White, Color.Black
        )
        colors.forEach { color ->
            val argb = color.toArgb()
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selectedColor == argb) 3.dp else 1.dp,
                        color = if (selectedColor == argb) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(argb) }
            ) {
                if (selectedColor == argb) {
                    Icon(
                        Icons.Rounded.Check, 
                        null, 
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.align(Alignment.Center).size(24.dp)
                    )
                }
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    extraContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bouncyClick(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Black)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                }
            }
            extraContent?.let {
                it()
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick { onCheckedChange(!checked) },
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Black)
                }
            }
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.85f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
