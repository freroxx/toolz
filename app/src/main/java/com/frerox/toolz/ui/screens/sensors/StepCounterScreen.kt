package com.frerox.toolz.ui.screens.sensors

import android.Manifest
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
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
    
    // Permission for Activity Recognition (Android 10+)
    val activityPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        null
    }

    // Permission for Notifications (Android 13+)
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fitness Tracker", fontWeight = FontWeight.Bold) },
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
            val hasActivityPermission = activityPermissionState?.status?.isGranted ?: true
            
            if (hasActivityPermission) {
                if (state.isSensorPresent) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
                        val progress = (state.steps.toFloat() / state.goal.toFloat()).coerceIn(0f, 1f)
                        val animatedProgress by animateFloatAsState(targetValue = progress, label = "Step Progress")
                        
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 20.dp,
                            strokeCap = StrokeCap.Round,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.DirectionsRun,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = state.steps.toString(),
                                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "of ${state.goal} steps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Text(
                        text = if (state.steps >= state.goal) "Daily Goal Reached! 🎉" else "Keep moving!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.steps >= state.goal) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )

                    if (notificationPermissionState?.status?.isGranted == false) {
                        Spacer(modifier = Modifier.height(32.dp))
                        TextButton(onClick = { notificationPermissionState.launchPermissionRequest() }) {
                            Text("Enable goal tracking notifications")
                        }
                    }
                } else {
                    Icon(Icons.AutoMirrored.Rounded.DirectionsRun, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Step counter sensor not detected on this device.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Text("We need permission to track your physical activity.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { activityPermissionState?.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
