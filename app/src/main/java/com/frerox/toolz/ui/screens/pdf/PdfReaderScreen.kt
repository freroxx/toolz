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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.data.pdf.PdfTocEntry
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.ExpressiveSlider
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.ToolzWavyCircularProgressIndicator
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.screens.pdf.components.PdfCover
import com.frerox.toolz.ui.screens.pdf.components.rememberZoomableState
import com.frerox.toolz.ui.screens.pdf.components.zoomablePage
import com.frerox.toolz.ui.theme.SquircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SepiaMatrix = ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)
private val InvertMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

/**
 * Remake reader: per-page zoom, paged/continuous, scrubber + strip,
 * embedded-text search + TOC + paper modes. Chrome tap-to-hide.
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
    val readingMode by viewModel.readingMode.collectAsStateWithLifecycle()
    val paperMode by viewModel.paperMode.collectAsStateWithLifecycle()
    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.isSearching.collectAsStateWithLifecycle()
    val pageTexts by viewModel.pageTexts.collectAsStateWithLifecycle()
    val textReady by viewModel.textReady.collectAsStateWithLifecycle()
    val haptic = rememberToolzHapticFeedback()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showChrome by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showToc by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showTextSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
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

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            ExpressiveTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            (title.ifBlank { "DOCUMENT" }).removeSuffix(".pdf").uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (doc.totalPages > 0) Text(
                            "PAGE ${doc.currentPageIndex + 1} / ${doc.totalPages}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                    }
                },
                navigationIcon = {
                    ToolzExpressiveIconButton(
                        onClick = { haptic.click(); onBack() },
                        modifier = Modifier.padding(start = 8.dp).size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MediumExpressiveShape
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp))
                    }
                },
                actions = {
                    ToolzExpressiveIconButton(
                        onClick = { haptic.tick(); showSearch = !showSearch },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (showSearch) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MediumExpressiveShape
                    ) {
                        Icon(
                            Icons.Rounded.Search, "Search", Modifier.size(20.dp),
                            tint = if (showSearch) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (toc.isNotEmpty()) ToolzExpressiveIconButton(
                        onClick = { haptic.tick(); showToc = true },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MediumExpressiveShape
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.List, "Contents", Modifier.size(20.dp))
                    }
                    ToolzExpressiveIconButton(
                        onClick = { haptic.tick(); showTextSheet = true },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MediumExpressiveShape
                    ) {
                        Icon(Icons.Rounded.TextFields, "Text", Modifier.size(20.dp))
                    }
                    Box {
                        ToolzExpressiveIconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.padding(end = 8.dp).size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            shape = MediumExpressiveShape
                        ) {
                            Icon(Icons.Rounded.MoreVert, "More", Modifier.size(20.dp))
                        }
                        ReaderOverflow(
                            expanded = showMenu,
                            paperMode = paperMode,
                            readingMode = readingMode,
                            keepScreenOn = keepScreenOn,
                            onDismiss = { showMenu = false },
                            onPaper = { viewModel.setPaperMode(it); haptic.tick() },
                            onReading = { viewModel.setReadingMode(it); haptic.tick() },
                            onKeepScreen = { keepScreenOn = !keepScreenOn; haptic.tick() },
                            onShare = {
                                showMenu = false
                                viewModel.activeUri.value?.let { viewModel.sharePdf(context, it, title) }
                            },
                            onPrint = {
                                showMenu = false
                                viewModel.activeUri.value?.let { viewModel.printPdf(context, it, title) }
                            },
                            onConvert = onConvert?.let { fn ->
                                {
                                    showMenu = false
                                    viewModel.activeUri.value?.let { fn(it.toString(), title) }
                                }
                            },
                            onDetails = { showMenu = false; showDetails = true },
                            onAttach = onAttachToNote?.let { { showMenu = false; it() } }
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                titleHorizontalAlignment = Alignment.CenterHorizontally
            )
        }

        // Search panel
        AnimatedVisibility(
            visible = showChrome && showSearch,
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut()
        ) {
            ReaderSearchPanel(
                query = searchQuery,
                onQuery = { searchQuery = it },
                searching = searching,
                results = results,
                onJump = { page ->
                    haptic.click()
                    viewModel.updatePage(page)
                    requestJump(page)
                },
                onClose = { showSearch = false; searchQuery = ""; viewModel.clearDocSearch() }
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                doc.isLoading -> ReaderLoading()
                doc.error == "NO_ACCESS" -> ReaderError(
                    title = "CAN'T OPEN FILE",
                    body = "Toolz lost permission for this file. Re-import it to restore access.",
                    action = "RETRY",
                    onAction = { viewModel.retryOpen() }
                )

                doc.error != null -> ReaderError(
                    title = "UNREADABLE PDF",
                    body = "This file looks corrupt or password-protected, which the reader doesn't support yet.",
                    action = "RETRY",
                    onAction = { viewModel.retryOpen() }
                )

                doc.isReady && doc.totalPages > 0 -> {
                    if (readingMode == PdfReadingMode.PAGED) {
                        PagedBody(
                            viewModel = viewModel,
                            total = doc.totalPages,
                            current = doc.currentPageIndex,
                            paperMode = paperMode,
                            onPage = { viewModel.updatePage(it) },
                            onTap = { showChrome = !showChrome }
                        )
                    } else {
                        ContinuousBody(
                            viewModel = viewModel,
                            total = doc.totalPages,
                            current = doc.currentPageIndex,
                            paperMode = paperMode,
                            onPage = { viewModel.updatePage(it) },
                            onTap = { showChrome = !showChrome }
                        )
                    }
                }
            }
        }

        // Bottom chrome: scrubber + strip
        AnimatedVisibility(
            visible = showChrome && doc.isReady && doc.totalPages > 1,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.zIndex(1f)
        ) {
            ReaderBottomBar(
                viewModel = viewModel,
                total = doc.totalPages,
                current = doc.currentPageIndex,
                onScrub = {
                    haptic.tick()
                    viewModel.updatePage(it)
                    requestJump(it)
                }
            )
        }
    }

    if (showToc) TocSheet(entries = toc, onJump = {
        viewModel.updatePage(it)
        requestJump(it)
        showToc = false
    }, onDismiss = { showToc = false })

    if (showTextSheet) ReaderTextSheet(
        viewModel = viewModel,
        pageTexts = pageTexts,
        textReady = textReady,
        current = doc.currentPageIndex,
        total = doc.totalPages,
        onJump = {
            viewModel.updatePage(it)
            requestJump(it)
        },
        onDismiss = { showTextSheet = false }
    )

    if (showDetails) ReaderDetailsSheet(
        viewModel = viewModel,
        title = title,
        onDismiss = { showDetails = false },
        onOpenNote = onOpenNote
    )
}

/** Cross-composable jump bus: bottom bar / search / TOC all drive the pager. */
private val pagerJump = kotlinx.coroutines.flow.MutableStateFlow(0 to 0)
private fun requestJump(page: Int) { pagerJump.value = page to (pagerJump.value.second + 1) }

