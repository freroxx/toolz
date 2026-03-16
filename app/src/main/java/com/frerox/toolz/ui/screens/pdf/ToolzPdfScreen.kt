package com.frerox.toolz.ui.screens.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfFiles by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val docState by viewModel.docState.collectAsStateWithLifecycle()
    val ocrData by viewModel.ocrData.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current

    val activeTab = openTabs.find { it.id == activeTabId }
    var showTextSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = if (uiState is PdfViewModel.PdfUiState.Viewer) activeTab?.title ?: "VIEWER" else "PDF VAULT",
                        transitionSpec = {
                            if (performanceMode) fadeIn() togetherWith fadeOut()
                            else (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                        },
                        label = "titleTransition"
                    ) { title ->
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState is PdfViewModel.PdfUiState.Viewer) viewModel.closeViewer()
                            else onNavigateBack()
                        },
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                },
                actions = {
                    if (uiState is PdfViewModel.PdfUiState.Viewer) {
                        val currentOcrData = ocrData
                        if (currentOcrData != null) {
                            IconButton(onClick = { showTextSheet = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Notes, "Show All Text")
                            }
                        }
                        Surface(
                            onClick = { viewModel.updateLastTool(PdfToolMode.OCR) },
                            shape = CircleShape,
                            color = if (activeTab?.lastTool == PdfToolMode.OCR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            contentColor = if (activeTab?.lastTool == PdfToolMode.OCR) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 8.dp).size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (activeTab?.lastTool == PdfToolMode.OCR) Icons.Rounded.AutoAwesome else Icons.Rounded.DocumentScanner,
                                    contentDescription = "OCR Scan",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    if (performanceMode) {
                        fadeIn() togetherWith fadeOut()
                    } else {
                        (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.95f))
                            .togetherWith(fadeOut(animationSpec = tween(400)))
                    }
                },
                label = "pdfStateTransition"
            ) { state ->
                when (state) {
                    is PdfViewModel.PdfUiState.Loading -> LoadingScreen()
                    is PdfViewModel.PdfUiState.Viewer -> {
                        ViewerContent(
                            viewModel = viewModel,
                            docState = docState,
                            activeTab = activeTab,
                            ocrData = ocrData,
                            performanceMode = performanceMode
                        )
                    }
                    else -> {
                        PdfFileListContent(
                            files = pdfFiles,
                            onFileClick = { file -> viewModel.openPdf(file.uri, file.name) },
                            onDeleteClick = { file -> viewModel.deleteFile(file) }
                        )
                    }
                }
            }

            activeTab?.let { tab ->
                if (tab.isOcrActive) {
                    OcrProgressOverlay(progress = tab.ocrProgress, performanceMode = performanceMode)
                }
            }
        }

        val currentOcrDataForSheet = ocrData
        if (showTextSheet && currentOcrDataForSheet != null) {
            OcrTextBottomSheet(
                ocrData = currentOcrDataForSheet,
                onDismiss = { showTextSheet = false },
                performanceMode = performanceMode
            )
        }
    }
}

