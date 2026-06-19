@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.frerox.toolz.ui.screens.media

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.ConversionEngine
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  FileConverterScreen — Premium Architecture Redesign
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FileConverterScreen(
    viewModel: FileConverterViewModel,
    onBack: () -> Unit,
    initialUri: Uri? = null,
    initialTitle: String? = null,
) {
    val uiState          by viewModel.uiState.collectAsState()
    val context           = LocalContext.current
    val vibrationManager  = LocalVibrationManager.current
    val hapticEnabled     = LocalHapticEnabled.current
    val performanceMode   = LocalPerformanceMode.current

    var highQuality         by remember { mutableStateOf(true) }
    var showFormatSheet     by remember { mutableStateOf(false) }
    var showAllFormatsSheet by remember { mutableStateOf(false) }
    var showInfoSheet       by remember { mutableStateOf(false) }

    // Handle incoming URI from deep-link / share intent
    LaunchedEffect(initialUri) {
        if (initialUri != null && uiState.selectedFiles.isEmpty()) {
            viewModel.onFilesSelected(listOf(initialUri))
        }
    }

    // BroadcastReceiver — listens to service updates
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                val qPos   = intent?.getIntExtra("queue_pos", 1) ?: 1
                val qTotal = intent?.getIntExtra("queue_total", 1) ?: 1
                when (intent?.action) {
                    "COM_FREROX_TOOLZ_CONVERSION_PROGRESS" -> {
                        viewModel.onConversionProgress(intent.getIntExtra("progress", 0), qPos, qTotal)
                    }
                    "COM_FREROX_TOOLZ_CONVERSION_SUCCESS" -> {
                        val path = intent.getStringExtra("output_path") ?: ""
                        viewModel.onConversionSuccess(path, qPos, qTotal)
                        if (hapticEnabled) vibrationManager?.vibrateSuccess()
                    }
                    "COM_FREROX_TOOLZ_CONVERSION_ERROR" -> {
                        viewModel.onConversionError(intent.getStringExtra("error_message") ?: "Unknown error")
                        vibrationManager?.vibrateError()
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("COM_FREROX_TOOLZ_CONVERSION_PROGRESS")
            addAction("COM_FREROX_TOOLZ_CONVERSION_SUCCESS")
            addAction("COM_FREROX_TOOLZ_CONVERSION_ERROR")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(uris)
            showFormatSheet = true
        }
    }

    // Bottom sheets
    if (showFormatSheet && uiState.selectedFiles.isNotEmpty()) {
        ConversionTypeSheet(
            uri       = uiState.selectedFiles.first().uri,
            fileCount = uiState.selectedFiles.size,
            mimeType  = uiState.selectedFiles.first().mimeType,
            onDismiss = { showFormatSheet = false; viewModel.clearSelection() },
            onTypeSelected = { type ->
                viewModel.startConversion(
                    uris       = uiState.selectedFiles.map { it.uri },
                    type       = type,
                    highQuality = highQuality,
                )
                showFormatSheet = false
            },
        )
    }
    if (showAllFormatsSheet) {
        AllFormatsSheet(onDismiss = { showAllFormatsSheet = false })
    }
    if (showInfoSheet) {
        EngineInfoSheet(onDismiss = { showInfoSheet = false })
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = "FILE CONVERTER",
                subtitle = "CONVERT ANY FILE FORMAT",
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            highQuality = !highQuality
                            vibrationManager?.vibrateTick()
                        },
                        modifier = Modifier
                            .padding(8.dp)
                            .clip(SmallExpressiveShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (highQuality) Icons.Rounded.HighQuality else Icons.Rounded.Speed,
                            contentDescription = "Quality",
                            tint = if (highQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .then(
                        if (!performanceMode) Modifier.fadingEdges(top = 24.dp, bottom = 24.dp)
                        else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Spacer(Modifier.height(16.dp))

            // ── Main animated state ───────────────────────────────────────────
            AnimatedContent(
                targetState    = Triple(uiState.conversionSuccess, uiState.isConverting, uiState.selectedFiles.isNotEmpty()),
                transitionSpec = {
                    (fadeIn(tween(500, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.94f, animationSpec = tween(500, easing = FastOutSlowInEasing)))
                        .togetherWith(fadeOut(tween(300)) + scaleOut(targetScale = 1.04f, animationSpec = tween(300)))
                },
                label = "mainState",
            ) { (success, converting, hasFiles) ->
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    when {
                        success    -> SuccessView(
                            outputFiles     = uiState.outputFiles,
                            selectedFiles   = uiState.selectedFiles,
                            conversionType  = uiState.conversionType,
                            highQuality     = highQuality,
                            performanceMode = performanceMode,
                            onReset         = { viewModel.reset() },
                        )
                        converting -> ConvertingView(
                            progress        = uiState.progress,
                            queuePos        = uiState.queuePos,
                            queueTotal      = uiState.queueTotal,
                            conversionType  = uiState.conversionType,
                            performanceMode = performanceMode,
                            onCancel        = { viewModel.cancelConversion(); vibrationManager?.vibrateTick() },
                        )
                        else       -> IdleView(
                            performanceMode  = performanceMode,
                            highQuality      = highQuality,
                            onToggleQuality  = { highQuality = it; vibrationManager?.vibrateTick() },
                            onSelectFile     = { launcher.launch("*/*") },
                            onShowAllFormats = { showAllFormatsSheet = true },
                            onShowInfo       = { showInfoSheet = true }
                        )
                    }
                }
            }

            // ── Error Dialog ─────────────────────────────────────────────────
            if (uiState.error != null) {
                ErrorDialog(
                    message = uiState.error!!,
                    onDismiss = { viewModel.dismissError() }
                )
            }

            // ── Recent conversions ────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.recentConversions.isNotEmpty() && !uiState.isConverting && !uiState.conversionSuccess,
                enter   = fadeIn(tween(600)) + slideInVertically { it / 2 },
                exit    = fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(40.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.History, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        Text(
                            text       = "Conversion History",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    uiState.recentConversions.reversed().forEachIndexed { i, recent ->
                        StaggeredEntrance(index = i) {
                            RecentConversionItem(
                                recent  = recent,
                                context = LocalContext.current,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
//  Idle View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun IdleView(
    performanceMode: Boolean,
    highQuality: Boolean,
    onToggleQuality: (Boolean) -> Unit,
    onSelectFile: () -> Unit,
    onShowAllFormats: () -> Unit,
    onShowInfo: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // Selection Hero
        HeroSelectionZone(
            performanceMode = performanceMode,
            onSelectFile = {
                vibrationManager?.vibrateClick()
                onSelectFile()
            }
        )

        // Quality Configuration
        StaggeredEntrance(index = 1) {
            QualityConfigCard(
                highQuality = highQuality,
                onToggle    = onToggleQuality,
            )
        }

        // Quick Types Category Grid
        StaggeredEntrance(index = 2) {
            QuickTypesGrid(onShowAllFormats = onShowAllFormats)
        }

        // Learn More Button
        StaggeredEntrance(index = 3) {
            ToolzOutlinedExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onShowInfo()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MediumExpressiveShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.Info, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("LEARN ABOUT THE ENGINE", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun HeroSelectionZone(
    performanceMode: Boolean,
    onSelectFile: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "hero")

    val pulseScale by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 1.015f,
            animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "pulse",
        )
    } else remember { mutableFloatStateOf(1f) }

    val iconTranslation by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = -12f,
            animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
            label = "icon",
        )
    } else remember { mutableFloatStateOf(0f) }

    ExpressiveCard(
        onClick = onSelectFile,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale },
        shape = ExtraLargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = 4.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer { translationY = iconTranslation }
                        .background(primary.copy(alpha = 0.08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint     = primary,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text       = "Tap to Select Files",
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign  = TextAlign.Center,
                )
                Text(
                    text      = "CONVERT MORE THAN 50 FILE FORMATS",
                    style     = MaterialTheme.typography.labelMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun QualityConfigCard(
    highQuality: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val color = if (highQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val containerColor = color.copy(alpha = 0.05f)
    
    ExpressiveCard(
        onClick = { onToggle(!highQuality) },
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        containerColor = containerColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (highQuality) Icons.Rounded.HighQuality else Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (highQuality) "Elite Quality" else "Performance Mode",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (highQuality) "Best visual/audio fidelity · Lanczos" else "Fastest conversion · Smaller files",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            ExpressiveSwitch(checked = highQuality, onCheckedChange = onToggle)
        }
    }
}

private data class FormatCategory(val icon: ImageVector, val label: String, val subtitle: String, val color: Color)

@Composable
private fun QuickTypesGrid(onShowAllFormats: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val categories = remember {
        listOf(
            FormatCategory(Icons.Rounded.Movie,       "Video",   "MP4, MKV, MOV", Color(0xFFD32F2F)),
            FormatCategory(Icons.Rounded.MusicNote,   "Audio",   "MP3, WAV, AAC", Color(0xFF1976D2)),
            FormatCategory(Icons.Rounded.Image,       "Image",   "PNG, JPG, WEBP", Color(0xFF388E3C)),
            FormatCategory(Icons.Rounded.Description, "Docs", "PDF, TXT, MD", Color(0xFFFBC02D)),
            FormatCategory(Icons.Rounded.Animation,   "Motion",  "GIF, WEBP", Color(0xFFC2185B)),
            FormatCategory(Icons.Rounded.Code,        "Vector",  "SVG to PNG/PDF", Color(0xFF7B1FA2)),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Supported Asset Types",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Surface(
                onClick = { 
                    vibrationManager?.vibrateTick()
                    onShowAllFormats() 
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    "VIEW ALL →",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        val rows = categories.chunked(2)
        rows.forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIdx, cat ->
                    ExpressiveCard(
                        onClick = { vibrationManager?.vibrateTick(); onShowAllFormats() },
                        modifier = Modifier.weight(1f),
                        shape = LargeExpressiveShape,
                        containerColor = cat.color.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, cat.color.copy(alpha = 0.15f)),
                        elevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).background(cat.color.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(cat.icon, null, modifier = Modifier.size(22.dp), tint = cat.color)
                            }
                            Column {
                                Text(cat.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
                                Text(cat.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Converting View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConvertingView(
    progress: Int,
    queuePos: Int,
    queueTotal: Int,
    conversionType: ConversionEngine.ConversionType?,
    performanceMode: Boolean,
    onCancel: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val vibrationManager = LocalVibrationManager.current

    val infiniteTransition = rememberInfiniteTransition(label = "converting")
    val pulseScale by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0.95f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        )
    } else remember { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Main animated progress section
        Box(
            contentAlignment = Alignment.Center, 
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
        ) {
            ExpressiveCircularProgressIndicator(
                progress = if (progress > 0) ({ progress / 100f }) else ({ 0f }),
                modifier = Modifier.fillMaxSize(),
                color = primary,
                trackColor = primary.copy(alpha = 0.1f),
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (progress > 0) {
                    Text(
                        text = "$progress%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = primary,
                        letterSpacing = (-1).sp
                    )
                } else {
                    ExpressiveLoadingWheel(modifier = Modifier.size(72.dp), color = primary)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        // Clean typography for state
        Text(
            text = "Converting Assets",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
        )
        
        Spacer(Modifier.height(8.dp))

        if (queueTotal > 1) {
            Text(
                text = "Processing $queuePos of $queueTotal",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        conversionType?.let {
            Surface(
                modifier = Modifier.padding(top = 16.dp),
                shape = CircleShape,
                color = primary.copy(alpha = 0.08f),
            ) {
                Text(
                    text = "TARGET: .${it.extension.uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
        
        Spacer(Modifier.height(56.dp))
        
        // Simple elegant abort button
        Surface(
            onClick = {
                vibrationManager?.vibrateTick()
                onCancel()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            modifier = Modifier.wrapContentSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text("Cancel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Success View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessView(
    outputFiles: List<String>,
    selectedFiles: List<FileInfo>,
    conversionType: ConversionEngine.ConversionType?,
    highQuality: Boolean,
    performanceMode: Boolean,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val successColor = Color(0xFF1E88E5) // Clean blue instead of heavy green

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Simple bouncy icon
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn()
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = successColor.copy(alpha = 0.1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(48.dp), tint = successColor)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Minimalist Typography
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Success!",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "${outputFiles.size} file${if (outputFiles.size > 1) "s" else ""} ready to use.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(Modifier.height(8.dp))

        // Expressive Summary Card
        ConversionSummaryCard(
            selectedFiles = selectedFiles,
            outputPaths = outputFiles,
            conversionType = conversionType,
            highQuality = highQuality,
            performanceMode = performanceMode
        )

        Spacer(Modifier.height(8.dp))

        // Clean action grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToolzOutlinedExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    try {
                        val file = File(outputFiles.first())
                        val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(uri) ?: "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share File"))
                    } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = LargeExpressiveShape
            ) {
                Icon(Icons.Rounded.IosShare, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share", fontWeight = FontWeight.Black)
            }

            ToolzExpressiveButton(
                onClick = {
                    vibrationManager?.vibrateClick()
                    onReset()
                },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = LargeExpressiveShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Done", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun ConversionSummaryCard(
    selectedFiles: List<FileInfo>,
    outputPaths: List<String>,
    conversionType: ConversionEngine.ConversionType?,
    highQuality: Boolean,
    performanceMode: Boolean
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    
    val totalInputSize = remember(selectedFiles) { selectedFiles.sumOf { it.size } }
    val totalOutputSize = remember(outputPaths) { outputPaths.sumOf { File(it).length() } }
    val sizeDiff = totalOutputSize - totalInputSize
    val sizeDiffPercent = if (totalInputSize > 0) (sizeDiff.toFloat() / totalInputSize * 100).toInt() else 0
    
    ExpressiveCard(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Header: Input -> Output
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("FORMAT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (selectedFiles.size == 1) selectedFiles.first().name.substringAfterLast(".", "UNKNOWN").uppercase() else "MIXED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        Text(
                            text = conversionType?.extension?.uppercase() ?: "???",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("QUALITY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Text(
                        text = if (highQuality) "ELITE" else "PERFORMANCE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (highQuality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            
            // Size Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("TOTAL SIZE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                    Text(formatFileSize(totalOutputSize), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("CHANGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = if (sizeDiff <= 0) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (sizeDiff <= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Text(
                            text = "${if (sizeDiff > 0) "+" else ""}$sizeDiffPercent%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (sizeDiff <= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }
            
            // Thumbnails
            Text("FILES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(outputPaths) { path ->
                    val file = File(path)
                    val ext = file.extension.lowercase()
                    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
                    
                    ExpressiveCard(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            try {
                                val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open Asset"))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(80.dp),
                        shape = MediumExpressiveShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        elevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (isImage && file.exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(file).build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = categoryIcon(conversionType?.category ?: ""),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            }
                            
                            // Extension Badge
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = ext.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Error Console
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Text("Conversion Failed", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("We are sorry, but the engine encountered an error while processing your files.", style = MaterialTheme.typography.bodyMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message))
                    android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("COPY ERROR LOG", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    onDismiss()
                }
            ) {
                Text("CLOSE", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Engine Info Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EngineInfoSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 32.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SettingsInputComponent, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            }
            
            Spacer(Modifier.height(24.dp))
            
            Text("Toolz File Converter", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("ARCHITECTURE & WORKFLOW", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
            
            Spacer(Modifier.height(32.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.weight(1f)) {
                item {
                    InfoSection(
                        icon = Icons.Rounded.Code,
                        title = "Core Architecture",
                        description = "Our engine is built on a custom native integration of FFmpeg, ensuring desktop-class performance and support for hundreds of legacy and modern codecs entirely offline."
                    )
                }
                item {
                    InfoSection(
                        icon = Icons.Rounded.Speed,
                        title = "Performance vs. Quality",
                        description = "Performance mode utilizes hardware-accelerated encoders (MediaCodec) when available, focusing on speed. Quality mode uses software-based Lanczos scaling and high-bitrate libx264/libx265 for maximum fidelity."
                    )
                }
                item {
                    InfoSection(
                        icon = Icons.Rounded.Security,
                        title = "Privacy First",
                        description = "No data ever leaves your device. All processing happens locally in isolated background services. We don't use cloud APIs, protecting your sensitive media from third-party servers."
                    )
                }
                item {
                    InfoSection(
                        icon = Icons.Rounded.Layers,
                        title = "Batch Pipeline",
                        description = "The engine manages a concurrent queue, automatically handling multiple file types in a single session. It intelligently routes images, videos, and PDFs to specialized sub-engines."
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            ToolzExpressiveButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = LargeExpressiveShape
            ) {
                Text("GOT IT", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoSection(icon: ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), lineHeight = 20.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Components & Sheets
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OutputFileCard(
    path: String,
    context: android.content.Context,
    vibrationManager: com.frerox.toolz.util.VibrationManager?,
) {
    val file = remember(path) { File(path) }
    ExpressiveCard(
        onClick = {
            vibrationManager?.vibrateClick()
            try {
                val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open Asset"))
            } catch (_: Exception) {}
        },
        modifier = Modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val ext = file.extension.lowercase()
            val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
            if (isImage && file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(file).build(),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(SmallExpressiveShape)
                )
            } else {
                Box(
                    modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), SmallExpressiveShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatFileSize(file.length()) + " · Ready to use", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun RecentConversionItem(
    recent: RecentConversion,
    context: android.content.Context,
) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    ExpressiveCard(
        onClick = {
            try {
                val file = File(recent.outputPath)
                if (file.exists()) {
                    val uri = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open File"))
                }
            } catch (_: Exception) {}
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val color = categoryColor(recent.category)
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(recent.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(File(recent.outputPath).name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(fmt.format(Date(recent.timestampMs)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ConversionTypeSheet(
    uri: Uri,
    fileCount: Int,
    mimeType: String,
    onDismiss: () -> Unit,
    onTypeSelected: (ConversionEngine.ConversionType) -> Unit,
) {
    val context = LocalContext.current
    val effectiveMime = mimeType.ifBlank { context.contentResolver.getType(uri) ?: "" }
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val availableTypes = remember(effectiveMime) {
        ConversionEngine.ConversionType.entries.filter { type ->
            if (effectiveMime.isBlank()) return@filter true
            type.inputMimes.any { effectiveMime.startsWith(it) || effectiveMime == it }
        }
    }

    val filteredTypes = remember(searchQuery, availableTypes) {
        availableTypes.filter {
            searchQuery.isBlank() || it.extension.contains(searchQuery, ignoreCase = true) || it.label.contains(searchQuery, ignoreCase = true)
        }.sortedByDescending { it.isPopular }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Select Output Format", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    if (fileCount > 1) {
                        Text("BATCH CONVERTING $fileCount FILES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search formats (mp4, webp, pdf...)") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.height(20.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredTypes) { type ->
                    TypeOptionItem(type = type, onClick = { onTypeSelected(type) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AllFormatsSheet(onDismiss: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val allTypes = ConversionEngine.ConversionType.entries.toList()
    val filteredTypes = remember(searchQuery) {
        allTypes.filter {
            searchQuery.isBlank() || it.extension.contains(searchQuery, ignoreCase = true) || it.label.contains(searchQuery, ignoreCase = true)
        }.groupBy { it.category }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, 
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Library of Formats", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search 50+ supported formats...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Spacer(Modifier.height(20.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                filteredTypes.forEach { (cat, types) ->
                    item {
                        Text(
                            text = cat.uppercase(), 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Black, 
                            color = MaterialTheme.colorScheme.primary, 
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp, start = 4.dp)
                        )
                    }
                    items(types) { type ->
                        ExpressiveCard(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = MediumExpressiveShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            elevation = 0.dp
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(".${type.extension.uppercase()}", modifier = Modifier.width(72.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Text(type.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                if (type.isPopular) Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = Color(0xFFFFD700))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun TypeOptionItem(type: ConversionEngine.ConversionType, onClick: () -> Unit) {
    val color = categoryColor(type.category)
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = 2.dp
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(categoryIcon(type.category), null, modifier = Modifier.size(24.dp), tint = color)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(type.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    if (type.isPopular) Icon(Icons.Rounded.Star, null, Modifier.size(14.dp), tint = Color(0xFFFFD700))
                }
                Text(".${type.extension} · ${type.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun categoryColor(category: String): Color = when (category) {
    "Videos"     -> Color(0xFFD32F2F)
    "Audio"      -> Color(0xFF1976D2)
    "Images"     -> Color(0xFF388E3C)
    "Documents"  -> Color(0xFFFBC02D)
    "Animations" -> Color(0xFFC2185B)
    else         -> MaterialTheme.colorScheme.primary
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "Audio"      -> Icons.Rounded.MusicNote
    "Animations" -> Icons.Rounded.Animation
    "Images"     -> Icons.Rounded.Image
    "Documents"  -> Icons.Rounded.Description
    else         -> Icons.Rounded.Movie
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
    else -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
}
