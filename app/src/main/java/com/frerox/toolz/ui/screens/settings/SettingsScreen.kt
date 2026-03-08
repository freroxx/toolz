package com.frerox.toolz.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
    val customPrimaryInt by viewModel.customPrimaryColor.collectAsState()
    
    val context = LocalContext.current

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setRingtoneUri(it.toString()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
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

            // Timer & Notifications
            SettingsSection(title = "Timer & Notifications") {
                SettingsItem(
                    title = "Timer Ringtone",
                    subtitle = ringtoneUri?.let { Uri.parse(it).lastPathSegment } ?: "Default Ringtone",
                    icon = Icons.Rounded.Audiotrack,
                    onClick = { ringtonePickerLauncher.launch("audio/*") }
                )
            }

            // Camera Settings
            SettingsSection(title = "Camera & Tools") {
                SettingsToggleItem(
                    title = "Shutter Sound",
                    subtitle = "Play sound when capturing images",
                    icon = Icons.Rounded.VolumeUp,
                    checked = shutterSound,
                    onCheckedChange = { viewModel.setShutterSoundEnabled(it) }
                )
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(
                                Color(0xFFFF6D00), Color(0xFF00BFA5), Color(0xFF2962FF),
                                Color(0xFFD50000), Color(0xFFAA00FF), Color(0xFF00C853)
                            ).forEach { color ->
                                val argb = color.toArgb()
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(color, MaterialTheme.shapes.small)
                                        .clickable { viewModel.setCustomPrimaryColor(argb) }
                                        .padding(2.dp)
                                        .let {
                                            if (customPrimaryInt == argb) {
                                                it.background(Color.White.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                                            } else it
                                        }
                                )
                            }
                            IconButton(onClick = { viewModel.setCustomPrimaryColor(null) }) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset Color")
                            }
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
                    Text("Toolz v1.3.0", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
            .let { if (onClick != null) it.bouncyClick(onClick = onClick) else it },
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
                    Icon(Icons.AutoMirrored.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
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
