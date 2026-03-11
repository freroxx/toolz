package com.frerox.toolz.ui.screens.math

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.math.MathHistory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquationSolverScreen(
    viewModel: EquationSolverViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EQUATION SOLVER", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Rounded.History, contentDescription = "History")
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = state.expression.ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (state.result.isNotEmpty()) {
                        Text(
                            text = "= ${state.result}",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            textAlign = TextAlign.End
                        )
                    }
                    
                    if (state.error != null) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Constants & Functions Bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item { 
                    FilterChip(
                        selected = state.isDegreeMode, 
                        onClick = { viewModel.toggleMode() },
                        label = { Text(if (state.isDegreeMode) "DEG" else "RAD") }
                    )
                }
                val constants = listOf("π", "e", "c", "G", "h")
                items(constants) { constant ->
                    val value = when(constant) {
                        "c" -> "299792458"
                        "G" -> "6.674e-11"
                        "h" -> "6.626e-34"
                        else -> constant
                    }
                    AssistChip(
                        onClick = { viewModel.appendSymbol(value) },
                        label = { Text(constant) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Keypad
            val buttons = listOf(
                listOf("C", "(", ")", "/"),
                listOf("7", "8", "9", "*"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "BS", "=")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        "=" -> viewModel.solve()
                                        else -> viewModel.appendSymbol(char)
                                    }
                                },
                                isAction = char == "=" || char == "/" || char == "*" || char == "-" || char == "+",
                                isClear = char == "C" || char == "BS"
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
    isClear: Boolean = false
) {
    val containerColor = when {
        isAction -> MaterialTheme.colorScheme.primaryContainer
        isClear -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when {
        isAction -> MaterialTheme.colorScheme.onPrimaryContainer
        isClear -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (text == "BS") {
            Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = contentColor)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxHeight(0.6f).padding(16.dp)) {
            Text(
                "Calculation History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.expression, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "= ${item.result}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
