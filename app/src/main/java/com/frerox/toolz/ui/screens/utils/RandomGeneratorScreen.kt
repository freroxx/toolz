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
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Settings
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
import com.frerox.toolz.ui.components.fadingEdge
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
                .fadingEdge(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.05f to Color.Black,
                        0.95f to Color.Black,
                        1f to Color.Transparent
                    ),
                    length = 24.dp
                )
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            
            // --- Number Generator Section ---
            SectionHeader(title = "Random Number", icon = Icons.Rounded.Casino)
            OutlinedCard(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.max,
                            onValueChange = { viewModel.onMaxChange(it) },
                            label = { Text("Max") },
                            modifier = Modifier.weight(1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.generateNumber() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        Text("Generate Number", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                    if (state.randomNumber.isNotEmpty()) {
                        Text(
                            text = state.randomNumber,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // --- Password Generator Section ---
            SectionHeader(title = "Password Generator", icon = Icons.Rounded.Lock)
            OutlinedCard(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Length", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = state.passwordLength.toInt().toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
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
                            label = "Lowercase Letters",
                            checked = state.includeLower,
                            onCheckedChange = { viewModel.onToggleLower(it) }
                        )
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
                    
                    if (state.includeSymbols) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.customSymbols,
                            onValueChange = { viewModel.onCustomSymbolsChange(it) },
                            label = { Text("Custom Symbols (Fallback if empty)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            singleLine = true
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { viewModel.generatePassword() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        Text("Generate Password", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                    
                    if (state.password.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.password,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
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

            // --- Random Words Section ---
            SectionHeader(title = "Random Words", icon = Icons.Rounded.TextFields)
            OutlinedCard(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Word Count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = state.wordCount.toInt().toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.wordCount,
                        onValueChange = { viewModel.onWordCountChange(it) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { viewModel.generateWords() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        Text("Generate Words", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                    
                    if (state.generatedWords.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.generatedWords,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(state.generatedWords)) },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                                }
                            }
                        }
                    }
                }
            }

            // --- Dice Roll Section ---
            SectionHeader(title = "Dice Roller", icon = Icons.Rounded.Casino)
            OutlinedCard(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Number of Dice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = state.diceCount.toInt().toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.diceCount,
                        onValueChange = { viewModel.onDiceCountChange(it) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sides (D-N)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "d${state.diceSides.toInt()}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.diceSides,
                        onValueChange = { viewModel.onDiceSidesChange(it) },
                        valueRange = 2f..100f,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { viewModel.rollDice() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                    ) {
                        Text("Roll Dice", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                    
                    if (state.diceResults.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Total Sum", 
                                    style = MaterialTheme.typography.labelMedium, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = state.totalDiceSum.toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(state.diceResults.size) { index ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = state.diceResults[index].toString(),
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
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
