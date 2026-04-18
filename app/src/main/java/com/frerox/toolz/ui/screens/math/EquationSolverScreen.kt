package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.math.MathHistory
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EquationSolverScreen(
    viewModel: EquationSolverViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var showSteps by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val performanceMode = LocalPerformanceMode.current

    Box(modifier = Modifier.fillMaxSize().toolzBackground()) {
        // Decorative background
        if (!performanceMode) {
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 100.dp, y = (-50).dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f), CircleShape)
            )
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        @Suppress("DEPRECATION")
                        Text("EQUATION SOLVER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp) 
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showHistory = true },
                            modifier = Modifier.padding(end = 12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = "History")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .padding(horizontal = 24.dp)
            ) {
                // Type Selector
                @Suppress("DEPRECATION")
                Text(
                    "CHOOSE EQUATION TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    items(EquationType.entries) { type ->
                        val isSelected = state.selectedType == type
                        Surface(
                            onClick = { viewModel.onTypeChange(type) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)),
                            modifier = Modifier.bouncyClick { viewModel.onTypeChange(type) }
                        ) {
                            @Suppress("DEPRECATION")
                            Text(
                                text = type.name,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Coefficient Inputs
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(40.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(28.dp)) {
                        EquationPreview(state)
                        
                        // Dynamic inputs based on type
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val keys = when (state.selectedType) {
                                EquationType.LINEAR -> listOf("a", "b")
                                EquationType.QUADRATIC -> listOf("a", "b", "c")
                                EquationType.CUBIC -> listOf("a", "b", "c", "d")
                                EquationType.QUARTIC -> listOf("a", "b", "c", "d", "e")
                                EquationType.SYSTEM2 -> listOf("a", "b", "c", "d", "e", "f")
                            }
                            
                            keys.forEach { key ->
                                CoeffInput(
                                    label = key,
                                    value = state.coefficients[key] ?: "",
                                    onValueChange = { viewModel.onCoefficientChange(key, it) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                Button(
                    onClick = { 
                        viewModel.solve()
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp).bouncyClick { viewModel.solve() },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("SOLVE EQUATION", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(visible = state.result.isNotEmpty()) {
                    Column {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                @Suppress("DEPRECATION")
                                Text("SOLUTION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Text(state.result, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { showSteps = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick { showSteps = true },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text("HOW TO SOLVE?", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                state.error?.let {
                    Text(
                        it, 
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showHistory) {
        HistoryBottomSheet(
            history = history,
            onDismiss = { showHistory = false },
            onSelect = { 
                // Restore state? This is tricky with coefficient map. 
                // For now just show history.
                showHistory = false
            }
        )
    }

    if (showSteps) {
        StepsBottomSheet(
            steps = state.steps,
            onDismiss = { showSteps = false }
        )
    }
}

@Composable
fun EquationPreview(state: SolverState) {
    val text = when (state.selectedType) {
        EquationType.LINEAR -> "${state.coefficients["a"].orEmpty().ifEmpty { "a" }}x + ${state.coefficients["b"].orEmpty().ifEmpty { "b" }} = 0"
        EquationType.QUADRATIC -> "${state.coefficients["a"].orEmpty().ifEmpty { "a" }}x² + ${state.coefficients["b"].orEmpty().ifEmpty { "b" }}x + ${state.coefficients["c"].orEmpty().ifEmpty { "c" }} = 0"
        EquationType.CUBIC -> "${state.coefficients["a"].orEmpty().ifEmpty { "a" }}x³ + ${state.coefficients["b"].orEmpty().ifEmpty { "b" }}x² + ${state.coefficients["c"].orEmpty().ifEmpty { "c" }}x + ${state.coefficients["d"].orEmpty().ifEmpty { "d" }} = 0"
        EquationType.QUARTIC -> "${state.coefficients["a"].orEmpty().ifEmpty { "a" }}x⁴ + ${state.coefficients["b"].orEmpty().ifEmpty { "b" }}x³ + ${state.coefficients["c"].orEmpty().ifEmpty { "c" }}x² + ${state.coefficients["d"].orEmpty().ifEmpty { "d" }}x + ${state.coefficients["e"].orEmpty().ifEmpty { "e" }} = 0"
        EquationType.SYSTEM2 -> {
            val s1 = "${state.coefficients["a"].orEmpty().ifEmpty { "a1" }}x + ${state.coefficients["b"].orEmpty().ifEmpty { "b1" }}y = ${state.coefficients["c"].orEmpty().ifEmpty { "c1" }}"
            val s2 = "${state.coefficients["d"].orEmpty().ifEmpty { "a2" }}x + ${state.coefficients["e"].orEmpty().ifEmpty { "b2" }}y = ${state.coefficients["f"].orEmpty().ifEmpty { "c2" }}"
            "$s1\n$s2"
        }
    }
    
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun CoeffInput(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.width(80.dp),
        label = { Text(label) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsBottomSheet(
    steps: List<String>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.7f).padding(horizontal = 28.dp).padding(bottom = 48.dp)) {
            @Suppress("DEPRECATION")
            Text(
                "SOLUTION STEPS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(steps.size) { index ->
                    Row {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                @Suppress("DEPRECATION")
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(steps[index], style = MaterialTheme.typography.bodyLarge)
                    }
                    if (index < steps.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 44.dp, top = 16.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isAction: Boolean = false,
    isClear: Boolean = false,
    isVariable: Boolean = false
) {
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isAction -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        isVariable -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    
    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        isAction -> MaterialTheme.colorScheme.onPrimaryContainer
        isClear -> MaterialTheme.colorScheme.onErrorContainer
        isVariable -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .height(54.dp)
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = if (!isAction && text != "=") BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (text == "BS") {
                Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = contentColor, modifier = Modifier.size(22.dp))
            } else {
                Text(
                    text = if (text.length > 2 && text.endsWith("(")) text.dropLast(1) else text,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = contentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    history: List<MathHistory>,
    onDismiss: () -> Unit,
    onSelect: (MathHistory) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.6f).padding(horizontal = 28.dp).padding(bottom = 32.dp)) {
            @Suppress("DEPRECATION")
            Text(
                "HISTORY LOG",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(history) { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClick { onSelect(item) },
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(item.expression, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Result: ${item.result}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
