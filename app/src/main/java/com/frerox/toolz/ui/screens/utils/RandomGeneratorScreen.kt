package com.frerox.toolz.ui.screens.utils

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ENTROPY ENGINE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp) },
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
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = "Clear History")
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
                    .padding(horizontal = 20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                
                // --- QUICK UTILS ---
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionChip("Flip Coin", Icons.Rounded.MonetizationOn) { viewModel.flipCoin() }
                    QuickActionChip("Decision", Icons.Rounded.QuestionMark) { viewModel.makeDecision() }
                    QuickActionChip("UUID", Icons.Rounded.Fingerprint) { viewModel.generateUuid() }
                    QuickActionChip("Letter", Icons.Rounded.SortByAlpha) { viewModel.generateLetter() }
                }

                // Luck & Decisions Result
                AnimatedVisibility(
                    visible = (state.coinResult.isNotEmpty() || state.decisionResult.isNotEmpty() || state.generatedUuid.isNotEmpty() || state.generatedLetter.isNotEmpty()) && !state.isGenerating,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val resultText = when {
                                    state.coinResult.isNotEmpty() -> state.coinResult
                                    state.decisionResult.isNotEmpty() -> state.decisionResult
                                    state.generatedUuid.isNotEmpty() -> state.generatedUuid
                                    state.generatedLetter.isNotEmpty() -> state.generatedLetter
                                    else -> ""
                                }
                                val resultLabel = when {
                                    state.coinResult.isNotEmpty() -> "COIN FLIP"
                                    state.decisionResult.isNotEmpty() -> "DECISION"
                                    state.generatedUuid.isNotEmpty() -> "UUID"
                                    state.generatedLetter.isNotEmpty() -> "LETTER"
                                    else -> ""
                                }
                                
                                Text(resultLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                
                                AnimatedContent(
                                    targetState = resultText,
                                    transitionSpec = {
                                        (scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()).togetherWith(scaleOut() + fadeOut())
                                    },
                                    label = "decision"
                                ) { text ->
                                    Text(
                                        text = text,
                                        style = if (text.length > 20) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.clickable { 
                                            clipboardManager.setText(AnnotatedString(text))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- NUMERIC ENTROPY ---
                RandomSection(title = "NUMERIC ENTROPY", icon = Icons.Rounded.Pin) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EntropyTextField(value = state.min, onValueChange = { viewModel.onMinChange(it) }, label = "MIN", modifier = Modifier.weight(1f))
                        EntropyTextField(value = state.max, onValueChange = { viewModel.onMaxChange(it) }, label = "MAX", modifier = Modifier.weight(1f))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Numbers, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("QUANTITY", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(state.quantity.toInt().toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = state.quantity, onValueChange = { viewModel.onQuantityChange(it) }, valueRange = 1f..100f)
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Architecture, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("DECIMALS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(state.decimalPlaces.toInt().toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                        }
                        Slider(value = state.decimalPlaces, onValueChange = { viewModel.onDecimalPlacesChange(it) }, valueRange = 0f..5f, steps = 4)
                    }

                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallToggle("Unique", !state.allowDuplicates) { viewModel.onToggleDuplicates(!it) }
                        SmallToggle("Sort", state.sortResults) { viewModel.onToggleSort(it) }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    EntropyButton(
                        text = "GENERATE DIGITS",
                        icon = Icons.Rounded.Refresh,
                        isLoading = state.isGenerating,
                        onClick = { viewModel.generateNumber() }
                    )
                    
                    AnimatedVisibility(visible = state.randomNumber.isNotEmpty(), enter = expandVertically() + fadeIn()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clickable { clipboardManager.setText(AnnotatedString(state.randomNumber)) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ) {
                            Text(
                                state.randomNumber,
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = if (state.randomNumber.length > 15) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // --- SECURE KEYGEN ---
                RandomSection(title = "SECURE KEYGEN", icon = Icons.Rounded.Lock) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("LENGTH", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(state.passwordLength.toInt().toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(value = state.passwordLength, onValueChange = { viewModel.onPasswordLengthChange(it) }, valueRange = 4f..128f)
                    }

                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallToggle("Lower", state.includeLower) { viewModel.onToggleLower(it) }
                        SmallToggle("Upper", state.includeUpper) { viewModel.onToggleUpper(it) }
                        SmallToggle("Digits", state.includeNumbers) { viewModel.onToggleNumbers(it) }
                        SmallToggle("Symbols", state.includeSymbols) { viewModel.onToggleSymbols(it) }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    EntropyButton(
                        text = "DEPLOY KEY",
                        icon = Icons.Rounded.Security,
                        onClick = { viewModel.generatePassword() }
                    )

                    AnimatedVisibility(visible = state.password.isNotEmpty(), enter = expandVertically() + fadeIn()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(state.password, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), fontWeight = FontWeight.Bold, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { state.passwordStrength },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                        color = when {
                                            state.passwordStrength < 0.4f -> Color(0xFFEF5350)
                                            state.passwordStrength < 0.7f -> Color(0xFFFFCA28)
                                            else -> Color(0xFF66BB6A)
                                        }
                                    )
                                }
                                IconButton(onClick = { clipboardManager.setText(AnnotatedString(state.password)) }) {
                                    Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // --- LIST ANALYTICS ---
                RandomSection(title = "LIST ANALYTICS", icon = Icons.AutoMirrored.Rounded.List) {
                    OutlinedTextField(
                        value = state.itemsToPick,
                        onValueChange = { viewModel.onItemsToPickChange(it) },
                        label = { Text("ITEMS (ONE PER LINE)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.pickItem() },
                            modifier = Modifier.weight(1f).height(50.dp).bouncyClick(onClick = { viewModel.pickItem() }),
                            enabled = !state.isGenerating,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (state.isGenerating && state.pickingIndex != -1) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Rounded.AdsClick, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("PICK")
                            }
                        }
                        Button(
                            onClick = { viewModel.shuffleList() },
                            modifier = Modifier.weight(1f).height(50.dp).bouncyClick(onClick = { viewModel.shuffleList() }),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SHUFFLE")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("TEAM COUNT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(state.teamCount.toInt().toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Slider(value = state.teamCount, onValueChange = { viewModel.onTeamCountChange(it) }, valueRange = 2f..10f, steps = 7)
                    }
                    
                    EntropyButton(
                        text = "SPLIT INTO TEAMS",
                        icon = Icons.Rounded.Groups,
                        onClick = { viewModel.generateTeams() },
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )

                    // Results
                    val items = state.itemsToPick.split("\n").filter { it.isNotBlank() }
                    
                    AnimatedVisibility(visible = state.isGenerating && state.pickingIndex != -1, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            items.getOrNull(state.pickingIndex) ?: "...",
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }

                    AnimatedVisibility(visible = state.pickedItem.isNotEmpty() && !state.isGenerating, enter = fadeIn() + slideInVertically()) {
                        Text(state.pickedItem, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }

                    if (state.generatedTeams.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.generatedTeams.forEachIndexed { index, team ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("TEAM ${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                                        Text(team.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // --- CHROMA GENERATOR ---
                RandomSection(title = "CHROMA GENERATOR", icon = Icons.Rounded.Palette) {
                    val animatedColor by animateColorAsState(Color(state.generatedColor), label = "color", animationSpec = tween(600))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(animatedColor)
                            .clickable { clipboardManager.setText(AnnotatedString(state.colorName)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                state.colorName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = if (animatedColor.luminance() > 0.5f) Color.Black else Color.White
                            )
                            Text(
                                "TAP TO COPY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = (if (animatedColor.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    EntropyButton(
                        text = "GENERATE COLOR",
                        icon = Icons.Rounded.ColorLens,
                        onClick = { viewModel.generateColor() },
                        containerColor = animatedColor.copy(alpha = 0.9f).compositeOver(Color.Black)
                    )
                }

                // --- DICE & WORDS ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Casino, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.rollDice() }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp)) {
                                Text("ROLL")
                            }
                            Spacer(Modifier.height(8.dp))
                            AnimatedContent(targetState = state.totalDiceSum, label = "dice") { sum ->
                                if (sum > 0) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(sum.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                                        Text(state.diceResults.joinToString(" "), style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.6f))
                                    }
                                } else {
                                    Text("-", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.alpha(0.2f))
                                }
                            }
                        }
                    }
                    
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.TextFields, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { viewModel.generateWords() }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                                Text("GEN")
                            }
                            Spacer(Modifier.height(8.dp))
                            AnimatedContent(targetState = state.generatedWords, label = "words") { words ->
                                if (words.isNotEmpty()) {
                                    Text(words.split("\n").first(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                                } else {
                                    Text("-", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.alpha(0.2f))
                                }
                            }
                        }
                    }
                }

                // --- HISTORY ---
                if (state.history.isNotEmpty()) {
                    SectionHeader(title = "HISTORY LOG", icon = Icons.Rounded.History)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.history.forEach { HistoryItem(it) }
                    }
                }

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun EntropyTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
fun EntropyButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "rotate"
    )

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
    ) {
        if (isLoading) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(24.dp).rotate(rotation))
        } else {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun QuickActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
        modifier = Modifier.height(44.dp).bouncyClick(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RandomSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(title = title, icon = icon)
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) { content() }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
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
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun SmallToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.height(40.dp).bouncyClick(onClick = { onCheckedChange(!checked) }),
        color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (checked) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun HistoryItem(result: RandomResult) {
    val clipboardManager = LocalClipboardManager.current
    val color = when(result.type) {
        "Number" -> MaterialTheme.colorScheme.primary
        "Password" -> MaterialTheme.colorScheme.error
        "Color" -> Color.Magenta
        "Date" -> MaterialTheme.colorScheme.secondary
        "Coin" -> Color(0xFFFFD700)
        "Decision" -> Color(0xFF00BCD4)
        "UUID" -> Color(0xFF9C27B0)
        "Letter" -> Color(0xFFFF5722)
        else -> MaterialTheme.colorScheme.tertiary
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { clipboardManager.setText(AnnotatedString(result.value)) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(result.type) {
                        "Number" -> Icons.Rounded.Pin
                        "Password" -> Icons.Rounded.Lock
                        "Color" -> Icons.Rounded.Palette
                        "Date" -> Icons.Rounded.CalendarToday
                        "Coin" -> Icons.Rounded.MonetizationOn
                        "Decision" -> Icons.Rounded.QuestionMark
                        "UUID" -> Icons.Rounded.Fingerprint
                        "Letter" -> Icons.Rounded.SortByAlpha
                        else -> Icons.Rounded.Info
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(result.type.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color, letterSpacing = 1.sp)
                Text(result.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            IconButton(onClick = { clipboardManager.setText(AnnotatedString(result.value)) }) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}
