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

package com.frerox.toolz.ui.screens.pdf

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.pdf.PdfTocEntry
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.MarkdownContent
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.screens.pdf.components.docZoomable
import com.frerox.toolz.ui.screens.pdf.components.rememberDocZoomState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

private val SepiaMatrix = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)
private val NightMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

/**
 * Document reader. Continuous pages with document-wide pinch zoom,
 * overlay chrome (double-tap for fullscreen), page slider,
 * search / outline / text / AI / appearance in sheets.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdfReaderScreen(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    onConvert: ((String, String) -> Unit)? = null,
    onAttachToNote: (() -> Unit)? = null,
    onOpenNote: ((Int) -> Unit)? = null
) {
    val doc by viewModel.docState.collectAsStateWithLifecycle()
    val title by viewModel.activeTitle.collectAsStateWithLifecycle()
    val uri by viewModel.activeUri.collectAsStateWithLifecycle()
    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.isSearching.collectAsStateWithLifecycle()
    val pageTexts by viewModel.pageTexts.collectAsStateWithLifecycle()
    val textReady by viewModel.textReady.collectAsStateWithLifecycle()
    val paperMode by viewModel.paperMode.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.pdfAiEnabled.collectAsStateWithLifecycle(initialValue = true)
    val haptic = rememberToolzHapticFeedback()
    val context = LocalContext.current

    var showChrome by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showToc by remember { mutableStateOf(false) }
    var showTextSheet by remember { mutableStateOf(false) }
    var textSheetPage by remember { mutableStateOf<Int?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var keepScreenOn by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(searchQuery) {
        snapshotFlow { searchQuery }.debounce(350).collectLatest { q ->
            if (showSearch) viewModel.searchInDocument(q)
        }
    }

    // Fades track the chrome so pages glide under the floating bars,
    // and relax to the screen edges in fullscreen.
    val topFade by animateDpAsState(
        if (showChrome) 88.dp else 0.dp, tween(300, easing = FastOutSlowInEasing), label = "topFade"
    )
    val bottomFade by animateDpAsState(
        if (showChrome) 104.dp else 0.dp, tween(300, easing = FastOutSlowInEasing), label = "bottomFade"
    )

    Box(Modifier.fillMaxSize()) {
        when {
            doc.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            doc.error == "NO_ACCESS" -> ReaderMessage(
                title = "Can't open this file",
                body = "Toolz lost permission for it. Re-import the file to restore access.",
                actionLabel = "Retry",
                onAction = { viewModel.retryOpen() }
            )

            doc.error != null -> ReaderMessage(
                title = "Can't open this file",
                body = "It looks corrupt or password-protected, which isn't supported.",
                actionLabel = "Retry",
                onAction = { viewModel.retryOpen() }
            )

            doc.isReady && doc.totalPages > 0 -> {
                key(uri.toString()) {
                    ContinuousBody(
                        viewModel = viewModel,
                        docKey = uri.toString(),
                        total = doc.totalPages,
                        current = doc.currentPageIndex,
                        paperMode = paperMode,
                        topFade = topFade,
                        bottomFade = bottomFade,
                        onPage = { viewModel.updatePage(it) },
                        onFullscreen = { showChrome = !showChrome },
                        onShowText = { page ->
                            textSheetPage = page
                            showTextSheet = true
                        }
                    )
                }
            }
        }

        // ── Overlay chrome: floats above the pages, never reflows them ──
        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { -it } +
                    fadeIn(tween(250)),
                exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { -it } +
                    fadeOut(tween(220))
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    ExpressiveTopAppBar(
                        title = {
                            Text(
                                title.ifBlank { "Document" }.removeSuffix(".pdf"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        subtitle = {
                            if (doc.totalPages > 0) {
                                Text("Page ${doc.currentPageIndex + 1} of ${doc.totalPages}")
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { haptic.click(); onBack() },
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    haptic.tick()
                                    if (showSearch) {
                                        showSearch = false
                                        searchQuery = ""
                                        viewModel.clearDocSearch()
                                    } else {
                                        showSearch = true
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search in document")
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    if (toc.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Contents") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.List, null) },
                                            onClick = { showMenu = false; showToc = true }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Document text") },
                                        leadingIcon = { Icon(Icons.Rounded.TextFields, null) },
                                        onClick = { showMenu = false; showTextSheet = true }
                                    )
                                    if (aiEnabled) {
                                        DropdownMenuItem(
                                            text = { Text("AI tools") },
                                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                                            onClick = { showMenu = false; showAiSheet = true }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Appearance") },
                                        leadingIcon = { Icon(Icons.Rounded.Palette, null) },
                                        onClick = { showMenu = false; showAppearance = true }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.activeUri.value?.let {
                                                viewModel.sharePdf(context, it, title)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Print") },
                                        leadingIcon = { Icon(Icons.Rounded.Print, null) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.activeUri.value?.let {
                                                viewModel.printPdf(context, it, title)
                                            }
                                        }
                                    )
                                    if (onConvert != null) {
                                        DropdownMenuItem(
                                            text = { Text("Convert") },
                                            leadingIcon = { Icon(Icons.Rounded.Transform, null) },
                                            onClick = {
                                                showMenu = false
                                                viewModel.activeUri.value?.let { fn ->
                                                    onConvert(fn.toString(), title)
                                                }
                                            }
                                        )
                                    }
                                    if (onAttachToNote != null) {
                                        DropdownMenuItem(
                                            text = { Text("Attach to note") },
                                            leadingIcon = { Icon(Icons.Rounded.AttachFile, null) },
                                            onClick = { showMenu = false; onAttachToNote() }
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("About this file") },
                                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                                        onClick = { showMenu = false; showDetails = true }
                                    )
                                }
                            }
                        },
                        titleHorizontalAlignment = Alignment.CenterHorizontally
                    )
                }
            }

            AnimatedVisibility(
                visible = showChrome && showSearch,
                enter = fadeIn(tween(250)),
                exit = fadeOut(tween(200))
            ) {
                SearchPanel(
                    query = searchQuery,
                    onQuery = { searchQuery = it },
                    searching = searching,
                    results = results,
                    onJump = { page ->
                        haptic.click()
                        viewModel.updatePage(page)
                        requestJump(page)
                    },
                    onClose = {
                        showSearch = false
                        searchQuery = ""
                        viewModel.clearDocSearch()
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = showChrome && doc.isReady && doc.totalPages > 1,
                enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                    fadeIn(tween(250)),
                exit = slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it } +
                    fadeOut(tween(220))
            ) {
                PageBar(
                    total = doc.totalPages,
                    current = doc.currentPageIndex,
                    onScrub = {
                        viewModel.updatePage(it)
                        requestJump(it)
                    }
                )
            }
        }
    }

    if (showToc) {
        TocSheet(
            entries = toc,
            onJump = {
                viewModel.updatePage(it)
                requestJump(it)
                showToc = false
            },
            onDismiss = { showToc = false }
        )
    }

    if (showTextSheet) {
        ReaderTextSheet(
            pageTexts = pageTexts,
            textReady = textReady,
            current = doc.currentPageIndex,
            total = doc.totalPages,
            startPage = (textSheetPage ?: doc.currentPageIndex).coerceIn(0, (doc.totalPages - 1).coerceAtLeast(0)),
            onJump = {
                viewModel.updatePage(it)
                requestJump(it)
            },
            onDismiss = {
                showTextSheet = false
                textSheetPage = null
            }
        )
    }

    if (showAiSheet) {
        AiSheet(
            viewModel = viewModel,
            onDismiss = { showAiSheet = false }
        )
    }

    if (showAppearance) {
        AppearanceSheet(
            paperMode = paperMode,
            keepScreenOn = keepScreenOn,
            onPaperMode = {
                haptic.tick()
                viewModel.setPaperMode(it)
            },
            onKeepScreenOn = {
                haptic.tick()
                keepScreenOn = it
            },
            onDismiss = { showAppearance = false }
        )
    }

    if (showDetails) {
        ReaderDetailsSheet(
            viewModel = viewModel,
            title = title,
            onDismiss = { showDetails = false },
            onOpenNote = onOpenNote
        )
    }
}

/** Jump bus: slider, search, outline and text sheet all drive the page list. */
private val jumpRequest = kotlinx.coroutines.flow.MutableStateFlow(0 to 0)
private fun requestJump(page: Int) {
    jumpRequest.value = page to (jumpRequest.value.second + 1)
}

