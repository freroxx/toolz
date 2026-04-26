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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.RectangleShape
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
import com.frerox.toolz.ui.theme.toolzBackground

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
                        @Suppress("DEPRECATION")
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
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Display Area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .bouncyClick { 
                                vibrationManager?.vibrateLongClick()
                                viewModel.onCopyResult()
                            },
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Formula
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

                // Keypad Area
                Box(
                    modifier = Modifier
                        .weight(3.5f)
                        .fillMaxWidth()
                ) {
                    Column {
                        // Scientific Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                onClick = { 
                                    vibrationManager?.vibrateClick()
                                    viewModel.onToggleMode() 
                                },
                                shape = CircleShape,
                                color = if (state.isScientific) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (state.isScientific) Icons.Rounded.Science else Icons.Rounded.Calculate,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = if (state.isScientific) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = state.isScientific,
                                transitionSpec = {
                                    (fadeIn(tween(400)) + scaleIn(initialScale = 0.95f))
                                        .togetherWith(fadeOut(tween(300)) + scaleOut(targetScale = 0.95f))
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
        }

        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
    val state by viewModel.uiState.collectAsState()
    
    // Popular scientific layout: Functions in upper grid, basic keys below
    val functions = listOf(
        "sin", "cos", "tan", "log", "ln",
        "√", "xⁿ", "π", "e", "(",
        ")", "deg", "inv", "abs", "CONST"
    )
    
    val basics = listOf(
        "AC", "DEL", "%", "÷",
        "7", "8", "9", "×",
        "4", "5", "6", "-",
        "1", "2", "3", "+",
        "0", ".", "!", "="
    )

    var showConstants by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Functions Grid (Top half, smaller buttons, tight)
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.1f)),
            userScrollEnabled = false
        ) {
            items(functions) { btn ->
                ScientificFunctionButton(
                    text = if (btn == "deg") (if (state.isDegreeMode) "DEG" else "RAD") else btn,
                    onClick = {
                        if (hapticEnabled) vibrationManager?.vibrateTick()
                        when (btn) {
                            "deg" -> viewModel.onToggleAngleMode()
                            "xⁿ" -> viewModel.onOperator("^")
                            "π" -> viewModel.onDigit("π")
                            "e" -> viewModel.onDigit("e")
                            "(", ")" -> viewModel.onOperator(btn)
                            "CONST" -> showConstants = true
                            else -> viewModel.onFunction(btn)
                        }
                    }
                )
            }
        }

        // Main Keypad (Bottom half, standard grid, no gaps)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            items(basics) { btn ->
                when (btn) {
                    "DEL" -> ScientificMainIconButton(Icons.AutoMirrored.Rounded.Backspace, {
                        if (hapticEnabled) vibrationManager?.vibrateTick()
                        viewModel.onBackspace() 
                    })
                    "AC" -> ScientificMainButton("AC", { 
                        if (hapticEnabled) vibrationManager?.vibrateLongClick()
                        viewModel.onClear() 
                    }, isClear = true)
                    "=" -> ScientificMainButton("=", { 
                        if (hapticEnabled) vibrationManager?.vibrateLongClick()
                        viewModel.onEquals() 
                    }, isEquals = true)
                    else -> ScientificMainButton(btn, {
                        if (hapticEnabled) vibrationManager?.vibrateClick()
                        when (btn) {
                            "+", "-", "×", "÷", "%" -> viewModel.onOperator(btn)
                            else -> viewModel.onDigit(btn)
                        }
                    })
                }
            }
        }
    }

    if (showConstants) {
        ConstantsDialog(
            onDismiss = { showConstants = false },
            onSelect = { value ->
                viewModel.onDigit(value)
                showConstants = false
            }
        )
    }
}

@Composable
fun ConstantsDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val constants = listOf(
        "π" to "3.14159265",
        "e" to "2.71828182",
        "φ" to "1.61803398",
        "c" to "299792458",
        "G" to "6.6743e-11",
        "h" to "6.62607e-34",
        "k" to "1.38064e-23",
        "NA" to "6.02214e23",
        "R" to "8.31446"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scientific Constants", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                constants.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (name, value) ->
                            Button(
                                onClick = { onSelect(value) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(name, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    Text(
                                        text = value.take(6) + "...",
                                        fontSize = 10.sp,
                                        modifier = Modifier.alpha(0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun CalculatorButton(text: String, onClick: () -> Unit) {
    val isOperator = text in listOf("=", "+", "-", "×", "÷")
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        text == "C" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }
    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.primary
        text == "C" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.aspectRatio(1f).fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

@Composable
fun CalculatorIconButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.aspectRatio(1f).fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
        }
    }
}

// Scientific Specific (No Gap Design)

@Composable
fun ScientificFunctionButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(48.dp).fillMaxWidth().bouncyClick(onClick = onClick),
        color = Color.Transparent,
        shape = RectangleShape,
        border = BorderStroke(0.2.dp, MaterialTheme.colorScheme.onSurface.copy(0.05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(0.8f)
            )
        }
    }
}

@Composable
fun ScientificMainButton(text: String, onClick: () -> Unit, isClear: Boolean = false, isEquals: Boolean = false) {
    val containerColor = when {
        isEquals -> MaterialTheme.colorScheme.primary
        isClear -> MaterialTheme.colorScheme.errorContainer.copy(0.15f)
        text in listOf("+", "-", "×", "÷", "%") -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val contentColor = when {
        isEquals -> MaterialTheme.colorScheme.onPrimary
        isClear -> MaterialTheme.colorScheme.error
        text in listOf("+", "-", "×", "÷", "%") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier.aspectRatio(1.1f).fillMaxWidth().bouncyClick(onClick = onClick),
        color = containerColor,
        shape = RectangleShape,
        border = BorderStroke(0.3.dp, MaterialTheme.colorScheme.onSurface.copy(0.08f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (isEquals) FontWeight.Black else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
fun ScientificMainIconButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.aspectRatio(1.1f).fillMaxWidth().bouncyClick(onClick = onClick),
        color = Color.Transparent,
        shape = RectangleShape,
        border = BorderStroke(0.3.dp, MaterialTheme.colorScheme.onSurface.copy(0.08f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}
