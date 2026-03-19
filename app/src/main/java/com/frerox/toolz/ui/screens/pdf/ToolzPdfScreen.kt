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
import com.frerox.toolz.ui.components.MarkdownSegment
import com.frerox.toolz.ui.components.parseMarkdownToSegments
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: (Int) -> Unit = {},
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfFiles      by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val activeTabId   by viewModel.activeTabId.collectAsStateWithLifecycle()
    val docState      by viewModel.docState.collectAsStateWithLifecycle()
    val ocrData       by viewModel.ocrData.collectAsStateWithLifecycle()
    val openTabs      by viewModel.openTabs.collectAsStateWithLifecycle()
    val performanceMode = LocalPerformanceMode.current
    val activeTab     = openTabs.find { it.id == activeTabId }
    var showTextSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = if (uiState is PdfUiState.Viewer)
                                activeTab?.title ?: "VIEWER"
                            else "PDF VAULT",
                            transitionSpec = {
                                if (performanceMode) fadeIn() togetherWith fadeOut()
                                else (slideInVertically { it } + fadeIn()) togetherWith
                                        (slideOutVertically { -it } + fadeOut())
                            },
                            label = "title",
                        ) { title ->
                            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick  = { if (uiState is PdfUiState.Viewer) viewModel.closeViewer() else onNavigateBack() },
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
                    },
                    actions = {
                        if (uiState is PdfUiState.Viewer) {
                            AnimatedVisibility(visible = ocrData != null,
                                enter = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                                exit  = fadeOut() + scaleOut()) {
                                IconButton(onClick = { showTextSheet = true }) {
                                    Icon(Icons.AutoMirrored.Rounded.Notes, "Extracted Text")
                                }
                            }
                            Surface(
                                onClick = { viewModel.updateLastTool(PdfToolMode.OCR) },
                                shape   = RoundedCornerShape(12.dp),
                                color   = if (activeTab?.lastTool == PdfToolMode.OCR) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (activeTab?.lastTool == PdfToolMode.OCR) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 12.dp).size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(if (activeTab?.lastTool == PdfToolMode.OCR) Icons.Rounded.AutoAwesome
                                    else Icons.Rounded.DocumentScanner,
                                        contentDescription = "OCR", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    if (performanceMode) fadeIn() togetherWith fadeOut()
                    else (fadeIn(tween(500)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(350))
                },
                label = "pdfState",
            ) { state ->
                when (state) {
                    is PdfUiState.Loading, is PdfUiState.Idle -> PdfLoadingScreen()
                    is PdfUiState.Viewer  -> ViewerContent(viewModel, docState, activeTab, ocrData, performanceMode)
                    else                  -> PdfFileListContent(pdfFiles,
                        onFileClick   = { viewModel.openPdf(it.uri, it.name) },
                        onDeleteClick = { viewModel.deleteFile(it) })
                }
            }
            activeTab?.let { tab ->
                if (tab.isOcrActive) OcrProgressOverlay(tab.ocrProgress, performanceMode)
            }
        }
        if (showTextSheet && ocrData != null) {
            OcrTextBottomSheet(
                ocrData         = ocrData!!,
                viewModel       = viewModel,
                onDismiss       = { showTextSheet = false; viewModel.clearSummary() },
                performanceMode = performanceMode,
            )
        }
    }
}

@Composable
private fun ViewerContent(
    viewModel: PdfViewModel, docState: DocumentState,
    activeTab: PdfWorkspaceTab?, ocrData: OcrDocumentData?, performanceMode: Boolean,
) {
    var scale  by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisible) { viewModel.updatePage(firstVisible) }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.07f))) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        Box(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(1f, if (performanceMode) 4f else 12f)
                        val sc = ns / scale
                        val focal = centroid - Offset(maxW / 2, maxH / 2)
                        val raw   = (offset + pan) * sc + focal * (1f - sc)
                        scale  = ns
                        offset = if (scale > 1f) {
                            val mx = (maxW * (scale - 1)) / 2f; val my = (maxH * (scale - 1)) / 2f
                            Offset(raw.x.coerceIn(-mx, mx), raw.y.coerceIn(-my, my))
                        } else Offset.Zero
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { c ->
                        if (scale > 1.1f) { scale = 1f; offset = Offset.Zero }
                        else {
                            scale = 3f
                            val f  = c - Offset(maxW / 2, maxH / 2)
                            val r  = -f * (3f - 1f)
                            val mx = (maxW * 2f) / 2f; val my = (maxH * 2f) / 2f
                            offset = Offset(r.x.coerceIn(-mx, mx), r.y.coerceIn(-my, my))
                        }
                    })
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentPadding = PaddingValues(vertical = 40.dp, horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                userScrollEnabled = scale <= 1.1f,
            ) {
                items(docState.totalPages) { i ->
                    PageContainer(i, viewModel, ocrData?.pages?.find { it.pageIndex == i },
                        activeTab?.lastTool ?: PdfToolMode.NAVIGATE, performanceMode)
                }
            }
        }
        AnimatedVisibility(
            visible  = docState.totalPages > 0,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).zIndex(1f),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
                shape = RoundedCornerShape(28.dp), tonalElevation = 8.dp,
                modifier = Modifier.shadow(if (performanceMode) 0.dp else 20.dp, RoundedCornerShape(28.dp)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.2f)),
            ) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoStories, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("PAGE ${docState.currentPageIndex + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(" / ${docState.totalPages}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
            }
        }
    }
}

