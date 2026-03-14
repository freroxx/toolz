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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquationSolverScreen(
    viewModel: EquationSolverViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text("EQUATION SOLVER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) 
                },
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
                        onClick = { showHistory = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = "History")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            // Display Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .clickable { focusRequester.requestFocus() },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End
                ) {
                    TextField(
                        value = state.expression,
                        onValueChange = { viewModel.onExpressionChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.End
                        ),
                        placeholder = { 
                            Text(
                                "x² + 5x + 6 = 0", 
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Monospace, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            ) 
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { 
                            viewModel.solve()
                            focusManager.clearFocus()
                        })
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AnimatedVisibility(
                        visible = state.result.isNotEmpty(),
                        enter = slideInVertically { it } + fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "SOLUTIONS: ${state.result}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    
                    state.error?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                text = it.uppercase(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // Controls & Constants
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { viewModel.toggleMode() },
                    color = if (state.isDegreeMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (state.isDegreeMode) "DEG" else "RAD",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (state.isDegreeMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.constants) { constant ->
                        Surface(
                            onClick = { viewModel.appendSymbol(constant.value) },
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                        ) {
                            Text(
                                constant.symbol,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Keypad
            val buttons = listOf(
                listOf("sin(", "cos(", "tan(", "log("),
                listOf("x", "^", "(", ")"),
                listOf("7", "8", "9", "/"),
                listOf("4", "5", "6", "*"),
                listOf("1", "2", "3", "-"),
                listOf("0", ".", "=", "+"),
                listOf("C", "BS")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(2.5f)
                    .padding(bottom = 24.dp)
                    .fadingEdge(
                        brush = Brush.verticalGradient(0f to Color.Transparent, 0.05f to Color.Black, 0.95f to Color.Black, 1f to Color.Transparent),
                        length = 16.dp
                    )
            ) {
                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { char ->
                            KeypadButton(
                                text = char,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (char) {
                                        "C" -> viewModel.clear()
                                        "BS" -> viewModel.backspace()
                                        "=" -> {
                                            viewModel.solve()
                                            focusManager.clearFocus()
                                        }
                                        else -> viewModel.appendSymbol(char)
                                    }
                                },
                                isAction = char == "=" || char == "/" || char == "*" || char == "-" || char == "+" || char == "^",
                                isClear = char == "C" || char == "BS",
                                isVariable = char == "x"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHistory) {
        HistoryBottomSheet(
            history = history,
            onDismiss = { showHistory = false },
            onSelect = { 
                viewModel.onExpressionChange(it.expression)
                showHistory = false
            }
        )
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.6f).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                "HISTORY LOG",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClick { onSelect(item) },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(item.expression, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
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
