/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.ui.components.ExpressiveRefreshIndicator
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

// Rings and progress speak in theme tokens only — the rest of the Whisper UI
// never hardcodes an accent color, and neither do Daily Ups.

/**
 * Entry row on the Whisper Chats tab. Cheap by design: no decrypt, no sync,
 * no story-server round trip — the hub does all the work when opened.
 */
@Composable
internal fun WhisperDailyUpsEntryRow(onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_Title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_EntrySub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Daily Ups hub: my strip, friends' rings, quota chip — and the full-screen
 * viewer as an in-place overlay (no extra nav route; the hub owns the
 * lifecycle so decrypted images and receipt state are shared by both).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperDailyUpsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCompose: () -> Unit,
    viewModel: WhisperDailyUpsViewModel = hiltViewModel(),
) {
    if (!WhisperDailyUpsConfig.ENABLED) return
    SecureWindow()

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val viewer by viewModel.viewer.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val failedImages by viewModel.failedImages.collectAsStateWithLifecycle()
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Gesture lane for the same thing the top-bar refresh icon does; the
    // expressive indicator keeps the pull visual identical to the repo's other
    // refreshed lists (e.g. DeviceInfo).
    val pullState = rememberPullToRefreshState()

    // Viewer-scoped failures only. The hub's sync failure renders inline in the
    // body: routing it here would clear it into a snackbar and then let the
    // screen fall through to "Nothing shared today yet" (false empty state).
    LaunchedEffect(state.viewerError) {
        state.viewerError?.let {
            snackbar.showSnackbar(it)
            viewModel.clearViewerError()
        }
    }

    // Pull-driven feature: entering the hub (every time) reconciles with the
    // server — deletes, new friend ups, expiry — before anything renders.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // The viewer shows a SNAPSHOT captured at open. If the story being read
    // vanishes underneath (deleted on another device, expired mid-view), the
    // overlay leaves too — otherwise the reader reacts into the void while
    // the resulting snackbar hides behind the black overlay.
    LaunchedEffect(state.mine, state.friends, viewer) {
        val v = viewer ?: return@LaunchedEffect
        val living = (state.mine + state.friends).map { it.storyId }.toSet()
        val current = v.stories.getOrNull(v.index)?.storyId
        if (current != null && current !in living) viewModel.closeViewer()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().toolzBackground(),
        snackbarHost = { GroupSnackbarHost(state = snackbar) },
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_Whisper_DailyUp_Title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = GroupCd.back())
                    }
                },
                actions = {
                    // The compose entry is the hub's primary affordance — top
                    // bar action AND the "+" ring below, never one alone. The
                    // VM gate survives recomposition and stops a double push.
                    IconButton(
                        onClick = {
                            if (viewModel.acceptNavigation()) onNavigateToCompose()
                        },
                    ) {
                        Icon(
                            Icons.Rounded.AddCircle,
                            contentDescription = stringResource(R.string.st_Whisper_DailyUp_AddCd),
                        )
                    }
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.st_Whisper_DailyUp_RefreshCd),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Pull-to-refresh parity with the chats list (WhisperMainScreen):
        // the hub syncs on open AND on the pull gesture now. state.loading
        // IS the refresh flag (it maps _refreshing 1:1 in the VM combiner).
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { viewModel.refresh() },
            state = pullState,
            indicator = {
                ExpressiveRefreshIndicator(
                    state = pullState,
                    isRefreshing = state.loading,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Pre-PTR the hub was a static Column: on small screens the lower
            // content simply CLIPPED with no way to reach it, and a pull had
            // nothing to grab — vertical scroll fixes both.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
            // Quota as a quiet pill: green-ish secondary when there is room,
            // error tonal once exhausted. The server is the real ceiling; this
            // is only the honest mirror of it.
            val quotaLeft = (WhisperDailyUpsConfig.DAILY_LIMIT - state.quotaUsed).coerceAtLeast(0)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (quotaLeft > 0) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.st_Whisper_DailyUp_QuotaLeft,
                        quotaLeft,
                        WhisperDailyUpsConfig.DAILY_LIMIT,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (quotaLeft > 0) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
            // Branch order is the honesty contract: a failed sync with no cache
            // is a FAILURE panel, never the "nothing shared today" empty state.
            val hasData = state.mine.isNotEmpty() || state.friends.isNotEmpty()
            when {
                state.loading && !hasData -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && !hasData -> {
                    GroupUiDeadEnd(
                        message = stringResource(R.string.st_Whisper_DailyUp_ErrSync),
                        onBack = onNavigateBack,
                        onRetry = { viewModel.refresh() },
                    )
                }
                else -> {
                    // Cached content survives a failed refresh: keep the stale
                    // rail on screen and say the sync failed above it.
                    GroupUiBanner(
                        message = state.error.orEmpty(),
                        visible = state.error != null,
                        isError = true,
                        actionLabel = stringResource(R.string.st_Whisper_DailyUp_Retry),
                        onAction = { viewModel.refresh() },
                        onDismiss = { viewModel.clearError() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    if (!state.loading && state.error == null && !hasData) {
                        WhisperEmptyState(
                            icon = Icons.Rounded.Timer,
                            title = stringResource(R.string.st_Whisper_DailyUp_EmptyTitle),
                            subtitle = stringResource(R.string.st_Whisper_DailyUp_EmptySub),
                        )
                        // An empty day is a call to action, not a wall — the CTA sits
                        // right under the copy, same action as the "+" ring.
                        ToolzExpressiveButton(
                            onClick = {
                                if (viewModel.acceptNavigation()) onNavigateToCompose()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = stringResource(R.string.st_Whisper_DailyUp_NewUp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        // One quiet rail; the hub reading order === viewer reading
                        // order: "+" to upload, then MY ups, then friends' — no
                        // section headers (rings already carry who + how long).
                        TodayRail(
                            mine = state.mine,
                            friends = state.friends,
                            onOpen = viewModel::openViewer,
                            onCompose = {
                                if (viewModel.acceptNavigation()) onNavigateToCompose()
                            },
                        )
                    }
                }
            }
        }
        }
    }

    AnimatedVisibility(
        visible = viewer != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val v = viewer ?: return@AnimatedVisibility
        StoryViewerOverlay(
            stories = v.stories,
            index = v.index,
            images = images,
            failedImages = failedImages,
            receipts = receipts,
            ownName = stringResource(R.string.st_Whisper_DailyUp_Mine),
            onShown = viewModel::onStoryDisplayed,
            onSeek = viewModel::seekViewer,
            onClose = viewModel::closeViewer,
            onReact = viewModel::toggleReaction,
            onDelete = viewModel::deleteMyStory,
            onRetryImage = viewModel::retryImage,
        )
    }

    // Errors raised while the FULLSCREEN viewer is up must surface ABOVE it:
    // the Scaffold host sits underneath the black overlay where nobody sees
    // it. Same SnackbarHostState, second host drawn last (winning z-order).
    if (viewer != null) {
        GroupSnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Hub parts
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun TodayRail(
    mine: List<WhisperDailyUpsViewModel.StoryItem>,
    friends: List<WhisperDailyUpsViewModel.StoryItem>,
    /** The "+" slot is always FIRST; my ups follow, then friends'. */
    onOpen: (String) -> Unit,
    onCompose: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "daily_up_add_slot") { NewUpRing(onClick = onCompose) }
        items(items = mine + friends, key = { "daily_up_${it.storyId}" }) { element ->
            StoryRing(element, onClick = { onOpen(element.storyId) })
        }
    }
}

@Composable
private fun StoryRing(item: WhisperDailyUpsViewModel.StoryItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(3.dp),
        ) {
            Surface(shape = CircleShape, modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    // Identity initials: media is decrypt-on-demand and rings stay
                    // cheap and business-agnostic on purpose (hub never decrypts).
                    Text(
                        text = if (item.isMine) stringResource(R.string.st_Whisper_You) else item.ownerName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (item.isMine) stringResource(R.string.st_Whisper_DailyUp_Mine) else item.ownerName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = remainingText(item.remainingMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewUpRing(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(3.dp),
        ) {
            Surface(shape = CircleShape, modifier = Modifier.size(48.dp)) {
                Box(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.st_Whisper_DailyUp_AddCd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.st_Whisper_DailyUp_NewUp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/** "22h", "4h 12m", "45m" — a story clock never needs finer granularity. */
private fun remainingText(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    return when {
        minutes >= 180L -> "${minutes / 60L}h"
        minutes >= 60L -> "${minutes / 60L}h ${minutes % 60L}m"
        else -> "${max(minutes, 1L)}m"
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Full-screen viewer overlay
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun StoryViewerOverlay(
    stories: List<WhisperDailyUpsViewModel.StoryItem>,
    index: Int,
    images: Map<String, ImageBitmap>,
    failedImages: Set<String>,
    receipts: WhisperDailyUpsViewModel.ReceiptsUi,
    ownName: String,
    onShown: (String) -> Unit,
    onSeek: (Int) -> Unit,
    onClose: () -> Unit,
    onReact: (storyId: String, emoji: String, on: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onRetryImage: (String) -> Unit,
) {
    BackHandler(onBack = onClose)
    val pagerState = rememberPagerState(initialPage = index, pageCount = { stories.size })
    var pressed by remember { mutableStateOf(false) }
    val navScope = rememberCoroutineScope()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // Follow the ViewModel's index (open-on-ring) and report settled drags back;
    // both flow into the same state so there is never a two-way fight.
    LaunchedEffect(index) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page in stories.indices && page != index) onSeek(page)
        }
    }
    LaunchedEffect(index) {
        if (index in stories.indices && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    // 7s auto-advance, paused while the reader holds the screen.
    LaunchedEffect(pagerState.currentPage, pressed) {
        if (!pressed && stories.isNotEmpty()) {
            delay(7_000)
            if (pagerState.currentPage < stories.size - 1) {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    val current = stories.getOrNull(index)
    // Receipt side-effects fire exactly when the page settles on the story.
    LaunchedEffect(current?.storyId) {
        current?.storyId?.let { onShown(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pendingDeleteId == null,
        ) { page ->
            val story = stories.getOrNull(page) ?: return@HorizontalPager
            StoryPage(
                story = story,
                image = images[story.storyId],
                failed = story.storyId in failedImages,
                onRetry = { onRetryImage(story.storyId) },
                displayName = if (story.isMine) ownName else story.ownerName,
                pressed = { p -> pressed = p },
                onNavigate = { delta ->
                    if (pendingDeleteId == null) {
                        val target = (pagerState.currentPage + delta)
                            .coerceIn(0, stories.size - 1)
                        if (target != pagerState.currentPage) {
                            navScope.launch { pagerState.animateScrollToPage(target) }
                        }
                    }
                },
            )
        }

        // Top chrome: back + one progress-bar segment per story + poster name.
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = GroupCd.back(),
                        tint = Color.White,
                    )
                }
                stories.forEachIndexed { i, _ ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    i < pagerState.currentPage -> Color.White.copy(alpha = 0.85f)
                                    i == pagerState.currentPage -> Color.White
                                    else -> Color.White.copy(alpha = 0.25f)
                                },
                            ),
                    )
                }
            }
            current?.let { c ->
                Text(
                    text = if (c.isMine) ownName else c.ownerName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, start = 12.dp),
                )
            }
        }

        // Bottom actions: receipts/delete for mine, reaction palette for friends.
        current?.let { c ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (c.isMine) {
                    val stats = receipts.ownStats[c.storyId]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.st_Whisper_DailyUp_ViewsCount, stats?.views ?: 0),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        IconButton(onClick = { pendingDeleteId = c.storyId }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.st_Whisper_DailyUp_DeleteCd),
                                tint = Color.White,
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // Reacting is ONLY honest with this line on screen: an
                        // emoji also tells the poster their story was viewed,
                        // even with receipts switched off (§11 server truth).
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.st_Whisper_DailyUp_ReactHint),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        ReactionPalette(
                            currentEmoji = receipts.myReactions[c.storyId].orEmpty(),
                            onToggle = { emoji, on -> onReact(c.storyId, emoji, on) },
                        )
                    }
                }
            }
        }

        pendingDeleteId?.let { id ->
            ConfirmDeleteDailyUpDialog(
                confirm = {
                    onDelete(id)
                    pendingDeleteId = null
                },
                dismiss = { pendingDeleteId = null },
            )
        }
    }
}

@Composable
private fun StoryPage(
    story: WhisperDailyUpsViewModel.StoryItem,
    image: ImageBitmap?,
    failed: Boolean,
    onRetry: () -> Unit,
    displayName: String,
    pressed: (Boolean) -> Unit,
    onNavigate: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(story.storyId) {
                detectTapGestures(
                    onPress = {
                        pressed(true)
                        runCatching { tryAwaitRelease() }
                        pressed(false)
                    },
                    // Standard story navigation: right two-thirds = next, left
                    // third = previous. Holds still pause (onPress above); the
                    // chrome (close / reaction palette) consumes its own taps.
                    onTap = { pos ->
                        onNavigate(if (pos.x > size.width * 0.33f) 1 else -1)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> {
                Image(
                    bitmap = image,
                    contentDescription = displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Failed decode/download: say so ON the page — an eternal spinner
            // next to a paused story reads as "frozen". Retry re-runs the
            // decrypt lane; the error snackbar ALSO has a copy now.
            failed -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.st_Whisper_DailyUp_ErrLoad),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onRetry) {
                        Text(
                            text = stringResource(R.string.st_Whisper_DailyUp_Retry),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun ReactionPalette(
    currentEmoji: String,
    onToggle: (String, Boolean) -> Unit,
) {
    // Six 48dp targets do not fit a 320dp phone with fixed spacing. Weighting
    // the cells keeps the full 48dp touch target on every supported width and
    // lets the palette fill the row cleanly instead of clipping the last emoji.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WhisperDailyUpsConfig.REACTION_EMOJIS.forEach { emoji ->
            val active = emoji == currentEmoji
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable {
                        onToggle(emoji, !active)
                    }
                    .then(
                        if (active) {
                            Modifier.background(Color.White.copy(alpha = 0.24f))
                        } else {
                            Modifier
                        },
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 26.sp)
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDailyUpDialog(confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.st_Whisper_DailyUp_DeleteLabel)) },
        text = { Text(stringResource(R.string.st_Whisper_DailyUp_DeleteConfirm)) },
        confirmButton = {
            Button(
                onClick = confirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.st_Whisper_DailyUp_DeleteLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
