package com.frerox.toolz.ui.screens.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.frerox.toolz.data.pdf.PdfFile
import com.frerox.toolz.databinding.LayoutPdfViewerBinding
import com.frerox.toolz.ui.components.bouncyClick
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolzPdfScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onSolveFormula: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfFiles by viewModel.pdfFiles.collectAsStateWithLifecycle()
    val nightProfile by viewModel.nightProfile.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val formulaState by viewModel.formulaState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showBottomSheet by remember { mutableStateOf<PdfFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .glassmorphicSurface()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (uiState is PdfViewModel.PdfUiState.Viewer) viewModel.closeViewer() else onNavigateBack()
                        },
                        modifier = Modifier.size(44.dp).clip(CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }

                    Text(
                        text = if (uiState is PdfViewModel.PdfUiState.Viewer) "INFINITE WORKSPACE" else "PDF TOOLS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Row {
                        IconButton(onClick = viewModel::toggleNightProfile) {
                            Icon(Icons.Rounded.DarkMode, contentDescription = "Night profile")
                        }
                        IconButton(onClick = { viewModel.updateLastTool(PdfToolMode.FORMULA_CAPTURE) }) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Capture formula")
                        }
                    }
                }

                if (uiState !is PdfViewModel.PdfUiState.Viewer) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.setSearchQuery(it)
                        },
                        placeholder = { Text("Search your documents...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }

                if (openTabs.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(openTabs, key = { it.id }) { tab ->
                            AssistChip(
                                onClick = { viewModel.switchTab(tab.id) },
                                label = { Text(tab.title.take(22), maxLines = 1) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close tab",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .bouncyClick { viewModel.closeTab(tab.id) }
                                    )
                                },
                                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                    containerColor = if (tab.id == activeTabId) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        bottomBar = {
            if (uiState is PdfViewModel.PdfUiState.Viewer) {
                ReaderBottomControls(viewModel = viewModel, nightProfile = nightProfile)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                is PdfViewModel.PdfUiState.Viewer -> {
                    InfinitePdfReader(
                        uri = state.uri,
                        viewModel = viewModel,
                        nightProfile = nightProfile
                    )
                }
                else -> {
                    PdfListContent(
                        pdfFiles = pdfFiles,
                        searchQuery = searchQuery,
                        onFileClick = { file -> viewModel.openPdf(file.uri, file.name) },
                        onMenuClick = { file -> showBottomSheet = file }
                    )
                }
            }
        }
    }

    showBottomSheet?.let { file ->
        PdfFileOptionsBottomSheet(file = file, onDismiss = { showBottomSheet = null })
    }

    if (formulaState.result != null || formulaState.error != null) {
        FormulaResultSheet(
            state = formulaState,
            onDismiss = viewModel::clearFormulaResult,
            onCopyLatex = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("latex", it))
            },
            onSolve = { onSolveFormula?.invoke(it) }
        )
    }
}

@Composable
private fun InfinitePdfReader(
    uri: Uri,
    viewModel: PdfViewModel,
    nightProfile: NightProfile
) {
    val context = LocalContext.current
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }
    val fragmentActivity = context as? FragmentActivity

    Column(modifier = Modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13 && fragmentActivity != null) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidViewBinding(LayoutPdfViewerBinding::inflate) {
                    val fragmentManager = fragmentActivity.supportFragmentManager
                    var pdfFragment = fragmentManager.findFragmentByTag("pdf_viewer") as? PdfViewerFragment
                    if (pdfFragment == null) {
                        pdfFragment = PdfViewerFragment()
                        fragmentManager.beginTransaction()
                            .replace(this.fragmentContainerView.id, pdfFragment, "pdf_viewer")
                            .commitNow()
                    }
                    pdfFragment.documentUri = uri
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(nightProfile.overlayColor())
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = {
                                val fakeRecognized = "x^2 + y^2 = z^2"
                                viewModel.startFormulaCapture(androidx.compose.ui.geometry.Rect.Zero)
                                viewModel.runFormulaCapture(fakeRecognized)
                            })
                        }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Integrated PDF viewer requires Android 12+ with SDK extension 13")
            }
        }

        ThumbnailScrubber(
            currentPage = currentPage,
            pageCount = pageCount,
            onPageScrubbed = { page ->
                currentPage = page
                viewModel.updatePage(page, pageCount)
            }
        )
    }

    LaunchedEffect(currentPage) {
        viewModel.updatePage(currentPage, pageCount)
    }
}

