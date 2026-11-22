/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.whisper

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frerox.toolz.R
import com.frerox.toolz.data.whisper.WhisperDailyUpsConfig
import com.frerox.toolz.data.whisper.WhisperProfile
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveRefreshIndicator
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveTextButton
import com.frerox.toolz.ui.components.ToolzTonalExpressiveButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.toolzBackground
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Rings and progress speak in theme tokens only — the rest of the Whisper UI
// never hardcodes an accent color, and neither do Daily Ups.

/**
 * Daily Ups hub: Your-day card, friends' poster rail, quota — and the
 * full-screen viewer as an in-place overlay (no extra nav route; the hub owns
 * the lifecycle so decrypted images and receipt state are shared by both).
 * [ownerArg] is the optional stories-bar owner passed by the route; it opens
 * that poster's viewer once after the first reconcile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperDailyUpsScreen(
    onNavigateBack: () -> Unit,
    ownerArg: String? = null,
    viewModel: WhisperDailyUpsViewModel = hiltViewModel(),
    composerViewModel: WhisperDailyUpsComposeViewModel = hiltViewModel(),
) {
    if (!WhisperDailyUpsConfig.ENABLED) {
        LaunchedEffect(Unit) { onNavigateBack() }
        return
    }
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
    // The composer is an in-place overlay: the hub's reconcile, quota and
    // viewer state survive the round trip, and there is only one Daily Ups
    // surface to learn.
    var showComposer by rememberSaveable { mutableStateOf(false) }
    val openComposer: () -> Unit = {
        composerViewModel.begin()
        showComposer = true
    }

    // Viewer-scoped failures only. The hub's sync failure renders inline in the
    // body: routing it here would clear it into a snackbar and then let the
    // screen fall through to "Nothing shared today yet" (false empty state).
    LaunchedEffect(state.viewerError) {
        state.viewerError?.let {
            snackbar.showSnackbar(it)
            viewModel.clearViewerError()
        }
    }

    // Pull-driven feature: every time the hub becomes RESUMED it reconciles
    // with the server — deletes, new friend ups, expiry. ON_RESUME (not a
    // one-shot LaunchedEffect) also covers returning from the composer with
    // launchSingleTop, where the destination is reused and would otherwise
    // keep showing a list that predates the just-published Up.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    // A stories-bar tap routes straight at that poster. Wait for the first
    // reconcile so a cold open can resolve the owner; consume the hand-off
    // exactly once, and never on a failed cold sync (a retry may still land).
    var ownerOpenConsumed by rememberSaveable(ownerArg) { mutableStateOf(false) }
    LaunchedEffect(ownerArg, state.loading, state.error, state.posterGroups) {
        val owner = ownerArg?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (ownerOpenConsumed || state.loading) return@LaunchedEffect
        if (state.error != null && state.posterGroups.isEmpty()) return@LaunchedEffect
        val poster = state.posterGroups.firstOrNull { it.ownerId == owner }
        // A cold open can beat the Room combine by one frame: hold the hand-off
        // until the reconciled list can answer for this owner.
        if (poster == null && state.posterGroups.isEmpty()) return@LaunchedEffect
        ownerOpenConsumed = true
        if (poster != null) {
            if (poster.isMine) viewModel.openMyViewer() else viewModel.openViewerForOwner(owner)
        }
    }

    // The viewer shows a SNAPSHOT captured at open. If the story being read
    // vanishes underneath (deleted on another device, expired mid-view), the
    // overlay leaves too — otherwise the reader reacts into the void while
    // the resulting snackbar hides behind the black overlay.
    LaunchedEffect(state.posterGroups, viewer) {
        val v = viewer ?: return@LaunchedEffect
        val living = state.posterGroups
            .flatMap { poster -> poster.stories.map { it.storyId } }
            .toSet()
        val current = v.currentStory?.storyId
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
                    val quotaLeft = (WhisperDailyUpsConfig.DAILY_LIMIT - state.quotaUsed).coerceAtLeast(0)
                    // One fixed 48dp slot: swapping a spinner for the icon used to
                    // shave 4dp off the row and shift Add on every refresh.
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !state.loading,
                    ) {
                        if (state.loading) {
                            GroupUiInlineLoading(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.st_Whisper_DailyUp_RefreshCd),
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (viewModel.acceptNavigation()) openComposer()
                        },
                        enabled = quotaLeft > 0,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.st_Whisper_DailyUp_AddCd),
                            modifier = Modifier.size(22.dp),
                        )
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
            // A keyed LazyColumn, not Column+verticalScroll: with a large
            // friend list the old body composed and decoded EVERY avatar and
            // photo up front. Lazy composition bounds that to what is on
            // screen, and the pull gesture still finds a scrollable child in
            // the loading/error branches because they are items too.
            // Branch order is the honesty contract: a failed sync with no cache
            // is a FAILURE panel, never a "nothing shared" empty day. A
            // successful empty day is still the Your-day card + no-friend line.
            val hasData = state.posterGroups.isNotEmpty()
            val ownCount = remember(state.posterGroups) {
                state.posterGroups.firstOrNull { it.isMine }?.stories?.size ?: 0
            }
            val friendPosters = remember(state.posterGroups) {
                state.posterGroups.filterNot { it.isMine }
            }
            val quotaLeft =
                (WhisperDailyUpsConfig.DAILY_LIMIT - state.quotaUsed)
                    .coerceAtLeast(0)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            ) {
                when {
                    state.loading && !hasData -> {
                        item(key = "loading") {
                            GroupUiCenterLoading(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                            )
                        }
                    }
                    state.error != null && !hasData -> {
                        item(key = "dead_end") {
                            GroupUiDeadEnd(
                                message = stringResource(R.string.st_Whisper_DailyUp_ErrSync),
                                onBack = onNavigateBack,
                                onRetry = { viewModel.refresh() },
                            )
                        }
                    }
                    else -> {
                        // Cached content survives a failed refresh: keep the
                        // hub on screen and say the sync failed above it.
                        item(key = "banner") {
                            GroupUiBanner(
                                message = state.error.orEmpty(),
                                visible = state.error != null,
                                isError = true,
                                actionLabel = stringResource(R.string.st_Whisper_DailyUp_Retry),
                                onAction = { viewModel.refresh() },
                                onDismiss = { viewModel.clearError() },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        item(key = "your_day") {
                            YourDayCard(
                                myId = state.myId,
                                myName = state.myName,
                                profile = state.profiles[state.myId],
                                ownCount = ownCount,
                                quotaLeft = quotaLeft,
                                ownCloseFriends = state.posterGroups
                                    .firstOrNull { it.isMine }
                                    ?.isCloseFriends == true,
                                onView = viewModel::openMyViewer,
                                onAdd = {
                                    if (viewModel.acceptNavigation()) onNavigateToCompose()
                                },
                            )
                        }
                        item(key = "section_header") {
                            SectionHeader(
                                text = stringResource(R.string.st_Whisper_DailyUp_SectionTitle) +
                                    if (friendPosters.isNotEmpty()) {
                                        " (${friendPosters.size})"
                                    } else {
                                        ""
                                    },
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 20.dp,
                                    bottom = 6.dp,
                                ),
                            )
                        }
                        if (friendPosters.isEmpty()) {
                            item(key = "empty") {
                                DailyUpsEmptyState(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        } else {
                            // The rail used to repeat this exact list as rings
                            // above the cards. One scannable surface, not two:
                            // the card carries the ring, the name, and the
                            // new-count together.
                            itemsIndexed(
                                items = friendPosters,
                                key = { _, poster -> poster.ownerId },
                            ) { index, poster ->
                                FriendStoryCard(
                                    poster = poster,
                                    profile = state.profiles[poster.ownerId],
                                    onClick = {
                                        viewModel.openViewerForOwner(poster.ownerId)
                                    },
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = if (index == 0) 0.dp else 10.dp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val mineFallback = stringResource(R.string.st_Whisper_DailyUp_Mine)
    AnimatedVisibility(
        visible = viewer != null,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            ),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
            scaleOut(
                targetScale = 0.94f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            ),
        modifier = Modifier.fillMaxSize(),
    ) {
        val v = viewer ?: return@AnimatedVisibility
        StoryViewerOverlay(
            viewer = v,
            images = images,
            failedImages = failedImages,
            receipts = receipts,
            profiles = state.profiles,
            ownName = state.myName.ifBlank { mineFallback },
            onShown = viewModel::onStoryDisplayed,
            onReady = viewModel::markCurrentSeen,
            onPreload = viewModel::preloadStory,
            onSeek = viewModel::seekViewer,
            onNext = viewModel::viewerNext,
            onPrev = viewModel::viewerPrev,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YourDayCard(
    myId: String,
    myName: String,
    profile: WhisperProfile?,
    ownCount: Int,
    quotaLeft: Int,
    ownCloseFriends: Boolean,
    onView: () -> Unit,
    onAdd: () -> Unit,
) {
    val youLabel = stringResource(R.string.st_Whisper_You)
    val name = myName.ifBlank { youLabel }
    val isLive = ownCount > 0
    val canAdd = quotaLeft > 0
    val quotaText = stringResource(
        R.string.st_Whisper_DailyUp_QuotaLeft,
        quotaLeft,
        WhisperDailyUpsConfig.DAILY_LIMIT,
    )

    ExpressiveCard(
        onClick = { if (isLive) onView() else onAdd() },
        enabled = isLive || canAdd,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(68.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val ringColor = when {
                        isLive && ownCloseFriends -> closeFriendsRingColor()
                        isLive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .border(
                                width = if (isLive) 3.dp else 2.dp,
                                color = ringColor,
                                shape = CircleShape,
                            ),
                    )
                    if (profile != null) {
                        WhisperAvatar(
                            profile = profile,
                            size = 58.dp,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    } else {
                        GroupAvatar(
                            name = name,
                            groupId = myId.ifBlank { "daily_ups_me" },
                            size = 58.dp,
                        )
                    }
                    if (isLive) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.TopEnd),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ownCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.st_Whisper_DailyUp_YourDay),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isLive && ownCloseFriends) CloseFriendsChip()
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isLive) {
                            stringResource(R.string.st_Whisper_DailyUp_YourDayLive, ownCount)
                        } else {
                            stringResource(R.string.st_Whisper_DailyUp_YourDayEmpty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // The quota is DAILY allowance, not a status of this card: keep it
            // on its own line so the action row can wrap at 200% font instead
            // of squeezing "Create Daily Up" off the edge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                DailyQuotaSegments(
                    left = quotaLeft,
                    total = WhisperDailyUpsConfig.DAILY_LIMIT,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = quotaText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isLive) {
                    ToolzTonalExpressiveButton(
                        onClick = onView,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.st_Whisper_DailyUp_View))
                    }
                }
                ToolzExpressiveButton(
                    onClick = onAdd,
                    enabled = canAdd,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isLive) {
                            stringResource(R.string.st_Whisper_DailyUp_Add)
                        } else {
                            stringResource(R.string.st_Whisper_DailyUp_CreateAction)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Five short bars for the daily allowance: filled = still available. The
 * textual quota sits next to it, so this is decoration for sighted users and
 * carries no semantics of its own.
 */
