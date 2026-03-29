package com.frerox.toolz.ui.screens.settings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.update.UpdateHelper
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    currentVersionName: String,
    currentVersionCode: Long,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val preferredAbi by viewModel.preferredAbi.collectAsState()
    var showAbiSettings by remember { mutableStateOf(false) }
    val performanceMode = LocalPerformanceMode.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { @Suppress("DEPRECATION") Text("APP GITHUB UPDATER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
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
                        onClick = { showAbiSettings = true },
                        modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "ABI Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().toolzBackground().padding(top = padding.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(48.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = Color.White
                    )
                }

                Text(
                    text = "Toolz v$currentVersionName",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    @Suppress("DEPRECATION")
                    Text(
                        text = "BUILD $currentVersionCode",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
                    },
                    label = "update_state"
                ) { state ->
                    when (state) {
                        is UpdateUiState.Downloading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                @Suppress("DEPRECATION")
                                Text(
                                    "INTEGRATING PATCH...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${state.progress.toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        is UpdateUiState.Checking -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                                Spacer(Modifier.height(20.dp))
                                @Suppress("DEPRECATION")
                                Text(
                                    "SYNCHRONIZING REPOSITORY...",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        is UpdateUiState.Error -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    state.message, 
                                    color = MaterialTheme.colorScheme.error, 
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { viewModel.checkForUpdates() },
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text("RETRY CONNECTION", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        else -> {
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 40.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("CHECK FOR ENGINE UPDATES", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAbiSettings) {
        AlertDialog(
            onDismissRequest = { showAbiSettings = false },
            title = { Text("Select Preferred ABI", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    listOf("AUTO", "armeabi-v7a", "arm64-v8a", "x86", "x86_64").forEach { abi ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setPreferredAbi(abi)
                                    showAbiSettings = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = preferredAbi == abi,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (abi == "AUTO") "Auto-detect (${viewModel.getDeviceAbi()})" else abi,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbiSettings = false }) {
                    Text("CLOSE")
                }
            }
        )
    }

    if (uiState is UpdateUiState.Success || uiState is UpdateUiState.ManifestSuccess) {
        val (latestVersion, changelog, apkUrl) = when (val state = uiState) {
            is UpdateUiState.Success -> {
                val bestAsset = UpdateHelper.getBestAsset(state.release.assets, preferredAbi)
                Triple(
                    state.release.tagName.removePrefix("v"), 
                    state.release.body ?: "No changelog.", 
                    bestAsset?.downloadUrl ?: ""
                )
            }
            is UpdateUiState.ManifestSuccess -> {
                val bestRelease = state.manifest.releases?.let { UpdateHelper.getBestRelease(it, preferredAbi) }
                Triple(
                    state.manifest.versionName, 
                    state.manifest.changelog ?: "No changelog.", 
                    bestRelease?.downloadUrl ?: ""
                )
            }
            else -> Triple("", "", "")
        }
        
        val isNewer = isNewerVersion(currentVersionName, latestVersion)

        ModalBottomSheet(
            onDismissRequest = { viewModel.resetState() },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                @Suppress("DEPRECATION")
                Text(
                    text = if (isNewer) "PATCH AVAILABLE" else "SYSTEM UP TO DATE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "v$latestVersion",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    if (isNewer) {
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            @Suppress("DEPRECATION")
                            Text(
                                "NEW",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                if (isNewer) {
                    @Suppress("DEPRECATION")
                    Text("CHANGELOG", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Surface(
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().heightIn(max = 200.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
                    ) {
                        LazyColumn(modifier = Modifier.padding(16.dp).then(if (performanceMode) Modifier else Modifier.fadingEdges(top = 12.dp, bottom = 12.dp))) {
                            item {
                                Text(changelog, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (apkUrl.isNotEmpty()) {
                                viewModel.startDownload(apkUrl)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(24.dp),
                        enabled = apkUrl.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("DEPLOY PATCH APK", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    if (apkUrl.isEmpty()) {
                        val targetAbi = if (preferredAbi == "AUTO") viewModel.getDeviceAbi() else preferredAbi
                        Text(
                            "No APK asset identified for $targetAbi architecture in this release.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                } else {
                    Text(
                        "You're running the latest high-fidelity build. No infrastructure updates required at this time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.resetState() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("DISMISS", fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun isNewerVersion(current: String, latest: String): Boolean {
    val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
    val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
    
    for (i in 0 until minOf(currentParts.size, latestParts.size)) {
        if (latestParts[i] > currentParts[i]) return true
        if (latestParts[i] < currentParts[i]) return false
    }
    return latestParts.size > currentParts.size
}
