package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onToggleMode() }) {
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = "Toggle Mode",
                            tint = if (state.isScientific) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Display Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = state.formula,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.display,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (state.display.length > 10) 48.sp else 64.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 2
                        )
                        state.error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Buttons Grid
            AnimatedContent(
                targetState = state.isScientific,
                transitionSpec = {
                    fadeIn() + slideInVertically { it } togetherWith fadeOut() + slideOutVertically { -it }
                }, label = ""
            ) { isScientific ->
                if (isScientific) {
                    ScientificButtons(viewModel)
                } else {
                    NormalButtons(viewModel)
                }
            }
        }
    }
}

@Composable
fun NormalButtons(viewModel: CalculatorViewModel) {
    val buttons = listOf(
        "C", "÷", "×", "DEL",
        "7", "8", "9", "-",
        "4", "5", "6", "+",
        "1", "2", "3", "=",
        "0", "00", ".", ""
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(buttons) { btn ->
            if (btn == "DEL") {
                CalcIconButton(Icons.AutoMirrored.Rounded.Backspace, { viewModel.onBackspace() })
            } else if (btn.isNotEmpty()) {
                CalcButton(
                    text = btn,
                    onClick = {
                        when (btn) {
                            "C" -> viewModel.onClear()
                            "=" -> viewModel.onEquals()
                            "+" -> viewModel.onOperator("+")
                            "-" -> viewModel.onOperator("-")
                            "×" -> viewModel.onOperator("*")
                            "÷" -> viewModel.onOperator("/")
                            else -> viewModel.onDigit(btn)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ScientificButtons(viewModel: CalculatorViewModel) {
    val buttons = listOf(
        "sin", "cos", "tan", "sqrt",
        "log", "ln", "(", ")",
        "π", "e", "^", "C",
        "7", "8", "9", "÷",
        "4", "5", "6", "×",
        "1", "2", "3", "-",
        "0", ".", "=", "+"
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(buttons) { btn ->
            CalcButton(
                text = btn,
                onClick = {
                    when (btn) {
                        "C" -> viewModel.onClear()
                        "=" -> viewModel.onEquals()
                        "+", "-", "×", "÷", "^", "(", ")" -> viewModel.onOperator(btn.replace("×", "*").replace("÷", "/"))
                        "sin", "cos", "tan", "log", "ln", "sqrt" -> viewModel.onFunction(btn)
                        "π" -> viewModel.onDigit("π")
                        "e" -> viewModel.onDigit("e")
                        else -> viewModel.onDigit(btn)
                    }
                },
                isSmall = true
            )
        }
    }
}

@Composable
fun CalcButton(text: String, onClick: () -> Unit, isSmall: Boolean = false) {
    val isOperator = text in listOf("=", "+", "-", "×", "÷", "^")
    val isAction = text in listOf("C", "DEL", "sin", "cos", "tan", "log", "ln", "sqrt", "(", ")", "π", "e")
    
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer
        isAction -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
        isAction -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Button(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(if (isSmall) 1.2f else 1f)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            style = if (isSmall) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CalcIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(icon, contentDescription = null)
    }
}
