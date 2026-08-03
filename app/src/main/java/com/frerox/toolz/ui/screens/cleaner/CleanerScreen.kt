/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

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
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.frerox.toolz.R
import com.frerox.toolz.data.cleaner.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.ui.theme.toolzBackground
import java.io.File
import kotlin.math.roundToInt

// ─── Color constants ──────────────────────────────────────────────────────────
private val kSuccess = Color(0xFF4CAF50)
private val kSuccessDim = Color(0xFF2E7D32)

// ─── Root Screen ─────────────────────────────────────────────────────────────

@Composable
fun CleanerScreen(
    onBack: () -> Unit,
    onNavigateToPdf: (Uri, String) -> Unit = { _, _ -> },
    onNavigateToMusic: (Uri) -> Unit = {},
    viewModel: CleanerViewModel = hiltViewModel(),
) {
    val context          = LocalContext.current
    val vibrationManager = LocalVibrationManager.current

    val scanState    by viewModel.scanState.collectAsState()
    val storageInfo  by viewModel.storageInfo.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val showPermDialog by viewModel.showPermissionDialog.collectAsState()
    val gridCategory by viewModel.gridCategory.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkPermission()
        viewModel.dismissPermissionDialog()
    }

    LaunchedEffect(Unit) { viewModel.checkPermission() }

    BackHandler(enabled = gridCategory != null) {
        viewModel.closeGridView()
    }

    // ── Permission dialog ─────────────────────────────────────────────────
    if (showPermDialog) {
        PermissionEducationDialog(
            onGrantClick = {
                vibrationManager?.vibrateClick()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    permissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            },
            onDismiss = { viewModel.dismissPermissionDialog() },
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(
        snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .toolzBackground()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            // Dynamic title based on state
            val title = when {
                gridCategory != null -> gridCategory!!.name
                else -> stringResource(R.string.st_CleanerScreen_9e2c)
            }
            val subtitle = when (val s = scanState) {
                is ScanState.Scanning -> "Scanning ${s.filesScanned} files…"
                is ScanState.Results  -> "${s.filesScanned} files analysed"
                is ScanState.Cleaning -> "Cleaning in progress…"
                is ScanState.Done     -> "Optimisation complete"
                else                  -> stringResource(R.string.st_CleanerScreen_1a2b)
            }

            ExpressiveTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = {
                    IconButton(onClick = {
                        vibrationManager?.vibrateClick()
                        if (gridCategory != null) viewModel.closeGridView() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_CleanerScreen_8f1a))
                    }
                },
                actions = {
                    AnimatedVisibility(visible = scanState is ScanState.Scanning) {
                        IconButton(onClick = {
                            vibrationManager?.vibrateClick()
                            viewModel.cancelScan()
                        }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.st_CleanerScreen_3d5b),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState  = scanState,
                contentKey   = { it::class },
                transitionSpec = {
                    (fadeIn(tween(420, easing = EaseOutCubic)) +
                            scaleIn(tween(420, easing = EaseOutCubic), initialScale = 0.93f)) togetherWith
                            (fadeOut(tween(260)) + scaleOut(targetScale = 1.04f))
                },
                label = "cleaner_state",
            ) { state ->
                when (state) {
                    is ScanState.Idle     -> IdleDashboard(
                        storageInfo   = storageInfo,
                        hasPermission = hasPermission,
                        onScanClick   = {
                            vibrationManager?.vibrateClick()
                            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                viewModel.showPermissionDialog()
                            } else {
                                viewModel.startScan()
                            }
                        },
                    )

                    is ScanState.Scanning -> ScanningView(state)

                    is ScanState.Results  -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ResultsView(
                                state              = state,
                                onToggleItem       = { catId, itemId ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.toggleCategoryItem(catId, itemId)
                                },
                                onToggleDuplicate  = { catId, hash, path ->
                                    vibrationManager?.vibrateClick()
                                    viewModel.toggleDuplicateFile(catId, hash, path)
                                },
                                onClean            = {
                                    vibrationManager?.vibrateLongClick()
                                    viewModel.deleteSelected()
                                },
                                onRescan           = {
                                    vibrationManager?.vibrateClick()
                                    viewModel.startScan()
                                },
                                onOpenFile         = { path, isApp ->
                                    vibrationManager?.vibrateClick()
                                    if (isApp) openAppSettings(context, path)
                                    else openFile(context, path, onNavigateToPdf, onNavigateToMusic)
                                },
                                onLongPressCategory = { category ->
                                    vibrationManager?.vibrateLongClick()
                                    viewModel.openGridView(category)
                                },
                            )
                            // Grid overlay
                            AnimatedVisibility(
                                visible = gridCategory != null,
                                enter   = slideInVertically(
                                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                                ) { it } + fadeIn(),
                                exit    = slideOutVertically { it } + fadeOut(tween(200)),
                                modifier = Modifier
                            ) {
                                gridCategory?.let { category ->
                                    SectionGridView(
                                        category         = category,
                                        onToggleItem     = { id -> viewModel.toggleCategoryItem(category.id, id) },
                                        onToggleDuplicate= { hash, path -> viewModel.toggleDuplicateFile(category.id, hash, path) },
                                        onOpenFile       = { path ->
                                            if (category.id == "unused_apps") openAppSettings(context, path)
                                            else openFile(context, path, onNavigateToPdf, onNavigateToMusic)
                                        },
                                        onClose          = { viewModel.closeGridView() },
                                    )
                                }
                            }
                        }
                    }

                    is ScanState.Cleaning -> CleaningView(state)
                    is ScanState.Done     -> DoneView(
                        result = state.result,
                        onDone = {
                            vibrationManager?.vibrateClick()
                            viewModel.resetState()
                        },
                    )
                    is ScanState.Error    -> ErrorView(
                        message   = state.message,
                        onRetry   = { viewModel.startScan() },
                        onDismiss = { viewModel.resetState() },
                    )
                }
            }
        }
    }
}