@Composable
private fun ThumbnailScrubber(
    currentPage: Int,
    pageCount: Int,
    onPageScrubbed: (Int) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var sliderValue by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphicSurface()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text("Page ${currentPage + 1} / ${pageCount.coerceAtLeast(1)}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                val snapped = it.roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                if (snapped != currentPage) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPageScrubbed(snapped)
                }
            },
            valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat()
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items((0 until pageCount.coerceAtMost(40)).toList()) { page ->
                Card(
                    modifier = Modifier
                        .size(width = 56.dp, height = 76.dp)
                        .bouncyClick { onPageScrubbed(page) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (page == currentPage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("${page + 1}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomControls(viewModel: PdfViewModel, nightProfile: NightProfile) {
    var zoom by remember { mutableFloatStateOf(1f) }
    Surface(modifier = Modifier.fillMaxWidth().glassmorphicSurface(), color = Color.Transparent) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("Workspace Tools", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfToolMode.entries.forEach { tool ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.updateLastTool(tool) },
                        label = { Text(tool.name.replace('_', ' ')) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Zoom ${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(value = zoom, onValueChange = {
                zoom = it
                viewModel.updateZoom(it)
            }, valueRange = 0.75f..3f)
            Text("Night profile: ${nightProfile.name}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Modifier.glassmorphicSurface(): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .graphicsLayer {
                renderEffect = RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP).asComposeRenderEffect()
            }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
    } else {
        this.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    }
}

private fun NightProfile.overlayColor(): Color {
    return when (this) {
        NightProfile.OFF -> Color.Transparent
        NightProfile.SOLARIZED_DARK -> Color(0xAA002B36)
        NightProfile.AMOLED_BLACK -> Color(0xCC000000)
    }
}

@Composable
fun PdfListContent(
    pdfFiles: List<PdfFile>,
    searchQuery: String,
    onFileClick: (PdfFile) -> Unit,
    onMenuClick: (PdfFile) -> Unit
) {
    val filteredFiles = pdfFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }

    if (pdfFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No PDF files found", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
        }
    } else if (filteredFiles.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No results for \"$searchQuery\"",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
        }
    } else {
        PdfFileList(files = filteredFiles, onFileClick = onFileClick, onMenuClick = onMenuClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfFileOptionsBottomSheet(file: PdfFile, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(text = file.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 24.dp))
            val options = listOf(
                Triple("Share Document", Icons.Rounded.Psychology, {}),
                Triple("Rename File", Icons.Rounded.AutoFixHigh, {}),
                Triple("Details", Icons.Rounded.Search, {}),
                Triple("Delete", Icons.Rounded.DarkMode, {})
            )
            options.forEach { (text, icon, action) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .bouncyClick { action(); onDismiss() },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(16.dp))
                        Text(text, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormulaResultSheet(
    state: FormulaCaptureState,
    onDismiss: () -> Unit,
    onCopyLatex: (String) -> Unit,
    onSolve: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Formula Capture", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.result?.let { result ->
                Text("Recognized", fontWeight = FontWeight.Bold)
                Text(result.plainText)
                Spacer(Modifier.height(8.dp))
                Text("LaTeX", fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(result.latex, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { onCopyLatex(result.latex) }) { Text("Copy as LaTeX") }
                    FilledTonalButton(onClick = { onSolve(result.plainText) }) { Text("Solve") }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
