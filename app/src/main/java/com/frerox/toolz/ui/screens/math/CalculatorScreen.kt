package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Decorative background elements
        Box(
            modifier = Modifier
                .size(350.dp)
                .offset(x = (-120).dp, y = (-120).dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(450.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 180.dp, y = 180.dp)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f), CircleShape)
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "CALCULATOR",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.onToggleMode() },
                            modifier = Modifier.padding(end = 12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(
                                if (state.isScientific) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
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
                        .weight(1.2f)
                        .padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        AnimatedContent(
                            targetState = state.formula,
                            transitionSpec = { 
                                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                            }, label = "formula"
                        ) { formula ->
                            Text(
                                text = formula,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        Text(
                            text = state.display,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (state.display.length > 12) 38.sp else if (state.display.length > 8) 48.sp else 64.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            lineHeight = if (state.display.length > 12) 44.sp else 70.sp
                        )
                        
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
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
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
                        Text(
                            "SCIENTIFIC MODE", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black, 
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            letterSpacing = 2.sp
                        )
                        
                        Surface(
                            onClick = { viewModel.onToggleAngleMode() },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Text(
                                if (state.isDegreeMode) "DEG" else "RAD",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Buttons Grid
                Box(modifier = Modifier.weight(3.5f)) {
                    AnimatedContent(
                        targetState = state.isScientific,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) + slideInVertically { it / 8 })
                                .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically { -it / 8 })
                        }, label = "keypad"
                    ) { isScientific ->
                        if (isScientific) {
                            ScientificKeypad(viewModel)
                        } else {
                            StandardKeypad(viewModel)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun StandardKeypad(viewModel: CalculatorViewModel) {
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
                "DEL" -> CalculatorIconButton(Icons.AutoMirrored.Rounded.Backspace, { viewModel.onBackspace() })
                "" -> Spacer(modifier = Modifier.aspectRatio(1f))
                else -> {
                    CalculatorButton(
                        text = btn,
                        onClick = {
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
                "DEL" -> CalculatorIconButton(Icons.AutoMirrored.Rounded.Backspace, { viewModel.onBackspace() }, isScientific = true)
                "C" -> {
                    CalculatorButton(
                        text = "C",
                        onClick = { viewModel.onClear() },
                        isScientific = true,
                        isClear = true
                    )
                }
                else -> {
                    CalculatorButton(
                        text = btn,
                        onClick = {
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
    val isOperator = text in listOf("=", "+", "-", "×", "÷", "^", "(", ")")
    val isFunction = text in listOf("sin", "cos", "tan", "log10", "ln", "sqrt", "abs", "exp", "inv", "asin", "acos", "atan")
    
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        isFunction -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
            .aspectRatio(if (isScientific) 1.4f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(if (isScientific) 16.dp else 24.dp),
        color = containerColor,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
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
    Surface(
        modifier = Modifier
            .aspectRatio(if (isScientific) 1.4f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(if (isScientific) 16.dp else 24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (isScientific) 22.dp else 28.dp)
            )
        }
    }
}