// ─── Idle Dashboard ───────────────────────────────────────────────────────────

@Composable
private fun IdleDashboard(
    storageInfo  : StorageInfo,
    hasPermission: Boolean,
    onScanClick  : () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.5f))

        // Arc indicator
        StorageArcIndicator(storageInfo = storageInfo, cleanableBytes = storageInfo.cleanableBytes)

        Spacer(Modifier.height(32.dp))

        // Quick stats row
        StorageStatsRow(storageInfo)

        Spacer(Modifier.height(32.dp))

        // Permission nudge
        AnimatedVisibility(
            visible = !hasPermission,
            enter   = expandVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
        ) {
            Surface(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape  = MediumExpressiveShape,
                color  = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.GppMaybe, null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.st_CleanerScreen_7c4d),
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color      = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            stringResource(R.string.st_CleanerScreen_5f6e),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Deep scan button
        ToolzExpressiveButton(
            onClick = onScanClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(22.dp),
        ) {
            Icon(Icons.Rounded.TravelExplore, null, Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.st_CleanerScreen_2b8a),
                fontWeight = FontWeight.Black,
                style      = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StorageStatsRow(storageInfo: StorageInfo) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StorageStatCard(
            modifier = Modifier.weight(1f),
            label    = stringResource(R.string.st_CleanerScreen_4d9c),
            value    = Formatter.formatFileSize(context, storageInfo.usedBytes),
            icon     = Icons.Rounded.Storage,
            accent   = MaterialTheme.colorScheme.primary,
        )
        StorageStatCard(
            modifier = Modifier.weight(1f),
            label    = stringResource(R.string.st_CleanerScreen_6a1b),
            value    = Formatter.formatFileSize(context, storageInfo.freeBytes),
            icon     = Icons.Rounded.FolderOpen,
            accent   = kSuccess,
        )
        if (storageInfo.cleanableBytes > 0) {
            StorageStatCard(
                modifier = Modifier.weight(1f),
                label    = stringResource(R.string.st_CleanerScreen_1b2c),
                value    = Formatter.formatFileSize(context, storageInfo.cleanableBytes),
                icon     = Icons.Rounded.AutoDelete,
                accent   = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun StorageStatCard(
    modifier: Modifier,
    label   : String,
    value   : String,
    icon    : ImageVector,
    accent  : Color,
) {
    Surface(
        modifier = modifier,
        shape    = MediumExpressiveShape,
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Storage Arc Indicator ────────────────────────────────────────────────────

@Composable
private fun StorageArcIndicator(
    storageInfo   : StorageInfo,
    cleanableBytes: Long,
    modifier      : Modifier = Modifier,
) {
    val context         = LocalContext.current
    val performanceMode = LocalPerformanceMode.current
    val primary  = MaterialTheme.colorScheme.primary
    val error    = MaterialTheme.colorScheme.error
    val outline  = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)

    val total   = storageInfo.totalBytes.coerceAtLeast(1L).toFloat()
    val usedF   = (storageInfo.usedBytes.toFloat() / total).coerceIn(0f, 1f)
    val cleanF  = (cleanableBytes.toFloat() / total).coerceIn(0f, 1f)
    val baseF   = (usedF - cleanF).coerceAtLeast(0f)

    val animBase by animateFloatAsState(
        targetValue   = baseF,
        animationSpec = if (performanceMode) tween(300) else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        label         = "arcBase",
    )
    val animClean by animateFloatAsState(
        targetValue   = cleanF,
        animationSpec = if (performanceMode) tween(300) else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        label         = "arcClean",
    )

    Box(modifier = modifier.size(300.dp), contentAlignment = Alignment.Center) {
        // Pulse glow
        if (!performanceMode) {
            val infiniteTransition = rememberInfiniteTransition(label = "glow")
            val glowScale by infiniteTransition.animateFloat(
                0.94f, 1.06f,
                infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                label = "glow",
            )
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                    .background(Brush.radialGradient(listOf(primary.copy(alpha = 0.12f), Color.Transparent)), CircleShape)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            val sw     = 40f
            val arcSz  = Size(size.width - sw, size.height - sw)
            val tl     = Offset(sw / 2f, sw / 2f)
            val start  = 135f
            val sweep  = 270f

            // Track
            drawArc(outline, start, sweep, false, tl, arcSz, style = Stroke(sw, cap = StrokeCap.Round))
            // Used (non-cleanable)
            if (animBase > 0f)
                drawArc(
                    Brush.sweepGradient(listOf(primary.copy(alpha = 0.7f), primary)),
                    start, sweep * animBase, false, tl, arcSz,
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            // Cleanable
            if (animClean > 0f)
                drawArc(
                    Brush.sweepGradient(listOf(error.copy(alpha = 0.7f), error)),
                    start + sweep * animBase, sweep * animClean, false, tl, arcSz,
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Formatter.formatFileSize(context, storageInfo.usedBytes),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.5).sp,
                ),
            )
            Text(
                "USED OF ${Formatter.formatFileSize(context, storageInfo.totalBytes)}",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            AnimatedVisibility(
                visible = cleanableBytes > 0,
                enter   = expandVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn() + scaleIn(),
            ) {
                Surface(
                    color    = kSuccess.copy(alpha = 0.14f),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, kSuccess.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.AutoDelete, null, Modifier.size(14.dp), tint = kSuccess)
                        Text(
                            "${Formatter.formatFileSize(context, cleanableBytes)} " + stringResource(R.string.st_CleanerScreen_3c4d),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color      = kSuccess,
                        )
                    }
                }
            }
        }
    }
}

// ─── Scanning View ────────────────────────────────────────────────────────────

@Composable
private fun ScanningView(state: ScanState.Scanning) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressiveScanningIndicator()

        Spacer(Modifier.height(40.dp))

        Text(
            state.currentCategory,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color      = MaterialTheme.colorScheme.primary,
            textAlign  = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // Animated progress bar
        val animProg by animateFloatAsState(
            targetValue   = state.progress,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            label         = "scanProgress",
        )
        LinearProgressIndicator(
            progress = { animProg },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
                .clip(CircleShape),
            color      = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap  = StrokeCap.Round,
        )

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScanStatPill(
                icon  = Icons.Rounded.FindInPage,
                label = stringResource(R.string.st_CleanerScreen_5d6e),
                value = "${state.filesScanned}",
            )
            ScanStatPill(
                icon  = Icons.Rounded.DeleteSweep,
                label = stringResource(R.string.st_CleanerScreen_7e8f),
                value = Formatter.formatFileSize(LocalContext.current, state.foundSize),
            )
        }
    }
}

@Composable
private fun ScanStatPill(icon: ImageVector, label: String, value: String) {
    Surface(
        shape = LargeExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            val animatedInt by animateIntAsState(
                targetValue   = value.filter { it.isDigit() }.take(8).toIntOrNull() ?: 0,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label         = "statAnim",
            )
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Expressive Scanning Indicator ───────────────────────────────────────────

@Composable
private fun ExpressiveScanningIndicator(modifier: Modifier = Modifier) {
    val primary         = MaterialTheme.colorScheme.primary
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "scan_anim")

    val rotation by infiniteTransition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "rotation",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(260.dp)) {
        if (!performanceMode) {
            val pulse by infiniteTransition.animateFloat(
                0.9f, 1.1f,
                infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "pulse",
            )
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulse)
                    .background(primary.copy(alpha = 0.09f), CircleShape)
                    .border(1.dp, primary.copy(alpha = 0.18f), CircleShape),
            )
        }

        Canvas(modifier = Modifier.size(210.dp)) {
            val sw = 6.dp.toPx()
            drawCircle(primary.copy(alpha = 0.05f), size.minDimension / 2f, style = Stroke(sw))
            rotate(rotation) {
                drawArc(
                    Brush.sweepGradient(
                        0f   to primary.copy(alpha = 0f),
                        0.5f to primary,
                        1f   to primary.copy(alpha = 0f),
                    ),
                    0f, 180f, false, style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }

        Surface(
            modifier       = Modifier.size(86.dp),
            shape          = ExtraLargeExpressiveShape,
            color          = MaterialTheme.colorScheme.primary,
            shadowElevation = 16.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                val iconScale by infiniteTransition.animateFloat(
                    0.8f, 1.1f,
                    infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "iconS",
                )
                Icon(
                    Icons.Rounded.TravelExplore,
                    null,
                    Modifier.size(40.dp).scale(iconScale),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// ─── Results View ─────────────────────────────────────────────────────────────

@Composable
private fun ResultsView(
    state              : ScanState.Results,
    onToggleItem       : (String, String) -> Unit,
    onToggleDuplicate  : (String, String, String) -> Unit,
    onClean            : () -> Unit,
    onRescan           : () -> Unit,
    onOpenFile         : (String, Boolean) -> Unit,
    onLongPressCategory: (CleanCategory) -> Unit,
) {
    val performanceMode = LocalPerformanceMode.current
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state           = listState,
            contentPadding  = PaddingValues(16.dp, 8.dp, 16.dp, 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().run {
                if (!performanceMode) fadingEdges(top = 16.dp, bottom = 24.dp) else this
            },
        ) {
            item { ResultsHeader(state.totalCleanableBytes, state.filesScanned, onRescan) }

            // Category breakdown chips
            item {
                CategoryBreakdownRow(state.categories)
            }

            items(state.categories, key = { it.id }) { category ->
                StaggeredEntrance(index = state.categories.indexOf(category).coerceAtMost(8)) {
                    CategoryCard(
                        category         = category,
                        onToggleItem     = { itemId -> onToggleItem(category.id, itemId) },
                        onToggleDuplicate= { h, p -> onToggleDuplicate(category.id, h, p) },
                        onOpenFile       = { path -> onOpenFile(path, category.id == "unused_apps") },
                        onLongPress      = { onLongPressCategory(category) },
                    )
                }
            }
        }

        // Slide-to-clean FAB
        AnimatedVisibility(
            visible  = state.totalCleanableBytes > 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { it / 2 } +
                    fadeIn() + scaleIn(initialScale = 0.92f),
            exit  = slideOutVertically { it / 2 } + fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            SlideToCleanButton(
                cleanableBytes = state.totalCleanableBytes,
                selectedBytes  = state.selectedBytes,
                onClean        = onClean,
            )
        }
    }
}

@Composable
private fun CategoryBreakdownRow(categories: List<CleanCategory>) {
    if (categories.isEmpty()) return
    val context = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalFadingEdges(left = 0.dp, right = 16.dp),
    ) {
        items(categories.filter { it.totalSize > 0 }, key = { it.id }) { cat ->
            val icon = iconForCategoryName(cat.icon)
            Surface(
                shape = SmallExpressiveShape,
                color = if (cat.isSafeToClean)
                    kSuccess.copy(alpha = 0.13f)
                else
                    MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(icon, null, Modifier.size(13.dp), tint = if (cat.isSafeToClean) kSuccess else MaterialTheme.colorScheme.primary)
                    Text(
                        Formatter.formatFileSize(context, cat.totalSize),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (cat.isSafeToClean) kSuccess else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultsHeader(totalSize: Long, filesScanned: Int, onRescan: () -> Unit) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = LargeExpressiveShape,
        color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.st_CleanerScreen_9f0a),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color      = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                )
                val animatedBytes by animateFloatAsState(
                    targetValue   = totalSize.toFloat(),
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    label         = "totalBytes",
                )
                Text(
                    Formatter.formatFileSize(context, animatedBytes.toLong()),
                    style      = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Found in $filesScanned scanned items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
            ToolzExpressiveIconButton(
                onClick = onRescan,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(24.dp))
            }
        }
    }
}

// ─── Category Card ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryCard(
    category        : CleanCategory,
    onToggleItem    : (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onOpenFile      : (String) -> Unit,
    onLongPress     : () -> Unit,
) {
    var expanded         by remember { mutableStateOf(false) }
    val context          = LocalContext.current
    val performanceMode  = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current

    val borderColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        else if (category.isSafeToClean && category.totalSize > 0) kSuccess.copy(alpha = 0.2f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label = "catBorder",
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (category.isSafeToClean && category.totalSize > 0)
            kSuccess.copy(alpha = 0.05f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "catSurface",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = SquircleShape,
        color    = surfaceColor,
        border   = BorderStroke(1.dp, borderColor),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick   = {
                            vibrationManager?.vibrateClick()
                            if (category.totalSize > 0) expanded = !expanded
                        },
                        onLongClick = {
                            vibrationManager?.vibrateLongClick()
                            onLongPress()
                        },
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Icon box
                val iconAccent = if (category.totalSize > 0) {
                    if (category.isSafeToClean) kSuccess else MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.onSurfaceVariant

                Surface(
                    modifier = Modifier.size(48.dp),
                    shape    = RoundedCornerShape(16.dp),
                    color    = iconAccent.copy(alpha = 0.14f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            iconForCategoryName(category.icon),
                            null,
                            Modifier.size(24.dp),
                            tint = iconAccent,
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        category.name,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (category.totalSize > 0) Formatter.formatFileSize(context, category.totalSize)
                            else "Optimised ✓",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = if (category.totalSize > 0) iconAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        if (category.isSafeToClean && category.totalSize > 0) {
                            Surface(
                                shape = CircleShape,
                                color = kSuccess.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    stringResource(R.string.st_CleanerScreen_a1b2),
                                    modifier   = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color      = kSuccessDim,
                                )
                            }
                        }
                    }
                }

                // Item count badge
                if (category.items.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        Text(
                            "${category.items.size}",
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style      = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color      = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                // Expand icon
                val expandRotation by animateFloatAsState(
                    targetValue   = if (expanded) 180f else 0f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                    label         = "expandRot",
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = expandRotation },
                )
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded && category.totalSize > 0,
                enter   = expandVertically(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(tween(180)),
                exit    = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
                Column(
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val displayItems = remember(category.items) { category.items.take(8) }

                    displayItems.forEachIndexed { idx, item ->
                        StaggeredEntrance(index = idx) {
                            when (item) {
                                is CleanItem.GenericFile -> GenericFileRow(item.file, onToggleItem, onOpenFile, category.isSafeToClean)
                                is CleanItem.Corpse      -> CorpseRow(item.entry, onToggleItem, onOpenFile, category.isSafeToClean)
                                is CleanItem.Duplicate   -> DuplicateGroupRow(item.group, onToggleDuplicate, onOpenFile)
                                is CleanItem.UnusedApp   -> UnusedAppRow(item.entry, onToggleItem)
                            }
                        }
                    }

                    if (category.items.size > displayItems.size) {
                        TextButton(
                            onClick   = {
                                vibrationManager?.vibrateTick()
                                onLongPress()
                            },
                            modifier  = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "View all ${category.items.size} items",
                                    fontWeight = FontWeight.Bold,
                                    style      = MaterialTheme.typography.labelMedium,
                                )
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Row items inside category ────────────────────────────────────────────────

@Composable
private fun GenericFileRow(
    file      : FileEntry,
    onToggle  : (String) -> Unit,
    onOpenFile: (String) -> Unit,
    isSafe    : Boolean = false,
) {
    val context          = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val accent           = if (isSafe) kSuccess else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MediumExpressiveShape)
            .clickable { vibrationManager?.vibrateClick(); onToggle(file.path) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked         = file.isSelected,
            onCheckedChange = { vibrationManager?.vibrateClick(); onToggle(file.path) },
            colors          = CheckboxDefaults.colors(checkedColor = accent),
        )
        FileThumbnail(file, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formatter.formatFileSize(context, file.sizeBytes)} · ${file.extension.uppercase()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { vibrationManager?.vibrateClick(); onOpenFile(file.path) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun CorpseRow(
    corpse    : CorpseEntry,
    onToggle  : (String) -> Unit,
    onOpenFile: (String) -> Unit,
    isSafe    : Boolean = false,
) {
    val vibrationManager = LocalVibrationManager.current
    val accent           = if (isSafe) kSuccess else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MediumExpressiveShape)
            .clickable { vibrationManager?.vibrateClick(); onToggle(corpse.path) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked         = corpse.isSelected,
            onCheckedChange = { vibrationManager?.vibrateClick(); onToggle(corpse.path) },
            colors          = CheckboxDefaults.colors(checkedColor = accent),
        )
        Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FolderOff, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(corpse.packageName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formatter.formatFileSize(LocalContext.current, corpse.sizeBytes)} · ${corpse.type.name} leftover",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnusedAppRow(entry: UnusedAppEntry, onToggle: (String) -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val context          = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MediumExpressiveShape)
            .clickable { vibrationManager?.vibrateClick(); onToggle(entry.packageName) }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.isSelected, onCheckedChange = { vibrationManager?.vibrateClick(); onToggle(entry.packageName) })
        Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(model = entry.icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.appName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Formatter.formatFileSize(context, entry.sizeBytes)} · Last used ${formatLastUsed(entry.lastUsed)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DuplicateGroupRow(
    group     : DuplicateGroup,
    onToggle  : (String, String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val context          = LocalContext.current
    val first            = group.files.firstOrNull()
    val fileName         = first?.path?.substringAfterLast('/') ?: "Unknown"
    val ext              = fileName.substringAfterLast('.', "").lowercase()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = MediumExpressiveShape,
        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                first?.let {
                    FileThumbnail(
                        FileEntry(fileName, it.path, group.sizeBytes, it.lastModified, ext),
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(fileName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${group.files.size} identical copies · ${Formatter.formatFileSize(context, group.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            group.files.forEachIndexed { index, file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SmallExpressiveShape)
                        .clickable { vibrationManager?.vibrateClick(); onToggle(group.hash, file.path) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked         = file.isSelected,
                        onCheckedChange = { vibrationManager?.vibrateClick(); onToggle(group.hash, file.path) },
                        modifier        = Modifier.size(20.dp),
                        colors          = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.path,
                            style    = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color    = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (index == 0) stringResource(R.string.st_CleanerScreen_c3d4) else stringResource(R.string.st_CleanerScreen_d5e6),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = if (index == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    IconButton(
                        onClick  = { vibrationManager?.vibrateClick(); onOpenFile(file.path) },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(Icons.Rounded.Visibility, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    }
                }
            }
        }
    }
}

// ─── File Thumbnail ───────────────────────────────────────────────────────────

@Composable
private fun FileThumbnail(file: FileEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isVideo = file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm", "flv")
    val icon    = iconForExtension(file.extension)

    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file.thumbnailUri ?: file.path)
                    .crossfade(true)
                    .apply { if (isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier     = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                },
                error = {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    }
                },
            )
            if (isVideo) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ─── Section Grid View ────────────────────────────────────────────────────────

@Composable
private fun SectionGridView(
    category         : CleanCategory,
    onToggleItem     : (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onOpenFile       : (String) -> Unit,
    onClose          : () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Grid header
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.st_CleanerScreen_e5f6))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(
                            "${category.items.size} items · ${Formatter.formatFileSize(LocalContext.current, category.totalSize)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Select-all toggle
                    val allSelected = category.items.all { item ->
                        when (item) {
                            is CleanItem.GenericFile -> item.file.isSelected
                            is CleanItem.Corpse      -> item.entry.isSelected
                            is CleanItem.Duplicate   -> item.group.files.any { it.isSelected }
                            is CleanItem.UnusedApp   -> item.entry.isSelected
                        }
                    }
                    FilledTonalIconButton(onClick = {
                        category.items.forEach { item ->
                            when (item) {
                                is CleanItem.GenericFile -> if (!allSelected) onToggleItem(item.file.path)
                                is CleanItem.Corpse      -> if (!allSelected) onToggleItem(item.entry.path)
                                is CleanItem.UnusedApp   -> if (!allSelected) onToggleItem(item.entry.packageName)
                                is CleanItem.Duplicate   -> item.group.files.drop(1).forEach { f ->
                                    if (!f.isSelected) onToggleDuplicate(item.group.hash, f.path)
                                }
                            }
                        }
                    }) {
                        Icon(if (allSelected) Icons.Rounded.DoneAll else Icons.Rounded.SelectAll, null)
                    }
                }
            }

            LazyVerticalGrid(
                columns             = GridCells.Adaptive(160.dp),
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(category.items, key = { item ->
                    when (item) {
                        is CleanItem.GenericFile -> "file_${item.file.path}"
                        is CleanItem.Corpse      -> "corpse_${item.entry.path}"
                        is CleanItem.Duplicate   -> "dupe_${item.group.hash}"
                        is CleanItem.UnusedApp   -> "app_${item.entry.packageName}"
                    }
                }) { item ->
                    GridItemCard(item, onToggleItem, onToggleDuplicate, onOpenFile, category.isSafeToClean)
                }
            }
        }
    }
}

@Composable
private fun GridItemCard(
    item             : CleanItem,
    onToggleItem     : (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onOpenFile       : (String) -> Unit,
    isSafe           : Boolean,
) {
    val context          = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val accent           = if (isSafe) kSuccess else MaterialTheme.colorScheme.primary

    val isSelected = when (item) {
        is CleanItem.GenericFile -> item.file.isSelected
        is CleanItem.Corpse      -> item.entry.isSelected
        is CleanItem.Duplicate   -> item.group.files.any { it.isSelected }
        is CleanItem.UnusedApp   -> item.entry.isSelected
    }
    val path = when (item) {
        is CleanItem.GenericFile -> item.file.path
        is CleanItem.Corpse      -> item.entry.path
        is CleanItem.Duplicate   -> item.group.files.firstOrNull()?.path ?: ""
        is CleanItem.UnusedApp   -> item.entry.packageName
    }
    val ext = if (item is CleanItem.UnusedApp) "apk" else path.substringAfterLast('.', "").lowercase()

    val selectScale by animateFloatAsState(
        targetValue   = if (isSelected) 0.92f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label         = "gridScale",
    )
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) accent else Color.Transparent,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "gridBorder",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer { scaleX = selectScale; scaleY = selectScale },
        shape  = BouncyShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(if (isSelected) 2.5.dp else 0.dp, borderColor),
        onClick = {
            vibrationManager?.vibrateClick()
            when (item) {
                is CleanItem.GenericFile -> onToggleItem(item.file.path)
                is CleanItem.Corpse      -> onToggleItem(item.entry.path)
                is CleanItem.UnusedApp   -> onToggleItem(item.entry.packageName)
                is CleanItem.Duplicate   -> {
                    val f = item.group.files.find { it.isSelected } ?: item.group.files.lastOrNull()
                    f?.let { onToggleDuplicate(item.group.hash, it.path) }
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item is CleanItem.UnusedApp) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    AsyncImage(item.entry.icon, null, Modifier.size(56.dp))
                }
            } else {
                val fileEntry = when (item) {
                    is CleanItem.GenericFile -> item.file
                    is CleanItem.Duplicate   -> item.group.files.first().let { f ->
                        FileEntry(f.path.substringAfterLast('/'), f.path, item.group.sizeBytes, f.lastModified, ext)
                    }
                    else -> FileEntry("", path, 0, 0, ext)
                }
                FileThumbnail(fileEntry, modifier = Modifier.fillMaxSize())
            }

            // Selection overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelected,
                enter   = fadeIn(tween(100)) + scaleIn(spring(Spring.DampingRatioMediumBouncy), 0.5f),
                exit    = fadeOut(tween(100)),
            ) {
                Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.18f)))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = Color.White)
                }
            }

            // Bottom label
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    .padding(8.dp),
            ) {
                Text(
                    when (item) {
                        is CleanItem.GenericFile -> item.file.name
                        is CleanItem.Corpse      -> item.entry.packageName
                        is CleanItem.UnusedApp   -> item.entry.appName
                        is CleanItem.Duplicate   -> item.group.files.firstOrNull()?.path?.substringAfterLast('/') ?: stringResource(R.string.st_CleanerScreen_g7h8)
                    },
                    style    = MaterialTheme.typography.labelSmall,
                    color    = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (item) {
                        is CleanItem.GenericFile -> Formatter.formatFileSize(context, item.file.sizeBytes)
                        is CleanItem.Corpse      -> Formatter.formatFileSize(context, item.entry.sizeBytes)
                        is CleanItem.UnusedApp   -> Formatter.formatFileSize(context, item.entry.sizeBytes)
                        is CleanItem.Duplicate   -> Formatter.formatFileSize(context, item.group.sizeBytes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }

            // Open button
            if (item !is CleanItem.UnusedApp) {
                IconButton(
                    onClick  = { vibrationManager?.vibrateClick(); onOpenFile(path) },
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(32.dp),
                ) {
                    Icon(Icons.Rounded.Visibility, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─── Cleaning Progress View ───────────────────────────────────────────────────

@Composable
private fun CleaningView(state: ScanState.Cleaning) {
    val performanceMode = LocalPerformanceMode.current
    val primary = MaterialTheme.colorScheme.primary

    val animatedProgress by animateFloatAsState(
        targetValue   = state.progress,
        animationSpec = if (performanceMode) tween(400) else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow),
        label         = "cleanProgress",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
            if (!performanceMode) {
                val infiniteTransition = rememberInfiniteTransition(label = "cleanPulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    0.04f, 0.12f,
                    infiniteRepeatable(tween(1400), RepeatMode.Reverse),
                    label = "pulseAlpha",
                )
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        primary.copy(alpha = pulseAlpha),
                        (size.minDimension / 2f) * (0.85f + animatedProgress * 0.15f),
                    )
                }
            }

            CircularProgressIndicator(
                progress     = { 1f },
                modifier     = Modifier.fillMaxSize().padding(14.dp),
                strokeWidth  = 14.dp,
                color        = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap    = StrokeCap.Round,
            )
            CircularProgressIndicator(
                progress     = { animatedProgress },
                modifier     = Modifier.fillMaxSize().padding(14.dp),
                strokeWidth  = 14.dp,
                color        = primary,
                strokeCap    = StrokeCap.Round,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(animatedProgress * 100).roundToInt()}%",
                    style      = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
                    color      = primary,
                )
                Text(
                    stringResource(R.string.st_CleanerScreen_i9j0),
                    style       = MaterialTheme.typography.labelSmall,
                    fontWeight  = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color       = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            state.currentFile.substringAfterLast('/'),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Done View ────────────────────────────────────────────────────────────────

@Composable
private fun DoneView(result: CleanResult, onDone: () -> Unit) {
    val context         = LocalContext.current
    val performanceMode = LocalPerformanceMode.current

    val infiniteTransition = rememberInfiniteTransition(label = "done_anim")
    val halo by infiniteTransition.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "donePulse",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer { if (!performanceMode) { scaleX = halo; scaleY = halo } }
                .background(MaterialTheme.colorScheme.primaryContainer, ExtraLargeExpressiveShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                null,
                Modifier.size(86.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            stringResource(R.string.st_CleanerScreen_k1l2),
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color      = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            Formatter.formatFileSize(context, result.freedBytes),
            style      = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            stringResource(R.string.st_CleanerScreen_m3n4) + " ${result.deletedCount} items",
            style  = MaterialTheme.typography.bodyLarge,
            color  = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )

        AnimatedVisibility(visible = result.failedCount > 0) {
            Text(
                "${result.failedCount} " + stringResource(R.string.st_CleanerScreen_o5p6),
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(48.dp))

        ToolzExpressiveButton(
            onClick  = onDone,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape    = RoundedCornerShape(22.dp),
        ) {
            Icon(Icons.Rounded.Home, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.st_CleanerScreen_q7r8), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
        }
    }
}

// ─── Error View ───────────────────────────────────────────────────────────────

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape    = ExtraLargeExpressiveShape,
            color    = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.st_CleanerScreen_s9t0), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(40.dp))
        ToolzExpressiveButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.st_CleanerScreen_u1v2), fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(12.dp))
        ToolzOutlinedExpressiveButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.st_CleanerScreen_w3x4), fontWeight = FontWeight.Black)
        }
    }
}

// ─── Slide-to-Clean Button ────────────────────────────────────────────────────

@Composable
private fun SlideToCleanButton(
    cleanableBytes: Long,
    selectedBytes : Long = cleanableBytes,
    onClean       : () -> Unit,
    modifier      : Modifier = Modifier,
) {
    val context          = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val density          = LocalDensity.current
    val primary          = MaterialTheme.colorScheme.primary

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var trackWidth by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp  = 64.dp
    val thumbSizePx  = with(density) { thumbSizeDp.toPx() }
    val paddingPx    = with(density) { 8.dp.toPx() }
    val maxDrag      = (trackWidth - thumbSizePx - paddingPx * 2).coerceAtLeast(0f)
    val progress     = if (maxDrag > 0) (dragOffset / maxDrag).coerceIn(0f, 1f) else 0f
    val isComplete   = progress >= 0.98f

    LaunchedEffect(isComplete) {
        if (isComplete) {
            vibrationManager?.vibrateLongClick()
            onClean()
            dragOffset = 0f
        }
    }

    val trackColorAnim by animateColorAsState(
        targetValue   = if (progress > 0.5f) primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(200),
        label         = "trackColor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .clip(CircleShape)
            .background(trackColorAnim)
            .border(1.5.dp, primary.copy(alpha = 0.25f), CircleShape)
            .pointerInput(trackWidth, maxDrag) {
                if (trackWidth <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { if (progress < 0.98f) dragOffset = 0f },
                    onHorizontalDrag = { _, dragAmt ->
                        dragOffset = (dragOffset + dragAmt).coerceIn(0f, maxDrag)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Label fades out as drag progresses
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 84.dp, end = 20.dp)
                .graphicsLayer { alpha = (1f - progress * 1.8f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.st_CleanerScreen_y5z6),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color      = primary,
            )
            if (selectedBytes < cleanableBytes) {
                Text(
                    "${Formatter.formatFileSize(context, selectedBytes)} " + stringResource(R.string.st_CleanerScreen_a7b8),
                    style = MaterialTheme.typography.labelSmall,
                    color = primary.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    Formatter.formatFileSize(context, cleanableBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = primary.copy(alpha = 0.6f),
                )
            }
        }

        // Progress fill
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.12f), primary.copy(alpha = 0.06f))))
            )
        }

        // Thumb
        Surface(
            modifier        = Modifier
                .offset(x = with(density) { (dragOffset + paddingPx).toDp() })
                .size(thumbSizeDp)
                .padding(4.dp),
            shape           = ExtraLargeExpressiveShape,
            color           = primary,
            shadowElevation = 10.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isComplete) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    null,
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// ─── Permission Dialog ────────────────────────────────────────────────────────

@Composable
private fun PermissionEducationDialog(onGrantClick: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.FolderSpecial, null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        },
        title = { Text(stringResource(R.string.st_CleanerScreen_c9d0), fontWeight = FontWeight.Black) },
        text = {
            Text(
                stringResource(R.string.st_CleanerScreen_e1f2),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            ToolzExpressiveButton(onClick = onGrantClick) {
                Text(stringResource(R.string.st_CleanerScreen_g3h4), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            ToolzOutlinedExpressiveButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_CleanerScreen_i5j6))
            }
        },
        shape = BouncyShape,
    )
}