@Composable
private fun PagedBody(
    viewModel: PdfViewModel,
    total: Int,
    current: Int,
    paperMode: PdfPaperMode,
    onPage: (Int) -> Unit,
    onTap: () -> Unit
) {
    val pager = rememberPagerState(initialPage = current.coerceIn(0, total - 1), pageCount = { total })
    LaunchedEffect(current) {
        if (pager.currentPage != current && pager.isScrollInProgress.not()) {
            try { pager.scrollToPage(current) } catch (_: Exception) { }
        }
    }
    LaunchedEffect(pager) {
        pagerJump.collectLatest { (page, _) ->
            if (page != pager.currentPage) try { pager.scrollToPage(page.coerceIn(0, total - 1)) } catch (_: Exception) { }
        }
    }
    LaunchedEffect(pager.currentPage, pager.isScrollInProgress) {
        if (!pager.isScrollInProgress && pager.currentPage != current) onPage(pager.currentPage)
    }
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
        pageSpacing = 14.dp,
        beyondViewportPageCount = 1
    ) { page ->
        PdfPage(
            viewModel = viewModel,
            pageIndex = page,
            paperMode = paperMode,
            onTap = onTap,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ContinuousBody(
    viewModel: PdfViewModel,
    total: Int,
    current: Int,
    paperMode: PdfPaperMode,
    onPage: (Int) -> Unit,
    onTap: () -> Unit
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceIn(0, total - 1))
    val first by remember { derivedStateOf { list.firstVisibleItemIndex } }
    LaunchedEffect(first) { if (first != current) onPage(first) }
    LaunchedEffect(Unit) {
        pagerJump.collectLatest { (page, _) ->
            try { list.scrollToItem(page.coerceIn(0, total - 1)) } catch (_: Exception) { }
        }
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceDim.copy(alpha = 0.35f)),
        contentPadding = PaddingValues(vertical = 20.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(total, key = { it }) { page ->
            PdfPage(
                viewModel = viewModel,
                pageIndex = page,
                paperMode = paperMode,
                onTap = onTap,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PdfPage(
    viewModel: PdfViewModel,
    pageIndex: Int,
    paperMode: PdfPaperMode,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uri = viewModel.activeUri.collectAsStateWithLifecycle().value ?: return
    val engine = viewModel.pdfRenderEngine
    var bitmap by remember(uri, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var aspect by remember(uri, pageIndex) { mutableFloatStateOf(0.707f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val holder = rememberZoomableState(maxScale = 5f, onZoomChanged = { zoom = it })
    val targetWidth = remember(zoom) { if (zoom > 1.5f) 1800 else 1080 }

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
                engine.renderPage(uri, pageIndex, targetWidth, highQuality = zoom > 1.5f)
            }
        } catch (_: Exception) {
            null
        }
    }

    val filter: ColorFilter? = remember(paperMode) {
        when (paperMode) {
            PdfPaperMode.SEPIA -> ColorFilter.colorMatrix(SepiaMatrix)
            PdfPaperMode.NIGHT -> ColorFilter.colorMatrix(InvertMatrix)
            else -> null
        }
    }
    val pageBg = when (paperMode) {
        PdfPaperMode.SEPIA -> Color(0xFFF5E9D0)
        PdfPaperMode.NIGHT -> Color(0xFF1A1A1A)
        else -> Color.White
    }

    Surface(
        modifier = modifier
            .aspectRatio(1f / aspect.coerceIn(0.4f, 3f))
            .shadow(10.dp, MediumExpressiveShape, clip = false)
            .zoomablePage(holder)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        shape = MediumExpressiveShape,
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
                ToolzWavyCircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    trackColor = Color.Transparent
                )
            }
            Surface(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                shape = SmallExpressiveShape,
                color = Color.Black.copy(alpha = 0.35f)
            ) {
                Text(
                    "${pageIndex + 1}", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(viewModel: PdfViewModel, total: Int, current: Int, onScrub: (Int) -> Unit) {
    var slider by remember(current) { mutableFloatStateOf(current.toFloat()) }
    val uri = viewModel.activeUri.collectAsStateWithLifecycle().value
    val engine = viewModel.pdfRenderEngine
    val haptic = rememberToolzHapticFeedback()
    val scope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        shape = LargeExpressiveShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = SmallExpressiveShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), modifier = Modifier.size(26.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoStories, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    "PAGE ${current + 1}", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp
                )
                Text(
                    "/ $total", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.weight(1f))
                var lastTick by remember { mutableIntStateOf(current) }
                ExpressiveSlider(
                    value = slider,
                    onValueChange = {
                        slider = it
                        val p = it.toInt().coerceIn(0, total - 1)
                        if (p != lastTick) {
                            lastTick = p
                            haptic.tick()
                        }
                    },
                    onValueChangeFinished = { onScrub(slider.toInt().coerceIn(0, total - 1)) },
                    valueRange = 0f..(total - 1).toFloat(),
                    modifier = Modifier.weight(2.2f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            val window = remember(current, total) {
                val lo = (current - 6).coerceAtLeast(0)
                val hi = (current + 14).coerceAtMost(total - 1)
                (lo..hi).toList()
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalFadingEdges(left = 0.dp, right = 32.dp)
            ) {
                items(window, key = { it }) { page ->
                    val selected = page == current
                    Surface(
                        shape = SmallExpressiveShape,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.size(width = 44.dp, height = 58.dp)
                            .clickable { onScrub(page) }
                    ) {
                        if (uri != null) {
                            PdfCover(uri = uri, renderEngine = engine, widthPx = 160, shape = SmallExpressiveShape)
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${page + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderSearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    results: List<com.frerox.toolz.data.pdf.PdfPageMatch>,
    onJump: (Int) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = LargeExpressiveShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search in document…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        inner()
                    }
                )
                if (searching) ToolzWavyCircularProgressIndicator(Modifier.size(20.dp))
                ToolzExpressiveIconButton(onClick = onClose, modifier = Modifier.size(34.dp), shape = SmallExpressiveShape) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                }
            }
            if (query.trim().length >= 2 && !searching) {
                if (results.isEmpty()) {
                    Text(
                        "No matches", Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "${results.size} PAGE${if (results.size == 1) "" else "S"}",
                        Modifier.padding(top = 8.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    LazyColumn(Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results.take(40), key = { it.pageIndex }) { match ->
                            Surface(
                                shape = MediumExpressiveShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth().clickable { onJump(match.pageIndex) }
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        "PAGE ${match.pageIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    match.snippets.firstOrNull()?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
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
private fun ReaderOverflow(
    expanded: Boolean,
    paperMode: PdfPaperMode,
    readingMode: PdfReadingMode,
    keepScreenOn: Boolean,
    onDismiss: () -> Unit,
    onPaper: (PdfPaperMode) -> Unit,
    onReading: (PdfReadingMode) -> Unit,
    onKeepScreen: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onConvert: (() -> Unit)?,
    onDetails: () -> Unit,
    onAttach: (() -> Unit)?
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        offset = DpOffset(0.dp, 8.dp)
    ) {
        DropdownMenuItem(
            text = { Text(if (readingMode == PdfReadingMode.CONTINUOUS) "Paged mode" else "Continuous mode", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.SwapVert, null, Modifier.size(20.dp)) },
            onClick = {
                onDismiss()
                onReading(if (readingMode == PdfReadingMode.CONTINUOUS) PdfReadingMode.PAGED else PdfReadingMode.CONTINUOUS)
            },
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        DropdownMenuItem(
            text = {
                Text(
                    when (paperMode) {
                        PdfPaperMode.PAPER -> "Sepia paper"
                        PdfPaperMode.SEPIA -> "Night paper"
                        PdfPaperMode.NIGHT -> "Paper white"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            leadingIcon = { Icon(Icons.Rounded.DarkMode, null, Modifier.size(20.dp)) },
            onClick = {
                onDismiss()
                onPaper(
                    when (paperMode) {
                        PdfPaperMode.PAPER -> PdfPaperMode.SEPIA
                        PdfPaperMode.SEPIA -> PdfPaperMode.NIGHT
                        PdfPaperMode.NIGHT -> PdfPaperMode.PAPER
                    }
                )
            },
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        DropdownMenuItem(
            text = { Text(if (keepScreenOn) "Screen: keep-on ✓" else "Keep screen on", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.Description, null, Modifier.size(20.dp)) },
            onClick = { onDismiss(); onKeepScreen() },
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DropdownMenuItem(
            text = { Text("Details", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.Info, null, Modifier.size(20.dp)) },
            onClick = onDetails,
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        DropdownMenuItem(
            text = { Text("Share", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(20.dp)) },
            onClick = onShare,
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        DropdownMenuItem(
            text = { Text("Print", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.Print, null, Modifier.size(20.dp)) },
            onClick = onPrint,
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        if (onConvert != null) DropdownMenuItem(
            text = { Text("Convert", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.Transform, null, Modifier.size(20.dp)) },
            onClick = onConvert,
            modifier = Modifier.clip(MediumExpressiveShape)
        )
        if (onAttach != null) DropdownMenuItem(
            text = { Text("Attach to note", fontWeight = FontWeight.Bold) },
            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp)) },
            onClick = onAttach,
            modifier = Modifier.clip(MediumExpressiveShape)
        )
    }
}

@Composable
private fun ReaderLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ToolzWavyCircularProgressIndicator(Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "OPENING DOCUMENT", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black, letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ReaderError(title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Surface(Modifier.size(88.dp), shape = SquircleShape, color = MaterialTheme.colorScheme.errorContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Description, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                body, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(onClick = onAction, shape = BouncyShape) {
                Text(action, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TocSheet(entries: List<PdfTocEntry>, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("CONTENTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.title + it.pageIndex }) { e ->
                    Surface(
                        shape = MediumExpressiveShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth().clickable { onJump(e.pageIndex) }
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("p.${e.pageIndex + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReaderTextSheet(
    viewModel: PdfViewModel,
    pageTexts: List<String>,
    textReady: Boolean,
    current: Int,
    total: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()
    var query by remember { mutableStateOf("") }
    var page by remember(current) { mutableIntStateOf(current) }
    val text = pageTexts.getOrNull(page)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DOCUMENT TEXT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(
                        if (textReady) "${pageTexts.count { it.isNotBlank() }} / $total PAGES WITH TEXT" else "INDEXING…",
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp
                    )
                }
                ToolzExpressiveIconButton(
                    onClick = {
                        text?.let {
                            val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("PDF", it))
                            android.widget.Toast.makeText(context, "Page copied", android.widget.Toast.LENGTH_SHORT).show()
                            haptic.click()
                        }
                    },
                    shape = MediumExpressiveShape
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolzExpressiveIconButton(
                    onClick = { page = (page - 1).coerceAtLeast(0) },
                    enabled = page > 0,
                    shape = SmallExpressiveShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("‹", fontWeight = FontWeight.Black)
                }
                Text(
                    "PAGE ${page + 1} / $total", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                ToolzExpressiveIconButton(
                    onClick = { page = (page + 1).coerceAtMost(total - 1) },
                    enabled = page < total - 1,
                    shape = SmallExpressiveShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Text("›", fontWeight = FontWeight.Black)
                }
                androidx.compose.material3.TextButton(onClick = { page = current; onJump(current) }) {
                    Text("FOLLOW", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = LargeExpressiveShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth().height(320.dp)
            ) {
                LazyColumn(Modifier.padding(18.dp)) {
                    item {
                        when {
                            text == null -> Text("Loading page text…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            text.isBlank() -> Column {
                                Text(
                                    "This looks like a scanned page — no embedded text.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Use OCR from the vault overflow or converter for image-only PDFs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            else -> SelectionContainer {
                                Text(text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
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
    var linked by remember { mutableStateOf<List<com.frerox.toolz.data.notepad.NoteAttachment>>(emptyList()) }
    LaunchedEffect(uri) {
        uri?.let { linked = viewModel.linkedNotesFor(it) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("DETAILS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                "${doc.totalPages} pages · page ${doc.currentPageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (linked.isNotEmpty() && onOpenNote != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(
                    "LINKED NOTES (${linked.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                linked.take(5).forEach { att ->
                    Surface(
                        shape = MediumExpressiveShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenNote(att.noteId) }
                    ) {
                        Text(
                            "Note #${att.noteId} · opens p.${att.pageHint + 1}",
                            Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
