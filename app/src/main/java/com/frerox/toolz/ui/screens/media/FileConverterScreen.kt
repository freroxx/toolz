@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.frerox.toolz.ui.screens.media

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExtraLargeExpressiveShape
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveCircularProgressIndicator
import com.frerox.toolz.ui.components.ExpressiveLoadingWheel
import com.frerox.toolz.ui.components.ExpressiveSwitch
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ExpressiveWavyLinearProgressIndicator
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzConnectedButtonGroup
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzOutlinedExpressiveButton
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.theme.LocalHapticEnabled
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import com.frerox.toolz.util.ConversionEngine
import java.io.File
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
//  FileConverterScreen — Root
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

    var highQuality          by remember { mutableStateOf(true) }
    var showAllFormatsSheet  by remember { mutableStateOf(false) }
    var showTypePicker       by remember { mutableStateOf(false) }
    var pendingUris          by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Handle incoming URI from deep-link / share intent
    LaunchedEffect(initialUri) {
        if (initialUri != null && uiState.selectedFileUri == null) {
            pendingUris    = listOf(initialUri)
            showTypePicker = true
        }
    }

    // Broadcast receiver — listens to service updates
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                when (intent?.action) {
                    "COM_FREROX_TOOLZ_CONVERSION_PROGRESS" -> {
                        viewModel.onConversionProgress(intent.getIntExtra("progress", 0))
                    }
                    "COM_FREROX_TOOLZ_CONVERSION_SUCCESS"  -> {
                        val path = intent.getStringExtra("output_path")
                        viewModel.onConversionFinished(true, path, null)
                        if (hapticEnabled) vibrationManager?.vibrateSuccess()
                    }
                    "COM_FREROX_TOOLZ_CONVERSION_ERROR"    -> {
                        val error = intent.getStringExtra("error_message")
                        viewModel.onConversionFinished(false, null, error)
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
            pendingUris    = uris
            showTypePicker = true
        }
    }

    // Bottom sheets
    if (showTypePicker && pendingUris.isNotEmpty()) {
        ConversionTypeSheet(
            uri            = pendingUris.first(),
            fileCount      = pendingUris.size,
            onDismiss      = { showTypePicker = false },
            onTypeSelected = { type ->
                viewModel.selectFiles(pendingUris, type, highQuality)
                showTypePicker = false
            },
        )
    }
    if (showAllFormatsSheet) {
        AllFormatsSheet(onDismiss = { showAllFormatsSheet = false })
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier       = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar         = {
            ExpressiveTopAppBar(
                title    = "File Converter",
                subtitle = "Transform any format, losslessly",
                navigationIcon = {
                    IconButton(onClick = {
                        vibrationManager?.vibrateClick()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors         = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor         = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                ),
                scrollBehavior = scrollBehavior,
                largeFlexible  = true,
                modifier       = Modifier.statusBarsPadding(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .then(
                    if (!performanceMode) Modifier.fadingEdges(top = 16.dp, bottom = 24.dp)
                    else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

            Spacer(Modifier.height(12.dp))

            // ── Quality toggle ────────────────────────────────────────────
            StaggeredEntrance(index = 0) {
                QualityToggleCard(
                    highQuality = highQuality,
                    onToggle    = {
                        highQuality = it
                        vibrationManager?.vibrateTick()
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Main conversion area (idle / converting / success) ────────
            AnimatedContent(
                targetState  = uiState.conversionSuccess to uiState.isConverting,
                transitionSpec = {
                    (fadeIn(tween(420, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.93f, animationSpec = tween(420, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            fadeOut(tween(260)) + scaleOut(targetScale = 1.04f, animationSpec = tween(260))
                        )
                },
                label = "mainContent",
            ) { (success, converting) ->
                if (success) {
                    SuccessView(
                        outputPath = uiState.outputPath ?: "",
                        category   = uiState.conversionType?.category ?: "Downloads",
                        onReset    = { viewModel.reset() },
                        performanceMode = performanceMode,
                    )
                } else {
                    ConversionView(
                        isConverting       = converting,
                        progress           = uiState.progress,
                        conversionType     = uiState.conversionType,
                        fileCount          = pendingUris.size.coerceAtLeast(1),
                        performanceMode    = performanceMode,
                        onSelectFile       = { launcher.launch("*/*") },
                        onShowAllFormats   = { showAllFormatsSheet = true },
                    )
                }
            }

            // ── Error banner ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.error != null,
                enter   = expandVertically() + fadeIn(),
                exit    = shrinkVertically() + fadeOut(),
            ) {
                if (uiState.error != null) {
                    ErrorBanner(
                        message = uiState.error!!,
                        onDismiss = { viewModel.reset() },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quality Toggle Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QualityToggleCard(
    highQuality: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vibrationManager = LocalVibrationManager.current
    val accentColor = if (highQuality)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.secondary

    val animatedAccent by animateColorAsState(
        targetValue   = accentColor,
        animationSpec = tween(400),
        label         = "qualityAccent",
    )

    ExpressiveCard(
        onClick   = {
            vibrationManager?.vibrateTick()
            onToggle(!highQuality)
        },
        modifier  = modifier.fillMaxWidth(),
        shape     = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Animated icon container
            val iconScale by animateFloatAsState(
                targetValue   = if (highQuality) 1.1f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness    = Spring.StiffnessMediumLow,
                ),
                label = "qualityIconScale",
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                    .background(animatedAccent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (highQuality) Icons.Rounded.HighQuality else Icons.Rounded.Speed,
                    contentDescription = null,
                    tint     = animatedAccent,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = if (highQuality) "Elite Quality" else "Fast Mode",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text  = if (highQuality) "Max bitrate · Lanczos upscaling" else "Speed-optimised · Smaller output",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ExpressiveSwitch(
                checked         = highQuality,
                onCheckedChange = { onToggle(it) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Conversion View (idle + converting)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConversionView(
    isConverting: Boolean,
    progress: Int,
    conversionType: ConversionEngine.ConversionType?,
    fileCount: Int,
    performanceMode: Boolean,
    onSelectFile: () -> Unit,
    onShowAllFormats: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // Drop zone card
        DropZoneCard(
            isConverting    = isConverting,
            progress        = progress,
            conversionType  = conversionType,
            fileCount       = fileCount,
            performanceMode = performanceMode,
            onSelectFile    = onSelectFile,
        )

        Spacer(Modifier.height(28.dp))

        // Format category chips
        FormatCategoryGrid(onShowAllFormats = onShowAllFormats)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Drop Zone Card (the large interactive card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DropZoneCard(
    isConverting: Boolean,
    progress: Int,
    conversionType: ConversionEngine.ConversionType?,
    fileCount: Int,
    performanceMode: Boolean,
    onSelectFile: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current

    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    // Pulsing scale for idle "tap me" affordance
    val infiniteTransition = rememberInfiniteTransition(label = "dropZone")
    val idlePulse by if (!isConverting && !performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 1.012f,
            animationSpec = infiniteRepeatable(
                tween(1600, easing = EaseInOutSine), RepeatMode.Reverse,
            ),
            label = "idlePulse",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    // Spinning border rotation for converting state
    val borderRotation by if (isConverting && !performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "borderRotation",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // Background glow for converting
    val glowAlpha by if (isConverting && !performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0.06f, targetValue = 0.16f,
            animationSpec = infiniteRepeatable(
                tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse,
            ),
            label = "glowAlpha",
        )
    } else {
        remember { mutableFloatStateOf(if (isConverting) 0.1f else 0f) }
    }

    val cardElevation by animateDpAsState(
        targetValue   = if (isConverting) 8.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label         = "cardElevation",
    )

    val animatedProgress by animateFloatAsState(
        targetValue   = progress.toFloat() / 100f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label         = "convProgress",
    )

    // Outer wrapper handles the spinning canvas border overlay
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .graphicsLayer { scaleX = idlePulse; scaleY = idlePulse }
            .then(
                if (isConverting && !performanceMode) {
                    Modifier.drawWithContent {
                        drawContent()
                        // Background glow
                        drawRoundRect(
                            color       = primary.copy(alpha = glowAlpha),
                            cornerRadius = CornerRadius(48.dp.toPx()),
                        )
                        // Spinning gradient border
                        rotate(degrees = borderRotation, pivot = Offset(size.width / 2f, size.height / 2f)) {
                            val strokePx = 3.dp.toPx()
                            drawRoundRect(
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        primary.copy(alpha = 0.4f),
                                        primary,
                                        secondary,
                                        primary,
                                        primary.copy(alpha = 0.4f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                ),
                                topLeft      = Offset(strokePx / 2f, strokePx / 2f),
                                size         = Size(size.width - strokePx, size.height - strokePx),
                                cornerRadius = CornerRadius(48.dp.toPx(), 48.dp.toPx()),
                                style        = Stroke(width = strokePx),
                            )
                        }
                    }
                } else Modifier
            ),
    ) {
        Surface(
            modifier       = Modifier.fillMaxSize(),
            shape          = ExtraLargeExpressiveShape,
            color          = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            tonalElevation = cardElevation,
            shadowElevation = if (performanceMode) 0.dp else cardElevation,
            border         = if (!isConverting) {
                BorderStroke(
                    1.5.dp,
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                        ),
                    ),
                )
            } else null,
        ) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .bouncyClick(
                        enabled  = !isConverting,
                        onClick  = {
                            vibrationManager?.vibrateClick()
                            onSelectFile()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState  = isConverting,
                    transitionSpec = {
                        (fadeIn(tween(400)) + scaleIn(initialScale = 0.88f, animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness    = Spring.StiffnessMediumLow,
                        ))).togetherWith(fadeOut(tween(220)))
                    },
                    label = "dropZoneState",
                ) { converting ->
                    if (converting) {
                        ConvertingContent(
                            progress        = progress,
                            animatedProgress = animatedProgress,
                            conversionType  = conversionType,
                            fileCount       = fileCount,
                        )
                    } else {
                        IdleDropContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleDropContent() {
    val primary = MaterialTheme.colorScheme.primary
    val infiniteTransition = rememberInfiniteTransition(label = "idleIcon")
    val iconBobble by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -8f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = EaseInOutSine), RepeatMode.Reverse,
        ),
        label = "iconBobble",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier            = Modifier.padding(40.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { translationY = iconBobble }
                .background(primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.FileUpload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint     = primary,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text       = "Select Media",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text      = "Tap to pick files · Multiple supported",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ConvertingContent(
    progress: Int,
    animatedProgress: Float,
    conversionType: ConversionEngine.ConversionType?,
    fileCount: Int,
) {
    val primary     = MaterialTheme.colorScheme.primary
    val secondary   = MaterialTheme.colorScheme.secondary

    // Animated percentage text
    val infiniteTransition = rememberInfiniteTransition(label = "converting")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "dots",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier            = Modifier.padding(32.dp),
    ) {
        // Circular progress with percentage overlay
        Box(contentAlignment = Alignment.Center) {
            if (progress > 0) {
                ExpressiveCircularProgressIndicator(
                    progress   = { animatedProgress },
                    modifier   = Modifier.size(112.dp),
                    color      = primary,
                    trackColor = primary.copy(alpha = 0.1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "$progress%",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color      = primary,
                    )
                    conversionType?.let {
                        Text(
                            text      = "→ ${it.extension.uppercase()}",
                            style     = MaterialTheme.typography.labelSmall,
                            color     = secondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                ExpressiveLoadingWheel(
                    modifier = Modifier.size(88.dp),
                    color    = primary,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text       = "Processing${".".repeat(dotCount.toInt().coerceIn(0, 3))}",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text  = if (fileCount > 1) "$fileCount files in queue" else "Optimising output…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            if (progress > 0) {
                ExpressiveWavyLinearProgressIndicator(
                    progress   = { animatedProgress },
                    modifier   = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .height(5.dp),
                    color      = primary,
                    trackColor = primary.copy(alpha = 0.12f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Format Category Grid
// ─────────────────────────────────────────────────────────────────────────────

private data class FormatCategory(
    val icon: ImageVector,
    val label: String,
    val color: Color,
)

private val formatCategories = listOf(
    FormatCategory(Icons.Rounded.Movie,      "Video",   Color(0xFFFF5722)),
    FormatCategory(Icons.Rounded.MusicNote,  "Audio",   Color(0xFF2196F3)),
    FormatCategory(Icons.Rounded.Image,      "Image",   Color(0xFF4CAF50)),
    FormatCategory(Icons.Rounded.Description,"Docs",    Color(0xFFFFC107)),
    FormatCategory(Icons.Rounded.Animation,  "GIF",     Color(0xFFE91E63)),
    FormatCategory(Icons.Rounded.MoreHoriz,  "More",    Color(0xFF9C27B0)),
)

@Composable
private fun FormatCategoryGrid(
    onShowAllFormats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = "Supported types",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                onClick    = onShowAllFormats,
                shape      = RoundedCornerShape(10.dp),
                color      = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.GridOn, null,
                        modifier = Modifier.size(12.dp),
                        tint     = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "All formats",
                        style      = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // 3 + 3 grid layout
        val rows = formatCategories.chunked(3)
        rows.forEachIndexed { rowIdx, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEachIndexed { colIdx, cat ->
                    StaggeredEntrance(index = rowIdx * 3 + colIdx) {
                        FormatCategoryChip(
                            category = cat,
                            onClick  = onShowAllFormats,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatCategoryChip(
    category: FormatCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vibrationManager = LocalVibrationManager.current
    ExpressiveCard(
        onClick   = {
            vibrationManager?.vibrateTick()
            onClick()
        },
        modifier  = modifier,
        shape     = MediumExpressiveShape,
        containerColor = category.color.copy(alpha = 0.09f),
        border    = BorderStroke(1.dp, category.color.copy(alpha = 0.18f)),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(category.color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    category.icon, null,
                    modifier = Modifier.size(20.dp),
                    tint     = category.color,
                )
            }
            Text(
                text       = category.label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color      = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Success View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuccessView(
    outputPath: String,
    category: String,
    onReset: () -> Unit,
    performanceMode: Boolean,
) {
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current

    // Spring pop-in on entry
    var entered by remember { mutableStateOf(false) }
    val entryScale by animateFloatAsState(
        targetValue   = if (entered) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "successEntry",
    )
    LaunchedEffect(Unit) { entered = true }

    // Infinite glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "successGlow")
    val glowScale by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 1.55f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
            label = "glowScale",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val glowAlpha by if (!performanceMode) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
            label = "glowAlpha",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val successGreen = Color(0xFF4CAF50)

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = entryScale; scaleY = entryScale },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {

        // Success icon with glow ring
        Box(
            modifier         = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Expanding glow ring
            if (!performanceMode) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                        .background(successGreen.copy(alpha = glowAlpha), CircleShape),
                )
            }

            // Steady inner glow
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(successGreen.copy(alpha = 0.12f), CircleShape),
            )

            // Check icon circle
            Surface(
                modifier = Modifier.size(84.dp),
                shape    = CircleShape,
                color    = successGreen.copy(alpha = 0.15f),
                border   = BorderStroke(2.dp, successGreen.copy(alpha = 0.7f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Check, null,
                        modifier = Modifier.size(44.dp),
                        tint     = successGreen,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text       = "Done!",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color      = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = "Saved to Toolz / $category",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )

        Spacer(Modifier.height(24.dp))

        // Output file card
        val fileName = outputPath.substringAfterLast("/")
        ExpressiveCard(
            onClick = {
                vibrationManager?.vibrateClick()
                try {
                    val file = File(outputPath)
                    val uri  = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open File"))
                } catch (_: Exception) { }
            },
            modifier  = Modifier.fillMaxWidth(),
            shape     = MediumExpressiveShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            elevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.InsertDriveFile, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = fileName,
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text  = "Tap to preview in app",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew, null,
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Share button
        ToolzOutlinedExpressiveButton(
            onClick  = {
                vibrationManager?.vibrateClick()
                try {
                    val file = File(outputPath)
                    val uri  = FileProvider.getUriForFile(context, "com.frerox.toolz.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = context.contentResolver.getType(uri) ?: "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share File"))
                } catch (_: Exception) { }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = SmallExpressiveShape,
        ) {
            Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share File", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        // Convert another
        ToolzExpressiveButton(
            onClick  = {
                vibrationManager?.vibrateClick()
                onReset()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape    = SmallExpressiveShape,
        ) {
            Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text       = "Convert Another File",
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Error Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vibrationManager = LocalVibrationManager.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape    = MediumExpressiveShape,
        color    = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.ErrorOutline, null,
                tint     = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text      = message,
                color     = MaterialTheme.colorScheme.error,
                style     = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier  = Modifier.weight(1f),
            )
            IconButton(
                onClick  = {
                    vibrationManager?.vibrateTick()
                    onDismiss()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Rounded.Close, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Conversion Type Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConversionTypeSheet(
    uri: Uri,
    fileCount: Int = 1,
    onDismiss: () -> Unit,
    onTypeSelected: (ConversionEngine.ConversionType) -> Unit,
) {
    val context          = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val mimeType         = remember(uri) { context.contentResolver.getType(uri) ?: "" }
    var searchQuery      by remember { mutableStateOf("") }
    var selectedCatIdx   by remember { mutableIntStateOf(0) }
    val sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val availableTypes = remember(mimeType) {
        ConversionEngine.ConversionType.entries.filter { type ->
            when {
                mimeType.startsWith("video")            -> type.name.startsWith("VIDEO_TO_")
                mimeType.startsWith("audio")            -> type.name.startsWith("AUDIO_TO_")
                mimeType.startsWith("image")            -> type.name.startsWith("IMAGE_TO_")
                mimeType.startsWith("application/pdf")  -> type.name.startsWith("PDF_TO_")
                else                                    -> true
            }
        }
    }

    // Unique categories in available types
    val categories = remember(availableTypes) {
        listOf("All") + availableTypes.map { it.category }.distinct().sorted()
    }

    val filteredTypes = remember(searchQuery, selectedCatIdx, availableTypes, categories) {
        val catFilter = categories.getOrNull(selectedCatIdx)
        availableTypes
            .filter { type ->
                val matchesCat = catFilter == null || catFilter == "All" || type.category == catFilter
                val matchesSearch = searchQuery.isBlank() ||
                        type.extension.contains(searchQuery, ignoreCase = true) ||
                        type.category.contains(searchQuery, ignoreCase = true)
                matchesCat && matchesSearch
            }
            .sortedWith(compareByDescending<ConversionEngine.ConversionType> { it.isPopular }.thenBy { it.extension })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Header
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text       = "Choose Output Format",
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color      = MaterialTheme.colorScheme.onSurface,
                        )
                        if (fileCount > 1) {
                            Text(
                                text  = "$fileCount files selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                    }
                }

                // Search field
                OutlinedTextField(
                    value            = searchQuery,
                    onValueChange    = { searchQuery = it },
                    modifier         = Modifier.fillMaxWidth(),
                    placeholder      = { Text("Search formats — mp4, wav, webp…") },
                    leadingIcon      = {
                        Icon(Icons.Rounded.Search, null, Modifier.size(20.dp))
                    },
                    trailingIcon     = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                            }
                        }
                    } else null,
                    shape            = RoundedCornerShape(18.dp),
                    singleLine       = true,
                    keyboardOptions  = KeyboardOptions(imeAction = ImeAction.Search),
                    colors           = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                // Category filter tabs
                if (categories.size > 1 && searchQuery.isBlank()) {
                    ToolzConnectedButtonGroup(
                        selectedIndex    = selectedCatIdx,
                        options          = categories,
                        onOptionSelected = {
                            vibrationManager?.vibrateTick()
                            selectedCatIdx = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Format list with fading edges
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fadingEdges(top = 8.dp, bottom = 24.dp),
            ) {
                if (filteredTypes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Search, null,
                                modifier = Modifier.size(48.dp),
                                tint     = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No formats found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier        = Modifier.fillMaxSize(),
                        contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val popularTypes   = filteredTypes.filter { it.isPopular && searchQuery.isBlank() }
                        val remainingTypes = filteredTypes.filter { !it.isPopular || searchQuery.isNotBlank() }

                        if (popularTypes.isNotEmpty()) {
                            item {
                                SectionLabel("⭐  Popular")
                            }
                            items(popularTypes, key = { it.name }) { type ->
                                TypeOptionItem(
                                    type    = type,
                                    onClick = { onTypeSelected(type) },
                                )
                            }
                        }

                        val grouped = remainingTypes.groupBy { it.category }
                        grouped.forEach { (cat, types) ->
                            item(key = "section_$cat") {
                                SectionLabel(cat.uppercase())
                            }
                            items(types, key = { it.name }) { type ->
                                TypeOptionItem(
                                    type    = type,
                                    onClick = { onTypeSelected(type) },
                                )
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  All Formats Sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AllFormatsSheet(onDismiss: () -> Unit) {
    val vibrationManager = LocalVibrationManager.current
    var searchQuery     by remember { mutableStateOf("") }
    var selectedCatIdx  by remember { mutableIntStateOf(0) }
    val sheetState      = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allTypes = remember { ConversionEngine.ConversionType.entries.toList() }
    val categories = remember(allTypes) {
        listOf("All") + allTypes.map { it.category }.distinct().sorted()
    }

    val filteredTypes = remember(searchQuery, selectedCatIdx, categories) {
        val catFilter = categories.getOrNull(selectedCatIdx)
        allTypes
            .filter { type ->
                val matchesCat = catFilter == null || catFilter == "All" || type.category == catFilter
                val matchesSearch = searchQuery.isBlank() ||
                        type.extension.contains(searchQuery, ignoreCase = true) ||
                        type.category.contains(searchQuery, ignoreCase = true) ||
                        type.name.contains(searchQuery, ignoreCase = true)
                matchesCat && matchesSearch
            }
            .sortedWith(compareByDescending<ConversionEngine.ConversionType> { it.isPopular }.thenBy { it.extension })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
        shape            = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text       = "All Supported Formats",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                    }
                }

                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text("Search all formats…") },
                    leadingIcon   = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) },
                    trailingIcon  = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Close, null) } }
                    } else null,
                    shape         = RoundedCornerShape(18.dp),
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                if (categories.size > 1 && searchQuery.isBlank()) {
                    ToolzConnectedButtonGroup(
                        selectedIndex    = selectedCatIdx,
                        options          = categories,
                        onOptionSelected = {
                            vibrationManager?.vibrateTick()
                            selectedCatIdx = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fadingEdges(top = 8.dp, bottom = 24.dp)) {
                LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (filteredTypes.isEmpty()) {
                        item {
                            Box(
                                modifier         = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No formats found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    } else {
                        val grouped = filteredTypes.groupBy { it.category }
                        grouped.forEach { (cat, types) ->
                            item(key = "all_section_$cat") {
                                SectionLabel(cat.uppercase())
                            }
                            items(
                                items = types,
                                key   = { "all_${it.name}" },
                            ) { type ->
                                AllFormatRow(type = type)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Type Option Item (in picker sheet)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TypeOptionItem(
    type: ConversionEngine.ConversionType,
    onClick: () -> Unit,
) {
    val vibrationManager = LocalVibrationManager.current
    val color            = categoryColor(type.category)
    val icon             = categoryIcon(type.category)

    val scale by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "typeItemScale",
    )

    Surface(
        onClick = {
            vibrationManager?.vibrateClick()
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape    = LargeExpressiveShape,
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text       = "→ ${type.extension.uppercase()}",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    if (type.isPopular) {
                        Icon(
                            Icons.Rounded.Star, null,
                            tint     = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text  = type.category + " · .${type.extension}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  All Format Row (in AllFormatsSheet — read-only, no click)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AllFormatRow(type: ConversionEngine.ConversionType) {
    val color = categoryColor(type.category)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = MediumExpressiveShape,
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
            Text(
                text       = type.extension.uppercase(),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier.width(52.dp),
            )
            if (type.isPopular) {
                Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
            }
            Text(
                text     = type.name
                    .replace("_", " ")
                    .lowercase(Locale.getDefault())
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Section Label
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color      = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier   = Modifier.padding(start = 4.dp, bottom = 2.dp, top = 8.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun categoryColor(category: String): Color = when (category) {
    "Videos"     -> Color(0xFFFF5722)
    "Audio"      -> Color(0xFF2196F3)
    "Images"     -> Color(0xFF4CAF50)
    "Documents"  -> Color(0xFFFFC107)
    "Animations" -> Color(0xFFE91E63)
    else         -> MaterialTheme.colorScheme.primary
}

private fun categoryIcon(category: String): ImageVector = when (category) {
    "Audio"      -> Icons.Rounded.MusicNote
    "Animations" -> Icons.Rounded.Animation
    "Images"     -> Icons.Rounded.Image
    "Documents"  -> Icons.Rounded.Description
    else         -> Icons.Rounded.Movie
}

// ─────────────────────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────────────────────

@PreviewLightDark
@Composable
private fun QualityCardPreview() {
    ToolzTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            QualityToggleCard(highQuality = true, onToggle = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun DropZoneIdlePreview() {
    ToolzTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DropZoneCard(
                isConverting    = false,
                progress        = 0,
                conversionType  = null,
                fileCount       = 1,
                performanceMode = false,
                onSelectFile    = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DropZoneConvertingPreview() {
    ToolzTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DropZoneCard(
                isConverting    = true,
                progress        = 62,
                conversionType  = null,
                fileCount       = 3,
                performanceMode = false,
                onSelectFile    = {},
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FormatCategoryGridPreview() {
    ToolzTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FormatCategoryGrid(onShowAllFormats = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorBannerPreview() {
    ToolzTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ErrorBanner(
                message   = "Conversion failed: unsupported codec in source file",
                onDismiss = {},
            )
        }
    }
}