package com.frerox.toolz.ui.screens.settings

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    onResetOnboarding: () -> Unit
) {
    val stepGoal by viewModel.stepGoal.collectAsState(initial = 10000)
    val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")
    val dynamicColor by viewModel.dynamicColor.collectAsState(initial = true)
    val customPrimaryInt by viewModel.customPrimaryColor.collectAsState(initial = null)
    val customSecondaryInt by viewModel.customSecondaryColor.collectAsState(initial = null)

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

    var showResetDialog by remember { mutableStateOf(false) }
    
    val searchQuery by viewModel.searchQuery.collectAsState(initial = "")

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("RESET DATA?", fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("This will clear your profile name and preferences. You'll need to go through onboarding again.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetOnboarding()
                        showResetDialog = false
                        onResetOnboarding()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("RESET ALL", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(36.dp)
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.background(Color.Transparent).statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        }
                        
                        Text(
                            text = "CONFIGURATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )

                        IconButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }

                    SearchField(
                        query = searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) }
                    )
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdge(
                        brush = Brush.verticalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent),
                        length = 48.dp
                    )
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                
                if (matches(searchQuery, "step goal", "health", "units", "qibla", "compass", "haptic", "vibration", "step counter", "tracker", "pill", "dashboard", "onboarding", "profile", "name")) {
                    SettingsSection(title = "GENERAL PREFERENCES") {
                        SettingsToggleItem(
                            title = "Show Toolz Pill",
                            subtitle = "Enable active tools overlay",
                            icon = Icons.Rounded.SmartButton,
                            checked = showToolzPill,
                            onCheckedChange = { viewModel.setShowToolzPill(it) }
                        )

                        SettingsToggleItem(
                            title = "Step Counter",
                            subtitle = "Background activity tracking",
                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                            checked = stepCounterEnabled,
                            onCheckedChange = { viewModel.setStepCounterEnabled(it) }
                        )

                        SettingsItem(
                            title = "User Identity",
                            subtitle = "Profile for $userName",
                            icon = Icons.Rounded.Person
                        ) {
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { viewModel.setUserName(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter your name") },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        SettingsItem(
                            title = "Unit System",
                            subtitle = "Select preferred units",
                            icon = Icons.Rounded.SquareFoot
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("METRIC", "IMPERIAL").forEach { unit ->
                                    val isSelected = unitSystem == unit
                                    Surface(
                                        onClick = { viewModel.setUnitSystem(unit) },
                                        modifier = Modifier.weight(1f).height(40.dp).bouncyClick {},
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(unit, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        
                        SettingsToggleItem(
                            title = "Haptic Feedback",
                            subtitle = "Tactile vibration",
                            icon = Icons.Rounded.Vibration,
                            checked = hapticFeedback,
                            onCheckedChange = { viewModel.setHapticFeedback(it) }
                        )

                        if (hapticFeedback) {
                            SettingsItem(
                                title = "Vibration Intensity",
                                subtitle = "Current: ${(hapticIntensity * 100).toInt()}%",
                                icon = Icons.Rounded.GraphicEq
                            ) {
                                Slider(
                                    value = hapticIntensity,
                                    onValueChange = { viewModel.setHapticIntensity(it) },
                                    valueRange = 0.1f..1f,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        SettingsToggleItem(
                            title = "Navigation",
                            subtitle = "Show Qibla in compass",
                            icon = Icons.Rounded.Explore,
                            checked = showQibla,
                            onCheckedChange = { viewModel.setShowQibla(it) }
                        )

                        SettingsItem(
                            title = "Activity Target",
                            subtitle = "Goal: $stepGoal steps",
                            icon = Icons.Rounded.Flag
                        ) {
                            Slider(
                                value = stepGoal.toFloat(),
                                onValueChange = { viewModel.setStepGoal(it.toInt()) },
                                valueRange = 1000f..30000f,
                                steps = 29,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                if (matches(searchQuery, "notifications", "tracker", "alerts", "music")) {
                    SettingsSection(title = "NOTIFICATIONS") {
                        SettingsToggleItem(
                            title = "Master Toggle",
                            subtitle = "All app alerts",
                            icon = Icons.Rounded.Notifications,
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                        
                        if (notificationsEnabled) {
                            SettingsToggleItem(
                                title = "Fitness Milestones",
                                subtitle = "Daily achievement alerts",
                                icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                checked = stepNotifications,
                                onCheckedChange = { viewModel.setStepNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Time Management",
                                subtitle = "Timer completion alerts",
                                icon = Icons.Rounded.Timer,
                                checked = timerNotifications,
                                onCheckedChange = { viewModel.setTimerNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Media Playback",
                                subtitle = "Song info and controls",
                                icon = Icons.Rounded.MusicNote,
                                checked = musicNotifications,
                                onCheckedChange = { viewModel.setMusicNotifications(it) }
                            )
                        }
                    }
                }

                if (matches(searchQuery, "music", "audio", "player", "speed", "equalizer", "visualizer")) {
                    SettingsSection(title = "MUSIC ENGINE") {
                        SettingsToggleItem(
                            title = "Smart Audio Focus",
                            subtitle = "Auto-pause for other sounds",
                            icon = Icons.Rounded.CenterFocusStrong,
                            checked = viewModel.musicAudioFocus.collectAsState(initial = true).value,
                            onCheckedChange = { viewModel.setMusicAudioFocus(it) }
                        )
                        
                        SettingsToggleItem(
                            title = "Dynamic Visualizer",
                            subtitle = "Waveforms in player",
                            icon = Icons.Rounded.BarChart,
                            checked = viewModel.showMusicVisualizer.collectAsState(initial = true).value,
                            onCheckedChange = { viewModel.setShowMusicVisualizer(it) }
                        )

                        SettingsToggleItem(
                            title = "Shake to Next",
                            subtitle = "Shake to skip track",
                            icon = Icons.Rounded.ScreenRotation,
                            checked = viewModel.musicShakeToSkip.collectAsState(initial = false).value,
                            onCheckedChange = { viewModel.setMusicShakeToSkip(it) }
                        )

                        SettingsItem(
                            title = "Playback Velocity",
                            subtitle = "Speed: ${"%.1f".format(viewModel.musicPlaybackSpeed.collectAsState(initial = 1.0f).value)}x",
                            icon = Icons.Rounded.Speed
                        ) {
                            Slider(
                                value = viewModel.musicPlaybackSpeed.collectAsState(initial = 1.0f).value,
                                onValueChange = { viewModel.setMusicPlaybackSpeed(it) },
                                valueRange = 0.5f..2.0f,
                                steps = 14,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                if (matches(searchQuery, "appearance", "theme", "dark mode", "colors", "secondary")) {
                    SettingsSection(title = "VISUAL INTERFACE") {
                        SettingsToggleItem(
                            title = "Material You",
                            subtitle = "Sync with wallpaper",
                            icon = Icons.Rounded.Palette,
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                        
                        if (!dynamicColor) {
                            SettingsItem(
                                title = "Custom Palette",
                                subtitle = "Primary & Secondary tones",
                                icon = Icons.Rounded.ColorLens
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("PRIMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            IconButton(onClick = { viewModel.setCustomPrimaryColor(null) }, modifier = Modifier.size(24.dp)) {
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
                                            Text("SECONDARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                                            IconButton(onClick = { viewModel.setCustomSecondaryColor(null) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        ColorPickerRow(
                                            selectedColor = customSecondaryInt ?: Color(0xFF00BFA5).toArgb(),
                                            onColorSelected = { viewModel.setCustomSecondaryColor(it) }
                                        )
                                    }
                                    
                                    BetterHexSelector(
                                        onPrimarySelected = { viewModel.setCustomPrimaryColor(it) },
                                        onSecondarySelected = { viewModel.setCustomSecondaryColor(it) }
                                    )
                                }
                            }
                        }
                        
                        SettingsItem(
                            title = "Interface Mode",
                            subtitle = "Theme: ${themeMode.lowercase().replaceFirstChar { it.uppercase() }}",
                            icon = Icons.Rounded.DarkMode
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                    val isSelected = themeMode == mode
                                    Surface(
                                        onClick = { viewModel.setThemeMode(mode) },
                                        modifier = Modifier.weight(1f).height(40.dp).bouncyClick {},
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(mode, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (matches(searchQuery, "widget", "color", "opacity")) {
                    SettingsSection(title = "DESKTOP WIDGETS") {
                        SettingsItem(
                            title = "Backdrop Aesthetics",
                            subtitle = "Transparency and color",
                            icon = Icons.Rounded.SettingsSuggest
                        ) {
                            ColorPickerRow(
                                selectedColor = widgetBgColor,
                                onColorSelected = { viewModel.setWidgetBackgroundColor(it) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Opacity, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Slider(
                                    value = widgetOpacity,
                                    onValueChange = { viewModel.setWidgetOpacity(it) },
                                    valueRange = 0.1f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${(widgetOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }

                AboutSection()
                
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

private fun matches(query: String, vararg keywords: String): Boolean {
    if (query.isEmpty()) return true
    return keywords.any { it.contains(query, ignoreCase = true) }
}

@Composable
fun BetterHexSelector(
    onPrimarySelected: (Int) -> Unit,
    onSecondarySelected: (Int) -> Unit
) {
    var hexInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { 
                    if (it.length <= 7) {
                        hexInput = it
                        isError = false
                    }
                },
                placeholder = { Text("#RRGGBB", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                isError = isError,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
            
            Row {
                Surface(
                    onClick = {
                        try {
                            val colorStr = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                            val color = Color(android.graphics.Color.parseColor(colorStr))
                            onPrimarySelected(color.toArgb())
                            hexInput = ""
                        } catch (e: Exception) {
                            isError = true
                        }
                    },
                    modifier = Modifier.size(44.dp).bouncyClick {},
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Colorize, "Primary", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(Modifier.width(8.dp))
                
                Surface(
                    onClick = {
                        try {
                            val colorStr = if (hexInput.startsWith("#")) hexInput else "#$hexInput"
                            val color = Color(android.graphics.Color.parseColor(colorStr))
                            onSecondarySelected(color.toArgb())
                            hexInput = ""
                        } catch (e: Exception) {
                            isError = true
                        }
                    },
                    modifier = Modifier.size(44.dp).bouncyClick {},
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Brush, "Secondary", tint = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (isError) {
            Text("Invalid HEX code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp, start = 4.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search settings...") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun AboutSection() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Build, 
                        contentDescription = null, 
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Toolz", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Version 1.8.0", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "The most powerful tool-kit for Android.\nCreated with precision by frerox.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {},
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).bouncyClick {}
            ) {
                Text("PRIVACY ARCHITECTURE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
fun ColorPickerRow(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selectedColor == argb) 3.dp else 1.dp,
                        color = if (selectedColor == argb) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(argb) }
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                }
            }
            extraContent?.let {
                Spacer(modifier = Modifier.height(14.dp))
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
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                }
            }
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}