@Composable
private fun DailyQuotaSegments(left: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < left) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun FriendStoryCard(
    poster: StoryPoster,
    profile: WhisperProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val someone = stringResource(R.string.st_Whisper_DailyUp_Someone)
    val name = poster.ownerName?.takeIf { it.isNotBlank() } ?: someone
    val storyCount = poster.stories.size
    val unseenCount = poster.unseenCount
    val isUnseen = unseenCount > 0

    val haptic = rememberToolzHapticFeedback()
    val ringColor = when {
        poster.isCloseFriends -> closeFriendsRingColor()
        isUnseen -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    ExpressiveCard(
        onClick = {
            haptic.click()
            onClick()
        },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .border(
                            width = if (isUnseen || poster.isCloseFriends) 2.5.dp else 1.5.dp,
                            color = ringColor,
                            shape = CircleShape,
                        ),
                )
                if (profile != null) {
                    WhisperAvatar(
                        profile = profile,
                        size = 44.dp,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                } else {
                    GroupAvatar(
                        name = name,
                        groupId = poster.ownerId,
                        size = 44.dp,
                    )
                }
                if (unseenCount >= 2) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = unseenCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isUnseen) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (poster.isCloseFriends) CloseFriendsChip()
                }
                val subtitleText = when {
                    isUnseen -> stringResource(R.string.st_Whisper_DailyUp_UnseenPill, unseenCount)
                    storyCount == 1 -> stringResource(R.string.st_Whisper_DailyUp_StoriesCountOne)
                    else -> stringResource(R.string.st_Whisper_DailyUp_StoriesCount, storyCount)
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUnseen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isUnseen) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isUnseen) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DailyUpsEmptyState(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_EmptyTitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_EmptyDesc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Full-screen viewer overlay
// ────────────────────────────────────────────────────────────────────────────

private const val STORY_DURATION_MS = 5_000L

/** Longer than this, a press is a hold-to-pause, not a navigation tap. */
private const val TAP_MAX_MS = 350L

@Composable
private fun StoryViewerOverlay(
    viewer: WhisperDailyUpsViewModel.ViewerState,
    images: Map<String, ImageBitmap>,
    failedImages: Set<String>,
    receipts: WhisperDailyUpsViewModel.ReceiptsUi,
    profiles: Map<String, WhisperProfile>,
    ownName: String,
    onShown: (String) -> Unit,
    onReady: () -> Unit,
    onPreload: (String) -> Unit,
    onSeek: (Int) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    onReact: (storyId: String, emoji: String, on: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onRetryImage: (String) -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val closeWithHaptic = {
        haptic.click()
        onClose()
    }
    BackHandler(onBack = closeWithHaptic)

    val current = viewer.currentStory
    val segmentStoryCount = viewer.currentSegment?.stories?.size ?: 0
    val flatStories = remember(viewer.segments) { viewer.segments.flatMap { it.stories } }
    val pagerState = rememberPagerState(
        initialPage = viewer.flatPosition,
        pageCount = { flatStories.size },
    )
    var pressed by remember { mutableStateOf(false) }
    var draggingDown by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(0f) }
    val navScope = rememberCoroutineScope()

    // Follow the pager's settled page for the lifetime of this open, and
    // report settled drags back. Keying this on viewer.flatPosition would
    // restart the collector during external navigation, immediately emitting
    // the OLD settled page and undoing the move.
    LaunchedEffect(flatStories.size) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page in flatStories.indices) onSeek(page)
        }
    }
    // Tap navigation is an instant cut, like a story viewer; drag navigation
    // stays the pager's own swipe. A cut also avoids a multi-frame slide where
    // the header/progress show the destination over the old page.
    LaunchedEffect(viewer.flatPosition) {
        if (viewer.flatPosition in flatStories.indices &&
            pagerState.currentPage != viewer.flatPosition
        ) {
            pagerState.scrollToPage(viewer.flatPosition)
        }
    }

    // A page change must never inherit a held/paused state from the old page.
    LaunchedEffect(viewer.segmentIndex, viewer.storyIndex) { pressed = false }

    // Segment crossing gets a light tick; the opening frame stays silent.
    var lastSegment by remember { mutableIntStateOf(viewer.segmentIndex) }
    LaunchedEffect(viewer.segmentIndex) {
        if (viewer.segmentIndex != lastSegment) {
            haptic.tick()
            lastSegment = viewer.segmentIndex
        }
    }

    val currentId = current?.storyId
    val imageReady = currentId?.let { images[it] } != null
    val failed = currentId != null && currentId in failedImages
    val hasNext = viewerNextPosition(
        viewer.segmentSizes,
        viewer.segmentIndex,
        viewer.storyIndex,
    ) != null
    val goNext: () -> Unit = {
        if (hasNext) onNext() else closeWithHaptic()
    }
    val goPrev: () -> Unit = {
        val prev = viewerPrevPosition(viewer.segmentSizes, viewer.segmentIndex, viewer.storyIndex)
        if (prev != null) {
            onPrev()
        } else {
            navScope.launch { progress.snapTo(0f) }
        }
    }

    // Decrypt + receipt/reaction reads begin as soon as the current story
    // changes; they are safe while the pager moves to it.
    LaunchedEffect(currentId) {
        currentId?.let(onShown)
    }
    // Seen + view receipt only once pixels are actually on screen AND the
    // pager is settled on this exact story, never on a loading/failed page and
    // never just because the ViewModel moved before the pager did.
    LaunchedEffect(currentId, imageReady, failed, pagerState.settledPage) {
        if (currentId != null &&
            imageReady &&
            !failed &&
            pagerState.settledPage == viewer.flatPosition
        ) {
            onReady()
        }
    }
    // Pre-warm the next story, crossing poster boundaries like viewerNext().
    LaunchedEffect(viewer.segmentIndex, viewer.storyIndex) {
        val next = viewerNextPosition(viewer.segmentSizes, viewer.segmentIndex, viewer.storyIndex)
            ?: return@LaunchedEffect
        viewer.segments.getOrNull(next.first)
            ?.stories
            ?.getOrNull(next.second)
            ?.storyId
            ?.let(onPreload)
    }

    // 5 s ready-gated progress. Holding pauses at the exact fraction; release
    // resumes with only the time that was left. Scrolling, an unsettled pager,
    // and the delete dialog pause too, so the clock never runs under a gesture
    // or while another story is still sliding into view.
    LaunchedEffect(viewer.segmentIndex, viewer.storyIndex, imageReady, failed) {
        progress.snapTo(0f)
        if (!imageReady || failed) return@LaunchedEffect
        snapshotFlow {
            pressed ||
                draggingDown ||
                pagerState.isScrollInProgress ||
                pagerState.settledPage != viewer.flatPosition ||
                pendingDeleteId != null
        }
            .collectLatest { pausedNow ->
                if (pausedNow) {
                    progress.stop()
                } else {
                    val remaining = ((1f - progress.value) * STORY_DURATION_MS)
                        .roundToLong()
                        .coerceAtLeast(1L)
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(remaining.toInt(), easing = LinearEasing),
                    )
                    goNext()
                }
            }
    }

    // Swipe-down dismissal: vertical only, so the horizontal pager keeps its
    // lane. The content follows the finger; the scrim fades behind it.
    val density = LocalDensity.current
    val maxDragPx = with(density) { 280.dp.toPx() }
    val dismissPx = with(density) { 120.dp.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val velocityTracker = remember { VelocityTracker() }
    val scrimAlpha = (1f - (dragY / maxDragPx) * 0.8f).coerceIn(0.25f, 1f)

    val nextLabel = stringResource(R.string.st_Whisper_DailyUp_Next)
    val previousLabel = stringResource(R.string.st_Whisper_DailyUp_Previous)
    val someone = stringResource(R.string.st_Whisper_DailyUp_Someone)
    val yourDay = stringResource(R.string.st_Whisper_DailyUp_YourDay)
    val remainingMinutes = (current?.remainingMs ?: 0L) / 60_000L
    val relativeLeft = when {
        remainingMinutes >= 60 -> stringResource(
            R.string.st_Whisper_DailyUp_LeftHours,
            remainingMinutes / 60,
        )
        remainingMinutes >= 1 -> stringResource(
            R.string.st_Whisper_DailyUp_LeftMinutes,
            remainingMinutes,
        )
        else -> stringResource(R.string.st_Whisper_DailyUp_LeftSoon)
    }
    val leftShort = stringResource(R.string.st_Whisper_DailyUp_LeftShort, relativeLeft)
    val storyOf = stringResource(
        R.string.st_Whisper_DailyUp_StoryOf,
        viewer.flatPosition + 1,
        viewer.flatTotal,
    )
    val segment = viewer.currentSegment

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        draggingDown = true
                        settleJob?.cancel()
                        velocityTracker.resetTracking()
                    },
                    onVerticalDrag = { change, amount ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        val raw = (dragY + amount).coerceAtLeast(0f)
                        dragY = if (raw <= maxDragPx) raw else maxDragPx + (raw - maxDragPx) * 0.2f
                    },
                    onDragEnd = {
                        draggingDown = false
                        val velocity = velocityTracker.calculateVelocity().y
                        if (dragY > dismissPx || velocity > 1_000f) {
                            closeWithHaptic()
                        } else {
                            settleJob = navScope.launch {
                                animate(
                                    initialValue = dragY,
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                ) { value, _ -> dragY = value }
                            }
                        }
                    },
                    onDragCancel = {
                        draggingDown = false
                        settleJob?.cancel()
                        settleJob = navScope.launch {
                            animate(
                                initialValue = dragY,
                                targetValue = 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            ) { value, _ -> dragY = value }
                        }
                    },
                )
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(nextLabel) {
                        if (pendingDeleteId == null) goNext()
                        true
                    },
                    CustomAccessibilityAction(previousLabel) {
                        if (pendingDeleteId == null) goPrev()
                        true
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragY.roundToInt()) },
        ) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 8.dp,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = pendingDeleteId == null,
            ) { page ->
                val story = flatStories.getOrNull(page) ?: return@HorizontalPager
                StoryPage(
                    story = story,
                    image = images[story.storyId],
                    failed = story.storyId in failedImages,
                    onRetry = { onRetryImage(story.storyId) },
                    displayName = if (story.isMine) ownName else story.ownerName.ifBlank { someone },
                    pressed = { p -> pressed = p },
                    onNext = goNext,
                    onPrev = goPrev,
                )
            }

            // White chrome floats over arbitrary photos. These gradients sit
            // under the controls (and consume no touches), so the progress
            // bar, owner, expiry and reactions stay legible on a bright sky.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.62f),
                            0.55f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
            )

            // Top chrome: one progress segment per story of the CURRENT poster,
            // avatar + owner + honest expiry, and the 48dp close target.
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                ViewerProgressBar(
                    progress = progress,
                    storyIndex = viewer.storyIndex,
                    segmentCount = segmentStoryCount,
                    storyOf = storyOf,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val segmentName = when {
                        segment == null -> someone
                        segment.isMine -> yourDay
                        !segment.ownerName.isNullOrBlank() -> segment.ownerName.orEmpty().trim()
                        else -> someone
                    }
                    val segmentProfile = segment?.let { profiles[it.ownerId] }
                    if (segmentProfile != null) {
                        WhisperAvatar(
                            profile = segmentProfile,
                            size = 32.dp,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    } else {
                        GroupAvatar(
                            name = if (segment?.isMine == true) ownName else segmentName,
                            groupId = segment?.ownerId ?: "daily_ups_viewer",
                            size = 32.dp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = segmentName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = leftShort,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    IconButton(
                        onClick = closeWithHaptic,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = GroupCd.close(),
                            tint = Color.White,
                        )
                    }
                }
            }

            // Bottom actions: honest own stats + delete, or the friend palette.
            // Never expose the destination story's actions for the one frame
            // before an instant cut lands (or during a drag).
            val pageSettled = pagerState.settledPage == viewer.flatPosition
            current?.takeIf { pageSettled }?.let { c ->
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                        .padding(bottom = 28.dp, start = 12.dp, end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (c.isMine) {
                        val stats = receipts.ownStats[c.storyId]
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    // Keep the delete target clear even at
                                    // large font sizes; centred means centred.
                                    .padding(horizontal = 56.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                when {
                                    !c.receiptsEnabled -> Text(
                                        text = stringResource(R.string.st_Whisper_DailyUp_ReceiptsOff),
                                        color = Color.White.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                    )
                                    stats == null -> CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White.copy(alpha = 0.85f),
                                        strokeWidth = 2.dp,
                                    )
                                    else -> {
                                        Text(
                                            text = stringResource(
                                                R.string.st_Whisper_DailyUp_ViewsCount,
                                                stats.views,
                                            ),
                                            color = Color.White.copy(alpha = 0.9f),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(
                                            text = if (stats.reactions == 0) {
                                                stringResource(R.string.st_Whisper_DailyUp_NoReactions)
                                            } else {
                                                stringResource(
                                                    R.string.st_Whisper_DailyUp_ReactionsCount,
                                                    stats.reactions,
                                                )
                                            },
                                            color = Color.White.copy(alpha = 0.75f),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { pendingDeleteId = c.storyId },
                                modifier = Modifier.align(Alignment.CenterEnd),
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.st_Whisper_DailyUp_DeleteCd),
                                    tint = Color.White,
                                )
                            }
                        }
                    } else if (imageReady && !failed) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.st_Whisper_DailyUp_ReactHint),
                                color = Color.White.copy(alpha = 0.8f),
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
        }

        // Fixed overlay: pausing never moves the chrome under the finger.
        AnimatedVisibility(
            visible = pressed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 76.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(R.string.st_Whisper_DailyUp_Paused),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
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

/**
 * Isolates the animated fill from the viewer's composition. Reading
 * [progress] here means only this thin row recomposes on every animation
 * frame; the pager, header, and bottom chrome stay untouched.
 */
@Composable
private fun ViewerProgressBar(
    progress: Animatable<Float, AnimationVector1D>,
    storyIndex: Int,
    segmentCount: Int,
    storyOf: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { stateDescription = storyOf },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(segmentCount) { i ->
            val fill = when {
                i < storyIndex -> 1f
                i == storyIndex -> progress.value
                else -> 0f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                if (fill > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill.coerceIn(0f, 1f))
                            .background(
                                Color.White.copy(
                                    alpha = if (i < storyIndex) 0.85f else 1f,
                                ),
                            ),
                    )
                }
            }
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
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    // pointerInput is keyed only by storyId, so it keeps the first callback
    // instances it saw. Read the current ones at invocation time; otherwise a
    // pre-composed last page can keep a stale "has next" decision after the
    // reader actually reaches it.
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrev by rememberUpdatedState(onPrev)
    val latestPressed by rememberUpdatedState(pressed)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(story.storyId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    latestPressed(true)
                    // Wait for the gesture to settle without consuming it, so
                    // the horizontal pager still owns horizontal drags and the
                    // vertical overlay still owns swipe-down.
                    val up = waitForUpOrCancellation()
                    latestPressed(false)
                    if (up != null && !up.isConsumed) {
                        // A press that lasts is a hold-to-pause, not a tap.
                        // Releasing it must resume the clock, never navigate.
                        val heldMs = up.uptimeMillis - down.uptimeMillis
                        if (heldMs < TAP_MAX_MS) {
                            // Standard story navigation: right two-thirds =
                            // next, left third = previous. The chrome (close /
                            // reactions) consumes its own taps.
                            if (up.position.x > size.width * 0.33f) latestOnNext() else latestOnPrev()
                        }
                    }
                }
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
    val haptic = rememberToolzHapticFeedback()
    val emojiFontSize = with(LocalDensity.current) { 26.dp.toSp() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.38f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WhisperDailyUpsConfig.REACTION_EMOJIS.forEach { emoji ->
            val active = emoji == currentEmoji
            val reactCd = stringResource(R.string.st_Whisper_DailyUp_ReactionCd, emoji)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable {
                        haptic.tick()
                        onToggle(emoji, !active)
                    }
                    .then(
                        if (active) {
                            Modifier
                                .background(Color.White.copy(alpha = 0.28f))
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clearAndSetSemantics {
                        contentDescription = reactCd
                        selected = active
                        role = Role.Button
                        onClick(label = reactCd) {
                            haptic.tick()
                            onToggle(emoji, !active)
                            true
                        }
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = emojiFontSize)
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDailyUpDialog(confirm: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_DeleteLabel),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.st_Whisper_DailyUp_DeleteConfirm),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            ToolzExpressiveButton(
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
            ToolzExpressiveTextButton(onClick = dismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}
