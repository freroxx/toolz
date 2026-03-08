package com.frerox.toolz.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val stepGoal by viewModel.stepGoal.collectAsState()
    val ringtoneUri by viewModel.ringtoneUri.collectAsState()
    val context = LocalContext.current

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setRingtoneUri(it.toString()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Step Goal Setting
            SettingsSection(title = "Health & Activity", icon = Icons.Rounded.Flag) {
                Column {
                    Text("Daily Step Goal: $stepGoal", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = stepGoal.toFloat(),
                        onValueChange = { viewModel.setStepGoal(it.toInt()) },
                        valueRange = 1000f..30000f,
                        steps = 29
                    )
                }
            }

            // Ringtone Setting
            SettingsSection(title = "Timer & Notifications", icon = Icons.Rounded.Audiotrack) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Timer Ringtone", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = ringtoneUri?.let { Uri.parse(it).lastPathSegment } ?: "Default",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Button(onClick = { ringtonePickerLauncher.launch("audio/*") }) {
                        Text("Choose")
                    }
                }
            }
            
            // App Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Toolz App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("The ultimate all-in-one toolkit for your Android device. Made by frerox (Alias: Yahia)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        content()
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}
