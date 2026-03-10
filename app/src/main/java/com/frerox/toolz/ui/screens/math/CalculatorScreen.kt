package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            ) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { Text("Calculator", fontWeight = FontWeight.ExtraBold) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Display Area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        AnimatedContent(
                            targetState = state.formula,
                            transitionSpec = { fadeIn() togetherWith fadeOut() }, label = ""
                        ) { formula ->
                            Text(
                                text = formula,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                textAlign = TextAlign.End,
                                minLines = 1
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = state.display,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (state.display.length > 12) 40.sp else 56.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 2
                        )
                        
                        state.error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Buttons Grid
            Box(modifier = Modifier.weight(2.5f)) {
                AnimatedContent(
                    targetState = state.isScientific,
                    transitionSpec = {
                        slideInHorizontally { if (it > 0) it else -it } + fadeIn() togetherWith 
                        slideOutHorizontally { if (it > 0) -it else it } + fadeOut()
                    }, label = ""
                ) { isScientific ->
                    if (isScientific) {
                        ScientificLayout(viewModel)
                    } else {
                        NormalLayout(viewModel)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun NormalLayout(viewModel: CalculatorViewModel) {
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(buttons) { btn ->
            when (btn) {
                "DEL" -> CalcIconButton(Icons.AutoMirrored.Rounded.Backspace, { viewModel.onBackspace() })
                "" -> Spacer(modifier = Modifier.aspectRatio(1f))
                else -> {
                    CalcButton(
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
fun ScientificLayout(viewModel: CalculatorViewModel) {
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(buttons) { btn ->
            CalcButton(
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

@Composable
fun CalcButton(
    text: String, 
    onClick: () -> Unit, 
    isScientific: Boolean = false
) {
    val isOperator = text in listOf("=", "+", "-", "×", "÷", "^")
    val isAction = text in listOf("C", "DEL", "sin", "cos", "tan", "log", "ln", "sqrt", "(", ")", "π", "e")
    
    val containerColor = when {
        text == "=" -> MaterialTheme.colorScheme.primary
        isOperator -> MaterialTheme.colorScheme.primaryContainer
        isAction -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    
    val contentColor = when {
        text == "=" -> MaterialTheme.colorScheme.onPrimary
        isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
        isAction -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .aspectRatio(if (isScientific) 1.2f else 1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        tonalElevation = if (isOperator) 4.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = if (isScientific) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = contentColor
            )
        }
    }
}

@Composable
fun CalcIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
