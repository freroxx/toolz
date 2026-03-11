package com.frerox.toolz.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
    val stepGoal by viewModel.stepGoal.collectAsState()
    val ringtoneUri by viewModel.ringtoneUri.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val shutterSound by viewModel.shutterSoundEnabled.collectAsState()
    val shutterSoundUri by viewModel.shutterSoundUri.collectAsState()
    val customPrimaryInt by viewModel.customPrimaryColor.collectAsState()
    val customSecondaryInt by viewModel.customSecondaryColor.collectAsState()

    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val stepNotifications by viewModel.stepNotifications.collectAsState()
    val timerNotifications by viewModel.timerNotifications.collectAsState()
    val voiceRecordNotifications by viewModel.voiceRecordNotifications.collectAsState()
    val musicNotifications by viewModel.musicNotifications.collectAsState()

    val widgetBgColor by viewModel.widgetBackgroundColor.collectAsState()
    val widgetOpacity by viewModel.widgetOpacity.collectAsState()
    
    val hapticFeedback by viewModel.hapticFeedback.collectAsState()
    val hapticIntensity by viewModel.hapticIntensity.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()
    val showQibla by viewModel.showQibla.collectAsState()
    val stepCounterEnabled by viewModel.stepCounterEnabled.collectAsState()
    val showToolzPill by viewModel.showToolzPill.collectAsState()
    val userName by viewModel.userName.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }
    
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setRingtoneUri(it.toString()) }
    }
    
    val shutterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setShutterSoundUri(it.toString()) }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("RESET DATA?", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = { Text("This will clear your profile name and preferences. You'll need to go through onboarding again.") },
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
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                    }
                }

                SearchField(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .fadingEdge(
                        brush = Brush.verticalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent),
                        length = 24.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 48.dp, top = 8.dp)
            ) {
                if (matches(searchQuery, "step goal", "health", "units", "qibla", "compass", "haptic", "vibration", "step counter", "tracker", "pill", "dashboard", "onboarding", "profile", "name")) {
                    SettingsSection(title = "GENERAL PREFERENCES") {
                        SettingsToggleItem(
                            title = "Show Toolz Pill",
                            subtitle = "Enable active tools overlay on dashboard",
                            icon = Icons.Rounded.SmartButton,
                            checked = showToolzPill,
                            onCheckedChange = { viewModel.setShowToolzPill(it) }
                        )

                        SettingsToggleItem(
                            title = "Step Counter",
                            subtitle = "Enable background activity tracking",
                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                            checked = stepCounterEnabled,
                            onCheckedChange = { viewModel.setStepCounterEnabled(it) }
                        )

                        SettingsItem(
                            title = "User Identity",
                            subtitle = "Managing profile for $userName",
                            icon = Icons.Rounded.Person
                        ) {
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { viewModel.setUserName(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter your name") },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                )
                            )
                        }

                        SettingsItem(
                            title = "Measurement System",
                            subtitle = "Select your preferred units",
                            icon = Icons.Rounded.SquareFoot
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                listOf("METRIC", "IMPERIAL").forEach { unit ->
                                    Surface(
                                        onClick = { viewModel.setUnitSystem(unit) },
                                        modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (unitSystem == unit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = if (unitSystem != unit) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(unit, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, color = if (unitSystem == unit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        
                        SettingsToggleItem(
                            title = "Haptic Feedback",
                            subtitle = "Tactile vibration on interactions",
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
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }

                        SettingsToggleItem(
                            title = "kaaba Navigation",
                            subtitle = "Show Qibla direction in compass",
                            icon = Icons.Rounded.Explore,
                            checked = showQibla,
                            onCheckedChange = { viewModel.setShowQibla(it) }
                        )

                        SettingsItem(
                            title = "Daily Activity Target",
                            subtitle = "Goal: $stepGoal steps",
                            icon = Icons.Rounded.Flag
                        ) {
                            Slider(
                                value = stepGoal.toFloat(),
                                onValueChange = { viewModel.setStepGoal(it.toInt()) },
                                valueRange = 1000f..30000f,
                                steps = 29,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }

                if (matches(searchQuery, "notifications", "tracker", "alerts", "music")) {
                    SettingsSection(title = "NOTIFICATIONS & ALERTS") {
                        SettingsToggleItem(
                            title = "Master Toggle",
                            subtitle = "Enable or disable all notifications",
                            icon = Icons.Rounded.Notifications,
                            checked = notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                        
                        if (notificationsEnabled) {
                            SettingsToggleItem(
                                title = "Fitness Milestones",
                                subtitle = "Daily goals and achievement alerts",
                                icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                                checked = stepNotifications,
                                onCheckedChange = { viewModel.setStepNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Time Management",
                                subtitle = "Timer and stopwatch completion alerts",
                                icon = Icons.Rounded.Timer,
                                checked = timerNotifications,
                                onCheckedChange = { viewModel.setTimerNotifications(it) }
                            )
                            SettingsToggleItem(
                                title = "Media Playback",
                                subtitle = "Show song information and controls",
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
                            subtitle = "Auto-pause when other apps play sound",
                            icon = Icons.Rounded.CenterFocusStrong,
                            checked = viewModel.musicAudioFocus.collectAsState().value,
                            onCheckedChange = { viewModel.setMusicAudioFocus(it) }
                        )
                        
                        SettingsToggleItem(
                            title = "Dynamic Visualizer",
                            subtitle = "Show animated waveforms in player",
                            icon = Icons.Rounded.BarChart,
                            checked = viewModel.showMusicVisualizer.collectAsState().value,
                            onCheckedChange = { viewModel.setShowMusicVisualizer(it) }
                        )

                        SettingsToggleItem(
                            title = "Shake to Next",
                            subtitle = "Shake device to skip current track",
                            icon = Icons.Rounded.ScreenRotation,
                            checked = viewModel.musicShakeToSkip.collectAsState().value,
                            onCheckedChange = { viewModel.setMusicShakeToSkip(it) }
                        )

                        SettingsItem(
                            title = "Playback Velocity",
                            subtitle = "Speed: ${"%.1f".format(viewModel.musicPlaybackSpeed.collectAsState().value)}x",
                            icon = Icons.Rounded.Speed
                        ) {
                            Slider(
                                value = viewModel.musicPlaybackSpeed.collectAsState().value,
                                onValueChange = { viewModel.setMusicPlaybackSpeed(it) },
                                valueRange = 0.5f..2.0f,
                                steps = 14,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }

                if (matches(searchQuery, "appearance", "theme", "dark mode", "colors", "secondary")) {
                    SettingsSection(title = "VISUAL INTERFACE") {
                        SettingsToggleItem(
                            title = "Material You",
                            subtitle = "Sync colors with your wallpaper",
                            icon = Icons.Rounded.Palette,
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                        
                        if (!dynamicColor) {
                            SettingsItem(
                                title = "Accent Colors",
                                subtitle = "Customize primary and secondary tones",
                                icon = Icons.Rounded.ColorLens
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    Column {
                                        Text("PRIMARY TONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ColorPickerRow(
                                                selectedColor = customPrimaryInt ?: Color(0xFF2962FF).toArgb(),
                                                onColorSelected = { viewModel.setCustomPrimaryColor(it) }
                                            )
                                            IconButton(onClick = { viewModel.setCustomPrimaryColor(null) }) {
                                                Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                    
                                    Column {
                                        Text("SECONDARY TONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ColorPickerRow(
                                                selectedColor = customSecondaryInt ?: Color(0xFF00BFA5).toArgb(),
                                                onColorSelected = { viewModel.setCustomSecondaryColor(it) }
                                            )
                                            IconButton(onClick = { viewModel.setCustomSecondaryColor(null) }) {
                                                Icon(Icons.Rounded.RestartAlt, null, tint = MaterialTheme.colorScheme.secondary)
                                            }
                                        }
                                    }
                                    
                                    CustomHexColorPicker(
                                        primaryColor = customPrimaryInt,
                                        secondaryColor = customSecondaryInt,
                                        onPrimarySelected = { viewModel.setCustomPrimaryColor(it) },
                                        onSecondarySelected = { viewModel.setCustomSecondaryColor(it) }
                                    )
                                }
                            }
                        }
                        
                        SettingsItem(
                            title = "Dark Interface",
                            subtitle = "Mode: ${themeMode.lowercase().replaceFirstChar { it.uppercase() }}",
                            icon = Icons.Rounded.DarkMode
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                    Surface(
                                        onClick = { viewModel.setThemeMode(mode) },
                                        modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (themeMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = if (themeMode != mode) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(mode, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, color = if (themeMode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                            subtitle = "Control visibility and transparency",
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
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${(widgetOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
                            }
                        }
                    }
                }

                AboutSection()
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun matches(query: String, vararg keywords: String): Boolean {
    if (query.isEmpty()) return true
    return keywords.any { it.contains(query, ignoreCase = true) }
}

@Composable
fun CustomHexColorPicker(
    primaryColor: Int?,
    secondaryColor: Int?,
    onPrimarySelected: (Int) -> Unit,
    onSecondarySelected: (Int) -> Unit
) {
    var hexInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(
            value = hexInput,
            onValueChange = { 
                hexInput = it
                isError = false
            },
            label = { Text("CUSTOM HEX CODE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black) },
            placeholder = { Text("#RRGGBB") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            isError = isError,
            supportingText = { if (isError) Text("Invalid color format", fontWeight = FontWeight.Bold) },
            trailingIcon = {
                Row(modifier = Modifier.padding(end = 8.dp)) {
                    IconButton(onClick = {
                        try {
                            val color = Color(android.graphics.Color.parseColor(if (hexInput.startsWith("#")) hexInput else "#$hexInput"))
                            onPrimarySelected(color.toArgb())
                            hexInput = ""
                        } catch (e: Exception) {
                            isError = true
                        }
                    }) {
                        Icon(Icons.Rounded.Colorize, "Primary", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        try {
                            val color = Color(android.graphics.Color.parseColor(if (hexInput.startsWith("#")) hexInput else "#$hexInput"))
                            onSecondarySelected(color.toArgb())
                            hexInput = ""
                        } catch (e: Exception) {
                            isError = true
                        }
                    }) {
                        Icon(Icons.Rounded.Brush, "Secondary", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search 50+ configuration options...") },
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
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
            .padding(24.dp),
        shape = RoundedCornerShape(40.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Build, 
                        contentDescription = null, 
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("TOOLZ PRO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text("Version 2.0 Expressive", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "The most powerful all-in-one utility suite for Android. Crafted with precision by frerox.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {},
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick {}
            ) {
                Text("PRIVACY ARCHITECTURE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun ColorPickerRow(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                        width = if (selectedColor == argb) 4.dp else 1.dp,
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
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(start = 8.dp)
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
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            extraContent?.let {
                Spacer(modifier = Modifier.height(20.dp))
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
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                }
            }
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
