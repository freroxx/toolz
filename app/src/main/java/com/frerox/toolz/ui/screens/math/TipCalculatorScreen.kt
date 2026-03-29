package com.frerox.toolz.ui.screens.math

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipCalculatorScreen(
    viewModel: TipCalculatorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val performanceMode = LocalPerformanceMode.current

    var showCurrencySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GRATUITY ENGINE", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCurrencySheet = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        if (state.isAiDetecting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(state.currencySymbol, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 16.dp, bottom = 16.dp))
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Main Result Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 16.dp,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "TOTAL PER PERSON",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            modifier = Modifier.alpha(0.8f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = String.format(Locale.getDefault(), "%s%.2f", state.currencySymbol, state.totalPerPerson),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 60.sp,
                                letterSpacing = (-2).sp
                            )
                        )
                        Spacer(Modifier.height(32.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ResultSubItem("BILL + TIP", String.format(Locale.getDefault(), "%s%.2f", state.currencySymbol, (state.billAmount.toDoubleOrNull() ?: 0.0) + state.totalTip))
                            VerticalDivider(modifier = Modifier.height(40.dp), color = Color.White.copy(alpha = 0.2f))
                            ResultSubItem("TOTAL TIP", String.format(Locale.getDefault(), "%s%.2f", state.currencySymbol, state.totalTip))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(28.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "CALCULATION DATA",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.5.sp
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        
                        OutlinedTextField(
                            value = state.billAmount,
                            onValueChange = { viewModel.onBillChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Bill Amount (${state.currencyCode})") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            prefix = { Text("${state.currencySymbol} ", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) },
                            shape = RoundedCornerShape(20.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(Modifier.height(32.dp))

                        // Tip Percentage
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TIP PERCENTAGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${state.tipPercentage.toInt()}%", 
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.titleMedium, 
                                    fontWeight = FontWeight.Black, 
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Slider(
                            value = state.tipPercentage,
                            onValueChange = { viewModel.onTipChange(it) },
                            valueRange = 0f..100f,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(10f, 15f, 20f, 25f).forEach { pct ->
                                val isSelected = state.tipPercentage == pct && state.customTip.isEmpty()
                                Surface(
                                    onClick = { viewModel.onTipChange(pct) },
                                    modifier = Modifier.weight(1f).height(44.dp).bouncyClick {},
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${pct.toInt()}%", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Custom Tip Percentage
                        OutlinedTextField(
                            value = state.customTip,
                            onValueChange = { viewModel.onCustomTipChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Custom Tip %") },
                            placeholder = { Text("Enter percentage") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            suffix = { Text("%", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) },
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(Modifier.height(36.dp))

                        // Split Count
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PERSON COUNT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(48.dp).bouncyClick { if (state.splitCount > 1) viewModel.onSplitChange(state.splitCount - 1) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Remove, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                
                                Text(
                                    text = state.splitCount.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Surface(
                                    modifier = Modifier.size(48.dp).bouncyClick { viewModel.onSplitChange(state.splitCount + 1) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        if (showCurrencySheet) {
            CurrencySelectorSheet(
                onDismiss = { showCurrencySheet = false },
                onSelect = { code, symbol ->
                    viewModel.onCurrencyChange(code, symbol)
                    showCurrencySheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelectorSheet(onDismiss: () -> Unit, onSelect: (String, String) -> Unit) {
    val currencies = remember {
        listOf(
            "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "INR" to "₹",
            "AUD" to "A$", "CAD" to "C$", "CHF" to "Fr", "CNY" to "¥", "HKD" to "HK$",
            "NZD" to "NZ$", "SEK" to "kr", "KRW" to "₩", "SGD" to "S$", "NOK" to "kr",
            "MXN" to "$", "RUB" to "₽", "ZAR" to "R", "TRY" to "₺", "BRL" to "R$",
            "TWD" to "NT$", "DKK" to "kr", "PLN" to "zł", "THB" to "฿", "IDR" to "Rp",
            "HUF" to "Ft", "CZK" to "Kč", "ILS" to "₪", "CLP" to "$", "PHP" to "₱",
            "AED" to "د.إ", "COP" to "$", "SAR" to "﷼", "MYR" to "RM", "RON" to "lei"
        ).sortedBy { it.first }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                "SELECT CURRENCY",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            
            Box(modifier = Modifier.heightIn(max = 400.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    currencies.forEach { (code, symbol) ->
                        ListItem(
                            headlineContent = { Text(code, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(symbol) },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(symbol, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            modifier = Modifier.bouncyClick { onSelect(code, symbol) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResultSubItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.alpha(0.7f), letterSpacing = 0.5.sp)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}
