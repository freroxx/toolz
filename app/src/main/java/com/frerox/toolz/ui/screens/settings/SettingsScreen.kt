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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
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
    val widgetAccentColor by viewModel.widgetAccentColor.collectAsState()
    val widgetOpacity by viewModel.widgetOpacity.collectAsState()
    
    val hapticFeedback by viewModel.hapticFeedback.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()
    val showQibla by viewModel.showQibla.collectAsState()

    // Music Player Settings
    val musicAudioFocus by viewModel.musicAudioFocus.collectAsState()
    val musicShakeToSkip by viewModel.musicShakeToSkip.collectAsState()
    val musicPlaybackSpeed by viewModel.musicPlaybackSpeed.collectAsState()
    val musicEqualizerPreset by viewModel.musicEqualizerPreset.collectAsState()
    val showMusicVisualizer by viewModel.showMusicVisualizer.collectAsState()
    
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

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .fadingEdge(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.05f to Color.Black,
                        0.95f to Color.Black,
                        1f to Color.Transparent
                    ),
                    length = 20.dp
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) }
            )

            if (matches(searchQuery, "step goal", "health", "units", "qibla", "compass")) {
                SettingsSection(title = "General") {
                    SettingsItem(
                        title = "Unit System",
                        subtitle = unitSystem.lowercase().replaceFirstChar { it.uppercase() },
                        icon = Icons.Rounded.SquareFoot
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("METRIC", "IMPERIAL").forEach { unit ->
                                FilterChip(
                                    selected = unitSystem == unit,
                                    onClick = { viewModel.setUnitSystem(unit) },
                                    label = { Text(unit.lowercase().replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    
                    SettingsToggleItem(
                        title = "Haptic Feedback",
                        subtitle = "Vibrate on interactions",
                        icon = Icons.Rounded.Vibration,
                        checked = hapticFeedback,
                        onCheckedChange = { viewModel.setHapticFeedback(it) }
                    )

                    SettingsToggleItem(
                        title = "Show Qibla",
                        subtitle = "Display Kaaba direction in compass",
                        icon = Icons.Rounded.Explore,
                        checked = showQibla,
                        onCheckedChange = { viewModel.setShowQibla(it) }
                    )

                    SettingsItem(
                        title = "Daily Step Goal",
                        subtitle = "$stepGoal steps",
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
                SettingsSection(title = "Notifications") {
                    SettingsToggleItem(
                        title = "Enable All Notifications",
                        subtitle = "Global notification toggle",
                        icon = Icons.Rounded.Notifications,
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                    
                    if (notificationsEnabled) {
                        SettingsToggleItem(
                            title = "Step Tracker",
                            subtitle = "Daily goals and milestones",
                            icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                            checked = stepNotifications,
                            onCheckedChange = { viewModel.setStepNotifications(it) }
                        )
                        SettingsToggleItem(
                            title = "Timer & Stopwatch",
                            subtitle = "Alerts and completion",
                            icon = Icons.Rounded.Timer,
                            checked = timerNotifications,
                            onCheckedChange = { viewModel.setTimerNotifications(it) }
                        )
                        SettingsToggleItem(
                            title = "Voice Recorder",
                            subtitle = "Recording status alerts",
                            icon = Icons.Rounded.Mic,
                            checked = voiceRecordNotifications,
                            onCheckedChange = { viewModel.setVoiceRecordNotifications(it) }
                        )
                        SettingsToggleItem(
                            title = "Music Player",
                            subtitle = "Playback controls and song info",
                            icon = Icons.Rounded.MusicNote,
                            checked = musicNotifications,
                            onCheckedChange = { viewModel.setMusicNotifications(it) }
                        )
                    }
                }
            }

            if (matches(searchQuery, "music", "audio", "player", "speed", "equalizer", "visualizer")) {
                SettingsSection(title = "Music Player") {
                    SettingsToggleItem(
                        title = "Audio Focus",
                        subtitle = "Pause music when other apps play audio",
                        icon = Icons.Rounded.CenterFocusStrong,
                        checked = musicAudioFocus,
                        onCheckedChange = { viewModel.setMusicAudioFocus(it) }
                    )
                    
                    SettingsToggleItem(
                        title = "Visualizer",
                        subtitle = "Show animated bars in player",
                        icon = Icons.Rounded.BarChart,
                        checked = showMusicVisualizer,
                        onCheckedChange = { viewModel.setShowMusicVisualizer(it) }
                    )

                    SettingsToggleItem(
                        title = "Shake to Skip",
                        subtitle = "Shake device to play next track",
                        icon = Icons.Rounded.ScreenRotation,
                        checked = musicShakeToSkip,
                        onCheckedChange = { viewModel.setMusicShakeToSkip(it) }
                    )

                    SettingsItem(
                        title = "Playback Speed",
                        subtitle = "${"%.1f".format(musicPlaybackSpeed)}x",
                        icon = Icons.Rounded.Speed
                    ) {
                        Slider(
                            value = musicPlaybackSpeed,
                            onValueChange = { viewModel.setMusicPlaybackSpeed(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 14,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    SettingsItem(
                        title = "Equalizer",
                        subtitle = musicEqualizerPreset,
                        icon = Icons.Rounded.Equalizer
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Normal", "Pop", "Rock", "Jazz", "Classical", "Bass Boost").forEach { preset ->
                                FilterChip(
                                    selected = musicEqualizerPreset == preset,
                                    onClick = { viewModel.setMusicEqualizerPreset(preset) },
                                    label = { Text(preset) }
                                )
                            }
                        }
                    }
                }
            }

            if (matches(searchQuery, "appearance", "theme", "dark mode", "colors", "secondary")) {
                SettingsSection(title = "Appearance") {
                    SettingsToggleItem(
                        title = "Dynamic Colors",
                        subtitle = "Use Material You theme colors",
                        icon = Icons.Rounded.Palette,
                        checked = dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                    
                    if (!dynamicColor) {
                        SettingsItem(
                            title = "Theme Colors",
                            subtitle = "Primary and secondary app colors",
                            icon = Icons.Rounded.ColorLens
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column {
                                    Text("Primary Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ColorPickerRow(
                                            selectedColor = customPrimaryInt ?: Color(0xFF2962FF).toArgb(),
                                            onColorSelected = { viewModel.setCustomPrimaryColor(it) }
                                        )
                                        IconButton(onClick = { viewModel.setCustomPrimaryColor(null) }) {
                                            Icon(Icons.Rounded.RestartAlt, null)
                                        }
                                    }
                                }
                                
                                Column {
                                    Text("Secondary Color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ColorPickerRow(
                                            selectedColor = customSecondaryInt ?: Color(0xFF00BFA5).toArgb(),
                                            onColorSelected = { viewModel.setCustomSecondaryColor(it) }
                                        )
                                        IconButton(onClick = { viewModel.setCustomSecondaryColor(null) }) {
                                            Icon(Icons.Rounded.RestartAlt, null)
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
                        title = "Theme Mode",
                        subtitle = themeMode.lowercase().replaceFirstChar { it.uppercase() },
                        icon = Icons.Rounded.DarkMode
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.lowercase().replaceFirstChar { it.uppercase() }) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (matches(searchQuery, "widget", "color", "opacity")) {
                SettingsSection(title = "Widgets") {
                    SettingsItem(
                        title = "Widget Background",
                        subtitle = "Color and opacity for all widgets",
                        icon = Icons.Rounded.SettingsSuggest
                    ) {
                        ColorPickerRow(
                            selectedColor = widgetBgColor,
                            onColorSelected = { viewModel.setWidgetBackgroundColor(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Opacity, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = widgetOpacity,
                                onValueChange = { viewModel.setWidgetOpacity(it) },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${(widgetOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(32.dp))
                        }
                    }
                }
            }

            if (matches(searchQuery, "audio", "music", "shutter", "ringtone")) {
                SettingsSection(title = "Audio & Media") {
                    SettingsItem(
                        title = "Timer Ringtone",
                        subtitle = if (ringtoneUri.isNullOrEmpty()) "Default Ringtone" else Uri.parse(ringtoneUri!!).lastPathSegment ?: "Custom",
                        icon = Icons.Rounded.Audiotrack,
                        onClick = { ringtonePickerLauncher.launch("audio/*") }
                    )
                    
                    SettingsToggleItem(
                        title = "Shutter Sound",
                        subtitle = "Play sound when capturing images",
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        checked = shutterSound,
                        onCheckedChange = { viewModel.setShutterSoundEnabled(it) }
                    )
                    
                    if (shutterSound) {
                        SettingsItem(
                            title = "Custom Shutter Sound",
                            subtitle = if (shutterSoundUri == null) "System Default" else Uri.parse(shutterSoundUri!!).lastPathSegment ?: "Custom",
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            onClick = { shutterPickerLauncher.launch("audio/*") }
                        )
                    }
                }
            }

            AboutSection()
            
            Spacer(modifier = Modifier.height(32.dp))
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
            label = { Text("Custom Hex Code") },
            placeholder = { Text("#RRGGBB") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            isError = isError,
            supportingText = { if (isError) Text("Invalid hex code") },
            trailingIcon = {
                Row {
                    IconButton(onClick = {
                        try {
                            val color = Color(android.graphics.Color.parseColor(if (hexInput.startsWith("#")) hexInput else "#$hexInput"))
                            onPrimarySelected(color.toArgb())
                            hexInput = ""
                        } catch (e: Exception) {
                            isError = true
                        }
                    }) {
                        Icon(Icons.Rounded.Colorize, "Apply Primary", tint = MaterialTheme.colorScheme.primary)
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
                        Icon(Icons.Rounded.Brush, "Apply Secondary", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        )
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
            .padding(16.dp),
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
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
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun AboutSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Build, 
                    contentDescription = null, 
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Toolz", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("Version 1.6.5", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "The ultimate toolkit for daily needs, made and designed by frerox",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {},
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Privacy Policy")
            }
        }
    }
}

@Composable
fun ColorPickerRow(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val colors = listOf(
            Color(0xFFFF6D00), Color(0xFF00BFA5), Color(0xFF2962FF),
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
                        color = if (selectedColor == argb) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
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
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
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
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                } else null
            )
        }
    }
}
