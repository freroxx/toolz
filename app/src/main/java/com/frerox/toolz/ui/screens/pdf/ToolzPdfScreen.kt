package com.frerox.toolz.ui.screens.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// PDF Reader Screen — Full M3 Expressive Redesign
//
// Architecture:
//   ToolzPdfScreen
//     ├── TopBar: ExpressiveTopAppBar with animated title + OCR toggle
//     ├── Body (AnimatedContent):
//     │     ├── PdfLoadingScreen   — WavyCircular spinner + ambient glow
//     │     ├── PdfFileListContent — StaggeredEntrance SquircleShape cards
//     │     └── ViewerContent      — Pinch-zoom pager + floating SquircleShape toolbar
//     │           ├── PageContainer  — fade-in pages with OCR overlay
//     │           └── Floating pills: page counter + tool mode switcher
//     └── Overlays: OcrProgressOverlay, TextSheet, RenameDialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: (Int) -> Unit = {},
    onNavigateToConverter: ((String, String) -> Unit)? = null,
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfFiles      by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val activeTabId   by viewModel.activeTabId.collectAsStateWithLifecycle()
    val docState      by viewModel.docState.collectAsStateWithLifecycle()
    val ocrData       by viewModel.ocrData.collectAsStateWithLifecycle()
    val openTabs      by viewModel.openTabs.collectAsStateWithLifecycle()
    val offlineMode   by viewModel.offlineModeEnabled.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    val vibrationManager = LocalVibrationManager.current
    val context       = LocalContext.current
    val activeTab     = openTabs.find { it.id == activeTabId }

    var showTextSheet     by remember { mutableStateOf(false) }
    var renamingFile      by remember { mutableStateOf<PdfFile?>(null) }
    var newFileName       by remember { mutableStateOf("") }
    var showRenameDialog  by remember { mutableStateOf(false) }
    var invertColors      by remember { mutableStateOf(false) }
    var showMenu          by remember { mutableStateOf(false) }

    val isViewer = uiState is PdfUiState.Viewer

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color.Transparent)
                    .statusBarsPadding(),
            ) {
                ExpressiveTopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = if (isViewer) activeTab?.title ?: "VIEWER" else "PDF VAULT",
                            transitionSpec = {
                                if (performanceMode) fadeIn() togetherWith fadeOut()
                                else (slideInVertically { it } + fadeIn()) togetherWith
                                        (slideOutVertically { -it } + fadeOut())
                            },
                            label = "pdfTitle",
                        ) { title ->
                            Text(
                                text = title.removeSuffix(".pdf").uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        ToolzExpressiveIconButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                if (isViewer) viewModel.closeViewer() else onNavigateBack()
                            },
                            modifier = Modifier.padding(start = 8.dp).size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            shape = MediumExpressiveShape,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, modifier = Modifier.size(20.dp))
                        }
                    },
                    actions = {
                        // OCR text button — only shown in viewer with OCR data
                        AnimatedVisibility(
                            visible = isViewer && ocrData != null,
                            enter = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            ToolzExpressiveIconButton(
                                onClick = {
                                    vibrationManager?.vibrateClick()
                                    showTextSheet = true
                                },
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                                shape = MediumExpressiveShape,
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Notes, "Extracted Text", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        // OCR mode toggle — only shown in viewer and when online
                        if (isViewer && !offlineMode) {
                            val ocrActive = activeTab?.lastTool == PdfToolMode.OCR
                            ToolzExpressiveIconButton(
                                onClick = {
                                    vibrationManager?.vibrateTick()
                                    viewModel.updateLastTool(
                                        if (ocrActive) PdfToolMode.NAVIGATE else PdfToolMode.OCR,
                                    )
                                },
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (ocrActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                shape = MediumExpressiveShape,
                            ) {
                                Icon(
                                    imageVector = if (ocrActive) Icons.Rounded.AutoAwesome else Icons.Rounded.DocumentScanner,
                                    contentDescription = "OCR",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (ocrActive) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Overflow menu — only in viewer
                        if (isViewer) {
                            Box {
                                ToolzExpressiveIconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.padding(end = 8.dp).size(40.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ),
                                    shape = MediumExpressiveShape,
                                ) {
                                    Icon(Icons.Rounded.MoreVert, "More Options", modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    offset = DpOffset(0.dp, 8.dp),
                                    shape = LargeExpressiveShape,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    ExpressiveDropdownItem(
                                        text = "Share PDF",
                                        icon = Icons.Rounded.Share,
                                        onClick = {
                                            showMenu = false
                                            activeTab?.uri?.let { viewModel.sharePdf(context, it, activeTab.title) }
                                            vibrationManager?.vibrateClick()
                                        },
                                    )
                                    ExpressiveDropdownItem(
                                        text = "Print",
                                        icon = Icons.Rounded.Print,
                                        onClick = {
                                            showMenu = false
                                            activeTab?.uri?.let { viewModel.printPdf(context, it, activeTab.title) }
                                            vibrationManager?.vibrateClick()
                                        },
                                    )
                                    if (onNavigateToConverter != null) {
                                        ExpressiveDropdownItem(
                                            text = "Convert",
                                            icon = Icons.Rounded.Transform,
                                            onClick = {
                                                showMenu = false
                                                activeTab?.uri?.let { uri ->
                                                    onNavigateToConverter(uri.toString(), activeTab.title)
                                                }
                                                vibrationManager?.vibrateClick()
                                            },
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    ExpressiveDropdownItem(
                                        text = "Rename",
                                        icon = Icons.Rounded.Edit,
                                        onClick = {
                                            showMenu = false
                                            activeTab?.let { tab ->
                                                renamingFile = pdfFiles.find { it.uri == tab.uri }
                                                newFileName = tab.title.removeSuffix(".pdf")
                                                showRenameDialog = true
                                            }
                                            vibrationManager?.vibrateTick()
                                        },
                                    )
                                    ExpressiveDropdownItem(
                                        text = if (invertColors) "Light Mode" else "Dark Mode",
                                        icon = if (invertColors) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        onClick = {
                                            showMenu = false
                                            invertColors = !invertColors
                                            vibrationManager?.vibrateTick()
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                    titleHorizontalAlignment = Alignment.CenterHorizontally,
                )
            }
        },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(paddingValues),
        ) {
            // ── Main content switch ────────────────────────────────────────────
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    if (performanceMode) fadeIn() togetherWith fadeOut()
                    else (fadeIn(tween(480)) + scaleIn(initialScale = 0.96f, animationSpec = tween(480))) togetherWith
                            fadeOut(tween(300))
                },
                label = "pdfUiState",
            ) { state ->
                when (state) {
                    is PdfUiState.Loading, is PdfUiState.Idle -> PdfLoadingScreen()
                    is PdfUiState.Viewer -> ViewerContent(
                        viewModel = viewModel,
                        docState = docState,
                        activeTab = activeTab,
                        ocrData = ocrData,
                        performanceMode = performanceMode,
                        invertColors = invertColors,
                    )
                    else -> PdfFileListContent(
                        files = pdfFiles,
                        onFileClick = { viewModel.openPdf(it.uri, it.name) },
                        onDeleteClick = { viewModel.deleteFile(it) },
                        onRenameClick = {
                            renamingFile = it
                            newFileName = it.name.removeSuffix(".pdf")
                            showRenameDialog = true
                        },
                        onPinClick = { viewModel.togglePin(it.uri.toString()) },
                        onSortChange = { viewModel.setSortOrder(it) },
                        currentSort = viewModel.sortOrder.collectAsStateWithLifecycle().value,
                    )
                }
            }

            // ── OCR progress overlay ──────────────────────────────────────────
            activeTab?.let { tab ->
                if (tab.isOcrActive) {
                    OcrProgressOverlay(progress = tab.ocrProgress, performanceMode = performanceMode)
                }
            }
        }

        // ── Rename dialog ──────────────────────────────────────────────────────
        if (showRenameDialog && renamingFile != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                shape = SquircleShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                title = {
                    Text("RENAME PDF", fontWeight = FontWeight.Black, letterSpacing = 1.sp, style = MaterialTheme.typography.headlineSmall)
                },
                text = {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MediumExpressiveShape,
                        label = { Text("File name") },
                        trailingIcon = {
                            if (newFileName.isNotEmpty()) {
                                IconButton(onClick = { newFileName = "" }) {
                                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                },
                confirmButton = {
                    ToolzExpressiveButton(
                        onClick = {
                            viewModel.renameFile(renamingFile!!, newFileName)
                            showRenameDialog = false
                            vibrationManager?.vibrateClick()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MediumExpressiveShape,
                    ) {
                        Text("RENAME", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRenameDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
                    }
                },
            )
        }

        // ── OCR text bottom sheet ──────────────────────────────────────────────
        if (showTextSheet && ocrData != null) {
            OcrTextBottomSheet(
                ocrData = ocrData!!,
                viewModel = viewModel,
                onDismiss = {
                    showTextSheet = false
                    viewModel.clearSummary()
                },
                performanceMode = performanceMode,
                offlineMode = offlineMode,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Expressive Dropdown Item helper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpressiveDropdownItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        modifier = Modifier.clip(MediumExpressiveShape),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PDF File List Content (internal full-screen version with sort + pin)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PdfFileListContent(
    files: List<PdfFile>,
    onFileClick: (PdfFile) -> Unit,
    onDeleteClick: (PdfFile) -> Unit,
    onRenameClick: (PdfFile) -> Unit,
    onPinClick: (PdfFile) -> Unit,
    onSortChange: (PdfSortOrder) -> Unit,
    currentSort: PdfSortOrder,
) {
    val vibrationManager = LocalVibrationManager.current

    if (files.isEmpty()) {
        PdfEmptyState()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sort control bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${files.size} FILES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp,
                modifier = Modifier.weight(1f),
            )
            ToolzConnectedButtonGroup(
                selectedIndex = currentSort.ordinal,
                options = listOf("New", "Name", "Size"),
                onOptionSelected = {
                    vibrationManager?.vibrateTick()
                    onSortChange(PdfSortOrder.entries[it])
                },
                modifier = Modifier.width(220.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .fadingEdges(top = 0.dp, bottom = 64.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(files, key = { it.uri.toString() }) { file ->
                var showFileMenu by remember { mutableStateOf(false) }
                StaggeredEntrance(index = files.indexOf(file)) {
                    Box {
                        PdfFileItem(
                            file = file,
                            onClick = { onFileClick(file) },
                            onMenuClick = {
                                vibrationManager?.vibrateTick()
                                showFileMenu = true
                            },
                        )
                        DropdownMenu(
                            expanded = showFileMenu,
                            onDismissRequest = { showFileMenu = false },
                            shape = LargeExpressiveShape,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            ExpressiveDropdownItem(
                                text = if (file.isPinned) "Unpin" else "Pin",
                                icon = Icons.Rounded.PushPin,
                                onClick = { showFileMenu = false; onPinClick(file); vibrationManager?.vibrateTick() },
                            )
                            ExpressiveDropdownItem(
                                text = "Rename",
                                icon = Icons.Rounded.Edit,
                                onClick = { showFileMenu = false; onRenameClick(file); vibrationManager?.vibrateTick() },
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            DropdownMenuItem(
                                text = { Text("Delete", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) },
                                onClick = { showFileMenu = false; onDeleteClick(file); vibrationManager?.vibrateTick() },
                                modifier = Modifier.clip(MediumExpressiveShape),
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(64.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading Screen — WavyCircular + ambient glow
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PdfLoadingScreen() {
    val performanceMode = LocalPerformanceMode.current
    val infiniteTransition = rememberInfiniteTransition(label = "pdfLoad")

    val glowPulse by if (!performanceMode) {
        infiniteTransition.animateFloat(0.7f, 1.3f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), "glow")
    } else remember { mutableFloatStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // Ambient glow
        if (!performanceMode) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .graphicsLayer { scaleX = glowPulse; scaleY = glowPulse; alpha = 0.12f }
                    .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary, Color.Transparent)), CircleShape),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                if (!performanceMode) {
                    ToolzWavyCircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(80.dp), strokeWidth = 4.dp)
                }
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = SquircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = if (performanceMode) 0.dp else 16.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
            Text(
                "SCANNING VAULT",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Locating your documents…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Viewer Content — pinch-zoom LazyColumn + floating action pills
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerContent(
    viewModel: PdfViewModel,
    docState: DocumentState,
    activeTab: PdfWorkspaceTab?,
    ocrData: OcrDocumentData?,
    performanceMode: Boolean,
    invertColors: Boolean = false,
) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisible) { viewModel.updatePage(firstVisible) }

    // Scale-animated page counter pill
    val pageCounterScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "pageCounter",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim.copy(alpha = 0.4f)),
    ) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(1f, if (performanceMode) 4f else 12f)
                        val sc = ns / scale
                        val focal = centroid - Offset(maxW / 2f, maxH / 2f)
                        val raw   = (offset + pan) * sc + focal * (1f - sc)
                        scale  = ns
                        offset = if (scale > 1f) {
                            val mx = (maxW * (scale - 1f)) / 2f
                            val my = (maxH * (scale - 1f)) / 2f
                            Offset(raw.x.coerceIn(-mx, mx), raw.y.coerceIn(-my, my))
                        } else Offset.Zero
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { c ->
                        if (scale > 1.1f) {
                            scale = 1f; offset = Offset.Zero
                        } else {
                            scale = 3f
                            val f  = c - Offset(maxW / 2f, maxH / 2f)
                            val r  = -f * (3f - 1f)
                            offset = Offset(r.x.coerceIn(-(maxW), maxW), r.y.coerceIn(-(maxH), maxH))
                        }
                    })
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                contentPadding = PaddingValues(vertical = 36.dp, horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                userScrollEnabled = scale <= 1.1f,
            ) {
                items(docState.totalPages) { i ->
                    PageContainer(
                        pageIndex = i,
                        viewModel = viewModel,
                        ocrPageData = ocrData?.pages?.find { it.pageIndex == i },
                        activeTool = activeTab?.lastTool ?: PdfToolMode.NAVIGATE,
                        performanceMode = performanceMode,
                        invertColors = invertColors,
                    )
                }
            }
        }

        // ── Floating page counter pill ─────────────────────────────────────────
        AnimatedVisibility(
            visible = docState.totalPages > 0,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .zIndex(1f),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                shape = BouncyShape,
                shadowElevation = if (performanceMode) 0.dp else 16.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.graphicsLayer { scaleX = pageCounterScale; scaleY = pageCounterScale },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = SmallExpressiveShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.AutoStories, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        text = "PAGE ${docState.currentPageIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "/ ${docState.totalPages}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    // Zoom indicator (shown when zoomed)
                    AnimatedVisibility(visible = scale > 1.1f) {
                        Surface(
                            shape = SmallExpressiveShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page Container — fade-in bitmap with OCR overlay
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageContainer(
    pageIndex: Int,
    viewModel: PdfViewModel,
    ocrPageData: OcrPageData?,
    activeTool: PdfToolMode,
    performanceMode: Boolean,
    invertColors: Boolean = false,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex) { bitmap = viewModel.getPageBitmap(pageIndex) }

    val alpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0.25f,
        animationSpec = if (performanceMode) snap() else tween(600, easing = FastOutSlowInEasing),
        label = "pageAlpha",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.707f)
            .graphicsLayer { this.alpha = alpha }
            .shadow(
                elevation = if (performanceMode || bitmap == null) 0.dp else 14.dp,
                shape = MediumExpressiveShape,
                clip = false,
            ),
        color = Color.White,
        shape = MediumExpressiveShape,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            bitmap?.let { bmp ->
                val colorFilter = if (invertColors) {
                    ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f,
                            ),
                        ),
                    )
                } else null

                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                )

                if (activeTool == PdfToolMode.OCR && ocrPageData != null) {
                    OcrOverlay(ocrPageData, bmp.width, bmp.height)
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (performanceMode) {
                    Text(
                        "PAGE ${pageIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    )
                } else {
                    ToolzWavyCircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        trackColor = Color.Transparent,
                    )
                }
            }

            // Page number badge in bottom-left corner
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                shape = SmallExpressiveShape,
                color = Color.Black.copy(alpha = 0.35f),
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OCR Text Highlight Overlay
// Each block is a tappable box that copies text to clipboard.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OcrOverlay(ocrPageData: OcrPageData, bitmapWidth: Int, bitmapHeight: Int) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scaleX = constraints.maxWidth.toFloat() / bitmapWidth
        val scaleY = constraints.maxHeight.toFloat() / bitmapHeight
        ocrPageData.blocks.forEach { block ->
            val blockType = block.type
            val highlightColor = when (blockType) {
                OcrBlockType.FORMULA    -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
                OcrBlockType.HEADER     -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                OcrBlockType.TABLE_CELL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else                    -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            }
            val borderColor = when (blockType) {
                OcrBlockType.FORMULA    -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                OcrBlockType.HEADER     -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                else                    -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (block.left * scaleX).roundToInt(),
                            (block.top * scaleY).roundToInt(),
                        )
                    }
                    .size(
                        with(density) { ((block.right - block.left) * scaleX).toDp() },
                        with(density) { ((block.bottom - block.top) * scaleY).toDp() },
                    )
                    .background(highlightColor, SmallExpressiveShape)
                    .then(
                        Modifier.clip(SmallExpressiveShape),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("OCR", block.text))
                        Toast.makeText(context, "✓ Text copied", Toast.LENGTH_SHORT).show()
                    },
            ) {
                // Border via nested box
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent, SmallExpressiveShape),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OCR Progress Overlay — WavyCircular spinner on scrim
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OcrProgressOverlay(progress: Float, performanceMode: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .zIndex(10f),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = SquircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(if (performanceMode) 0.dp else 28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier.width(300.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // WavyCircular spinner wrapping the icon
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    if (!performanceMode) {
                        ToolzWavyCircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = SquircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val infiniteTransition = rememberInfiniteTransition(label = "ocrIcon")
                            val spin by if (!performanceMode) {
                                infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), "spin")
                            } else remember { mutableFloatStateOf(0f) }
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                null,
                                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = spin },
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "AI TEXT SCAN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Processing document…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(24.dp))

                // Wavy linear progress bar
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = if (performanceMode) snap() else spring(Spring.StiffnessLow),
                    label = "ocrProgress",
                )
                ToolzWavyLinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OCR Text Bottom Sheet — full text extraction + AI summary + search
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OcrTextBottomSheet(
    ocrData: OcrDocumentData,
    viewModel: PdfViewModel,
    onDismiss: () -> Unit,
    performanceMode: Boolean,
    offlineMode: Boolean,
) {
    val context       = LocalContext.current
    val vibrationManager = LocalVibrationManager.current
    val pdfSummary    by viewModel.pdfSummary.collectAsStateWithLifecycle()
    val isSummarizing by viewModel.isSummarizing.collectAsStateWithLifecycle()
    var textSize      by remember { mutableFloatStateOf(16f) }
    var searchQuery   by remember { mutableStateOf("") }

    val allText = remember(ocrData) {
        ocrData.pages.joinToString("\n\n") { page ->
            page.fullText
                ?: "─── PAGE ${page.pageIndex + 1} ───\n${page.blocks.joinToString("\n") { it.text }}"
        }
    }
    val filteredText = remember(allText, searchQuery) {
        if (searchQuery.isBlank()) allText
        else allText.split("\n\n").filter { it.contains(searchQuery, ignoreCase = true) }.joinToString("\n\n")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp, 4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f), CircleShape),
                )
            }
        },
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            // ── Header ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "EXTRACTED TEXT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                            Text(
                                "${ocrData.pages.size} PAGES",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)) {
                            Text(
                                "${allText.split(Regex("\\s+")).count { it.isNotBlank() }} WORDS",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }

                ToolzExpressiveIconButton(
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("PDF Text", allText))
                        Toast.makeText(context, "All text copied", Toast.LENGTH_SHORT).show()
                        vibrationManager?.vibrateClick()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MediumExpressiveShape,
                ) {
                    Icon(Icons.Rounded.ContentCopy, "Copy All", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // ── Search field ───────────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = BouncyShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        ),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search in document…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            }
                            inner()
                        },
                    )
                    AnimatedVisibility(visible = searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Scrollable content ─────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fadingEdges(top = 0.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                // AI summarise block (only when online)
                if (!offlineMode) {
                    item {
                        AnimatedContent(
                            targetState = when {
                                isSummarizing -> "loading"
                                pdfSummary != null -> "done"
                                else -> "idle"
                            },
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                            label = "sumState",
                        ) { state ->
                            when (state) {
                                "loading" -> Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                    shape = LargeExpressiveShape,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        ToolzWavyCircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.tertiary,
                                            trackColor = Color.Transparent,
                                        )
                                        Text(
                                            "Summarising with AI…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }

                                "done" -> Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f),
                                    shape = LargeExpressiveShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(Modifier.padding(18.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Surface(
                                                shape = SmallExpressiveShape,
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                                                modifier = Modifier.size(24.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                                }
                                            }
                                            Text("AI SUMMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                                            TextButton(
                                                onClick = { viewModel.clearSummary() },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            ) {
                                                Text("Dismiss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), fontWeight = FontWeight.Black)
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        SelectionContainer {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                parseMarkdownToSegments(pdfSummary ?: "").forEach { seg ->
                                                    MarkdownSegment(seg, baseFontSize = textSize.sp)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            "Groq · llama-3.3-70b-versatile",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }

                                else -> Surface(
                                    onClick = { viewModel.summarizePdf(allText) },
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                                    shape = LargeExpressiveShape,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(18.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Surface(
                                            shape = MediumExpressiveShape,
                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                            modifier = Modifier.size(44.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.tertiary)
                                            }
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text("Summarise with AI", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                            Text("Key points via Groq llama-3.3-70b", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.65f), fontWeight = FontWeight.Bold)
                                        }
                                        Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Extracted text section
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                            Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.size(22.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("EXTRACTED CONTENT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                            if (searchQuery.isNotBlank()) {
                                Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
                                    Text("FILTERED", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = LargeExpressiveShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f)),
                        ) {
                            SelectionContainer {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (filteredText.isBlank() && searchQuery.isNotEmpty()) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        ) {
                                            Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(32.dp).alpha(0.4f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                "No matches found for \"$searchQuery\"",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    } else {
                                        parseMarkdownToSegments(filteredText).forEach { seg ->
                                            MarkdownSegment(seg, baseFontSize = textSize.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Text size slider — sticky bottom ───────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MediumExpressiveShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = SmallExpressiveShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    ExpressiveSlider(
                        value = textSize,
                        onValueChange = { textSize = it },
                        valueRange = 12f..32f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    )
                    Spacer(Modifier.width(14.dp))
                    Surface(
                        shape = SmallExpressiveShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = "${textSize.toInt()}sp",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}