@Composable
private fun PageContainer(
    pageIndex: Int, viewModel: PdfViewModel, ocrPageData: OcrPageData?,
    activeTool: PdfToolMode, performanceMode: Boolean,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pageIndex) { bitmap = viewModel.getPageBitmap(pageIndex) }
    val alpha by animateFloatAsState(if (bitmap != null) 1f else 0.3f,
        if (performanceMode) snap() else tween(700), label = "pAlpha")
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(0.707f).graphicsLayer { this.alpha = alpha }
            .shadow(if (performanceMode || bitmap == null) 0.dp else 12.dp, RoundedCornerShape(20.dp), false),
        color = Color.White, shape = RoundedCornerShape(20.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            bitmap?.let { bmp ->
                Image(bmp.asImageBitmap(), "Page ${pageIndex+1}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (activeTool == PdfToolMode.OCR && ocrPageData != null)
                    OcrOverlay(ocrPageData, bmp.width, bmp.height)
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (performanceMode) Text("PAGE ${pageIndex+1}", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary.copy(0.25f))
                else CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary.copy(0.2f), strokeCap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun OcrOverlay(ocrPageData: OcrPageData, bitmapWidth: Int, bitmapHeight: Int) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scaleX  = with(density) { maxWidth.toPx()  } / bitmapWidth
        val scaleY  = with(density) { maxHeight.toPx() } / bitmapHeight
        ocrPageData.blocks.forEach { block ->
            Box(
                Modifier
                    .offset { IntOffset((block.left * scaleX).roundToInt(), (block.top * scaleY).roundToInt()) }
                    .size(with(density) { ((block.right - block.left) * scaleX).toDp() },
                        with(density) { ((block.bottom - block.top) * scaleY).toDp() })
                    .background(MaterialTheme.colorScheme.primary.copy(0.12f))
                    .clickable {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("OCR", block.text))
                        Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                    }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrTextBottomSheet(
    ocrData: OcrDocumentData, viewModel: PdfViewModel,
    onDismiss: () -> Unit, performanceMode: Boolean,
) {
    val context       = LocalContext.current
    val pdfSummary    by viewModel.pdfSummary.collectAsStateWithLifecycle()
    val isSummarizing by viewModel.isSummarizing.collectAsStateWithLifecycle()
    var textSize      by remember { mutableFloatStateOf(16f) }

    val allText = remember(ocrData) {
        ocrData.pages.joinToString("\n\n") { page ->
            if (page.fullText != null) page.fullText else {
                "--- PAGE ${page.pageIndex + 1} ---\n" + page.blocks.joinToString("\n") { it.text }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape            = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f), CircleShape))
            }
        },
        modifier = Modifier.padding(top = 16.dp) // Avoid hitting the very top
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {

            // Header
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("EXTRACTED TEXT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("${ocrData.pages.size} pages scanned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                }
                IconButton(
                    onClick  = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("PDF Text", allText))
                        Toast.makeText(context, "All text copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                ) { Icon(Icons.Rounded.ContentCopy, "Copy All", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }

            // AI Summarize section
            AnimatedContent(
                targetState = when { isSummarizing -> "loading"; pdfSummary != null -> "done"; else -> "idle" },
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "sumState",
            ) { state ->
                when (state) {
                    "loading" -> Surface(
                        color  = MaterialTheme.colorScheme.tertiaryContainer.copy(0.35f),
                        shape  = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.tertiary)
                            Text("Summarising with AI…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.Black)
                        }
                    }
                    "done" -> Surface(
                        color  = MaterialTheme.colorScheme.tertiaryContainer.copy(0.25f), shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(0.2f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Text("AI SUMMARY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.clearSummary() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text("Dismiss", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f), fontWeight = FontWeight.Black)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            
                            val segments = remember(pdfSummary) { parseMarkdownToSegments(pdfSummary ?: "") }
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    segments.forEach { seg -> MarkdownSegment(seg) }
                                }
                            }
                            
                            Spacer(Modifier.height(10.dp))
                            Text("Groq · llama-3.3-70b-versatile", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontWeight = FontWeight.Black)
                        }
                    }
                    else -> Surface(
                        onClick  = { viewModel.summarizePdf(allText) },
                        color    = MaterialTheme.colorScheme.tertiaryContainer.copy(0.2f), shape = RoundedCornerShape(20.dp),
                        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(0.18f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Column(Modifier.weight(1f)) {
                                Text("Summarise with AI", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text("Key points via Groq llama-3.3-70b", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(0.7f), fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary.copy(0.6f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Text size slider
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.TextFields, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                    Spacer(Modifier.width(16.dp))
                    Slider(
                        value = textSize,
                        onValueChange = { textSize = it },
                        valueRange = 10f..32f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "${textSize.toInt()}sp",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(42.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Text content
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
            ) {
                LazyColumn(contentPadding = PaddingValues(20.dp), modifier = Modifier.fillMaxSize()) {
                    item {
                        SelectionContainer {
                             Text(
                                text = allText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = textSize.sp,
                                    lineHeight = (textSize * 1.55f).sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun OcrProgressOverlay(progress: Float, performanceMode: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).zIndex(10f), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerHigh), elevation = CardDefaults.cardElevation(if (performanceMode) 0.dp else 24.dp), modifier = Modifier.width(300.dp)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val inf = rememberInfiniteTransition(label = "os")
                val spinR by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2000, easing = LinearEasing)), "sp")
                val spin = if (performanceMode) 0f else spinR
                Box(Modifier.size(80.dp).clip(CircleShape).background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest))), Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(36.dp).graphicsLayer { rotationZ = spin }, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(20.dp))
                Text("AI TEXT SCAN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Processing document…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(20.dp))
                val ap by animateFloatAsState(progress, if (performanceMode) snap() else spring(Spring.StiffnessLow), label = "p")
                LinearProgressIndicator(progress = { ap }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primary.copy(0.1f), strokeCap = StrokeCap.Round)
                Spacer(Modifier.height(10.dp))
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PdfFileListContent(files: List<PdfFile>, onFileClick: (PdfFile) -> Unit, onDeleteClick: (PdfFile) -> Unit) {
    if (files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(40.dp).offset(y = (-40).dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "empty")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 0.5f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(160.dp).graphicsLayer { this.alpha = alpha },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(0.05f)
                    ) {}
                    Icon(
                        Icons.Rounded.FindInPage,
                        null,
                        Modifier.size(80.dp).alpha(0.2f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(Modifier.height(32.dp))
                Text(
                    "NO PDFS FOUND",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "We searched your device but couldn't find any PDF documents. Try downloading one or checking your downloads folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("DOCUMENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp) }
            items(files, key = { it.uri.toString() }) { file ->
                Surface(onClick = { onFileClick(file) }, modifier = Modifier.fillMaxWidth().bouncyClick { onFileClick(file) },
                    shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.2f))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(60.dp), RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (file.thumbnail != null) Image(file.thumbnail.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(3.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(0.4f), shape = RoundedCornerShape(6.dp)) {
                                    Text("${file.pageCount} pages", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Text("%.1f MB".format(file.size / 1048576.0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = { onDeleteClick(file) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error.copy(0.6f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfLoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "pdfLoading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotation },
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary.copy(0.2f),
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round
                )
                CircularProgressIndicator(
                    progress = { 0.25f },
                    modifier = Modifier.fillMaxSize(0.8f).graphicsLayer { rotationZ = -rotation * 1.5f },
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = StrokeCap.Round
                )
                Icon(
                    Icons.Rounded.PictureAsPdf,
                    null,
                    modifier = Modifier.size(32.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "SCANNING VAULT",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Locating your documents...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}