@Composable
private fun ContinuousBody(
    viewModel: PdfViewModel,
    docKey: String,
    total: Int,
    current: Int,
    paperMode: PdfPaperMode,
    topFade: androidx.compose.ui.unit.Dp,
    bottomFade: androidx.compose.ui.unit.Dp,
    onPage: (Int) -> Unit,
    onFullscreen: () -> Unit,
    onShowText: (Int) -> Unit
) {
    // Fresh list per document, opened at the saved page.
    val list = rememberLazyListState(
        initialFirstVisibleItemIndex = current.coerceIn(0, total - 1)
    )
    // One zoom level for the whole document: the list scales as a single
    // canvas, so pages can never overlap. While zoomed the list locks and
    // drags pan instead; pinch back out to scroll again.
    val zoom = rememberDocZoomState(docKey)
    val first by remember { derivedStateOf { list.firstVisibleItemIndex } }
    LaunchedEffect(first) { if (first != current) onPage(first) }
    LaunchedEffect(Unit) {
        // Guard against replay: a fresh collector must ignore the stale
        // jump that opened a previous document, or the reader would
        // reopen mid-file. Only newer generations scroll.
        var lastSeen = jumpRequest.value.second
        jumpRequest.collectLatest { (page, generation) ->
            if (generation != lastSeen) {
                lastSeen = generation
                try {
                    list.scrollToItem(page.coerceIn(0, total - 1))
                } catch (_: Exception) {
                }
            }
        }
    }
    // Sharper bitmaps only once zoom crosses the threshold.
    val hiRes by remember { derivedStateOf { zoom.scale > 1.5f } }
    LazyColumn(
        state = list,
        userScrollEnabled = !zoom.zoomed,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .docZoomable(zoom)
            .fadingEdges(top = topFade, bottom = bottomFade),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(total, key = { it }) { page ->
            PdfPage(
                viewModel = viewModel,
                pageIndex = page,
                hiRes = hiRes,
                paperMode = paperMode,
                onFullscreen = onFullscreen,
                onShowText = { onShowText(page) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PdfPage(
    viewModel: PdfViewModel,
    pageIndex: Int,
    hiRes: Boolean,
    paperMode: PdfPaperMode,
    onFullscreen: () -> Unit,
    onShowText: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uri = viewModel.activeUri.collectAsStateWithLifecycle().value ?: return
    val engine = viewModel.pdfRenderEngine
    var bitmap by remember(uri, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var aspect by remember(uri, pageIndex) { mutableFloatStateOf(0.707f) }
    val targetWidth = if (hiRes) 1800 else 1080

    LaunchedEffect(uri, pageIndex, targetWidth) {
        aspect = try {
            withContext(Dispatchers.IO) {
                engine.getPageSize(uri, pageIndex)?.let { s ->
                    if (s.width > 0) s.height.toFloat() / s.width.toFloat() else 0.707f
                } ?: 0.707f
            }
        } catch (_: Exception) {
            0.707f
        }
        bitmap = try {
            withContext(Dispatchers.IO) {
                engine.renderPage(uri, pageIndex, targetWidth, highQuality = hiRes)
            }
        } catch (_: Exception) {
            null
        }
    }

    val filter: ColorFilter? = remember(paperMode) {
        when (paperMode) {
            PdfPaperMode.SEPIA -> ColorFilter.colorMatrix(SepiaMatrix)
            PdfPaperMode.NIGHT -> ColorFilter.colorMatrix(NightMatrix)
            else -> null
        }
    }
    val pageBg = when (paperMode) {
        PdfPaperMode.SEPIA -> Color(0xFFF5E9D0)
        PdfPaperMode.NIGHT -> Color(0xFF1B1B1B)
        else -> Color.White
    }

    Surface(
        modifier = modifier
            .aspectRatio(1f / aspect.coerceIn(0.4f, 3f))
            .shadow(4.dp, RoundedCornerShape(12.dp), clip = false)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onFullscreen() },
                    onLongPress = { onShowText() }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = pageBg
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = filter
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun PageBar(
    total: Int,
    current: Int,
    onScrub: (Int) -> Unit
) {
    var slider by remember(current) { mutableFloatStateOf(current.toFloat()) }
    val haptic = rememberToolzHapticFeedback()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Text(
                "${current + 1} / $total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp)
            )
            Slider(
                value = slider,
                onValueChange = {
                    slider = it
                    haptic.tick()
                },
                onValueChangeFinished = {
                    onScrub(slider.toInt().coerceIn(0, total - 1))
                },
                valueRange = 0f..(total - 1).toFloat()
            )
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    results: List<com.frerox.toolz.data.pdf.PdfPageMatch>,
    onJump: (Int) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search in document") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                    } else {
                        androidx.compose.material3.IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            if (query.trim().length >= 2 && !searching) {
                if (results.isEmpty()) {
                    Text(
                        "No matches",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        Modifier.height(200.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(results.take(40), key = { it.pageIndex }) { match ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onJump(match.pageIndex) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "Page ${match.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                match.snippets.firstOrNull()?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
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
private fun ReaderMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(
    entries: List<PdfTocEntry>,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Contents",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(entries, key = { it.title + it.pageIndex }) { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onJump(e.pageIndex) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        e.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "${e.pageIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTextSheet(
    pageTexts: List<String>,
    textReady: Boolean,
    current: Int,
    total: Int,
    startPage: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()
    var page by remember(startPage) { mutableIntStateOf(startPage) }
    val text = pageTexts.getOrNull(page)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Document text", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (textReady) {
                            val n = pageTexts.count { it.isNotBlank() }
                            "$n of $total pages have text"
                        } else {
                            "Reading text…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        text?.takeIf { it.isNotBlank() }?.let {
                            val cb = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE
                            ) as android.content.ClipboardManager
                            cb.setPrimaryClip(
                                android.content.ClipData.newPlainText("PDF", it)
                            )
                            android.widget.Toast.makeText(
                                context, "Page copied", android.widget.Toast.LENGTH_SHORT
                            ).show()
                            haptic.click()
                        }
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy page text")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { page = (page - 1).coerceAtLeast(0) },
                    enabled = page > 0
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous page")
                }
                Text(
                    "Page ${page + 1} of $total",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = { page = (page + 1).coerceAtMost(total - 1) },
                    enabled = page < total - 1
                ) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next page")
                }
                TextButton(onClick = { page = current; onJump(current) }) {
                    Text("Follow")
                }
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                LazyColumn(Modifier.padding(16.dp)) {
                    item {
                        when {
                            text == null -> Text(
                                "Loading…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            text.isBlank() -> Text(
                                "This page has no embedded text — it looks like a scan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            else -> SelectionContainer {
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(
    paperMode: PdfPaperMode,
    keepScreenOn: Boolean,
    onPaperMode: (PdfPaperMode) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            PaperOption(
                label = "Paper",
                description = "Black on white, as printed",
                selected = paperMode == PdfPaperMode.PAPER,
                swatch = Color.White,
                onClick = { onPaperMode(PdfPaperMode.PAPER) }
            )
            PaperOption(
                label = "Sepia",
                description = "Warm tint, easier on the eyes",
                selected = paperMode == PdfPaperMode.SEPIA,
                swatch = Color(0xFFF5E9D0),
                onClick = { onPaperMode(PdfPaperMode.SEPIA) }
            )
            PaperOption(
                label = "Night",
                description = "Light text on dark pages",
                selected = paperMode == PdfPaperMode.NIGHT,
                swatch = Color(0xFF1B1B1B),
                onClick = { onPaperMode(PdfPaperMode.NIGHT) }
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onKeepScreenOn(!keepScreenOn) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen on", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Don't sleep while reading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
            }
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PaperOption(
    label: String,
    description: String,
    selected: Boolean,
    swatch: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = swatch,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.size(32.dp)
        ) {}
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSheet(
    viewModel: PdfViewModel,
    onDismiss: () -> Unit
) {
    val pageTexts by viewModel.pageTexts.collectAsStateWithLifecycle()
    val summary by viewModel.pdfSummary.collectAsStateWithLifecycle()
    val summarizing by viewModel.isSummarizing.collectAsStateWithLifecycle()
    val enhanced by viewModel.enhancedText.collectAsStateWithLifecycle()
    val enhancing by viewModel.isEnhancing.collectAsStateWithLifecycle()
    val offline by viewModel.offlineModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val aiEnabled by viewModel.pdfAiEnabled.collectAsStateWithLifecycle(initialValue = true)
    var askedSummary by remember { mutableStateOf(false) }
    var askedExtract by remember { mutableStateOf(false) }
    val hasText = remember(pageTexts) { pageTexts.any { it.isNotBlank() } }
    // pdfAiEnabled already folds offline mode in, but keep the explicit
    // offline hint so the reason is obvious.
    val runnable = hasText && aiEnabled && !summarizing && !enhancing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("AI tools", style = MaterialTheme.typography.titleLarge)
                if (!aiEnabled && !offline) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AI tools are turned off — enable them in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (offline) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Offline mode is on — AI needs a connection and a Groq key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AiFeatureCard(
                    title = "Summarize",
                    description = "Short overview of the whole document with key points.",
                    actionLabel = if (summarizing) "Summarizing…" else "Summarize",
                    busy = summarizing,
                    enabled = runnable,
                    onRun = {
                        askedSummary = true
                        viewModel.summarizeDocument()
                    }
                ) {
                    when {
                        summarizing -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Text(
                                "Reading the document…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        summary != null -> SelectionContainer {
                            MarkdownContent(summary!!)
                        }

                        askedSummary -> Text(
                            "Couldn't reach AI. Check your connection, Groq key, or offline mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                AiFeatureCard(
                    title = "Smart extract",
                    description = "The same text, cleaned up and structured for reading.",
                    actionLabel = if (enhancing) "Extracting…" else "Extract",
                    busy = enhancing,
                    enabled = runnable,
                    onRun = {
                        askedExtract = true
                        viewModel.enhanceDocument()
                    }
                ) {
                    when {
                        enhancing -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Text(
                                "Structuring the text…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        enhanced != null -> SelectionContainer {
                            MarkdownContent(enhanced!!)
                        }

                        askedExtract -> Text(
                            "Couldn't reach AI. Check your connection, Groq key, or offline mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                if (!hasText) {
                    Text(
                        "This document has no extractable text yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AiFeatureCard(
    title: String,
    description: String,
    actionLabel: String,
    busy: Boolean,
    enabled: Boolean,
    onRun: () -> Unit,
    result: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onRun, enabled = enabled && !busy) {
                    Text(actionLabel)
                }
            }
            result()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderDetailsSheet(
    viewModel: PdfViewModel,
    title: String,
    onDismiss: () -> Unit,
    onOpenNote: ((Int) -> Unit)?
) {
    val uri = viewModel.activeUri.collectAsStateWithLifecycle().value
    val doc by viewModel.docState.collectAsStateWithLifecycle()
    var linked by remember {
        mutableStateOf<List<com.frerox.toolz.data.notepad.NoteAttachment>>(emptyList())
    }
    LaunchedEffect(uri) {
        uri?.let { linked = viewModel.linkedNotesFor(it) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("About this file", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (doc.totalPages == 1) "1 page" else "${doc.totalPages} pages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (linked.isNotEmpty() && onOpenNote != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Attached to ${linked.size} note${if (linked.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                linked.take(5).forEach { att ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenNote(att.noteId) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Note ${att.noteId}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Page ${att.pageHint + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(16.dp))
        }
    }
}
