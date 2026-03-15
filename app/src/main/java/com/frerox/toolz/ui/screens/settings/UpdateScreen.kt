package com.frerox.toolz.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.components.SquigglySlider
import com.frerox.toolz.ui.screens.utils.InstallHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    currentVersionName: String,
    currentVersionCode: Long
) {
    var updateManifest by remember { mutableStateOf<com.frerox.toolz.data.update.UpdateManifest?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var downloadId by remember { mutableLongStateOf(-1L) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        TopAppBar(
            title = { Text("Update Center") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
            }

            Text(
                text = "Toolz v$currentVersionName",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Build $currentVersionCode",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedContent(
                targetState = isDownloading,
                transitionSpec = {
                    fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                },
                label = "update_state"
            ) { downloading ->
                if (downloading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Downloading Update...",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SquigglySlider(
                            value = downloadProgress,
                            onValueChange = {},
                            valueRange = 0f..100f,
                            activeColor = MaterialTheme.colorScheme.primary,
                            isPlaying = true
                        )
                        Text("${downloadProgress.toInt()}%")
                    }
                } else {
                    Button(
                        onClick = {
                            isChecking = true
                            // Simulation for now
                            scope.launch {
                                delay(2000)
                                isChecking = false
                                // In a real app, you'd fetch from GitHub here
                                updateManifest = com.frerox.toolz.data.update.UpdateManifest(
                                    versionCode = (currentVersionCode + 1).toInt(),
                                    versionName = "1.7.7",
                                    apkUrl = "https://example.com/toolz.apk",
                                    changelog = "### What's New\n- New Update System\n- Improved App Icon\n- Bug fixes",
                                    isCritical = false,
                                    sha256 = ""
                                )
                            }
                        },
                        enabled = !isChecking,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Check for Updates")
                        }
                    }
                }
            }
        }
    }

    if (updateManifest != null) {
        ModalBottomSheet(
            onDismissRequest = { updateManifest = null },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    "New Version Available",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "v${updateManifest?.versionName}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Changelog", fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .padding(vertical = 8.dp)
                ) {
                    item {
                        Text(updateManifest?.changelog ?: "")
                    }
                }

                Button(
                    onClick = {
                        val manifest = updateManifest ?: return@Button
                        isDownloading = true
                        updateManifest = null
                        startDownload(context, manifest.apkUrl) { progress ->
                            downloadProgress = progress
                            if (progress >= 100f) {
                                isDownloading = false
                                // Trigger install
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Update & Restart")
                }
            }
        }
    }
}

private fun startDownload(context: Context, url: String, onProgress: (Float) -> Unit) {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("Toolz Update")
        .setDescription("Downloading latest version...")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "update.apk")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    val id = downloadManager.enqueue(request)
    // Progress tracking would be done via a Query in a real implementation
}