// ─── Private helpers ──────────────────────────────────────────────────────────

private fun iconForCategoryName(name: String): ImageVector = when (name) {
    "DeleteSweep"    -> Icons.Rounded.DeleteSweep
    "FileCopy"       -> Icons.Rounded.FileCopy
    "AutoDelete"     -> Icons.Rounded.AutoDelete
    "Straighten"     -> Icons.Rounded.Straighten
    "FolderOff"      -> Icons.Rounded.FolderOff
    "AppSettingsAlt" -> Icons.Rounded.AppSettingsAlt
    "Description"    -> Icons.Rounded.Description
    else             -> Icons.Rounded.Folder
}

private fun iconForExtension(ext: String): ImageVector = when (ext.lowercase()) {
    "pdf"                              -> Icons.Rounded.PictureAsPdf
    "mp3", "wav", "m4a", "ogg","flac" -> Icons.Rounded.Audiotrack
    "jpg","jpeg","png","gif","webp","bmp" -> Icons.Rounded.Image
    "mp4","mkv","avi","mov","webm"     -> Icons.Rounded.Movie
    "zip","rar","7z","tar"             -> Icons.Rounded.FolderZip
    "apk"                              -> Icons.Rounded.Android
    "txt","doc","docx","xls","xlsx","ppt","pptx" -> Icons.Rounded.Description
    else                               -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

private fun formatLastUsed(time: Long): String {
    if (time == 0L) return "a long time ago"
    val days = (System.currentTimeMillis() - time) / 86_400_000L
    return when {
        days == 0L -> "today"
        days == 1L -> "yesterday"
        days < 30  -> "$days days ago"
        else       -> "${days / 30} months ago"
    }
}

private fun openAppSettings(context: Context, packageName: String) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

private fun openFile(
    context          : Context,
    path             : String,
    onNavigateToPdf  : (Uri, String) -> Unit,
    onNavigateToMusic: (Uri) -> Unit,
) {
    val file = File(path)
    if (!file.exists()) return
    val ext = file.extension.lowercase()

    val mediaUri = getMediaStoreUri(context, path, ext)
    val uri = mediaUri ?: runCatching {
        FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
    }.getOrElse { Uri.fromFile(file) }

    when (ext) {
        "pdf" -> onNavigateToPdf(uri, file.name)
        "mp3", "wav", "m4a", "ogg", "flac" -> onNavigateToMusic(uri)
        else -> runCatching {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    context.getString(R.string.st_CleanerScreen_k7l8),
                )
            )
        }
    }
}

private fun getMediaStoreUri(context: Context, path: String, ext: String): Uri? {
    val collection = when (ext) {
        "mp3", "wav", "m4a", "ogg", "flac"     -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        "pdf"                                   -> MediaStore.Files.getContentUri("external")
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "mp4", "mkv", "avi", "mov", "webm"      -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else                                    -> return null
    }
    return runCatching {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DATA}=?",
            arrayOf(path),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst())
                ContentUris.withAppendedId(collection, cursor.getLong(0))
            else null
        }
    }.getOrNull()
}