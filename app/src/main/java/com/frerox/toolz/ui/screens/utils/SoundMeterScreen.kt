package com.frerox.toolz.ui.screens.utils

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SoundMeterScreen(
    viewModel: SoundMeterViewModel,
    onBack: () -> Unit
) {
    val db by viewModel.decibels.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    
    val animatedDb by animateFloatAsState(targetValue = db)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sound Meter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (permissionState.status.isGranted) {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", animatedDb),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "dB",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Spacer(modifier = Modifier.height(64.dp))
                
                // Visualization
                DecibelGauge(db = animatedDb)
                
                Spacer(modifier = Modifier.height(64.dp))
                
                Button(
                    onClick = {
                        if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
                    },
                    modifier = Modifier.size(100.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
            } else {
                Text("Microphone permission is required.")
                Button(onClick = { permissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun DecibelGauge(db: Float) {
    val progress = (db / 100f).coerceIn(0f, 1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
        val width = size.width
        val height = size.height
        
        // Background
        drawLine(
            color = Color.Gray.copy(alpha = 0.2f),
            start = Offset(0f, height / 2),
            end = Offset(width, height / 2),
            strokeWidth = height,
            cap = StrokeCap.Round
        )
        
        // Progress
        drawLine(
            color = if (db > 80) Color.Red else Color.Green,
            start = Offset(0f, height / 2),
            end = Offset(width * progress, height / 2),
            strokeWidth = height,
            cap = StrokeCap.Round
        )
    }
}
