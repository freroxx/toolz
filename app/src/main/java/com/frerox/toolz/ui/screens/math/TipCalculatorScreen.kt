package com.frerox.toolz.ui.screens.math

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipCalculatorScreen(
    viewModel: TipCalculatorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tip Calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            OutlinedTextField(
                value = state.billAmount,
                onValueChange = { viewModel.onBillChange(it) },
                label = { Text("Bill Amount") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("$ ") }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Tip: ${state.tipPercentage.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Slider(
                value = state.tipPercentage,
                onValueChange = { viewModel.onTipChange(it) },
                valueRange = 0f..50f,
                steps = 10
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Split", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.onSplitChange(state.splitCount - 1) }) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        text = state.splitCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(onClick = { viewModel.onSplitChange(state.splitCount + 1) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Increase")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    ResultRow("Total Tip", String.format(Locale.getDefault(), "$ %.2f", state.totalTip))
                    Spacer(modifier = Modifier.height(16.dp))
                    ResultRow("Per Person", String.format(Locale.getDefault(), "$ %.2f", state.totalPerPerson), isLarge = true)
                }
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String, isLarge: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = if (isLarge) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
        Text(
            text = value,
            style = if (isLarge) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
