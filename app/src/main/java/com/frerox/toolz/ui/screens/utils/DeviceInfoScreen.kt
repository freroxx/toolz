package com.frerox.toolz.ui.screens.utils

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit,
    viewModel: DeviceInfoViewModel = hiltViewModel()
) {
    val deviceData by viewModel.deviceData.collectAsState()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { @Suppress("DEPRECATION") Text("DEVICE INTELLIGENCE", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        vibrationManager?.vibrateClick()
                        viewModel.refresh() 
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Hero Section
                DeviceHeroSection(deviceData)

                // System Core
                InfoSection(
                    title = "CORE ARCHITECTURE",
                    icon = Icons.Rounded.Memory,
                    items = listOf(
                        "SoC" to deviceData.soc,
                        "Processor" to deviceData.hardware,
                        "CPU Cores" to "${deviceData.cpuCores} Cores",
                        "Max Freq" to deviceData.cpuFreq,
                        "Hardware" to deviceData.hardware,
                        "Device" to deviceData.device
                    )
                )

                // Memory & Storage
                InfoSection(
                    title = "MEMORY & STORAGE",
                    icon = Icons.Rounded.Storage,
                    items = listOf(
                        "Total RAM" to formatSize(deviceData.totalRam),
                        "Available RAM" to formatSize(deviceData.availRam),
                        "Internal Storage" to formatSize(deviceData.totalInternal),
                        "Available Storage" to formatSize(deviceData.availInternal)
                    )
                )

                // Display
                InfoSection(
                    title = "DISPLAY MATRIX",
                    icon = Icons.Rounded.Screenshot,
                    items = listOf(
                        "Resolution" to deviceData.screenRes,
                        "Density" to deviceData.screenDensity,
                        "Refresh Rate" to "${deviceData.refreshRate} Hz",
                        "Screen Size" to "${String.format(Locale.getDefault(), "%.1f", deviceData.screenSize)}\""
                    )
                )

                // Battery
                InfoSection(
                    title = "BATTERY STATUS",
                    icon = Icons.Rounded.BatteryChargingFull,
                    items = listOf(
                        "Level" to "${deviceData.batteryLevel}%",
                        "Capacity" to deviceData.batteryCapacity,
                        "Health" to deviceData.batteryHealth,
                        "Technology" to deviceData.batteryTech,
                        "Voltage" to deviceData.batteryVoltage,
                        "Temperature" to deviceData.batteryTemp
                    )
                )

                // Software
                InfoSection(
                    title = "SOFTWARE ECOSYSTEM",
                    icon = Icons.Rounded.Android,
                    items = listOf(
                        "Version" to deviceData.androidVersion,
                        "API Level" to deviceData.apiLevel.toString(),
                        "Security Patch" to deviceData.securityPatch,
                        "Build ID" to deviceData.buildId,
                        "Kernel" to deviceData.kernelVersion,
                        "Root Access" to if (deviceData.isRooted) "Detected" else "No"
                    )
                )

                // Others
                InfoSection(
                    title = "EXTRAS & NETWORK",
                    icon = Icons.Rounded.Hub,
                    items = listOf(
                        "Cameras" to if (deviceData.cameras.isNotEmpty()) deviceData.cameras.joinToString(", ") else "None",
                        "Total Sensors" to "${deviceData.sensorsCount} Sensors",
                        "IP Address" to deviceData.wifiIp
                    )
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun DeviceHeroSection(data: DetailedDeviceData) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(48.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = data.model.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = data.brand,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusBadge(text = "API ${data.apiLevel}", color = MaterialTheme.colorScheme.secondary)
                StatusBadge(text = data.androidVersion, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        @Suppress("DEPRECATION")
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}

@Composable
fun InfoSection(title: String, icon: ImageVector, items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            @Suppress("DEPRECATION")
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f).padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroup.toDouble()), units[digitGroup])
}
