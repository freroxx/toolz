package com.frerox.toolz.ui.screens.media.ai

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.util.VibrationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingAiBottomSheet(
    viewModel: NowPlayingAiViewModel,
    onDismiss: () -> Unit,
    vibrationManager: VibrationManager
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        NowPlayingAiContent(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = onDismiss,
            vibrationManager = vibrationManager
        )
    }
}

@Composable
fun NowPlayingAiContent(
    uiState: NowPlayingAiUiState,
    viewModel: NowPlayingAiViewModel,
    onDismiss: () -> Unit,
    vibrationManager: VibrationManager
) {
    Box(
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 42.dp, topEnd = 42.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
    ) {
        // Atmospheric Background Blur
        AsyncImage(
            model = uiState.currentSong?.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.15f)
                .blur(80.dp),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.fillMaxSize()) {
            AiHeader(
                selectedTab = uiState.selectedTab,
                onTabSelected = { 
                    vibrationManager.vibrateClick()
                    viewModel.selectTab(it) 
                },
                onRefresh = {
                    vibrationManager.vibrateClick()
                    viewModel.refreshCurrentTab()
                },
                onDismiss = onDismiss
            )

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = uiState.selectedTab,
                    transitionSpec = {
                        fadeIn(tween(400)) + slideInHorizontally { if (targetState.index > initialState.index) it else -it } togetherWith
                                fadeOut(tween(300)) + slideOutHorizontally { if (targetState.index > initialState.index) -it else it }
                    },
                    label = "TabContent"
                ) { targetTab ->
                    when (targetTab) {
                        AiTab.Lyrics -> LyricsTab(uiState.lyricsState, viewModel, vibrationManager)
                        AiTab.MoreInfo -> MoreInfoTab(uiState.moreInfoState)
                        AiTab.MusicTaste -> TasteTab(uiState.tasteState)
                    }
                }
            }
        }
    }
}

@Composable
fun AiHeader(
    selectedTab: AiTab,
    onTabSelected: (AiTab) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(44.dp, 5.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                shape = CircleShape
            ) {}
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "MUSIC INTELLIGENCE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(24.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf(AiTab.Lyrics, AiTab.MoreInfo, AiTab.MusicTaste)
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                val backgroundColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    label = "tabBg"
                )
                val contentColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabContent"
                )

                Surface(
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = backgroundColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tab.title.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = contentColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(modifier = Modifier.alpha(0.05f))
    }
}

