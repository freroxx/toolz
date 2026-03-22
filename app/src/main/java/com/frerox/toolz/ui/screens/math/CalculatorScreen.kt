package com.frerox.toolz.ui.screens.math

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DeleteOutline
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    var showHistory by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(Color.Transparent).statusBarsPadding()) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "MATH ENGINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                onBack()
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { 
                                vibrationManager?.vibrateClick()
                                showHistory = true
                            },
                            modifier = Modifier
                                .padding(8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = "History")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 24.dp)
        ) {
            // Display Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .bouncyClick { 
                            vibrationManager?.vibrateLongClick()
                            // Copy to clipboard logic could go here or via ViewModel
                            viewModel.onCopyResult()
                        },
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Formula / History line
                    AnimatedContent(
                        targetState = state.formula,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "formula"
                    ) { formula ->
                        Text(
                            text = formula,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    
                    // Main Display
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = state.display,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (state.display.length > 10) 48.sp else 72.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Live Result
                    AnimatedVisibility(
                        visible = state.liveResult != null && state.liveResult != state.display,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = "= ${state.liveResult ?: ""}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    state.error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Keypad Area
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxWidth()
            ) {
                Column {
                    // Scientific Toggle Row (Compact)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { 
                                vibrationManager?.vibrateClick()
                                viewModel.onToggleMode() 
                            },
                            shape = CircleShape,
                            color = if (state.isScientific) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (state.isScientific) Icons.Rounded.Science else Icons.Rounded.Calculate,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (state.isScientific) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = state.isScientific,
                            transitionSpec = {
                                (fadeIn(tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)))
                                    .togetherWith(fadeOut(tween(300)) + scaleOut(targetScale = 0.92f))
                            }, label = "keypad"
                        ) { isScientific ->
                            if (isScientific) {
                                ScientificKeypad(viewModel)
                            } else {
                                StandardKeypad(viewModel)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.navigationBarsPadding())
        }

        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "HISTORY",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    
                    if (state.history.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No history yet", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(state.history) { item ->
                                Column(modifier = Modifier.fillMaxWidth().bouncyClick {
                                    viewModel.onClear()
                                    // Optionally populate display with result
                                    // viewModel.onDigit(item.second)
                                    showHistory = false
                                }) {
                                    Text(
                                        item.first,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        "= ${item.second}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
fun StandardKeypad(viewModel: CalculatorViewModel) {
    val hapticEnabled = LocalHapticEnabled.current
    val vibrationManager = LocalVibrationManager.current
    val buttons = listOf(
        "C", "÷", "×", "DEL",
        "7", "8", "9", "-",
        "4", "5", "6", "+",
        "1", "2", "3", "=",
        "0", "00", ".", "%"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(buttons) { btn ->
            when (btn) {
                "DEL" -> CalculatorIconButton(Icons.AutoMirrored.Rounded.Backspace, {
                    if (hapticEnabled) vibrationManager?.vibrateTick()
                    viewModel.onBackspace() 
                })
                else -> {
                    CalculatorButton(
                        text = btn,
                        onClick = {
                            if (hapticEnabled) {
                                if (btn == "=") vibrationManager?.vibrateLongClick()
                                else vibrationManager?.vibrateClick()
                            }
                            when (btn) {
                                "C" -> viewModel.onClear()
                                "=" -> viewModel.onEquals()
                                "+" -> viewModel.onOperator("+")
                                "-" -> viewModel.onOperator("-")
                                "×" -> viewModel.onOperator("×")
                                "÷" -> viewModel.onOperator("÷")
                                "%" -> viewModel.onOperator("/100")
                                else -> viewModel.onDigit(btn)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ScientificKeypad(viewModel: CalculatorViewModel) {
    val hapticEnabled = LocalHapticEnabled.current
    val vibrationManager = LocalVibrationManager.current
    val buttons = listOf(
        "2nd", "deg", "sin", "cos", "tan",
        "xⁿ", "log", "ln", "(", ")",
        "√", "AC", "DEL", "%", "÷",
        "7", "8", "9", "×",
        "4", "5", "6", "-",
        "1", "2", "3", "+",
        "0", ".", "π", "e", "="
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(buttons) { btn ->
            when (btn) {
                "DEL" -> CalculatorIconButton(Icons.AutoMirrored.Rounded.Backspace, {
                    if (hapticEnabled) vibrationManager?.vibrateTick()
                    viewModel.onBackspace() 
                }, isScientific = true)
                "AC" -> {
                    CalculatorButton(
                        text = "AC",
                        onClick = { 
                            if (hapticEnabled) vibrationManager?.vibrateLongClick()
                            viewModel.onClear() 
                        },
                        isScientific = true,
                        isClear = true
                    )
                }
                "deg" -> {
                    CalculatorButton(
                        text = if (viewModel.uiState.collectAsState().value.isDegreeMode) "DEG" else "RAD",
                        onClick = { 
                            if (hapticEnabled) vibrationManager?.vibrateClick()
                            viewModel.onToggleAngleMode() 
                        },
                        isScientific = true
                    )
                }
                else -> {
                    CalculatorButton(
                        text = btn,
                        onClick = {
                            if (hapticEnabled) {
                                if (btn == "=") vibrationManager?.vibrateLongClick()
                                else vibrationManager?.vibrateClick()
                            }
                            when (btn) {
                                "=" -> viewModel.onEquals()
                                "2nd" -> { /* Toggle 2nd functions */ }
                                "xⁿ" -> viewModel.onOperator("^")
                                "AC" -> viewModel.onClear()
                                "+", "-", "×", "÷", "(", ")", "%" -> viewModel.onOperator(btn)
                                "sin", "cos", "tan", "log", "ln", "√" -> viewModel.onFunction(btn)
                                "π" -> viewModel.onDigit("π")
                                "e" -> viewModel.onDigit("e")
                                else -> viewModel.onDigit(btn)
                            }
                        },
                        isScientific = true
                    )
                }
            }
        }
    }
}

@Composable
fun CalculatorButton(
    text: String, 
    onClick: () -> Unit, 
    isScientific: Boolean = false,
    isClear: Boolean = false
) {
    val isOperator = text in listOf("=", "+", "-", "×", "÷", "^", "(", ")")
    val isFunction = text in listOf("sin", "cos", "tan", "log10", "ln", "sqrt", "abs", "exp", "inv", "asin", "acos", "atan")
    
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        isFunction -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }
    
    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.primary
        isClear -> MaterialTheme.colorScheme.error
        isFunction -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .aspectRatio(if (isScientific) 1.5f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(if (text == "=") 0.dp else 1.dp, contentColor.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = if (isScientific) {
                    if (text.length > 3) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CalculatorIconButton(icon: ImageVector, onClick: () -> Unit, isScientific: Boolean = false) {
    Surface(
        modifier = Modifier
            .aspectRatio(if (isScientific) 1.5f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (isScientific) 20.dp else 28.dp)
            )
        }
    }
}
