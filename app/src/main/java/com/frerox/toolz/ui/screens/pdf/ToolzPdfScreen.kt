package com.frerox.toolz.ui.screens.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.frerox.toolz.R
import com.frerox.toolz.data.pdf.PdfAnnotation
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.databinding.LayoutPdfViewerBinding
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToNote: (Int) -> Unit,
    onSolveFormula: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfFiles by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val nightProfile by viewModel.nightProfile.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val extractedText by viewModel.extractedText.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showBottomSheet by remember { mutableStateOf<PdfFile?>(null) }
    var showRenameDialog by remember { mutableStateOf<PdfFile?>(null) }
    var showDetailsDialog by remember { mutableStateOf<PdfFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var deletingFileId by remember { mutableStateOf<Uri?>(null) }
    var showOcrResult by remember { mutableStateOf(false) }

    val activeTab = openTabs.find { it.id == activeTabId }
    val isAmoled = nightProfile == NightProfile.AMOLED_BLACK

    Scaffold(
        containerColor = if (isAmoled) Color.Black else Color.Transparent,
        topBar = {
            if (uiState !is PdfViewModel.PdfUiState.Viewer) {
                Surface(
                    color = if (isAmoled) Color.Black else Color.Transparent,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
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
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack, 
                                    contentDescription = "Back",
                                    tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "PDF TOOLS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                            )

                            var showSortMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Sort, 
                                        contentDescription = "Sort",
                                        tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.background(
                                        if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface, 
                                        RoundedCornerShape(20.dp)
                                    )
                                ) {
                                    PdfSortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                viewModel.setSortOrder(order)
                                                showSortMenu = false
                                            },
                                            leadingIcon = {
                                                if (sortOrder == order) Icon(Icons.Rounded.Check, null)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.setSearchQuery(it)
                            },
                            placeholder = { Text("Search your documents...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = if (isAmoled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                focusedContainerColor = if (isAmoled) Color(0xFF222222) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )

                        if (openTabs.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .fadingEdge(
                                        brush = Brush.horizontalGradient(
                                            0f to Color.Transparent,
                                            0.05f to Color.Black,
                                            0.95f to Color.Black,
                                            1f to Color.Transparent
                                        ),
                                        length = 16.dp
                                    )
                            ) {
                                items(openTabs, key = { it.id }) { tab ->
                                    val isActive = tab.id == activeTabId
                                    Surface(
                                        onClick = { viewModel.switchTab(tab.id) },
                                        color = if (isActive) MaterialTheme.colorScheme.primary 
                                                else if (isAmoled) Color(0xFF1A1A1A) 
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = tab.title.take(15),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isActive) MaterialTheme.colorScheme.onPrimary 
                                                        else if (isAmoled) Color.White.copy(alpha = 0.7f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Close",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { viewModel.closeTab(tab.id) },
                                                tint = if (isActive) MaterialTheme.colorScheme.onPrimary 
                                                        else if (isAmoled) Color.White.copy(alpha = 0.4f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (uiState is PdfViewModel.PdfUiState.Viewer) {
                Column {
                    if (activeTab?.lastTool == PdfToolMode.HIGHLIGHTER) {
                        HighlighterToolbar(
                            onUndo = { viewModel.undoLastAnnotation() },
                            onRedo = { viewModel.redoAnnotation() },
                            onReset = { viewModel.clearAllAnnotations() },
                            isAmoled = isAmoled
                        )
                    } else if (activeTab?.lastTool == PdfToolMode.TEXT_SELECT) {
                        TextSelectToolbar(
                            onViewFullText = { showOcrResult = true },
                            onResetOcr = { viewModel.resetOcr() },
                            isAmoled = isAmoled
                        )
                    }
                    ReaderBottomControls(
                        activeTool = activeTab?.lastTool ?: PdfToolMode.NAVIGATE,
                        onToolSelected = viewModel::updateLastTool,
                        isAmoled = isAmoled
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState is PdfViewModel.PdfUiState.Viewer) {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = viewModel::toggleNightProfile,
                        containerColor = if (isAmoled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        AnimatedContent(targetState = nightProfile, label = "") { profile ->
                            Icon(
                                imageVector = when(profile) {
                                    NightProfile.OFF -> Icons.Rounded.LightMode
                                    NightProfile.SOLARIZED_DARK -> Icons.Rounded.Contrast
                                    NightProfile.AMOLED_BLACK -> Icons.Rounded.DarkMode
                                },
                                contentDescription = "Night profile"
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick = viewModel::closeViewer,
                        containerColor = if (isAmoled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.primary,
                        contentColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close Viewer")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isAmoled) Color.Black else Color.Transparent)
        ) {
            when (val state = uiState) {
                is PdfViewModel.PdfUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Scanning for PDFs...",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isAmoled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is PdfViewModel.PdfUiState.Viewer -> {
                    Column {
                        if (activeTab?.isOcrActive == true) {
                            LinearProgressIndicator(
                                progress = { activeTab.ocrProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                            Surface(
                                color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Polishing document OCR... ${(activeTab.ocrProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        InfinitePdfReader(
                            uri = state.uri,
                            viewModel = viewModel,
                            nightProfile = nightProfile,
                            activeTool = activeTab?.lastTool ?: PdfToolMode.NAVIGATE,
                            annotations = annotations
                        )
                    }
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
        }
    }

    if (showOcrResult) {
        OcrResultDialog(
            text = extractedText ?: "No text extracted yet.",
            onDismiss = { showOcrResult = false },
            isAmoled = isAmoled
        )
    }

    showBottomSheet?.let { file ->
        PdfFileOptionsBottomSheet(
            file = file,
            isAmoled = isAmoled,
            onDismiss = { showBottomSheet = null },
            onDelete = { 
                deletingFileId = file.uri
                showBottomSheet = null
                scope.launch {
                    delay(600)
                    viewModel.deleteFile(file)
                    deletingFileId = null
                }
            },
            onRename = { 
                showRenameDialog = file
                showBottomSheet = null
            },
            onPin = {
                viewModel.togglePin(file)
                showBottomSheet = null
            },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, file.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share PDF"))
                showBottomSheet = null
            },
            onDetails = {
                showDetailsDialog = file
                showBottomSheet = null
            },
            onNotes = {
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
            }
        )
    }

    showRenameDialog?.let { file ->
        var newName by remember { mutableStateOf(file.name.removeSuffix(".pdf")) }
        Dialog(
            onDismissRequest = { showRenameDialog = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.padding(24.dp).widthIn(max = 400.dp),
                shape = RoundedCornerShape(32.dp),
                color = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Rename File", 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Black,
                        color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = if (isAmoled) Color.Black else Color.Transparent,
                            focusedContainerColor = if (isAmoled) Color.Black else Color.Transparent,
                            unfocusedTextColor = if (isAmoled) Color.White else Color.Unspecified,
                            focusedTextColor = if (isAmoled) Color.White else Color.Unspecified
                        )
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    viewModel.renameFile(file, newName)
                                }
                                showRenameDialog = null
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary,
                                contentColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("RENAME", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    showDetailsDialog?.let { file ->
        Dialog(
            onDismissRequest = { showDetailsDialog = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.padding(24.dp).widthIn(max = 400.dp),
                shape = RoundedCornerShape(32.dp),
                color = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "File Details", 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Black,
                        color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DetailItem(Icons.Rounded.Description, "Name", file.name, isAmoled)
                        DetailItem(Icons.Rounded.Storage, "Size", formatSize(file.size), isAmoled)
                        DetailItem(Icons.Rounded.Pages, "Pages", file.pageCount.toString(), isAmoled)
                        DetailItem(Icons.Rounded.Event, "Modified", formatDate(file.lastModified), isAmoled)
                        DetailItem(Icons.Rounded.Folder, "Path", file.uri.path ?: "Unknown", isAmoled)
                    }
                    Spacer(Modifier.height(32.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = { showDetailsDialog = null },
                            colors = ButtonDefaults.textButtonColors(contentColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary)
                        ) { 
                            Text("CLOSE", fontWeight = FontWeight.Bold) 
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlighterToolbar(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    isAmoled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (isAmoled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = if (isAmoled) Color.White else Color.Unspecified)
                }
                IconButton(onClick = onRedo) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, null, tint = if (isAmoled) Color.White else Color.Unspecified)
                }
            }
            TextButton(onClick = onReset, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TextSelectToolbar(
    onViewFullText: () -> Unit,
    onResetOcr: () -> Unit,
    isAmoled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (isAmoled) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 4.dp,
        border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onViewFullText) {
                Icon(Icons.Rounded.Article, null, modifier = Modifier.size(18.dp), tint = if (isAmoled) Color.White else Color.Unspecified)
                Spacer(Modifier.width(8.dp))
                Text("View Extracted Text", color = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary)
            }
            VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = if (isAmoled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            TextButton(onClick = onResetOcr) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Rescan", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrResultDialog(
    text: String,
    onDismiss: () -> Unit,
    isAmoled: Boolean
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).heightIn(min = 400.dp, max = 600.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Extracted Text", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (isAmoled) Color.White else Color.Unspecified)
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Extracted PDF Text", text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isAmoled) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            ) {
                Box(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text(text, color = if (isAmoled) Color.White.copy(alpha = 0.8f) else Color.Unspecified)
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("CLOSE")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailItem(icon: ImageVector, label: String, value: String, isAmoled: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (isAmoled) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (isAmoled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
            Text(
                value, 
                style = MaterialTheme.typography.bodyMedium, 
                fontWeight = FontWeight.Medium,
                color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InfinitePdfReader(
    uri: Uri,
    viewModel: PdfViewModel,
    nightProfile: NightProfile,
    activeTool: PdfToolMode,
    annotations: List<PdfAnnotation>
) {
    val context = LocalContext.current
    val fragmentActivity = context as? FragmentActivity
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (fragmentActivity != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeTool) {
                        if (activeTool == PdfToolMode.HIGHLIGHTER) {
                            detectDragGestures(
                                onDragStart = { dragStart = it },
                                onDragEnd = {
                                    if (dragStart != null && dragEnd != null) {
                                        viewModel.addHighlight(
                                            Rect(dragStart!!, dragEnd!!),
                                            Color.Yellow.copy(alpha = 0.4f).toArgb()
                                        )
                                    }
                                    dragStart = null
                                    dragEnd = null
                                },
                                onDrag = { change, _ ->
                                    dragEnd = change.position
                                }
                            )
                        }
                    }
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
                    val themedContext = remember { ContextThemeWrapper(context, R.style.Theme_Toolz_PdfViewer) }
                    
                    AndroidViewBinding(
                        factory = { inflater, parent, attachToParent ->
                            val binding = LayoutPdfViewerBinding.inflate(inflater.cloneInContext(themedContext), parent, attachToParent)
                            binding
                        }
                    ) {
                        val fragmentManager = fragmentActivity.supportFragmentManager
                        var pdfFragment = fragmentManager.findFragmentByTag("pdf_viewer") as? PdfViewerFragment
                        if (pdfFragment == null) {
                            pdfFragment = PdfViewerFragment()
                            fragmentManager.beginTransaction()
                                .replace(this.fragmentContainerView.id, pdfFragment, "pdf_viewer")
                                .commitNow()
                        }
                        
                        try {
                            pdfFragment.documentUri = uri
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Integrated PDF viewer requires Android 12+ with SDK extension 13", modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
                    }
                }

                // Highlighting Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    annotations.forEach { ann ->
                        if (ann.type == com.frerox.toolz.data.pdf.AnnotationType.HIGHLIGHTER) {
                            val coords = ann.data.split(",").map { it.toFloat() }
                            if (coords.size == 4) {
                                drawRoundRect(
                                    color = Color(ann.color),
                                    topLeft = Offset(coords[0], coords[1]),
                                    size = Size(coords[2] - coords[0], coords[3] - coords[1]),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                    }
                    
                    // Live Dragging Highlight
                    if (dragStart != null && dragEnd != null) {
                        drawRoundRect(
                            color = Color.Yellow.copy(alpha = 0.3f),
                            topLeft = Offset(
                                minOf(dragStart!!.x, dragEnd!!.x),
                                minOf(dragStart!!.y, dragEnd!!.y)
                            ),
                            size = Size(
                                Math.abs(dragEnd!!.x - dragStart!!.x),
                                Math.abs(dragEnd!!.y - dragStart!!.y)
                            ),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
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
        } else {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: Context is not FragmentActivity", modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun ReaderBottomControls(
    activeTool: PdfToolMode,
    onToolSelected: (PdfToolMode) -> Unit,
    isAmoled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = if (isAmoled) Color(0xFF1A1A1A).copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, if (isAmoled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolItem(
                icon = Icons.Rounded.TextFields,
                label = "Text Select",
                isSelected = activeTool == PdfToolMode.TEXT_SELECT,
                onClick = { onToolSelected(PdfToolMode.TEXT_SELECT) },
                isAmoled = isAmoled
            )
            
            ToolItem(
                icon = Icons.Rounded.Brush,
                label = "Highlight",
                isSelected = activeTool == PdfToolMode.HIGHLIGHTER,
                onClick = { onToolSelected(PdfToolMode.HIGHLIGHTER) },
                isAmoled = isAmoled
            )
        }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isSelected) (if (isAmoled) Color.White else MaterialTheme.colorScheme.primary) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) (if (isAmoled) Color.Black else MaterialTheme.colorScheme.onPrimary) 
                       else (if (isAmoled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) (if (isAmoled) Color.White else MaterialTheme.colorScheme.primary) 
                    else (if (isAmoled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

private fun NightProfile.overlayColor(): Color {
    return when (this) {
        NightProfile.OFF -> Color.Transparent
        NightProfile.SOLARIZED_DARK -> Color(0x33FDF6E3).compositeOver(Color(0xFF002B36))
        NightProfile.AMOLED_BLACK -> Color(0xFF000000).copy(alpha = 0.85f)
    }
}

private fun formatSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.2f MB".format(mb) else "%.2f KB".format(kb)
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
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

    if (pdfFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Description, null, modifier = Modifier.size(80.dp), tint = if (isAmoled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                Text(text = "No PDF files found", fontWeight = FontWeight.Bold, color = if (isAmoled) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline)
            }
        }
    } else if (filteredFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No results for \"$searchQuery\"",
                fontWeight = FontWeight.Bold,
                color = if (isAmoled) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadingEdge(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.02f to Color.Black,
                    0.98f to Color.Black,
                    1f to Color.Transparent
                ),
                length = 24.dp
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredFiles, key = { it.uri.toString() }) { file ->
                val isDeleting = deletingFileId == file.uri
                PdfFileItem(
                    file = file,
                    isDeleting = isDeleting,
                    isAmoled = isAmoled,
                    onClick = { onFileClick(file) },
                    onMenuClick = { onMenuClick(file) }
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
    val alpha by animateFloatAsState(if (isDeleting) 0f else 1f, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "")
    val scale by animateFloatAsState(if (isDeleting) 0.75f else 1f, animationSpec = tween(600, easing = LinearOutSlowInEasing), label = "")
    val colorTransition by animateColorAsState(
        if (isDeleting) Color.Red.copy(alpha = 0.5f) 
        else if (isAmoled) Color(0xFF1A1A1A) 
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        animationSpec = tween(400),
        label = ""
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            },
        shape = RoundedCornerShape(24.dp),
        color = colorTransition,
        border = if (file.isPinned) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
        shadowElevation = if (isDeleting) 0.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (isAmoled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PictureAsPdf, null, tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (file.isPinned) {
                        Icon(Icons.Rounded.PushPin, null, modifier = Modifier.size(14.dp), tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.6.dp))
                    }
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${formatSize(file.size)} • ${file.pageCount} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAmoled) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Rounded.MoreVert, null, tint = if (isAmoled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
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
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onNotes: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = if (isAmoled) Color(0xFF121212) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            color = Color.Transparent,
            border = if (isAmoled) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isAmoled) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PictureAsPdf, null, tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = file.name, 
                            fontWeight = FontWeight.Black, 
                            style = MaterialTheme.typography.titleMedium, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis,
                            color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = "PDF Document", style = MaterialTheme.typography.labelMedium, color = if (isAmoled) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val options = listOf(
                    OptionItem("Share Document", Icons.Rounded.Share, if (isAmoled) Color.White else MaterialTheme.colorScheme.primary, onShare),
                    OptionItem(if (file.isPinned) "Unpin File" else "Pin File", Icons.Rounded.PushPin, if (isAmoled) Color.White else MaterialTheme.colorScheme.secondary, onPin),
                    OptionItem("Rename File", Icons.Rounded.Edit, if (isAmoled) Color.White else MaterialTheme.colorScheme.secondary, onRename),
                    OptionItem("Details", Icons.Rounded.Info, if (isAmoled) Color.White else MaterialTheme.colorScheme.tertiary, onDetails),
                    OptionItem("Notes", Icons.AutoMirrored.Rounded.Notes, if (isAmoled) Color.White else MaterialTheme.colorScheme.primary, onNotes),
                    OptionItem("Delete", Icons.Rounded.Delete, MaterialTheme.colorScheme.error, onDelete)
                )
                
                options.forEach { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .bouncyClick { item.action() },
                        color = Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(item.color.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, null, tint = item.color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.size(16.dp))
                            Text(
                                item.text, 
                                fontWeight = FontWeight.Bold, 
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private data class OptionItem(val text: String, val icon: ImageVector, val color: Color, val action: () -> Unit)