@Composable
private fun ViewerContent(
    viewModel: PdfViewModel,
    docState: DocumentState,
    activeTab: PdfWorkspaceTab?,
    ocrData: OcrDocumentData?,
    performanceMode: Boolean
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()
    
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    
    LaunchedEffect(firstVisibleItemIndex) {
        viewModel.updatePage(firstVisibleItemIndex)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
    ) {
        val constraints = this.constraints
        val maxWidth = constraints.maxWidth.toFloat()
        val maxHeight = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(performanceMode) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (scale * zoom).coerceIn(1f, if (performanceMode) 4f else 12f)
                        
                        // To keep the zoom centered on fingers:
                        // 1. Calculate how much we scaled
                        val scaleChange = newScale / oldScale
                        
                        // 2. Adjust offset to maintain focal point under fingers
                        // Offset is from center, centroid is from top-left
                        val focalPointFromCenter = centroid - Offset(maxWidth / 2, maxHeight / 2)
                        
                        val newOffset = (offset + pan) * scaleChange + focalPointFromCenter * (1f - scaleChange)
                        
                        scale = newScale
                        
                        if (scale > 1f) {
                            val maxX = (maxWidth * (scale - 1)) / 2f
                            val maxY = (maxHeight * (scale - 1)) / 2f
                            
                            offset = Offset(
                                x = newOffset.x.coerceIn(-maxX, maxX),
                                y = newOffset.y.coerceIn(-maxY, maxY)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { centroid ->
                            if (scale > 1.1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 3f
                                val focalPointFromCenter = centroid - Offset(maxWidth / 2, maxHeight / 2)
                                offset = -focalPointFromCenter * (3f - 1f)
                                
                                val maxX = (maxWidth * (3f - 1)) / 2f
                                val maxY = (maxHeight * (3f - 1)) / 2f
                                offset = Offset(
                                    x = offset.x.coerceIn(-maxX, maxX),
                                    y = offset.y.coerceIn(-maxY, maxY)
                                )
                            }
                        }
                    )
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentPadding = PaddingValues(vertical = 40.dp, horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                userScrollEnabled = scale <= 1.1f
            ) {
                items(docState.totalPages) { index ->
                    PageContainer(
                        pageIndex = index,
                        viewModel = viewModel,
                        ocrPageData = ocrData?.pages?.find { it.pageIndex == index },
                        activeTool = activeTab?.lastTool ?: PdfToolMode.NAVIGATE,
                        performanceMode = performanceMode
                    )
                }
            }
        }

        // Floating Page Indicator Pill
        AnimatedVisibility(
            visible = docState.totalPages > 0,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .zIndex(1f)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 8.dp,
                modifier = Modifier.shadow(if (performanceMode) 0.dp else 24.dp, RoundedCornerShape(32.dp)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "PAGE ${docState.currentPageIndex + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = " / ${docState.totalPages}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PageContainer(
    pageIndex: Int,
    viewModel: PdfViewModel,
    ocrPageData: OcrPageData?,
    activeTool: PdfToolMode,
    performanceMode: Boolean
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = viewModel.getPageBitmap(pageIndex)
    }

    val containerAlpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0.4f,
        animationSpec = if (performanceMode) snap() else tween(800),
        label = "pageAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.707f)
            .graphicsLayer { alpha = containerAlpha }
            .shadow(
                elevation = if (performanceMode || bitmap == null) 0.dp else 12.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false
            ),
        color = Color.White,
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                if (activeTool == PdfToolMode.OCR && ocrPageData != null) {
                    OcrOverlay(ocrPageData, it.width, it.height)
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (performanceMode) {
                    Text(
                        "PAGE ${pageIndex + 1}", 
                        style = MaterialTheme.typography.labelMedium, 
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrOverlay(
    ocrPageData: OcrPageData,
    bitmapWidth: Int,
    bitmapHeight: Int
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scaleX = with(density) { maxWidth.toPx() } / bitmapWidth
        val scaleY = with(density) { maxHeight.toPx() } / bitmapHeight

        ocrPageData.blocks.forEach { block ->
            val left = block.left * scaleX
            val top = block.top * scaleY
            val width = (block.right - block.left) * scaleX
            val height = (block.bottom - block.top) * scaleY

            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(width = with(density) { width.toDp() }, height = with(density) { height.toDp() })
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", block.text))
                        Toast.makeText(context, "Text captured", Toast.LENGTH_SHORT).show()
                    }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTextBottomSheet(
    ocrData: OcrDocumentData,
    onDismiss: () -> Unit,
    performanceMode: Boolean
) {
    val context = LocalContext.current
    var textSize by remember { mutableFloatStateOf(16f) }
    
    val allText = remember(ocrData) {
        ocrData.pages.joinToString("\n\n") { page ->
            "--- PAGE ${page.pageIndex + 1} ---\n" + page.blocks.joinToString("\n") { it.text }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EXTRACTED TEXT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Scanned ${ocrData.pages.size} pages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("All Extracted Text", allText))
                        Toast.makeText(context, "Full document text copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(Icons.Rounded.ContentCopy, "Copy All", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Text Size Slider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                Slider(
                    value = textSize,
                    onValueChange = { textSize = it },
                    valueRange = 10f..32f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                Text(
                    text = "${textSize.toInt()}PX",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(40.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = allText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = textSize.sp,
                                lineHeight = (textSize * 1.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun OcrProgressOverlay(progress: Float, performanceMode: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .zIndex(10f),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(48.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (performanceMode) 0.dp else 32.dp),
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(Modifier.height(32.dp))
                
                Text(
                    "INTELLIGENT OCR",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "Processing document content...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(32.dp))

                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = if (performanceMode) snap() else spring(stiffness = Spring.StiffnessLow),
                    label = "ocrProgress"
                )
                
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    strokeCap = StrokeCap.Round
                )

                Spacer(Modifier.height(20.dp))
                
                Text(
                    text = "${(progress * 100).toInt()}% COMPLETE",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun PdfFileListContent(
    files: List<PdfFile>,
    onFileClick: (PdfFile) -> Unit,
    onDeleteClick: (PdfFile) -> Unit
) {
    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Description,
                    null,
                    modifier = Modifier.size(80.dp).alpha(0.1f),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
                Text("No PDF files found", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "ALL DOCUMENTS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
            }
            items(files, key = { it.uri.toString() }) { file ->
                PdfFileItemCard(
                    file = file,
                    onClick = { onFileClick(file) },
                    onDelete = { onDeleteClick(file) }
                )
            }
        }
    }
}

@Composable
private fun PdfFileItemCard(
    file: PdfFile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (file.thumbnail != null) {
                        Image(
                            bitmap = file.thumbnail.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.pageCount} PAGES • ${"%.2f".format(file.size / 1024.0 / 1024.0)} MB",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            strokeCap = StrokeCap.Round, 
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
            strokeWidth = 6.dp
        )
    }
}
