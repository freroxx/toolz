package com.frerox.toolz.ui.screens.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.ui.components.fadingEdge
import com.frerox.toolz.ui.components.bouncyClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfFiles by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val nightProfile by viewModel.nightProfile.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val docState by viewModel.docState.collectAsStateWithLifecycle()
    val extractedText by viewModel.extractedText.collectAsStateWithLifecycle()
    val ocrData by viewModel.ocrData.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showBottomSheet by remember { mutableStateOf<PdfFile?>(null) }
    var showRenameDialog by remember { mutableStateOf<PdfFile?>(null) }
    var showDetailsDialog by remember { mutableStateOf<PdfFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var deletingFileId by remember { mutableStateOf<Uri?>(null) }
    var showOcrResult by remember { mutableStateOf(false) }
    var internalSearchQuery by remember { mutableStateOf("") }
    var isSearchingInDoc by remember { mutableStateOf(false) }

    val activeTab = openTabs.find { it.id == activeTabId }
    val isAmoled = nightProfile == NightProfile.AMOLED_BLACK

    Scaffold(
        containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.background,
        topBar = {
            if (uiState !is PdfViewModel.PdfUiState.Viewer) {
                PdfListTopBar(
                    onNavigateBack = onNavigateBack,
                    sortOrder = sortOrder,
                    onSortOrderChange = viewModel::setSortOrder,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { 
                        searchQuery = it
                        viewModel.setSearchQuery(it)
                    },
                    openTabs = openTabs,
                    activeTabId = activeTabId,
                    onTabSwitch = viewModel::switchTab,
                    onTabClose = viewModel::closeTab,
                    isAmoled = isAmoled
                )
            } else {
                ViewerTopBar(
                    title = activeTab?.title ?: "PDF Viewer",
                    viewModel = viewModel,
                    docState = docState,
                    isSearching = isSearchingInDoc,
                    searchQuery = internalSearchQuery,
                    onSearchQueryChange = { 
                        internalSearchQuery = it
                        viewModel.searchInDocument(it)
                    },
                    onToggleSearch = { 
                        isSearchingInDoc = !isSearchingInDoc
                        if (!isSearchingInDoc) {
                            internalSearchQuery = ""
                            viewModel.searchInDocument("")
                        }
                    },
                    onBack = viewModel::closeViewer,
                    isAmoled = isAmoled
                )
            }
        },
        bottomBar = {
            if (uiState is PdfViewModel.PdfUiState.Viewer) {
                Surface(
                    color = if (isAmoled) Color.Black.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp),
                    border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
                ) {
                    Column {
                        if (activeTab?.lastTool == PdfToolMode.TEXT_SELECT) {
                            TextSelectToolbar(
                                currentLanguage = activeTab.ocrLanguage,
                                onLanguageSelect = { viewModel.setOcrLanguage(it) },
                                onViewFullText = { showOcrResult = true },
                                onResetOcr = { viewModel.resetOcr() },
                                isAmoled = isAmoled
                            )
                        }
                        
                        ThumbnailScrubber(
                            totalPages = docState.totalPages,
                            currentPage = docState.currentPageIndex,
                            onPageSelected = { page ->
                                viewModel.updatePage(page)
                            },
                            getThumbnail = { viewModel.getThumbnail(it) },
                            isAmoled = isAmoled
                        )

                        ReaderBottomControls(
                            activeTool = activeTab?.lastTool ?: PdfToolMode.NAVIGATE,
                            onToolSelected = { tool ->
                                viewModel.updateLastTool(tool)
                                if (tool == PdfToolMode.SEARCH) {
                                    isSearchingInDoc = true
                                }
                            },
                            isAmoled = isAmoled
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(if (isAmoled) Color.Black else Color.Transparent)
        ) {
            when (val state = uiState) {
                is PdfViewModel.PdfUiState.Loading -> LoadingScreen(isAmoled)
                is PdfViewModel.PdfUiState.Viewer -> {
                    ViewerContent(
                        viewModel = viewModel,
                        docState = docState,
                        activeTab = activeTab,
                        ocrData = ocrData,
                        nightProfile = nightProfile,
                        isAmoled = isAmoled
                    )
                }
                else -> {
                    PdfListContent(
                        pdfFiles = pdfFiles,
                        searchQuery = searchQuery,
                        deletingFileId = deletingFileId,
                        isAmoled = isAmoled,
                        onFileClick = { file -> viewModel.openPdf(file.uri, file.name) },
                        onMenuClick = { file -> showBottomSheet = file }
                    )
                }
            }
            
            activeTab?.let { tab ->
                if (tab.isOcrActive) {
                    FormulaScanningOverlay(progress = tab.ocrProgress, isAmoled = isAmoled)
                }
            }
        }
    }

    if (showOcrResult) {
        OcrResultDialog(
            text = extractedText ?: "No text extracted yet.",
            onDismiss = { showOcrResult = false },
            isAmoled = isAmoled
        )
    }

    FileActionComponents(
        showBottomSheet = showBottomSheet,
        showRenameDialog = showRenameDialog,
        showDetailsDialog = showDetailsDialog,
        onDismissBottomSheet = { showBottomSheet = null },
        onDismissRename = { showRenameDialog = null },
        onDismissDetails = { showDetailsDialog = null },
        onDelete = { file ->
            deletingFileId = file.uri
            showBottomSheet = null
            scope.launch {
                delay(600)
                viewModel.deleteFile(file)
                deletingFileId = null
            }
        },
        onRename = { file ->
            showRenameDialog = file
            showBottomSheet = null
        },
        onRenameConfirm = { file, name ->
            viewModel.renameFile(file, name)
            showRenameDialog = null
        },
        onPin = { file ->
            viewModel.togglePin(file)
            showBottomSheet = null
        },
        onShare = { file ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, file.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
            showBottomSheet = null
        },
        onDetails = { file ->
            showDetailsDialog = file
            showBottomSheet = null
        },
        onNotes = { file ->
            viewModel.navigateToNotesForPdf(file) { noteId ->
                if (noteId != null) {
                    onNavigateToNote(noteId)
                } else {
                    viewModel.createNoteForPdf(file) { newId ->
                        onNavigateToNote(newId)
                    }
                }
            }
            showBottomSheet = null
        },
        isAmoled = isAmoled
    )
}

@Composable
private fun ViewerContent(
    viewModel: PdfViewModel,
    docState: DocumentState,
    activeTab: PdfWorkspaceTab?,
    ocrData: OcrDocumentData?,
    nightProfile: NightProfile,
    isAmoled: Boolean
) {
    Column {
        if (activeTab?.isOcrActive == true) {
            OcrProgressHeader(activeTab.ocrProgress, isAmoled)
        }

        Box(modifier = Modifier.weight(1f)) {
            InfinitePdfReader(
                viewModel = viewModel,
                docState = docState,
                ocrData = ocrData,
                nightProfile = nightProfile,
                activeTool = activeTab?.lastTool ?: PdfToolMode.NAVIGATE
            )

            if (docState.isSearching || docState.searchResults.isNotEmpty()) {
                SearchResultsOverlay(
                    docState = docState, 
                    onPageSelected = viewModel::updatePage,
                    isAmoled = isAmoled
                )
            }
        }
    }
}

@Composable
private fun OcrProgressHeader(progress: Float, isAmoled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isAmoled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Analyzing document structure...",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isAmoled) Color.White else Color.Unspecified
            )
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = if (isAmoled) Color.White else Color.Unspecified
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = if (isAmoled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )
    }
}

@Composable
private fun BoxScope.SearchResultsOverlay(
    docState: DocumentState, 
    onPageSelected: (Int) -> Unit,
    isAmoled: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .align(Alignment.BottomCenter),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = if (isAmoled) Color(0xFF1A1A1A).copy(alpha = 0.95f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (docState.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Searching engine...", style = MaterialTheme.typography.labelSmall, color = if (isAmoled) Color.White else Color.Unspecified)
                    } else {
                        Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${docState.searchResults.size} matches indexed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = if (isAmoled) Color.White else Color.Unspecified
                        )
                    }
                }
                
                if (docState.searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(docState.searchResults) { pageIndex ->
                            Surface(
                                onClick = { onPageSelected(pageIndex) },
                                color = if (docState.currentPageIndex == pageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(40.dp).bouncyClick { onPageSelected(pageIndex) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        (pageIndex + 1).toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = if (docState.currentPageIndex == pageIndex) MaterialTheme.colorScheme.onPrimary else (if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfinitePdfReader(
    viewModel: PdfViewModel,
    docState: DocumentState,
    ocrData: OcrDocumentData?,
    nightProfile: NightProfile,
    activeTool: PdfToolMode
) {
    val listState = rememberLazyListState()
    
    val visiblePageIndex by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        }
    }

    LaunchedEffect(visiblePageIndex) {
        viewModel.updatePage(visiblePageIndex)
    }

    LaunchedEffect(docState.currentPageIndex) {
        if (!listState.isScrollInProgress && docState.currentPageIndex != visiblePageIndex) {
            listState.animateScrollToItem(docState.currentPageIndex)
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .fadingEdge(
                    brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                    length = 40.dp
                ),
            contentPadding = PaddingValues(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            items(docState.totalPages) { index ->
                PageContainer(
                    pageIndex = index,
                    viewModel = viewModel,
                    ocrPageData = ocrData?.pages?.find { it.pageIndex == index },
                    activeTool = activeTool,
                    showPageNumber = docState.showPages
                )
            }
        }

        if (nightProfile != NightProfile.OFF) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(nightProfile.overlayColor())
            )
        }
    }
}

@Composable
private fun PageContainer(
    pageIndex: Int,
    viewModel: PdfViewModel,
    ocrPageData: OcrPageData?,
    activeTool: PdfToolMode,
    showPageNumber: Boolean = true
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(pageIndex) {
        bitmap = viewModel.getPageBitmap(pageIndex)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .then(
                    if (bitmap != null && bitmap!!.height > 0) {
                        Modifier.aspectRatio(bitmap!!.width.toFloat() / bitmap!!.height.toFloat())
                    } else {
                        Modifier.aspectRatio(0.707f)
                    }
                )
                .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.2f)),
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
                    
                    PageOverlay(
                        ocrPageData = ocrPageData,
                        activeTool = activeTool,
                        bitmapWidth = it.width,
                        bitmapHeight = it.height
                    )
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            }
        }
        if (showPageNumber) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "PAGE ${pageIndex + 1}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
private fun PageOverlay(
    ocrPageData: OcrPageData?,
    activeTool: PdfToolMode,
    bitmapWidth: Int,
    bitmapHeight: Int
) {
    if (activeTool == PdfToolMode.TEXT_SELECT && ocrPageData != null) {
        val context = LocalContext.current
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val viewWidth = with(density) { maxWidth.toPx() }
            val viewHeight = with(density) { maxHeight.toPx() }
            
            val scaleX = viewWidth / bitmapWidth
            val scaleY = viewHeight / bitmapHeight
            
            ocrPageData.blocks.forEach { block ->
                val left = block.left * scaleX
                val top = block.top * scaleY
                val width = (block.right - block.left) * scaleX
                val height = (block.bottom - block.top) * scaleY
                
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { left.toDp() }, y = with(density) { top.toDp() })
                        .size(width = with(density) { width.toDp() }, height = with(density) { height.toDp() })
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PDF Text", block.text))
                            Toast.makeText(context, "Fragment copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                )
            }
        }
    }
}

@Composable
private fun ThumbnailScrubber(
    totalPages: Int,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    getThumbnail: suspend (Int) -> Bitmap?,
    isAmoled: Boolean
) {
    if (totalPages <= 1) return
    
    val listState = rememberLazyListState()
    
    LaunchedEffect(currentPage) {
        listState.animateScrollToItem(currentPage)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 12.dp,
        border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(totalPages) { index ->
                ThumbnailItem(
                    index = index,
                    isSelected = index == currentPage,
                    getThumbnail = getThumbnail,
                    onClick = { onPageSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    index: Int,
    isSelected: Boolean,
    getThumbnail: suspend (Int) -> Bitmap?,
    onClick: () -> Unit
) {
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(index) {
        thumb = getThumbnail(index)
    }

    val scale by animateFloatAsState(if (isSelected) 1.15f else 1f, label = "")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Surface(
            modifier = Modifier
                .width(60.dp)
                .height(84.dp)
                .bouncyClick(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
            color = Color.LightGray.copy(alpha = 0.1f),
            shadowElevation = if (isSelected) 12.dp else 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                thumb?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            (index + 1).toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ReaderBottomControls(
    activeTool: PdfToolMode,
    onToolSelected: (PdfToolMode) -> Unit,
    isAmoled: Boolean
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .fillMaxWidth()
            .background(
                if (isAmoled) Color(0xFF1A1A1A).copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(32.dp)
            )
            .border(
                1.dp, 
                if (isAmoled) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                RoundedCornerShape(32.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolItem(
            icon = Icons.Rounded.PanTool,
            label = "NAVIGATE",
            isSelected = activeTool == PdfToolMode.NAVIGATE,
            onClick = { onToolSelected(PdfToolMode.NAVIGATE) },
            isAmoled = isAmoled
        )
        ToolItem(
            icon = Icons.Rounded.TextFields,
            label = "OCR ANALYZE",
            isSelected = activeTool == PdfToolMode.TEXT_SELECT,
            onClick = { onToolSelected(PdfToolMode.TEXT_SELECT) },
            isAmoled = isAmoled
        )
        ToolItem(
            icon = Icons.Rounded.Search,
            label = "FIND TEXT",
            isSelected = activeTool == PdfToolMode.SEARCH,
            onClick = { onToolSelected(PdfToolMode.SEARCH) },
            isAmoled = isAmoled
        )
    }
}

@Composable
private fun ToolItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isAmoled: Boolean
) {
    val animatedColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary else (if (isAmoled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)),
        label = ""
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(26.dp),
            tint = animatedColor
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            color = animatedColor,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun PdfListTopBar(
    onNavigateBack: () -> Unit,
    sortOrder: PdfSortOrder,
    onSortOrderChange: (PdfSortOrder) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    openTabs: List<PdfWorkspaceTab>,
    activeTabId: String?,
    onTabSwitch: (String) -> Unit,
    onTabClose: (String) -> Unit,
    isAmoled: Boolean
) {
    Surface(
        color = if (isAmoled) Color.Black else Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface)
                }

                Text(
                    text = "PDF ENGINE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, "Sort", tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        PdfSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    onSortOrderChange(order)
                                    showSortMenu = false
                                },
                                leadingIcon = { if (sortOrder == order) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Locate document in repository...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(32.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = if (isAmoled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedContainerColor = if (isAmoled) Color(0xFF222222) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )

            if (openTabs.isNotEmpty()) {
                TabRow(openTabs, activeTabId, onTabSwitch, onTabClose, isAmoled)
            }
        }
    }
}

@Composable
private fun TabRow(
    openTabs: List<PdfWorkspaceTab>,
    activeTabId: String?,
    onTabSwitch: (String) -> Unit,
    onTabClose: (String) -> Unit,
    isAmoled: Boolean
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        items(openTabs, key = { it.id }) { tab ->
            val isActive = tab.id == activeTabId
            Surface(
                onClick = { onTabSwitch(tab.id) },
                color = if (isActive) MaterialTheme.colorScheme.primary
                        else if (isAmoled) Color(0xFF1A1A1A)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(40.dp),
                border = if (!isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = tab.title.take(18),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                else if (isAmoled) Color.White.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    IconButton(
                        onClick = { onTabClose(tab.id) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close, null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    else if (isAmoled) Color.White.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerTopBar(
    title: String,
    viewModel: PdfViewModel,
    docState: DocumentState,
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onBack: () -> Unit,
    isAmoled: Boolean
) {
    Surface(
        color = if (isAmoled) Color.Black.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)) else null
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = if (isAmoled) Color.White else Color.Unspecified)
                }

                if (isSearching) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Filter document content...") },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = onToggleSearch) { Icon(Icons.Rounded.Close, null) }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedTextColor = if (isAmoled) Color.White else Color.Unspecified,
                            focusedTextColor = if (isAmoled) Color.White else Color.Unspecified
                        )
                    )
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isAmoled) Color.White else Color.Unspecified
                    )
                    IconButton(onClick = viewModel::toggleShowPages) {
                        Icon(
                            if (docState.showPages) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, 
                            null, 
                            tint = if (isAmoled) Color.White else Color.Unspecified
                        )
                    }
                    IconButton(onClick = onToggleSearch) {
                        Icon(Icons.Rounded.Search, null, tint = if (isAmoled) Color.White else Color.Unspecified)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSelectToolbar(
    currentLanguage: OcrLanguage,
    onLanguageSelect: (OcrLanguage) -> Unit,
    onViewFullText: () -> Unit,
    onResetOcr: () -> Unit,
    isAmoled: Boolean
) {
    var showLanguageMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        color = if (isAmoled) Color(0xFF1A1A1A).copy(alpha = 0.95f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 6.dp,
        border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                TextButton(onClick = { showLanguageMenu = true }) {
                    Icon(Icons.Rounded.Language, null, modifier = Modifier.size(22.dp), tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(currentLanguage.displayName.split(" ").first(), fontWeight = FontWeight.Black, color = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                }
                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { showLanguageMenu = false },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    OcrLanguage.entries.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.displayName, fontWeight = FontWeight.Bold) },
                            onClick = {
                                onLanguageSelect(lang)
                                showLanguageMenu = false
                            },
                            leadingIcon = {
                                if (lang == currentLanguage) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        )
                    }
                }
            }

            VerticalDivider(modifier = Modifier.height(28.dp).width(1.5.dp), color = (if (isAmoled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.25f))
            
            TextButton(onClick = onViewFullText) {
                Icon(Icons.AutoMirrored.Rounded.Article, null, modifier = Modifier.size(22.dp), tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("DOCUMENT TEXT", fontWeight = FontWeight.Black, color = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
            }
            
            VerticalDivider(modifier = Modifier.height(28.dp).width(1.5.dp), color = (if (isAmoled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer).copy(alpha = 0.25f))
            
            IconButton(onClick = onResetOcr) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(24.dp), tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LoadingScreen(isAmoled: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 6.dp, strokeCap = StrokeCap.Round)
            Spacer(Modifier.height(32.dp))
            Text("OPTIMIZING CORE RENDERER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun FileActionComponents(
    showBottomSheet: PdfFile?,
    showRenameDialog: PdfFile?,
    showDetailsDialog: PdfFile?,
    onDismissBottomSheet: () -> Unit,
    onDismissRename: () -> Unit,
    onDismissDetails: () -> Unit,
    onDelete: (PdfFile) -> Unit,
    onRename: (PdfFile) -> Unit,
    onRenameConfirm: (PdfFile, String) -> Unit,
    onPin: (PdfFile) -> Unit,
    onShare: (PdfFile) -> Unit,
    onDetails: (PdfFile) -> Unit,
    onNotes: (PdfFile) -> Unit,
    isAmoled: Boolean
) {
    showBottomSheet?.let { file ->
        PdfFileOptionsBottomSheet(
            file = file, isAmoled = isAmoled, onDismiss = onDismissBottomSheet,
            onDelete = { onDelete(file) }, onRename = { onRename(file) }, onPin = { onPin(file) },
            onShare = { onShare(file) }, onDetails = { onDetails(file) }, onNotes = { onNotes(file) }
        )
    }

    showRenameDialog?.let { file ->
        var newName by remember { mutableStateOf(file.name.removeSuffix(".pdf")) }
        AlertDialog(
            onDismissRequest = onDismissRename,
            title = { Text("RENAME DOCUMENT", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    placeholder = { Text("Entry identifier") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
            },
            confirmButton = {
                Button(onClick = { onRenameConfirm(file, newName) }, shape = RoundedCornerShape(12.dp)) { Text("UPDATE", fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = onDismissRename) { Text("CANCEL", fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(40.dp),
            containerColor = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        )
    }

    showDetailsDialog?.let { file ->
        AlertDialog(
            onDismissRequest = onDismissDetails,
            title = { Text("METADATA PROFILE", fontWeight = FontWeight.Black, letterSpacing = 1.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailRow(Icons.Rounded.Description, "IDENTIFIER", file.name)
                    DetailRow(Icons.Rounded.Storage, "PAYLOAD SIZE", pdfFormatSize(file.size))
                    DetailRow(Icons.Rounded.Pages, "PAGE INDEX", file.pageCount.toString())
                    DetailRow(Icons.Rounded.Event, "LAST MODIFIED", pdfFormatDate(file.lastModified))
                }
            },
            confirmButton = {
                Button(onClick = { onDismissDetails() }, shape = RoundedCornerShape(12.dp)) { Text("CLOSE", fontWeight = FontWeight.Black) }
            },
            shape = RoundedCornerShape(40.dp),
            containerColor = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultDialog(
    text: String,
    onDismiss: () -> Unit,
    isAmoled: Boolean
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isAmoled) Color.Black.copy(alpha = 0.98f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = if (isAmoled) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)) },
        modifier = Modifier.fillMaxHeight(0.9f),
        tonalElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "EXTRACTED ANALYTICS", 
                    style = MaterialTheme.typography.labelLarge, 
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("PDF OCR", text))
                        Toast.makeText(context, "Data indexed to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
            
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 32.dp),
                color = if (isAmoled) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(32.dp),
                border = BorderStroke(1.dp, if (isAmoled) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            ) {
                Box(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = text, 
                        style = MaterialTheme.typography.bodyLarge, 
                        lineHeight = 28.sp,
                        color = if (isAmoled) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFileOptionsBottomSheet(
    file: PdfFile,
    isAmoled: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onShare: (PdfFile) -> Unit,
    onDetails: (PdfFile) -> Unit,
    onNotes: (PdfFile) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 40.dp)) {
            Row(modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(60.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        if (file.thumbnail != null) {
                            Image(
                                bitmap = file.thumbnail.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Rounded.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isAmoled) Color.White else Color.Unspecified)
                    Text("DOCUMENT • ${pdfFormatSize(file.size)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Black)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = if (isAmoled) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            OptionItem("Distribute Document", Icons.Rounded.Share, { onShare(file) }, isAmoled)
            OptionItem(if (file.isPinned) "Release from Pins" else "Pin to Top", Icons.Rounded.PushPin, onPin, isAmoled)
            OptionItem("Modify Identifier", Icons.Rounded.Edit, onRename, isAmoled)
            OptionItem("Technical Details", Icons.Rounded.Info, { onDetails(file) }, isAmoled)
            OptionItem("Archived Notes", Icons.AutoMirrored.Rounded.Notes, { onNotes(file) }, isAmoled)
            OptionItem("Purge from Storage", Icons.Rounded.Delete, onDelete, isAmoled, isError = true)
        }
    }
}

@Composable
private fun OptionItem(text: String, icon: ImageVector, onClick: () -> Unit, isAmoled: Boolean, isError: Boolean = false) {
    ListItem(
        headlineContent = { Text(text, color = if (isError) MaterialTheme.colorScheme.error else if (isAmoled) Color.White else Color.Unspecified, fontWeight = FontWeight.Black) },
        leadingContent = { Icon(icon, null, tint = if (isError) MaterialTheme.colorScheme.error else if (isAmoled) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun PdfListContent(
    pdfFiles: List<PdfFile>,
    searchQuery: String,
    deletingFileId: Uri?,
    isAmoled: Boolean,
    onFileClick: (PdfFile) -> Unit,
    onMenuClick: (PdfFile) -> Unit
) {
    val filteredFiles = pdfFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }

    val isSearching = searchQuery.isNotEmpty()
    if (pdfFiles.isEmpty() || (isSearching && filteredFiles.isEmpty())) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, 
                modifier = Modifier.padding(32.dp)
            ) {
                Surface(
                    modifier = Modifier.size(140.dp),
                    shape = RoundedCornerShape(48.dp),
                    color = (if (isAmoled) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(2.dp, (if (isAmoled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)))
                ) {
                    Icon(
                        if (isSearching) Icons.Rounded.SearchOff else Icons.Rounded.Article, 
                        contentDescription = null, 
                        modifier = Modifier.padding(40.dp), 
                        tint = if (isAmoled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(40.dp))
                Text(
                    text = if (isSearching) "ZERO MATCHES" else "REPOSITORY VOID",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (isAmoled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.secondary,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (isSearching) "The encryption index returned no results for your query." else "Initialize your document library by adding PDF payloads.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isAmoled) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.85f),
                    fontWeight = FontWeight.Black,
                    lineHeight = 24.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadingEdge(
                brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent)),
                length = 32.dp
            ),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(filteredFiles, key = { _, file -> file.uri.toString() }) { _, file ->
                val isDeleting = deletingFileId == file.uri
                PdfFileItem(
                    file = file, isDeleting = isDeleting, isAmoled = isAmoled,
                    onClick = { onFileClick(file) }, onMenuClick = { onMenuClick(file) }
                )
            }
        }
    }
}

@Composable
fun PdfFileItem(
    file: PdfFile,
    isDeleting: Boolean,
    isAmoled: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val alpha by animateFloatAsState(if (isDeleting) 0f else 1f, animationSpec = tween(600), label = "")
    val scale by animateFloatAsState(if (isDeleting) 0.8f else 1f, animationSpec = tween(600), label = "")
    
    val colorTransition by animateColorAsState(
        if (isDeleting) Color.Red.copy(alpha = 0.4f)
        else if (isAmoled) Color(0xFF1A1A1A)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        animationSpec = tween(400),
        label = ""
    )

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha; this.scaleX = scale; this.scaleY = scale },
        shape = RoundedCornerShape(32.dp), color = colorTransition,
        border = if (file.isPinned) BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        tonalElevation = 4.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(68.dp), shape = RoundedCornerShape(18.dp),
                color = if (isAmoled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (file.thumbnail != null) {
                        Image(
                            bitmap = file.thumbnail.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Rounded.PictureAsPdf, null, tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (file.isPinned) {
                        Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text = file.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface)
                }
                Text(text = "${pdfFormatSize(file.size)} • ${file.pageCount} PAGES", style = MaterialTheme.typography.labelSmall, color = if (isAmoled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, null, modifier = Modifier.size(14.dp), tint = Color.Gray.copy(alpha = 0.6f))
                    Spacer(Modifier.width(6.dp))
                    Text(pdfFormatDate(file.lastModified), style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Rounded.MoreVert, null, tint = if (isAmoled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
        }
    }
}

private fun NightProfile.overlayColor(): Color {
    return when (this) {
        NightProfile.OFF -> Color.Transparent
        NightProfile.SOLARIZED_DARK -> Color(0x33FDF6E3).compositeOver(Color(0xFF002B36))
        NightProfile.AMOLED_BLACK -> Color(0xFF000000).copy(alpha = 0.85f)
    }
}

private fun pdfFormatSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.2f MB".format(Locale.US, mb) else "%.2f KB".format(Locale.US, kb)
}

private fun pdfFormatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000)).uppercase()
}

@Composable
fun FormulaScanningOverlay(progress: Float, isAmoled: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background((if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                val infiniteTransition = rememberInfiniteTransition(label = "scanning")
                val scanOffset by infiniteTransition.animateFloat(
                    initialValue = -90f,
                    targetValue = 90f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scanLine"
                )
                
                Surface(
                    modifier = Modifier.size(180.dp),
                    shape = RoundedCornerShape(48.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        null,
                        modifier = Modifier.padding(56.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(3.dp)
                        .offset(y = scanOffset.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.primary, Color.Transparent)
                            )
                        )
                        .shadow(12.dp, spotColor = MaterialTheme.colorScheme.primary)
                )
            }
            
            Spacer(Modifier.height(56.dp))
            @Suppress("DEPRECATION")
            Text(
                "NEURAL OCR ENGINE ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(20.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(240.dp).height(10.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                strokeCap = StrokeCap.Round
            )
            
            Spacer(Modifier.height(20.dp))
            Text(
                "INDEXING STRUCTURE: ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(48.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                @Suppress("DEPRECATION")
                Text(
                    "CONVERTING ANALYTICS TO LOCAL CACHE",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
