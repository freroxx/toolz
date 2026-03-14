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
                .size(300.dp)
                .offset(x = (-100).dp, y = (-100).dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 150.dp, y = 150.dp)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f), CircleShape)
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "CALCULATOR",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.padding(12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(24.dp))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.onToggleMode() },
                            modifier = Modifier.padding(end = 12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(
                                if (state.isScientific) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Science,
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        horizontalAlignment = Alignment.End
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
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        
                        Text(
                            text = state.display,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (state.display.length > 12) 42.sp else if (state.display.length > 8) 54.sp else 72.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 2,
                            lineHeight = if (state.display.length > 12) 48.sp else 78.sp
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
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { viewModel.onToggleAngleMode() },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Text(
                                if (state.isDegreeMode) "DEGREE" else "RADIAN",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Buttons Grid
                Box(modifier = Modifier.weight(3f)) {
                    AnimatedContent(
                        targetState = state.isScientific,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.92f))
                                .togetherWith(fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.92f))
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
        "0", "00", ".", ""
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
        "sin", "cos", "tan", "(",
        "sqrt", "log", "ln", ")",
        "π", "e", "^", "÷",
        "7", "8", "9", "×",
        "4", "5", "6", "-",
        "1", "2", "3", "+",
        "C", "0", ".", "=",
        "DEL"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(buttons) { btn ->
            when (btn) {
                "DEL" -> CalculatorIconButton(Icons.AutoMirrored.Rounded.Backspace, { viewModel.onBackspace() }, isScientific = true)
                else -> {
                    CalculatorButton(
                        text = btn,
                        onClick = {
                            when (btn) {
                                "C" -> viewModel.onClear()
                                "=" -> viewModel.onEquals()
                                "+", "-", "×", "÷", "^", "(", ")" -> viewModel.onOperator(btn)
                                "sin", "cos", "tan", "log", "ln", "sqrt" -> viewModel.onFunction(btn)
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
    isScientific: Boolean = false
) {
    val isOperator = text in listOf("=", "+", "-", "×", "÷", "^")
    val isFunction = text in listOf("sin", "cos", "tan", "log", "ln", "sqrt")
    val isClear = text == "C"
    
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        isClear -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
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
            .aspectRatio(if (isScientific) 1.2f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 2.dp,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = if (isScientific) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = contentColor
            )
        }
    }
}

@Composable
fun CalculatorIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, isScientific: Boolean = false) {
    Surface(
        modifier = Modifier
            .aspectRatio(if (isScientific) 1.2f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 2.dp,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
