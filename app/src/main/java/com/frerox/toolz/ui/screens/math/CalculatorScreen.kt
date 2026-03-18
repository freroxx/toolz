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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "MATH ENGINE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = "History")
                    }
                    IconButton(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            viewModel.onToggleMode() 
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (state.isScientific) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            imageVector = if (state.isScientific) Icons.Rounded.Science else Icons.Rounded.Calculate,
                            contentDescription = "Toggle Mode",
                            tint = if (state.isScientific) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
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
                    .weight(1.2f)
                    .padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(40.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    AnimatedContent(
                        targetState = state.formula,
                        transitionSpec = { 
                            if (performanceMode) {
                                fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                            } else {
                                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                            }
                        }, label = "formula"
                    ) { formula ->
                        Text(
                            text = formula,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    
                    Text(
                        text = state.display,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = if (state.display.length > 12) 40.sp else if (state.display.length > 8) 52.sp else 72.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        lineHeight = if (state.display.length > 12) 48.sp else 76.sp
                    )
                    
                    state.error?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            @Suppress("DEPRECATION")
                            Text(
                                text = it.uppercase(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }

            if (state.isScientific) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @Suppress("DEPRECATION")
                    Text(
                        "ADVANCED CALCULUS", 
                        style = MaterialTheme.typography.labelSmall, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp
                    )
                    
                    Surface(
                        onClick = { 
                            vibrationManager?.vibrateClick()
                            viewModel.onToggleAngleMode() 
                        },
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        @Suppress("DEPRECATION")
                        Text(
                            if (state.isDegreeMode) "DEG" else "RAD",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            // Buttons Grid
            Box(modifier = Modifier.weight(3.8f)) {
                AnimatedContent(
                    targetState = state.isScientific,
                    transitionSpec = {
                        if (performanceMode) {
                            fadeIn(animationSpec = snap()) togetherWith fadeOut(animationSpec = snap())
                        } else {
                            (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.95f))
                                .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.95f))
                        }
                    }, label = "keypad"
                ) { isScientific ->
                    if (isScientific) {
                        ScientificKeypad(viewModel)
                    } else {
                        StandardKeypad(viewModel)
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
        "sin", "cos", "tan", "inv",
        "asin", "acos", "atan", "exp",
        "sqrt", "log10", "ln", "abs",
        "π", "e", "^", "÷",
        "7", "8", "9", "×",
        "4", "5", "6", "-",
        "1", "2", "3", "+",
        "(", "0", ")", "=",
        "C", "DEL"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(buttons) { btn ->
            when (btn) {
                "DEL" -> CalculatorIconButton(Icons.AutoMirrored.Rounded.Backspace, {
                    if (hapticEnabled) vibrationManager?.vibrateTick()
                    viewModel.onBackspace() 
                }, isScientific = true)
                "C" -> {
                    CalculatorButton(
                        text = "C",
                        onClick = { 
                            if (hapticEnabled) vibrationManager?.vibrateLongClick()
                            viewModel.onClear() 
                        },
                        isScientific = true,
                        isClear = true
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
                                "+", "-", "×", "÷", "^", "(", ")" -> viewModel.onOperator(btn)
                                "sin", "cos", "tan", "log10", "ln", "sqrt", "abs", "exp", "inv", "asin", "acos", "atan" -> viewModel.onFunction(btn)
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
    val performanceMode = LocalPerformanceMode.current
    val isOperator = text in listOf("=", "+", "-", "×", "÷", "^", "(", ")")
    val isFunction = text in listOf("sin", "cos", "tan", "log10", "ln", "sqrt", "abs", "exp", "inv", "asin", "acos", "atan")
    
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        isFunction -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    
    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
        isClear -> MaterialTheme.colorScheme.onErrorContainer
        isFunction -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .aspectRatio(if (isScientific) 1.45f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(if (isScientific) 20.dp else 32.dp),
        color = containerColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shadowElevation = if (performanceMode) 0.dp else 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            @Suppress("DEPRECATION")
            Text(
                text = text,
                style = if (isScientific) {
                    if (text.length > 3) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.headlineSmall
                },
                fontWeight = FontWeight.Black,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CalculatorIconButton(icon: ImageVector, onClick: () -> Unit, isScientific: Boolean = false) {
    val performanceMode = LocalPerformanceMode.current
    Surface(
        modifier = Modifier
            .aspectRatio(if (isScientific) 1.45f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(if (isScientific) 20.dp else 32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        shadowElevation = if (performanceMode) 0.dp else 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (isScientific) 24.dp else 32.dp)
            )
        }
    }
}