@Composable
fun LyricsTab(
    state: AiLyricsState,
    viewModel: NowPlayingAiViewModel,
    vibrationManager: VibrationManager
) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    val playbackPosition by viewModel.playbackPositionMs.collectAsState()
    val listState = rememberLazyListState()

    // Perfect Sync: Competing with the 0.5s delay by shifting the "lookahead" window.
    val syncedPosition = playbackPosition + 500L

    val currentLineIndex = remember(syncedPosition, state.syncedLyrics) {
        if (state.isSynced && state.syncedLyrics.isNotEmpty()) {
            state.syncedLyrics.indexOfLast { it.timeMs <= syncedPosition }.coerceAtLeast(0)
        } else -1
    }

    // Smooth Auto-scroll with specific duration
    LaunchedEffect(currentLineIndex, state.isAutoScrollEnabled) {
        if (state.isAutoScrollEnabled && currentLineIndex >= 0) {
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -450
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            AiShimmerLyrics()
        } else if (state.lyrics.isEmpty() || state.lyrics == "Lyrics not found.") {
            AiEmptyState(message = "Tap Refresh to fetch lyrics")
        } else {
            if (state.isSynced) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 80.dp, bottom = 80.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { 
                                    if (state.isAutoScrollEnabled) {
                                        viewModel.onManualScroll()
                                        vibrationManager.vibrateClick()
                                    }
                                },
                                onDrag = { _, _ -> }
                            )
                        },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp), // More compact spacing
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(state.syncedLyrics) { index, line ->
                        val isCurrent = index == currentLineIndex
                        val color by animateColorAsState(
                            if (isCurrent) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            animationSpec = tween(600),
                            label = "lineColor"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isCurrent) 1.12f else 1f,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                            label = "lineScale"
                        )
                        val blurRadius by animateDpAsState(
                            targetValue = if (isCurrent) 0.dp else 1.5.dp,
                            animationSpec = tween(600),
                            label = "lineBlur"
                        )
                        val alpha by animateFloatAsState(
                            targetValue = if (isCurrent) 1f else 0.45f,
                            animationSpec = tween(600),
                            label = "lineAlpha"
                        )

                        Text(
                            text = line.content,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp, // Slightly smaller for compactness
                                lineHeight = 38.sp, // Compact line height
                                letterSpacing = (-0.5).sp,
                                textAlign = TextAlign.Center
                            ),
                            color = color,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .blur(blurRadius)
                        )
                    }
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 40.dp, bottom = 40.dp)
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MarkdownText(
                        markdown = state.lyrics,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 34.sp,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(160.dp))
                }
            }

            // Enhanced FAB with subtle glow
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Surface(
                    onClick = { 
                        vibrationManager.vibrateClick()
                        viewModel.toggleAutoScroll() 
                    },
                    modifier = Modifier
                        .height(64.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(32.dp),
                            spotColor = if (state.isAutoScrollEnabled) MaterialTheme.colorScheme.primary else Color.Black
                        ),
                    shape = RoundedCornerShape(32.dp),
                    color = if (state.isAutoScrollEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (state.isAutoScrollEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconScale by animateFloatAsState(if (state.isAutoScrollEnabled) 1.2f else 1f)
                        Icon(
                            if (state.isAutoScrollEnabled) Icons.Rounded.Sync else Icons.Rounded.SyncDisabled, 
                            null, 
                            modifier = Modifier.size(24.dp).scale(iconScale)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            if (state.isAutoScrollEnabled) "SYNC ACTIVE" else "MANUAL MODE", 
                            fontWeight = FontWeight.Black, 
                            style = MaterialTheme.typography.labelLarge,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MoreInfoTab(state: AiMoreInfoState) {
    if (state.isLoading) {
        AiShimmerInfo()
    } else if (state.artistVitals.isEmpty() && state.songMeaning.isEmpty()) {
        AiEmptyState(message = "Tap Refresh to generate insights")
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fadingEdges(top = 24.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            if (state.artistVitals.isNotEmpty()) {
                InfoCard(
                    title = "Artist Heritage",
                    content = state.artistVitals,
                    icon = Icons.Rounded.Person,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.songMeaning.isNotEmpty()) {
                InfoCard(
                    title = "Deep Meaning",
                    content = state.songMeaning,
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun InfoCard(title: String, content: String, icon: ImageVector, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = color
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            )
        }
    }
}

@Composable
fun TasteTab(state: AiTasteState) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.recommendations.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "CURATED FOR YOUR SOUL",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.isLoading) {
            AiShimmerTaste()
        } else if (state.recommendations.isEmpty()) {
            AiEmptyState(message = "Tap Refresh to discover music")
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth().fadingEdges(top = 0.dp, bottom = 0.dp) // Row fading edges would be horizontal, but fadingEdges is vertical.
            ) {
                items(state.recommendations) { rec ->
                    RecommendationCard(rec)
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(rec: AiRecommendation) {
    Surface(
        modifier = Modifier
            .width(280.dp)
            .height(320.dp)
            .bouncyClick { /* Handle play */ },
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle Gradient background
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                )
            ))

            Column(modifier = Modifier.padding(24.dp)) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = rec.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = rec.artist.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = rec.explanation,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                Icons.Rounded.PlayCircleFilled,
                null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(40.dp).alpha(0.8f),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun MarkdownText(markdown: String, style: androidx.compose.ui.text.TextStyle) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val annotatedString = remember(markdown, primaryColor, onSurfaceColor) { 
        parseMarkdown(markdown, primaryColor, onSurfaceColor) 
    }
    Text(
        text = annotatedString, 
        style = style,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

fun parseMarkdown(text: String, primaryColor: Color, onSurfaceColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            when {
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 26.sp, color = primaryColor)) {
                        append(line.substring(2))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                        append(line.substring(3))
                    }
                }
                else -> {
                    val parts = line.split("**")
                    parts.forEachIndexed { i, part ->
                        if (i % 2 == 1) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Black, color = onSurfaceColor)) {
                                appendInlineFormatting(part)
                            }
                        } else {
                            appendInlineFormatting(part)
                        }
                    }
                }
            }
            if (index < lines.size - 1) append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineFormatting(text: String) {
    val parts = text.split("*")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(part)
            }
        } else {
            append(part)
        }
    }
}

@Composable
fun AiShimmerLyrics() {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        repeat(10) { i ->
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(if (i % 3 == 0) 0.5f else 0.9f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }
    }
}

@Composable
fun AiShimmerInfo() {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        repeat(2) {
            ShimmerBox(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(32.dp)))
        }
    }
}

@Composable
fun AiShimmerTaste() {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        repeat(2) {
            ShimmerBox(Modifier.size(280.dp, 320.dp).clip(RoundedCornerShape(36.dp)))
        }
    }
}

@Composable
fun ShimmerBox(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    )
}

@Composable
fun AiEmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Icon(
                Icons.Rounded.TipsAndUpdates,
                null,
                modifier = Modifier.size(80.dp).alpha(0.1f),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AiSparkleButton(
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val glowScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    
    val glowAlpha by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .scale(glowScale)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        
        Surface(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = "AI Sparkle",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
