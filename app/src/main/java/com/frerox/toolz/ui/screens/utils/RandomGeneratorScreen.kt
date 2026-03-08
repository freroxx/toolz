package com.frerox.toolz.ui.screens.utils

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomGeneratorScreen(
    viewModel: RandomGeneratorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Random Generator") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Number Generator Section
            SectionHeader(title = "Random Number", icon = Icons.Rounded.Casino)
            OutlinedCard(
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = state.min,
                            onValueChange = { viewModel.onMinChange(it) },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large
                        )
                        OutlinedTextField(
                            value = state.max,
                            onValueChange = { viewModel.onMaxChange(it) },
                            label = { Text("Max") },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.generateNumber() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Generate Number", style = MaterialTheme.typography.titleMedium)
                    }
                    if (state.randomNumber.isNotEmpty()) {
                        Text(
                            text = state.randomNumber,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Password Generator Section
            SectionHeader(title = "Password Generator", icon = Icons.Rounded.Lock)
            OutlinedCard(
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Length", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.passwordLength.toInt().toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.passwordLength,
                        onValueChange = { viewModel.onPasswordLengthChange(it) },
                        valueRange = 4f..32f,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ConfigurationToggle(
                            label = "Uppercase Letters",
                            checked = state.includeUpper,
                            onCheckedChange = { viewModel.onToggleUpper(it) }
                        )
                        ConfigurationToggle(
                            label = "Include Numbers",
                            checked = state.includeNumbers,
                            onCheckedChange = { viewModel.onToggleNumbers(it) }
                        )
                        ConfigurationToggle(
                            label = "Special Symbols",
                            checked = state.includeSymbols,
                            onCheckedChange = { viewModel.onToggleSymbols(it) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { viewModel.generatePassword() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Generate Password", style = MaterialTheme.typography.titleMedium)
                    }
                    
                    if (state.password.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.password,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(state.password)) },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigurationToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            icon, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title, 
            style = MaterialTheme.typography.headlineSmall, 
            fontWeight = FontWeight.ExtraBold
        )
    }
}
