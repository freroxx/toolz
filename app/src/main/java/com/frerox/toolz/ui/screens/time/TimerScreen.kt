package com.frerox.toolz.ui.screens.time

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var minutesInput by remember { mutableStateOf("0") }
    var secondsInput by remember { mutableStateOf("0") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timer") },
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
            verticalArrangement = Arrangement.Center
        ) {
            if (state.initialTime == 0L || state.isFinished) {
                Text(
                    text = "Set Timer",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = minutesInput,
                        onValueChange = { if (it.length <= 2) minutesInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Min") },
                        modifier = Modifier.width(80.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(" : ", style = MaterialTheme.typography.headlineLarge)
                    OutlinedTextField(
                        value = secondsInput,
                        onValueChange = { if (it.length <= 2) secondsInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Sec") },
                        modifier = Modifier.width(80.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        val m = minutesInput.toIntOrNull() ?: 0
                        val s = secondsInput.toIntOrNull() ?: 0
                        if (m > 0 || s > 0) {
                            viewModel.setTimer(m, s)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set")
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(280.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { 
                            if (state.initialTime > 0) state.remainingTime.toFloat() / state.initialTime else 0f 
                        },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 12.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = formatTimerTime(state.remainingTime),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Light
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(64.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilledTonalIconButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset")
                    }
                    
                    FloatingActionButton(
                        onClick = { viewModel.toggleStartStop() },
                        modifier = Modifier.size(80.dp),
                        containerColor = if (state.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isRunning) "Pause" else "Start",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                
                if (state.isFinished) {
                    Text(
                        text = "Time's up!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimerTime(timeMillis: Long): String {
    val totalSeconds = (timeMillis + 999) / 1000 // Round up
    val minutes = (totalSeconds / 60) % 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
