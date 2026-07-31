package com.frerox.toolz.ui.screens.browser

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.frerox.toolz.data.browser.TabEntry
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.screens.search.components.FaviconDisplay

// ─── Accent Colors ────────────────────────────────────────────────────────────

private val ElectricViolet    = Color(0xFF7B6EF6)
private val ElectricVioletDim = Color(0xFF4A3FB8)
private val NeonCyan          = Color(0xFF38F5D4)
private val DangerRed         = Color(0xFFFF4D6A)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabManagementScreen(
    onBack: () -> Unit,
    onTabClick: (id: String, url: String) -> Unit,
    onNewTab: () -> Unit,
    viewModel: WebViewViewModel = hiltViewModel(),
) {
    val tabs        by viewModel.tabs.collectAsState(initial = emptyList())
    val activeTabId by viewModel.activeTabId.collectAsState(initial = null)

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelect by remember { derivedStateOf { selectedIds.isNotEmpty() } }

    // Animate header background on multi-select
    val topBarColor by animateColorAsState(
        targetValue   = if (isMultiSelect)
            MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label         = "topBarColor",
    )

    Scaffold(
        topBar = {
            Surface(color = topBarColor, shadowElevation = 0.dp) {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            AnimatedContent(
                                targetState  = isMultiSelect,
                                transitionSpec = {
                                    (fadeIn() + slideInVertically { -it / 2 }) togetherWith
                                            (fadeOut() + slideOutVertically { it / 2 })
                                },
                                label = "tabTitle",
                            ) { multiSelect ->
                                if (multiSelect) {
                                    Text(
                                        "${selectedIds.size} selected",
                                        fontWeight = FontWeight.Bold,
                                        color      = ElectricViolet,
                                    )
                                } else {
                                    Row(
                                        verticalAlignment     = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            "Tabs",
                                            fontWeight = FontWeight.Black,
                                            style      = MaterialTheme.typography.headlineSmall,
                                            letterSpacing = (-0.5).sp
                                        )
                                        if (tabs.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = ElectricViolet.copy(alpha = 0.12f),
                                            ) {
                                                Text(
                                                    "${tabs.size}",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style      = MaterialTheme.typography.labelMedium,
                                                    color      = ElectricViolet,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = if (isMultiSelect) { { selectedIds = emptySet() } } else onBack
                            ) {
                                AnimatedContent(
                                    targetState = isMultiSelect,
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith
                                                (scaleOut(targetScale = 0.7f) + fadeOut())
                                    },
                                    label = "navIcon",
                                ) { multiSelect ->
                                    Icon(
                                        if (multiSelect) Icons.Rounded.Close else Icons.Rounded.Close,
                                        contentDescription = if (multiSelect) "Cancel selection" else "Close",
                                    )
                                }
                            }
                        },
                        actions = {
                            AnimatedContent(
                                targetState  = isMultiSelect,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label        = "topActions",
                            ) { multiSelect ->
                                Row {
                                    if (multiSelect) {
                                        // Select / deselect all
                                        IconButton(onClick = {
                                            selectedIds = if (selectedIds.size == tabs.size)
                                                emptySet()
                                            else
                                                tabs.map { it.id }.toSet()
                                        }) {
                                            Icon(
                                                Icons.Rounded.DoneAll,
                                                contentDescription = "Select all",
                                                tint = ElectricViolet,
                                            )
                                        }
                                        // Delete selected
                                        IconButton(onClick = {
                                            viewModel.closeTabs(selectedIds)
                                            selectedIds = emptySet()
                                        }) {
                                            Icon(
                                                Icons.Rounded.DeleteSweep,
                                                contentDescription = "Close selected",
                                                tint = DangerRed,
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = onNewTab) {
                                            Icon(Icons.Rounded.Add, contentDescription = "New tab", tint = ElectricViolet)
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                    )

                    // Multi-select progress bar
                    AnimatedVisibility(
                        visible = isMultiSelect,
                        enter   = expandVertically(),
                        exit    = shrinkVertically(),
                    ) {
                        com.frerox.toolz.ui.components.ExpressiveLinearProgressIndicator(
                            progress = { if (tabs.isEmpty()) 0f else selectedIds.size.toFloat() / tabs.size },
                            modifier  = Modifier.fillMaxWidth(),
                            color     = ElectricViolet,
                            trackColor = ElectricViolet.copy(alpha = 0.12f),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isMultiSelect,
                enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit    = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick        = onNewTab,
                    icon           = { Icon(Icons.Rounded.Add, null) },
                    text           = { Text("New tab") },
                    shape          = RoundedCornerShape(20.dp),
                    containerColor = ElectricViolet,
                    contentColor   = Color.White,
                )
            }
        },
    ) { padding ->
        if (tabs.isEmpty()) {
            EmptyTabsView(
                onNewTab = onNewTab,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyVerticalGrid(
                columns             = GridCells.Fixed(2),
                contentPadding      = PaddingValues(
                    start  = 12.dp, end = 12.dp,
                    top    = 12.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement   = Arrangement.spacedBy(10.dp),
                modifier              = Modifier
                    .padding(top = padding.calculateTopPadding())
                    .fillMaxSize(),
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                    val isSelected = selectedIds.contains(tab.id)
                    val isActive   = tab.id == activeTabId

                    PremiumTabCard(
                        tab           = tab,
                        index         = index,
                        isSelected    = isSelected,
                        isActive      = isActive,
                        isMultiSelect = isMultiSelect,
                        onClick       = {
                            if (isMultiSelect) {
                                selectedIds = if (isSelected) selectedIds - tab.id else selectedIds + tab.id
                            } else {
                                onTabClick(tab.id, tab.url)
                            }
                        },
                        onLongClick   = {
                            if (!isMultiSelect) selectedIds = setOf(tab.id)
                        },
                        onClose       = { viewModel.closeTab(tab.id) },
                    )
                }
            }
        }
    }
}

// ─── Premium Tab Card ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumTabCard(
    tab: TabEntry,
    index: Int,
    isSelected: Boolean,
    isActive: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // Staggered entry animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index.coerceAtMost(10) * 35L)
        visible = true
    }

    // Card visual states
    val cardElevation by animateDpAsState(
        targetValue   = when {
            isSelected -> 0.dp
            isActive   -> 3.dp
            else       -> 1.dp
        },
        label = "tabCardElevation",
    )

    val borderColor by animateColorAsState(
        targetValue   = when {
            isSelected -> ElectricViolet
            isActive   -> ElectricViolet.copy(alpha = 0.4f)
            else       -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        },
        animationSpec = tween(200),
        label         = "tabBorderColor",
    )

    val cardColor by animateColorAsState(
        targetValue   = when {
            isSelected -> ElectricViolet.copy(alpha = 0.1f)
            isActive   -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            else       -> MaterialTheme.colorScheme.surfaceColorAtElevation(cardElevation)
        },
        animationSpec = tween(200),
        label         = "tabCardColor",
    )

    AnimatedVisibility(
        visible       = visible,
        enter         = scaleIn(
            initialScale  = 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        ) + fadeIn(tween(150)),
    ) {
        Box(
            modifier = Modifier
                .height(240.dp)
                .fillMaxWidth()
        ) {
            ExpressiveCard(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = cardColor,
                border = BorderStroke(1.5.dp, borderColor),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Header row ────────────────────────────────────────────────
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FaviconDisplay(
                            url      = tab.url,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            tab.title,
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f),
                            color      = if (isActive) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        )
                        if (!isMultiSelect) {
                            IconButton(
                                onClick  = onClose,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }

                    // ── Preview area ──────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    ) {
                        if (tab.previewPath != null) {
                            AsyncImage(
                                model              = tab.previewPath,
                                contentDescription = null,
                                modifier           = Modifier.fillMaxSize(),
                                contentScale       = ContentScale.Crop,
                            )
                            // Gradient overlay on preview for readability
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f))
                                        )
                                    )
                            )
                        } else {
                            // URL text fallback
                            Column(
                                modifier            = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Rounded.Public,
                                    null,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .alpha(0.2f),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    tab.url,
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    fontSize  = 10.sp,
                                    lineHeight = 14.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ── Active indicator dot (bottom-end) ─────────────────────────────
            AnimatedVisibility(
                visible = isActive && !isMultiSelect,
                enter   = scaleIn() + fadeIn(),
                exit    = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                ActiveDot()
            }

            // ── Multi-select checkbox (top-end) ───────────────────────────────
            AnimatedVisibility(
                visible  = isMultiSelect,
                enter    = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit     = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                MultiSelectIndicator(isSelected = isSelected)
            }
        }
    }
}

// ─── Active Dot ───────────────────────────────────────────────────────────────

@Composable
private fun ActiveDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "activePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.5f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulseScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
        // Outer pulse
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(ElectricViolet.copy(alpha = 0.25f))
        )
        // Solid core
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(NeonCyan, ElectricViolet))
                )
        )
    }
}

