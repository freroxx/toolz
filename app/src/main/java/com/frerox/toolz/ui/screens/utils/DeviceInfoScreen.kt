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

package com.frerox.toolz.ui.screens.utils

import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.frerox.toolz.data.device.DeviceSpecUiModel
import com.frerox.toolz.data.device.QuickSpecItem
import com.frerox.toolz.data.device.SpecCategory
import com.frerox.toolz.ui.components.BouncyShape
import com.frerox.toolz.ui.components.fadingEdges
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveCarousel
import com.frerox.toolz.ui.components.ExpressiveContainedLoadingIndicator
import com.frerox.toolz.ui.components.ExpressiveRefreshIndicator
import com.frerox.toolz.ui.components.ExpressiveTopAppBar
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.StaggeredEntrance
import com.frerox.toolz.ui.components.ToolzExpressiveButton
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.components.rememberToolzHapticFeedback
import com.frerox.toolz.ui.theme.toolzBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit,
    viewModel: DeviceInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    DeviceInfoScreenContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onUpdateQuery = viewModel::updateQuery,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceInfoScreenContent(
    state: DeviceInfoUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateQuery: (String) -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    val haptic = rememberToolzHapticFeedback()
    val isRefreshing = state.isRefreshing
    var showEditQueryDialog by remember { mutableStateOf(false) }

    if (showEditQueryDialog) {
        EditQueryDialog(
            currentQuery = state.queryModel,
            onDismiss = { showEditQueryDialog = false },
            onConfirm = { newQuery ->
                onUpdateQuery(newQuery)
                showEditQueryDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_DeviceInfoScreen_a1b2),
                subtitle = stringResource(R.string.st_DeviceInfoScreen_c3d4),
                navigationIcon = {
                    ToolzExpressiveIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_DeviceInfoScreen_e5f6))
                    }
                },
                actions = {
                    ToolzExpressiveIconButton(
                        onClick = {
                            haptic.tick()
                            onRefresh()
                        },
                        enabled = !isRefreshing,
                        modifier = Modifier.padding(end = 8.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.st_DeviceInfoScreen_g7h8))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            indicator = {
                ExpressiveRefreshIndicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = 32.dp, bottom = 32.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    StaggeredEntrance(index = 0) {
                        HeroHeader(
                            state = state,
                            onRetry = onRefresh,
                            onLongClick = { 
                                haptic.longClick()
                                showEditQueryDialog = true 
                            }
                        )
                    }
                }

                state.remoteSpec?.let { spec ->
                    if (spec.quickSpecs.isNotEmpty()) {
                        item {
                            StaggeredEntrance(index = 1) {
                                QuickSpecsSection(spec.quickSpecs)
                            }
                        }
                    }

                    if (spec.categories.isNotEmpty()) {
                        item {
                            SectionLabel(
                                icon = Icons.Rounded.TableRows,
                                title = stringResource(R.string.st_DeviceInfoScreen_u1v2),
                                subtitle = stringResource(R.string.st_DeviceInfoScreen_w3x4),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                        itemsIndexed(
                            items = spec.categories,
                            key = { _, item -> item.name },
                        ) { index, category ->
                            StaggeredEntrance(index = index + 2) {
                                SpecCategoryCard(
                                    category = category,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }
                } ?: run {
                    if (state.isRemoteLoading) {
                        item {
                            MarketShimmer(modifier = Modifier.padding(horizontal = 20.dp))
                        }
                    }
                }

                item {
                    val startIndex = (state.remoteSpec?.categories?.size ?: 0) + 3
                    StaggeredEntrance(index = startIndex) {
                        LocalHardwareSection(
                            data = state.localDevice,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(
    state: DeviceInfoUiState,
    onRetry: () -> Unit,
    onLongClick: () -> Unit,
) {
    val spec = state.remoteSpec
    val context = LocalContext.current
    val haptic = rememberToolzHapticFeedback()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Immersive Image Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .graphicsLayer {
                    clip = true
                    shape = BouncyShape
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Decorative Background Glow
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset(y = 20.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )

            val imageScale by animateFloatAsState(
                targetValue = if (state.isRemoteLoading) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                label = "heroImageScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .scale(imageScale)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (spec?.image?.isNotBlank() == true) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(spec.image)
                            .httpHeaders(
                                NetworkHeaders.Builder()
                                    .set("Referer", "https://www.gsmarena.com/")
                                    .set("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                                    .build()
                            )
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color.White),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Smartphone,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                }
            }

            if (state.isRemoteLoading) {
                ExpressiveContainedLoadingIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }

        // Device Info & Status
        ExpressiveCard(
            onClick = onLongClick,
            modifier = Modifier.fillMaxWidth(),
            shape = LargeExpressiveShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
            elevation = 0.dp,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = spec?.name?.takeIf { it.isNotBlank() } ?: state.localDevice.model,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    
                    Text(
                        text = "${state.localDevice.brand.uppercase()} • ${state.localDevice.hardware.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        letterSpacing = 2.sp
                    )
                }

                AnimatedContent(
                    targetState = HeaderStatus(
                        loading = state.isRemoteLoading,
                        error = state.remoteError,
                        updated = state.lastUpdatedMillis,
                        isFromCache = state.isFromCache
                    ),
                    transitionSpec = {
                        (fadeIn(tween(400)) + scaleIn(initialScale = 0.92f)) togetherWith 
                        (fadeOut(tween(300)) + scaleOut(targetScale = 0.92f)) using SizeTransform(clip = false)
                    },
                    label = "status_transition"
                ) { status ->
                    when {
                        status.loading -> StatusPill(
                            icon = Icons.Rounded.Refresh,
                            text = stringResource(R.string.st_DeviceInfoScreen_i9j0),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        status.error != null -> ErrorRetryPill(status.error, onRetry)

                        status.updated != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusPill(
                                    icon = Icons.Rounded.CheckCircle,
                                    text = stringResource(R.string.st_DeviceInfoScreen_verified, formatTime(status.updated)),
                                    color = MaterialTheme.colorScheme.tertiary,
                                )

                                if (status.isFromCache) {
                                    StatusPill(
                                        icon = Icons.Rounded.Storage,
                                        text = stringResource(R.string.st_DeviceInfoScreen_k1l2),
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                            if (spec?.sourceUrl?.isNotBlank() == true) {
                                ToolzExpressiveIconButton(
                                    onClick = {
                                        haptic.click()
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(spec.sourceUrl))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(40.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    ),
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.st_DeviceInfoScreen_m3n4), modifier = Modifier.size(20.dp))
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
private fun StatusPill(
    icon: ImageVector,
    text: String,
    color: Color,
) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.15f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = color,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ErrorRetryPill(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusPill(
            icon = Icons.Rounded.ErrorOutline,
            text = message,
            color = MaterialTheme.colorScheme.error,
        )
        ToolzExpressiveButton(
            onClick = onRetry,
            modifier = Modifier.height(44.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = MediumExpressiveShape
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.st_DeviceInfoScreen_o5p6), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun QuickSpecsSection(quickSpecs: List<QuickSpecItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel(
            icon = Icons.Rounded.PhoneAndroid,
            title = stringResource(R.string.st_DeviceInfoScreen_q7r8),
            subtitle = stringResource(R.string.st_DeviceInfoScreen_s9t0),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        ExpressiveCarousel(
            items = quickSpecs,
            preferredItemWidth = 160.dp,
            itemSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) { item ->
            QuickSpecExpressiveCard(item = item)
        }
    }
}

@Composable
private fun QuickSpecExpressiveCard(
    item: QuickSpecItem,
    modifier: Modifier = Modifier
) {
    val icon = iconForSpec(item.name)
    ExpressiveCard(
        onClick = {},
        modifier = modifier.height(130.dp),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SpecCategoryCard(
    category: SpecCategory,
    modifier: Modifier = Modifier
) {
    ExpressiveCard(
        onClick = { },
        modifier = modifier.fillMaxWidth(),
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MediumExpressiveShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForCategory(category.name),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${category.items.size} Technical Details",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                        MediumExpressiveShape
                    )
                    .padding(12.dp)
            ) {
                category.items.forEachIndexed { index, detail ->
                    SpecDetailRow(
                        name = detail.name,
                        value = detail.value,
                    )
                    if (index != category.items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecDetailRow(name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(0.4f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.6f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun LocalHardwareSection(
    data: DetailedDeviceData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel(
            icon = Icons.Rounded.DeveloperBoard,
            title = stringResource(R.string.st_DeviceInfoScreen_c3d4),
            subtitle = stringResource(R.string.st_DeviceInfoScreen_local_hardware),
        )

        InfoMetricGrid(
            items = listOf(
                MetricItem(Icons.Rounded.Memory, stringResource(R.string.st_DeviceInfoScreen_a7b8), data.soc),
                MetricItem(Icons.Rounded.Storage, stringResource(R.string.st_DeviceInfoScreen_c9d0), "${formatSize(data.availRam)} free / ${formatSize(data.totalRam)}"),
                MetricItem(Icons.Rounded.Smartphone, stringResource(R.string.st_DeviceInfoScreen_e1f2), "${data.screenRes} @ ${data.refreshRate}Hz"),
                MetricItem(Icons.Rounded.BatteryChargingFull, stringResource(R.string.st_DeviceInfoScreen_g3h4), "${data.batteryLevel}% • ${data.batteryHealth}"),
                MetricItem(Icons.Rounded.Android, stringResource(R.string.st_DeviceInfoScreen_i5j6), "${data.androidVersion} (API ${data.apiLevel})"),
                MetricItem(Icons.Rounded.Sensors, stringResource(R.string.st_DeviceInfoScreen_k7l8), stringResource(R.string.st_DeviceInfoScreen_detected_sensors, data.sensorsCount)),
                MetricItem(Icons.Rounded.CameraAlt, stringResource(R.string.st_DeviceInfoScreen_m9n0), data.cameras.takeIf { it.isNotEmpty() }?.joinToString() ?: stringResource(R.string.st_DeviceInfoScreen_no_camera_info)),
                MetricItem(Icons.Rounded.Wifi, stringResource(R.string.st_DeviceInfoScreen_o1p2), data.wifiIp),
            ),
        )
    }
}

@Composable
private fun InfoMetricGrid(items: List<MetricItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    MetricCard(item = item, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCard(
    item: MetricItem,
    modifier: Modifier = Modifier,
) {
    ExpressiveCard(
        onClick = {},
        modifier = modifier.height(124.dp),
        shape = MediumExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Column {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.sp
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

private data class MetricItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

private data class HeaderStatus(
    val loading: Boolean,
    val error: String?,
    val updated: Long?,
    val isFromCache: Boolean = false
)

@Composable
private fun MarketShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "marketShimmer")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "marketShimmerShift"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        start = Offset(shift - 1000f, shift - 1000f),
        end = Offset(shift, shift)
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SectionLabel(
            icon = Icons.Rounded.Refresh,
            title = stringResource(R.string.st_DeviceInfoScreen_q3r4),
            subtitle = stringResource(R.string.st_DeviceInfoScreen_s5t6),
        )
        
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(brush, LargeExpressiveShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f), LargeExpressiveShape)
            )
        }
    }
}

@Composable
private fun EditQueryDialog(
    currentQuery: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentQuery) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.st_DeviceInfoScreen_u7v8), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.st_DeviceInfoScreen_w9x0),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue: String -> text = newValue },
                    label = { Text(stringResource(R.string.st_DeviceInfoScreen_a1b3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MediumExpressiveShape
                )
            }
        },
        confirmButton = {
            ToolzExpressiveButton(
                onClick = { onConfirm(text) },
                shape = SmallExpressiveShape
            ) {
                Text(stringResource(R.string.st_DeviceInfoScreen_c3d5), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.st_DeviceInfoScreen_e5f7), fontWeight = FontWeight.Bold)
            }
        },
        shape = LargeExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

private fun iconForCategory(category: String): ImageVector {
    val key = category.lowercase(Locale.getDefault())
    return when {
        "network" in key -> Icons.Rounded.CellTower
        "display" in key -> Icons.Rounded.Smartphone
        "platform" in key -> Icons.Rounded.Memory
        "memory" in key -> Icons.Rounded.Storage
        "camera" in key -> Icons.Rounded.CameraAlt
        "battery" in key -> Icons.Rounded.BatteryChargingFull
        "comms" in key || "wlan" in key -> Icons.Rounded.Wifi
        else -> Icons.Rounded.TableRows
    }
}

private fun iconForSpec(name: String): ImageVector {
    val key = name.lowercase(Locale.getDefault())
    return when {
        "display" in key || "size" in key -> Icons.Rounded.Smartphone
        "chipset" in key || "cpu" in key || "os" in key -> Icons.Rounded.Memory
        "memory" in key || "storage" in key -> Icons.Rounded.Storage
        "camera" in key -> Icons.Rounded.CameraAlt
        "battery" in key || "charging" in key -> Icons.Rounded.BatteryChargingFull
        else -> Icons.Rounded.PhoneAndroid
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroup = (kotlin.math.log10(size.toDouble()) / kotlin.math.log10(1024.0))
        .toInt()
        .coerceIn(units.indices)
    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        size / 1024.0.pow(digitGroup.toDouble()),
        units[digitGroup],
    )
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
