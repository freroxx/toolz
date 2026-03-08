package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun StepCounterScreen(
    viewModel: StepCounterViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val permissionState = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        null
    }

    DisposableEffect(permissionState?.status?.isGranted ?: true) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Step Counter") },
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
            if (permissionState == null || permissionState.status.isGranted) {
                if (state.isSensorPresent) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
                        val progress = (state.steps.toFloat() / state.goal.toFloat()).coerceIn(0f, 1f)
                        val animatedProgress by animateFloatAsState(targetValue = progress)
                        
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 16.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsRun,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = state.steps.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Goal: ${state.goal}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Text(
                        text = if (state.steps >= state.goal) "Goal Reached! 🎉" else "${state.goal - state.steps} steps left",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (state.steps >= state.goal) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text("Step counter sensor not available on this device.")
                }
            } else {
                Text("Activity recognition permission is required to count steps.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