// ─── Multi-select Checkbox ────────────────────────────────────────────────────

@Composable
private fun MultiSelectIndicator(isSelected: Boolean) {
    val scale by animateFloatAsState(
        targetValue   = if (isSelected) 1f else 0.9f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "checkScale",
    )

    Box(
        modifier = Modifier
            .size(22.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isSelected) ElectricViolet
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
            .border(
                BorderStroke(
                    width = 1.5.dp,
                    color = if (isSelected) ElectricViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = isSelected,
            enter   = scaleIn(spring(Spring.DampingRatioLowBouncy)) + fadeIn(),
            exit    = scaleOut() + fadeOut(),
        ) {
            Icon(
                Icons.Rounded.Check,
                null,
                modifier = Modifier.size(14.dp),
                tint     = Color.White,
            )
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyTabsView(
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Ambient background
    val infiniteTransition = rememberInfiniteTransition(label = "emptyAmbient")
    val ambientPhase by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label         = "emptyAmbientPhase",
    )

    Box(
        modifier        = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Soft ambient circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color  = ElectricViolet.copy(alpha = 0.04f),
                radius = size.minDimension * 0.55f,
                center = Offset(
                    size.width / 2 + kotlin.math.sin(ambientPhase * 2 * Math.PI.toFloat()) * 20f,
                    size.height / 2 + kotlin.math.cos(ambientPhase * 2 * Math.PI.toFloat()) * 15f,
                ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(500)) + scaleIn(initialScale = 0.92f, animationSpec = spring(Spring.DampingRatioMediumBouncy)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier            = Modifier.padding(40.dp),
            ) {
                // Icon container
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape    = RoundedCornerShape(36.dp),
                    color    = ElectricViolet.copy(alpha = 0.08f),
                    border   = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.15f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Layers,
                            null,
                            modifier = Modifier.size(44.dp),
                            tint     = ElectricViolet.copy(alpha = 0.6f),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "No open tabs",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Start a new tab to begin browsing",
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onNewTab,
                    shape   = RoundedCornerShape(16.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open new tab", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}