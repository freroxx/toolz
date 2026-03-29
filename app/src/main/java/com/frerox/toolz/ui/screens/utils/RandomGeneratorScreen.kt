package com.frerox.toolz.ui.screens.utils

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RandomGeneratorScreen(
    viewModel: RandomGeneratorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val performanceMode = LocalPerformanceMode.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { @Suppress("DEPRECATION") Text("RANDOM ENGINE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 32.dp))
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                
                // --- Number Generator Section ---
                SectionHeader(title = "NUMERIC ENTROPY", icon = Icons.Rounded.Casino)
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = state.min,
                                onValueChange = { viewModel.onMinChange(it) },
                                label = { Text("MIN") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.max,
                                onValueChange = { viewModel.onMaxChange(it) },
                                label = { Text("MAX") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.generateNumber() },
                            modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick { viewModel.generateNumber() },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("GENERATE DIGITS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        
                        AnimatedContent(
                            targetState = state.randomNumber,
                            transitionSpec = {
                                (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.8f))
                            }, label = "num_result"
                        ) { number ->
                            if (number.isNotEmpty()) {
                                Text(
                                    text = number,
                                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // --- Password Generator Section ---
                SectionHeader(title = "SECURE KEYGEN", icon = Icons.Rounded.Lock)
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("LENGTH", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = state.passwordLength.toInt().toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = state.passwordLength,
                            onValueChange = { viewModel.onPasswordLengthChange(it) },
                            valueRange = 4f..64f,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            maxItemsInEachRow = 2,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SmallToggle("Lowercase", state.includeLower) { viewModel.onToggleLower(it) }
                            SmallToggle("Uppercase", state.includeUpper) { viewModel.onToggleUpper(it) }
                            SmallToggle("Numbers", state.includeNumbers) { viewModel.onToggleNumbers(it) }
                            SmallToggle("Symbols", state.includeSymbols) { viewModel.onToggleSymbols(it) }
                        }
                        
                        AnimatedVisibility(visible = state.includeSymbols) {
                            OutlinedTextField(
                                value = state.customSymbols,
                                onValueChange = { viewModel.onCustomSymbolsChange(it) },
                                label = { Text("CUSTOM SYMBOLS") },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { viewModel.generatePassword() },
                            modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick { viewModel.generatePassword() },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("DEPLOY KEY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        
                        AnimatedContent(
                            targetState = state.password,
                            transitionSpec = {
                                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                            }, label = "pwd_result"
                        ) { pwd ->
                            if (pwd.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pwd,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            // Strength Bar
                                            LinearProgressIndicator(
                                                progress = { state.passwordStrength },
                                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                                color = when {
                                                    state.passwordStrength < 0.4f -> Color.Red
                                                    state.passwordStrength < 0.7f -> Color.Yellow
                                                    else -> Color.Green
                                                },
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { clipboardManager.setText(AnnotatedString(pwd)) },
                                            modifier = Modifier.scale(0.9f)
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Dice Section ---
            SectionHeader(title = "DICE QUANTUM", icon = Icons.Rounded.Casino)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DICE COUNT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(state.diceCount.toInt().toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = state.diceCount,
                        onValueChange = { viewModel.onDiceCountChange(it) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SIDES (D?)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(state.diceSides.toInt().toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                    }
                    Slider(
                        value = state.diceSides,
                        onValueChange = { viewModel.onDiceSidesChange(it) },
                        valueRange = 2f..100f,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Button(
                        onClick = { viewModel.rollDice() },
                        modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick { viewModel.rollDice() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("ROLL PROBABILITY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    
                    AnimatedContent(
                        targetState = state.diceResults,
                        transitionSpec = {
                            (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut())
                        }, label = "dice_result"
                    ) { results ->
                        if (results.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = results.joinToString("  "),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                if (results.size > 1) {
                                    Text(
                                        text = "SUM: ${state.totalDiceSum}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- Random Words Section ---
            SectionHeader(title = "LEXICAL ANALYTICS", icon = Icons.Rounded.TextFields)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WORD COUNT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(state.wordCount.toInt().toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                    }
                    Slider(
                        value = state.wordCount,
                        onValueChange = { viewModel.onWordCountChange(it) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Button(
                        onClick = { viewModel.generateWords() },
                        modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick { viewModel.generateWords() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("EXTRACT WORDS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    
                    AnimatedVisibility(
                        visible = state.generatedWords.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.generatedWords,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { clipboardManager.setText(AnnotatedString(state.generatedWords)) },
                                    modifier = Modifier.scale(0.9f)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.tertiary)
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
fun SmallToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.width(140.dp).height(44.dp).bouncyClick { onCheckedChange(!checked) },
        color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.6f)
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        @Suppress("DEPRECATION")
        Text(
            title, 
            style = MaterialTheme.typography.labelMedium, 
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}
