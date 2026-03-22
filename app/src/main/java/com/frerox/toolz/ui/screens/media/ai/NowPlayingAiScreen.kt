package com.frerox.toolz.ui.screens.media.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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

// ─────────────────────────────────────────────────────────────────────────────
// Root Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingAiBottomSheet(
    viewModel: NowPlayingAiViewModel,
    onDismiss: () -> Unit,
    vibrationManager: VibrationManager
) {
    val uiState by viewModel.uiState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.65f)
    ) {
        NowPlayingAiContent(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = onDismiss,
            vibrationManager = vibrationManager
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content shell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NowPlayingAiContent(
    uiState: NowPlayingAiUiState,
    viewModel: NowPlayingAiViewModel,
    onDismiss: () -> Unit,
    vibrationManager: VibrationManager
) {
    Box(
        modifier = Modifier
            .fillMaxHeight(0.93f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
    ) {
        // Blurred album art atmospheric background
        AsyncImage(
            model = uiState.currentSong?.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.1f)
                .blur(100.dp),
            contentScale = ContentScale.Crop,
            error = painterResource(android.R.drawable.ic_media_play)
        )

        // Vignette over blur — surface fades in from bottom so text remains legible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
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
                        val dir = if (targetState.index > initialState.index) 1 else -1
                        (fadeIn(tween(280)) + slideInHorizontally(
                            spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
                        ) { dir * it / 3 })
                            .togetherWith(
                                fadeOut(tween(180)) + slideOutHorizontally(
                                    spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
                                ) { -dir * it / 3 }
                            )
                    },
                    label = "AiTabContent"
                ) { tab ->
                    when (tab) {
                        AiTab.Lyrics     -> LyricsTab(uiState.lyricsState, viewModel, vibrationManager)
                        AiTab.MoreInfo   -> MoreInfoTab(uiState.moreInfoState)
                        AiTab.MusicTaste -> TasteTab(uiState.tasteState)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiHeader(
    selectedTab: AiTab,
    onTabSelected: (AiTab) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        // Drag handle
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            )
        }

        // Title row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sparkle icon + label
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "MUSIC AI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Powered by LRC lib.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Refresh — small pill
                Surface(
                    onClick = onRefresh,
                    modifier = Modifier.height(36.dp).wrapContentWidth().bouncyClick {},
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("Refresh", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }
                // Dismiss
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Tab pills — matching PillTabRow style from MusicPlayerScreen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(AiTab.Lyrics, AiTab.MoreInfo, AiTab.MusicTaste).forEach { tab ->
                val isSelected = selectedTab == tab
                val weight by animateFloatAsState(
                    if (isSelected) 1.25f else 1f,
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                    label = "aiTabW"
                )
                Surface(
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .weight(weight)
                        .height(44.dp)
                        .bouncyClick {},
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(
                                imageVector = when (tab) {
                                    AiTab.Lyrics     -> Icons.Rounded.Lyrics
                                    AiTab.MoreInfo   -> Icons.AutoMirrored.Rounded.MenuBook
                                    AiTab.MusicTaste -> Icons.Rounded.Recommend
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn(tween(180)) + expandHorizontally(tween(220, easing = FastOutSlowInEasing)),
                                exit  = fadeOut(tween(100)) + shrinkHorizontally(tween(180, easing = FastOutSlowInEasing))
                            ) {
                                Text(
                                    tab.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(modifier = Modifier.alpha(0.06f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Lyrics Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LyricsTab(
    state: AiLyricsState,
    viewModel: NowPlayingAiViewModel,
    vibrationManager: VibrationManager
) {
    // Keep screen on while lyrics are displayed
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val playbackPosition by viewModel.playbackPositionMs.collectAsState()
    val listState = rememberLazyListState()

    // Shift playback position forward slightly to compensate for render latency
    val syncedPosition = playbackPosition + 400L

    val currentLineIndex = remember(syncedPosition, state.syncedLyrics) {
        if (state.isSynced && state.syncedLyrics.isNotEmpty()) {
            state.syncedLyrics.indexOfLast { it.timeMs <= syncedPosition }.coerceAtLeast(0)
        } else -1
    }

    // ── Better manual scroll detection ───────────────────────────────────────
    // Track whether the list is currently being programmatically scrolled.
    // If the user physically moves the list (isScrollInProgress while NOT
    // triggered by animateScrollToItem) we consider it a manual gesture.
    val isAutoScrollInFlight = remember { mutableStateOf(false) }

    LaunchedEffect(currentLineIndex, state.isAutoScrollEnabled) {
        if (state.isAutoScrollEnabled && currentLineIndex >= 0) {
            isAutoScrollInFlight.value = true
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -420
            )
            isAutoScrollInFlight.value = false
        }
    }

    // Detect user-initiated scroll: list is scrolling AND it wasn't us who started it
    val isScrolling = listState.isScrollInProgress
    LaunchedEffect(isScrolling) {
        if (isScrolling && !isAutoScrollInFlight.value && state.isAutoScrollEnabled) {
            vibrationManager.vibrateClick()
            viewModel.onManualScroll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> AiShimmerLyrics()

            state.lyrics.isEmpty() || state.lyrics == "Lyrics not found." ->
                AiEmptyState(
                    icon = Icons.Rounded.Lyrics,
                    message = "No lyrics found",
                    sub = "Tap Refresh to fetch lyrics"
                )

            state.isSynced -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 72.dp, bottom = 100.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(state.syncedLyrics) { index, line ->
                        val isCurrent = index == currentLineIndex
                        // Batch all animations under one key to avoid independent recomp
                        val animFrac by animateFloatAsState(
                            if (isCurrent) 1f else 0f,
                            tween(550),
                            label = "lyricFrac$index"
                        )
                        val scale by animateFloatAsState(
                            if (isCurrent) 1.10f else 1f,
                            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                            label = "lyricScale$index"
                        )
                        val primary = MaterialTheme.colorScheme.primary
                        val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                        // Interpolate color manually to avoid animateColorAsState overhead in list
                        val r = dim.red + (primary.red - dim.red) * animFrac
                        val g = dim.green + (primary.green - dim.green) * animFrac
                        val b = dim.blue + (primary.blue - dim.blue) * animFrac
                        val a = 0.18f + (1f - 0.18f) * animFrac

                        Text(
                            text = line.content,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                lineHeight = 32.sp,
                                letterSpacing = (-0.3).sp,
                                textAlign = TextAlign.Center
                            ),
                            color = Color(r, g, b, a),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    // Blur inactive lines softly (API 31+); fall back to alpha
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        renderEffect = if (!isCurrent && animFrac < 0.5f)
                                            AndroidRenderEffect.createBlurEffect(1.2f, 1.2f, AndroidShader.TileMode.CLAMP)
                                                .asComposeRenderEffect()
                                        else null
                                    }
                                }
                        )
                    }
                }
            }

            else -> {
                // Plain (non-synced) lyrics scroll
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 32.dp, bottom = 80.dp)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MarkdownText(
                        markdown = state.lyrics,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 32.sp,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center
                        )
                    )
                    Spacer(Modifier.height(120.dp))
                }
            }
        }

        // ── Sync / Manual toggle FAB ──────────────────────────────────────────
        if (!state.isLoading && state.isSynced) {
            val fabScale by animateFloatAsState(
                if (state.isAutoScrollEnabled) 1f else 1.04f,
                spring(Spring.DampingRatioMediumBouncy),
                label = "fabScale"
            )
            Surface(
                onClick = {
                    vibrationManager.vibrateClick()
                    viewModel.toggleAutoScroll()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 22.dp)
                    .height(56.dp)
                    .scale(fabScale)
                    .shadow(
                        elevation = if (state.isAutoScrollEnabled) 10.dp else 6.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = if (state.isAutoScrollEnabled) MaterialTheme.colorScheme.primary else Color.Black
                    )
                    .bouncyClick {},
                shape = RoundedCornerShape(28.dp),
                color = if (state.isAutoScrollEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (state.isAutoScrollEnabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Crossfade(state.isAutoScrollEnabled, animationSpec = tween(220), label = "syncIcon") { active ->
                        Icon(
                            if (active) Icons.Rounded.Sync else Icons.Rounded.SyncDisabled,
                            null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        if (state.isAutoScrollEnabled) "SYNCED" else "MANUAL",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Manual mode pill banner — shown when user has taken control
            if (!state.isAutoScrollEnabled) {
                Surface(
                    onClick = {
                        vibrationManager.vibrateClick()
                        viewModel.toggleAutoScroll()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 22.dp)
                        .bouncyClick {},
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(Icons.Rounded.TouchApp, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Tap MANUAL to re-sync",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// More Info Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MoreInfoTab(state: AiMoreInfoState) {
    when {
        state.isLoading -> AiShimmerInfo()

        state.artistVitals.isEmpty() && state.songMeaning.isEmpty() ->
            AiEmptyState(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                message = "No insights yet",
                sub = "Tap Refresh to generate insights"
            )

        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .fadingEdges(top = 20.dp, bottom = 40.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (state.artistVitals.isNotEmpty()) {
                InfoCard(
                    title = "Artist Heritage",
                    content = state.artistVitals,
                    icon = Icons.Rounded.Person,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
            if (state.songMeaning.isNotEmpty()) {
                InfoCard(
                    title = "Deep Meaning",
                    content = state.songMeaning,
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    accentColor = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun InfoCard(title: String, content: String, icon: ImageVector, accentColor: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    color = accentColor
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.07f))
            Spacer(Modifier.height(16.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Taste / Recommendations Tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TasteTab(state: AiTasteState) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Section header
        if (!state.isLoading) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(modifier = Modifier.size(34.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Recommend, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                }
                Column {
                    Text(
                        "CURATED FOR YOU",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Based on what you're listening to",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        when {
            state.isLoading -> AiShimmerTaste()
            state.recommendations.isEmpty() -> AiEmptyState(
                icon = Icons.Rounded.Recommend,
                message = "No recommendations yet",
                sub = "Tap Refresh to discover music"
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
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
            .width(260.dp)
            .height(300.dp)
            .bouncyClick { /* Handle play */ },
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Soft gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                            )
                        )
                    )
            )

            Column(modifier = Modifier.padding(22.dp)) {
                // Music note icon surface
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    rec.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    rec.artist.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.07f))
                Spacer(Modifier.height(10.dp))
                Text(
                    rec.explanation,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play icon
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Markdown renderer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MarkdownText(markdown: String, style: androidx.compose.ui.text.TextStyle) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotated = remember(markdown, primary, onSurface) {
        parseMarkdown(markdown, primary, onSurface)
    }
    Text(
        text = annotated,
        style = style,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

fun parseMarkdown(text: String, primaryColor: Color, onSurfaceColor: Color): AnnotatedString =
    buildAnnotatedString {
        text.split("\n").forEachIndexed { i, line ->
            when {
                line.startsWith("# ") -> withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 24.sp, color = primaryColor)) {
                    append(line.drop(2))
                }
                line.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)) {
                    append(line.drop(3))
                }
                else -> {
                    line.split("**").forEachIndexed { j, part ->
                        if (j % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Black, color = onSurfaceColor)) {
                            appendInlineItalic(part)
                        } else appendInlineItalic(part)
                    }
                }
            }
            if (i < text.split("\n").size - 1) append("\n")
        }
    }

private fun AnnotatedString.Builder.appendInlineItalic(text: String) {
    text.split("*").forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part) }
        else append(part)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shimmer states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiShimmerLyrics() {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val widths = listOf(0.4f, 0.85f, 0.6f, 0.9f, 0.5f, 0.78f, 0.45f, 0.88f, 0.55f, 0.72f)
        widths.forEach { w ->
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(w)
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
            )
        }
    }
}

@Composable
fun AiShimmerInfo() {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        repeat(2) {
            ShimmerBox(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
        }
    }
}

@Composable
fun AiShimmerTaste() {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(2) {
            ShimmerBox(
                Modifier
                    .size(260.dp, 300.dp)
                    .clip(RoundedCornerShape(32.dp))
            )
        }
    }
}

@Composable
fun ShimmerBox(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerA"
    )
    Box(modifier = modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)))
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiEmptyState(
    icon: ImageVector,
    message: String,
    sub: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(40.dp)
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                }
            }
            Text(
                message,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Sparkle button (used in FullPlayerView secondary controls)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiSparkleButton(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "sparkle")
    val glowScale by transition.animateFloat(
        1f, 1.22f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sScale"
    )
    val glowAlpha by transition.animateFloat(
        0.08f, 0.28f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .scale(glowScale)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary, Color.Transparent)),
                    CircleShape
                )
        )
        Surface(
            onClick = onClick,
            modifier = Modifier.size(46.dp).bouncyClick {},
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}