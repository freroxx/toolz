package com.frerox.toolz.ui.screens.cleaner

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.data.cleaner.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CleanerScreen(
    onBack: () -> Unit,
    onNavigateToPdf: (Uri, String) -> Unit = { _, _ -> },
    onNavigateToMusic: (Uri) -> Unit = {},
    viewModel: CleanerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val performanceMode = LocalPerformanceMode.current

    val scanState by viewModel.scanState.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val showPermDialog by viewModel.showPermissionDialog.collectAsState()
    val gridCategory by viewModel.gridCategory.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkPermission()
        viewModel.dismissPermissionDialog()
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermission()
    }

    BackHandler(enabled = gridCategory != null) {
        viewModel.closeGridView()
    }

    if (showPermDialog) {
        PermissionEducationDialog(
            onGrantClick = {
                vibrationManager?.vibrateClick()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    permissionLauncher.launch(intent)
                }
            },
            onDismiss = { viewModel.dismissPermissionDialog() }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (gridCategory != null) gridCategory!!.name.uppercase() else "FILE CLEANER",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        vibrationManager?.vibrateClick()
                        if (gridCategory != null) {
                            viewModel.closeGridView()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    if (scanState is ScanState.Scanning) {
                        IconButton(onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.cancelScan()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = scanState,
                contentKey = { it::class },
                transitionSpec = {
                    fadeIn(tween(400)) togetherWith fadeOut(tween(400))
                },
                label = "state_transition"
            ) { state ->
                when (state) {
                    is ScanState.Idle -> {
                        IdleDashboard(
                            storageInfo = storageInfo,
                            hasPermission = hasPermission,
                            onScanClick = {
                                vibrationManager?.vibrateClick()
                                if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    viewModel.showPermissionDialog()
                                } else {
                                    viewModel.startScan()
                                }
                            }
                        )
                    }

                    is ScanState.Scanning -> {
                        ScanningView(state = state)
                    }

                    is ScanState.Results -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ResultsView(
                                state = state,
                                onToggleItem = { catId, itemId ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.toggleCategoryItem(catId, itemId)
                                },
                                onToggleDuplicate = { catId, hash, path ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.toggleDuplicateFile(catId, hash, path)
                                },
                                onClean = {
                                    vibrationManager?.vibrateLongClick()
                                    viewModel.deleteSelected()
                                },
                                onRescan = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.startScan()
                                },
                                onOpenFile = { path, isApp ->
                                    vibrationManager?.vibrateClick()
                                    if (isApp) {
                                        openAppSettings(context, path)
                                    } else {
                                        openFile(context, path, onNavigateToPdf, onNavigateToMusic)
                                    }
                                },
                                onLongPressCategory = { category ->
                                    vibrationManager?.vibrateLongClick()
                                    viewModel.openGridView(category)
                                }
                            )

                            AnimatedVisibility(
                                visible = gridCategory != null,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                gridCategory?.let { category ->
                                    SectionGridView(
                                        category = category,
                                        onToggleItem = { itemId ->
                                            vibrationManager?.vibrateClick()
                                            viewModel.toggleCategoryItem(category.id, itemId)
                                        },
                                        onToggleDuplicate = { hash, path ->
                                            vibrationManager?.vibrateClick()
                                            viewModel.toggleDuplicateFile(category.id, hash, path)
                                        },
                                        onOpenFile = { path ->
                                            vibrationManager?.vibrateClick()
                                            if (category.id == "unused_apps") {
                                                openAppSettings(context, path)
                                            } else {
                                                openFile(context, path, onNavigateToPdf, onNavigateToMusic)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is ScanState.Cleaning -> {
                        CleaningView(state = state)
                    }

                    is ScanState.Done -> {
                        DoneView(
                            result = state.result,
                            onDone = {
                                vibrationManager?.vibrateClick()
                                viewModel.resetState()
                            }
                        )
                    }

                    is ScanState.Error -> {
                        ErrorView(
                            message = state.message,
                            onRetry = { viewModel.startScan() },
                            onDismiss = { viewModel.resetState() }
                        )
                    }
                }
            }
        }
    }
}

private fun openAppSettings(context: Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun openFile(
    context: Context,
    path: String,
    onNavigateToPdf: (Uri, String) -> Unit,
    onNavigateToMusic: (Uri) -> Unit
) {
    val file = File(path)
    if (!file.exists()) return

    val ext = file.extension.lowercase()
    
    val mediaStoreUri = getMediaStoreUri(context, path, ext)
    
    val uri = mediaStoreUri ?: try {
        FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
    } catch (e: Exception) {
        Uri.fromFile(file)
    }

    when (ext) {
        "pdf" -> onNavigateToPdf(uri, file.name)
        "mp3", "wav", "m4a", "ogg", "flac" -> onNavigateToMusic(uri)
        else -> {
            try {
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open file with"))
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}

private fun getMediaStoreUri(context: Context, path: String, ext: String): Uri? {
    val collection = when (ext) {
        "mp3", "wav", "m4a", "ogg", "flac" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        "pdf" -> MediaStore.Files.getContentUri("external")
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "mp4", "mkv", "avi", "mov", "webm" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else -> return null
    }
    
    val projection = arrayOf(MediaStore.MediaColumns._ID)
    val selection = MediaStore.MediaColumns.DATA + "=?"
    val selectionArgs = arrayOf(path)
    
    return try {
        context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                ContentUris.withAppendedId(collection, id)
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun IdleDashboard(
    storageInfo: StorageInfo,
    hasPermission: Boolean,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StorageArcIndicator(
            storageInfo = storageInfo,
            cleanableBytes = storageInfo.cleanableBytes
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onScanClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Rounded.TravelExplore, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                "DEEP SCAN DEVICE",
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (!hasPermission) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onScanClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.GppMaybe, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Grant permission for deeper scan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningView(state: ScanState.Scanning) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveScanningIndicator()

        Spacer(Modifier.height(48.dp))

        Text(
            state.currentCategory.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScanningStatItem("FILES SCANNED", state.filesScanned.toLong())
            ScanningStatItem("JUNK FOUND", state.foundSize)
        }
    }
}

@Composable
private fun ScanningStatItem(label: String, value: Long) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val animatedValue by animateIntAsState(
            targetValue = value.toInt(),
            animationSpec = tween(500),
            label = "stat_value"
        )
        Text(
            if (label.contains("JUNK")) Formatter.formatFileSize(context, value) else "$animatedValue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ResultsView(
    state: ScanState.Results,
    onToggleItem: (String, String) -> Unit,
    onToggleDuplicate: (String, String, String) -> Unit,
    onClean: () -> Unit,
    onRescan: () -> Unit,
    onOpenFile: (String, Boolean) -> Unit,
    onLongPressCategory: (CleanCategory) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ResultsHeader(
                    totalSize = state.totalCleanableBytes,
                    filesScanned = state.filesScanned,
                    onRescan = onRescan
                )
            }

            items(state.categories, key = { it.id }) { category ->
                CategoryCard(
                    category = category,
                    onToggleItem = { onToggleItem(category.id, it) },
                    onToggleDuplicate = { hash, path -> onToggleDuplicate(category.id, hash, path) },
                    onOpenFile = { path -> onOpenFile(path, category.id == "unused_apps") },
                    onLongPress = { onLongPressCategory(category) }
                )
            }
        }

        if (state.totalCleanableBytes > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                SlideToCleanButton(
                    cleanableBytes = state.totalCleanableBytes,
                    onClean = onClean
                )
            }
        }
    }
}

@Composable
private fun ResultsHeader(
    totalSize: Long,
    filesScanned: Int,
    onRescan: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "TOTAL OPTIMIZABLE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    Formatter.formatFileSize(context, totalSize),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Identified in $filesScanned items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            FilledIconButton(
                onClick = onRescan,
                modifier = Modifier.size(48.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan")
            }
        }
    }
}

@Composable
private fun CleaningView(state: ScanState.Cleaning) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CleaningProgressIndicator(progress = state.progress)

        Spacer(Modifier.height(48.dp))

        Text(
            "PURGING FILES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(12.dp))

        Text(
            state.currentFile.substringAfterLast('/'),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DoneView(
    result: CleanResult,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "DEVICE OPTIMIZED",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
        Text(
            Formatter.formatFileSize(context, result.freedBytes),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Successfully cleared ${result.deletedCount} items",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )

        if (result.failedCount > 0) {
            Text(
                "${result.failedCount} items skipped due to access restrictions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("RETURN TO DASHBOARD", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
        Text("Scan Interrupted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onRetry, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("TRY AGAIN", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) { 
            Text("GO BACK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
