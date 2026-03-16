package com.frerox.toolz.ui.screens.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val deviceData = remember { getDeviceInfo(context) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("DEVICE INTELLIGENCE", fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = MaterialTheme.typography.labelMedium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hero Section
            DeviceHeroSection(deviceData)

            // Dynamic Sections
            InfoSection(title = "CORE ARCHITECTURE", icon = Icons.Rounded.Memory, items = deviceData.hardwareInfo)
            InfoSection(title = "SOFTWARE ECOSYSTEM", icon = Icons.Rounded.Android, items = deviceData.softwareInfo)
            InfoSection(title = "DISPLAY MATRIX", icon = Icons.Rounded.Screenshot, items = deviceData.displayInfo)
            InfoSection(title = "MEMORY & STORAGE", icon = Icons.Rounded.Storage, items = deviceData.storageInfo)

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun DeviceHeroSection(data: DeviceData) {
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
                text = data.modelName.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = data.brandName,
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

data class DeviceData(
    val brandName: String,
    val modelName: String,
    val androidVersion: String,
    val apiLevel: Int,
    val hardwareInfo: List<Pair<String, String>>,
    val softwareInfo: List<Pair<String, String>>,
    val displayInfo: List<Pair<String, String>>,
    val storageInfo: List<Pair<String, String>>
)

private fun getDeviceInfo(context: Context): DeviceData {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()

    // API 31+ handling
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        wm.defaultDisplay
    }
    @Suppress("DEPRECATION")
    display?.getMetrics(metrics)

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    val internalStorage = StatFs(Environment.getDataDirectory().path)
    val totalInternal = internalStorage.blockCountLong * internalStorage.blockSizeLong
    val availableInternal = internalStorage.availableBlocksLong * internalStorage.blockSizeLong

    return DeviceData(
        brandName = Build.BRAND.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
        modelName = Build.MODEL,
        androidVersion = "Android ${Build.VERSION.RELEASE}",
        apiLevel = Build.VERSION.SDK_INT,
        hardwareInfo = listOf(
            "Processor" to Build.HARDWARE,
            "Board" to Build.BOARD,
            "Hardware" to Build.HARDWARE,
            "Manufacturer" to Build.MANUFACTURER,
            "Product" to Build.PRODUCT,
            "Bootloader" to Build.BOOTLOADER,
            "Supported ABIs" to Build.SUPPORTED_ABIS.joinToString(", ")
        ),
        softwareInfo = listOf(
            "OS Version" to Build.VERSION.RELEASE,
            "API Level" to Build.VERSION.SDK_INT.toString(),
            "Build ID" to Build.ID,
            "Security Patch" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A"),
            "Kernel" to (System.getProperty("os.version")?.toString() ?: "N/A"),
            "Language" to Locale.getDefault().displayName
        ),
        displayInfo = listOf(
            "Resolution" to "${metrics.widthPixels} x ${metrics.heightPixels}",
            "Density" to "${metrics.densityDpi} DPI",
            "Refresh Rate" to (display?.mode?.refreshRate?.toInt()?.let { "$it Hz" } ?: "N/A"),
            "Screen Size" to "${String.format(Locale.getDefault(), "%.1f", calculateScreenSize(metrics))}\""
        ),
        storageInfo = listOf(
            "Total RAM" to formatSize(memoryInfo.totalMem),
            "Available RAM" to formatSize(memoryInfo.availMem),
            "Internal Storage" to formatSize(totalInternal),
            "Available Storage" to formatSize(availableInternal)
        )
    )
}

private fun calculateScreenSize(metrics: DisplayMetrics): Double {
    val x = (metrics.widthPixels.toDouble() / metrics.xdpi).let { it * it }
    val y = (metrics.heightPixels.toDouble() / metrics.ydpi).let { it * it }
    return Math.sqrt(x + y)
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroup.toDouble()), units[digitGroup])
}
