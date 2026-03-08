package com.frerox.toolz.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.components.bouncyClick

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

    // New Settings
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val stepNotifications by viewModel.stepNotifications.collectAsState()
    val timerNotifications by viewModel.timerNotifications.collectAsState()
    val voiceRecordNotifications by viewModel.voiceRecordNotifications.collectAsState()

    val widgetBgColor by viewModel.widgetBackgroundColor.collectAsState()
    val widgetAccentColor by viewModel.widgetAccentColor.collectAsState()
    val widgetOpacity by viewModel.widgetOpacity.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
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
            Column {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.ExtraBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search settings...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Health & Activity
            SettingsSection(title = "Health & Activity") {
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

            // Notifications Section
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
                }
            }

            // Widgets Section
            SettingsSection(title = "Widgets") {
                SettingsItem(
                    title = "Widget Background Color",
                    subtitle = "Choose background for all widgets",
                    icon = Icons.Rounded.SettingsSuggest
                ) {
                    ColorPickerRow(
                        selectedColor = widgetBgColor,
                        onColorSelected = { viewModel.setWidgetBackgroundColor(it) }
                    )
                }
                SettingsItem(
                    title = "Widget Accent Color",
                    subtitle = "Accent color for progress bars and icons",
                    icon = Icons.Rounded.ColorLens
                ) {
                    ColorPickerRow(
                        selectedColor = widgetAccentColor,
                        onColorSelected = { viewModel.setWidgetAccentColor(it) }
                    )
                }
                SettingsItem(
                    title = "Widget Opacity",
                    subtitle = "${(widgetOpacity * 100).toInt()}%",
                    icon = Icons.Rounded.Opacity
                ) {
                    Slider(
                        value = widgetOpacity,
                        onValueChange = { viewModel.setWidgetOpacity(it) },
                        valueRange = 0.1f..1f,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Audio & Media
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
                        icon = Icons.Rounded.QueueMusic,
                        onClick = { shutterPickerLauncher.launch("audio/*") }
                    )
                }
            }

            // Appearance
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
                        title = "Custom Primary Color",
                        subtitle = if (customPrimaryInt != null) "Custom color active" else "Default color",
                        icon = Icons.Rounded.ColorLens
                    ) {
                        ColorPickerRow(
                            selectedColor = customPrimaryInt ?: Color(0xFF2962FF).toArgb(),
                            onColorSelected = { viewModel.setCustomPrimaryColor(it) }
                        )
                        IconButton(onClick = { viewModel.setCustomPrimaryColor(null) }) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset Color")
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

            // App Info Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Build, 
                        contentDescription = null, 
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Toolz v1.5.2", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("The ultimate toolkit for daily needs", style = MaterialTheme.typography.bodyMedium)
                    Text("Made by frerox", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(onClick = {}) {
                        Text("Privacy Policy")
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPickerRow(selectedColor: Int, onColorSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val colors = listOf(
            Color(0xFFFF6D00), Color(0xFF00BFA5), Color(0xFF2962FF),
            Color(0xFFD50000), Color(0xFFAA00FF), Color(0xFF00C853), Color.White, Color.Black
        )
        colors.forEach { color ->
            val argb = color.toArgb()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, MaterialTheme.shapes.small)
                    .clickable { onColorSelected(argb) }
                    .then(
                        if (selectedColor == argb) {
                            Modifier.background(Color.Gray.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                        } else Modifier
                    )
                    .padding(2.dp)
            )
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onClick != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            extraContent?.let {
                Spacer(modifier = Modifier.height(12.dp))
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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
