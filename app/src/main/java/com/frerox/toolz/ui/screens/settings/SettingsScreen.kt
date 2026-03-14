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
            title = { Text("RESET CORE DATA?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = { Text("This will purge your profile name, preferences, and reset the initial engine setup.") },
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
                    Text("FACTORY RESET", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SETTINGS", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fadingEdge(
                        brush = Brush.verticalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent),
                        length = 32.dp
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                
                if (matches(searchQuery, "step goal", "health", "units", "qibla", "compass", "haptic", "vibration", "step counter", "tracker", "pill", "dashboard", "onboarding", "profile", "name")) {
                    SettingsSection(title = "CORE PREFERENCES") {
                        SettingsToggleItem(
                            title = "Toolz Status Pill",
                            subtitle = "Interactive background tool overlay",
                            icon = Icons.Rounded.SmartButton,
                            checked = showToolzPill,
                            onCheckedChange = { viewModel.setShowToolzPill(it) }
                        )

                        SettingsToggleItem(
                            title = "Background Step Tracker",
                            subtitle = "Real-time activity engine",
                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                            checked = stepCounterEnabled,
                            onCheckedChange = { viewModel.setStepCounterEnabled(it) }
                        )

                        SettingsItem(
                            title = "User Profile",
                            subtitle = "Identity: $userName",
                            icon = Icons.Rounded.AccountCircle
                        ) {
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { viewModel.setUserName(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Display Name") },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )
                        }

                        SettingsItem(
                            title = "Measurement Standards",
                            subtitle = "Selected system: ${unitSystem.lowercase()}",
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
                                        modifier = Modifier.weight(1f).height(44.dp).bouncyClick {},
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(unit, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        
                        SettingsToggleItem(
                            title = "Haptic Engine",
                            subtitle = "System-wide tactile feedback",
                            icon = Icons.Rounded.Vibration,
                            checked = hapticFeedback,
                            onCheckedChange = { viewModel.setHapticFeedback(it) }
                        )

                        if (hapticFeedback) {
                            SettingsItem(
                                title = "Vibration Amplitude",
                                subtitle = "Force: ${(hapticIntensity * 100).toInt()}%",
                                icon = Icons.Rounded.GraphicEq
                            ) {
                                Slider(
                                    value = hapticIntensity,
                                    onValueChange = { viewModel.setHapticIntensity(it) },
                                    valueRange = 0.1f..1f,
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        SettingsToggleItem(
                            title = "Advanced Navigation",
                            subtitle = "Qibla orientation in Compass",
                            icon = Icons.Rounded.Explore,
                            checked = showQibla,
                            onCheckedChange = { viewModel.setShowQibla(it) }
                        )

                        SettingsItem(
                            title = "Fitness Objective",
                            subtitle = "Daily target: $stepGoal steps",
                            icon = Icons.Rounded.EmojiEvents
                        ) {
                            Slider(
                                value = stepGoal.toFloat(),
                                onValueChange = { viewModel.setStepGoal(it.toInt()) },
                                valueRange = 1000f..30000f,
                                steps = 29,
                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                if (matches(searchQuery, "notifications", "tracker", "alerts", "music")) {
                    SettingsSection(title = "ALERT MODULES") {
                        SettingsToggleItem(
                            title = "Global Alerts",
                            subtitle = "Master notification toggle",
                            icon = Icons.Rounded.NotificationsActive,
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                        
                        if (notificationsEnabled) {
                            SettingsToggleItem(
                                title = "Fitness Achievements",
                                subtitle = "Step milestones and alerts",
                                icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                checked = stepNotifications,
                                onCheckedChange = { viewModel.setStepNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Timer Completions",
                                subtitle = "Countdown engine alerts",
                                icon = Icons.Rounded.Timer,
                                checked = timerNotifications,
                                onCheckedChange = { viewModel.setTimerNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Media Playback Status",
                                subtitle = "Music controls in drawer",
                                icon = Icons.Rounded.MusicNote,
                                checked = musicNotifications,
                                onCheckedChange = { viewModel.setMusicNotifications(it) }
                            )
                        }
                    }
                }

                if (matches(searchQuery, "appearance", "theme", "dark mode", "colors", "secondary")) {
                    SettingsSection(title = "VISUAL INTERFACE") {
                        SettingsToggleItem(
                            title = "Dynamic Theming",
                            subtitle = "Material You wallpaper sync",
                            icon = Icons.Rounded.Palette,
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                        
                        if (!dynamicColor) {
                            SettingsItem(
                                title = "Chroma Engine",
                                subtitle = "Primary & Secondary tones",
                                icon = Icons.Rounded.ColorLens
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("PRIMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
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
                                            Text("SECONDARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
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
                            title = "Interface Mode",
                            subtitle = "Selected: ${themeMode.lowercase()}",
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
                                        modifier = Modifier.weight(1f).height(44.dp).bouncyClick {},
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null
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
                    SettingsSection(title = "DESKTOP MODULES") {
                        SettingsItem(
                            title = "Widget Aesthetics",
                            subtitle = "Visual background configuration",
                            icon = Icons.Rounded.SettingsSuggest
                        ) {
                            ColorPickerRow(
                                selectedColor = widgetBgColor,
                                onColorSelected = { viewModel.setWidgetBackgroundColor(it) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Opacity, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Slider(
                                    value = widgetOpacity,
                                    onValueChange = { viewModel.setWidgetOpacity(it) },
                                    valueRange = 0.1f..1f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("${(widgetOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
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
fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search configurations...") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    )
}

@Composable
fun AboutSection() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
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
                        Icons.Rounded.Bolt, 
                        contentDescription = null, 
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("TOOLZ PRECISION", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text("V1.8.5 STABLE ENGINE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "A unified architecture for personal efficiency and hardware management. Local-first, private by design.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ColorPickerRow(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    .size(44.dp)
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
                        modifier = Modifier.align(Alignment.Center).size(20.dp)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
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
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
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
                Spacer(modifier = Modifier.height(16.dp))
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
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(44.dp),
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
