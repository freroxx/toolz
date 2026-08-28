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

package com.frerox.toolz.ui.screens.media.sections


import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.annotation.OptIn as AnnotationOptIn
import androidx.media3.common.util.UnstableApi
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.frerox.toolz.data.music.MusicTrack
import com.frerox.toolz.data.music.*
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalIsDarkTheme
import java.util.*

import com.frerox.toolz.ui.screens.media.MusicPlayerViewModel
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.ui.screens.media.PlaylistPickerDialog
import com.frerox.toolz.ui.screens.media.SongPickerDialog
import com.frerox.toolz.ui.screens.media.components.TagEditorSheet

// ─────────────────────────────────────────────────────────────────────────────
// Track List
// ─────────────────────────────────────────────────────────────────────────────

@UnstableApi
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackList(
    tracks: List<MusicTrack>,
    state: MusicUiState,
    viewModel: MusicPlayerViewModel,
    searchQuery: String = "",
    onOpenFullPlayer: () -> Unit,
    onDownload: (MusicTrack) -> Unit
) {
    if (tracks.isEmpty() && !state.isLoading) {
        EmptyMusicPlaceholder(onScan = { viewModel.scanMusic() })
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var editingTrack by remember { mutableStateOf<MusicTrack?>(null) }

    // Partition once per track-list change, not on every recomposition —
    // these lists back the lazy items directly.
    val offlineTracks = remember(tracks) { tracks.filter { it.path != null } }
    val onlineTracks = remember(tracks) { tracks.filter { it.path == null && it.sourceUrl != null } }
    val showOnlineSection = onlineTracks.isNotEmpty() && state.isOnline

    // Index of the online header within the flattened lazy item list, so the
    // tween arrow can jump straight to it — 1 offset for the offline header.
    val onlineHeaderIndex = if (offlineTracks.isNotEmpty()) offlineTracks.size + 1 else 0

    Box(modifier = Modifier.fillMaxSize()) {
        // Slim loading bar at the very top — visible during incremental scan
        // while tracks are already appearing (non-blocking UI)
        if (state.isLoading) {
            ExpressiveLinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.TopCenter)
                    .zIndex(1f),
                color = MaterialTheme.colorScheme.primary,
                strokeCap = StrokeCap.Round
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().fadingEdges(top = 12.dp, bottom = 20.dp),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                bottom = 130.dp, top = if (state.isLoading) 5.dp else 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (offlineTracks.isNotEmpty()) {
                item(key = "offline_header") {
                    TrackSectionHeader(
                        label = stringResource(R.string.st_MusicPlayerScreen_off10),
                        count = offlineTracks.size,
                        icon = Icons.Rounded.Storage,
                        color = MaterialTheme.colorScheme.primary,
                        showTweenArrow = showOnlineSection,
                        arrowPointsDown = true,
                        onTween = {
                            // Was animateScrollToItem — on a LazyColumn that
                            // animates a smooth scroll THROUGH every
                            // intermediate item between the current
                            // position and the target, so with a large
                            // library (hundreds of offline tracks) jumping
                            // to the online section had to measure/compose
                            // its way through all of them during the
                            // animation, scaling directly with library
                            // size. This is a "jump to section" action, not
                            // an in-view scroll, so an instant jump is both
                            // the right UX call and removes the lag
                            // entirely — scrollToItem is O(1) regardless of
                            // list length.
                            scope.launch { listState.scrollToItem(onlineHeaderIndex) }
                        },
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)
                    )
                }
                // performanceMode and fast-scroll both skip the staggered
                // entrance — it's a per-item animation cost that compounds
                // badly once many rows enter/leave the viewport per frame.
                itemsIndexed(offlineTracks, key = { _, t -> t.uri }) { index, track ->
                    val isSelected = state.selectedTracks.contains(track.uri)
                    TrackListItem(
                        track = track,
                        isSelected = isSelected,
                        state = state,
                        viewModel = viewModel,
                        tracks = tracks,
                        onOpenFullPlayer = onOpenFullPlayer,
                        searchQuery = searchQuery,
                        onDownload = onDownload,
                        onEditTags = { editingTrack = it },
                        modifier = if (state.performanceMode) Modifier else Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = tween(220, easing = FastOutSlowInEasing)
                        )
                    )
                }
            }

            if (showOnlineSection) {
                item(key = "online_header") {
                    TrackSectionHeader(
                        label = stringResource(R.string.st_MusicPlayerScreen_on11),
                        count = onlineTracks.size,
                        icon = Icons.Rounded.Cloud,
                        color = MaterialTheme.colorScheme.secondary,
                        showTweenArrow = offlineTracks.isNotEmpty(),
                        arrowPointsDown = false,
                        onTween = {
                            scope.launch { listState.scrollToItem(0) }
                        },
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 24.dp, bottom = 10.dp)
                    )
                }
                itemsIndexed(onlineTracks, key = { _, t -> t.uri }) { index, track ->
                    val isSelected = state.selectedTracks.contains(track.uri)
                    TrackListItem(
                        track = track,
                        isSelected = isSelected,
                        state = state,
                        viewModel = viewModel,
                        tracks = tracks,
                        onOpenFullPlayer = onOpenFullPlayer,
                        searchQuery = searchQuery,
                        onDownload = onDownload,
                        onEditTags = { editingTrack = it },
                        modifier = if (state.performanceMode) Modifier else Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = tween(220, easing = FastOutSlowInEasing)
                        )
                    )
                }
            }
        } // end LazyColumn

        editingTrack?.let { t ->
            TagEditorSheet(
                track = t,
                onDismiss = { editingTrack = null },
                onSave = { title, artist, album, thumb, lyrics ->
                    viewModel.editTrackTags(t, title, artist, album, thumb, lyrics) { ok ->
                        editingTrack = null
                    }
                }
            )
        }
    } // end Box
}

// Compact M3-expressive pill used for the Offline/Online section headers.
// A tiny circular chevron sits at the trailing edge so the person can jump
// straight to the other section without scrolling past a long list.
@Composable
fun TrackSectionHeader(
    label: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    showTweenArrow: Boolean,
    arrowPointsDown: Boolean,
    onTween: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
                color = color
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "· $count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.65f)
            )
            if (showTweenArrow) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    onClick = onTween,
                    modifier = Modifier.size(28.dp).bouncyClick(onClick = onTween),
                    shape = CircleShape,
                    color = color.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (arrowPointsDown) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                            contentDescription = if (arrowPointsDown) "Jump to online tracks" else "Jump to offline tracks",
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TrackListItem(
    track: MusicTrack,
    isSelected: Boolean,
    state: MusicUiState,
    viewModel: MusicPlayerViewModel,
    tracks: List<MusicTrack>,
    onOpenFullPlayer: () -> Unit,
    searchQuery: String,
    onDownload: (MusicTrack) -> Unit,
    onEditTags: (MusicTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    if (state.performanceMode) {
        TrackItem(
            track = track,
            isCurrent = track.uri == state.currentTrack?.uri,
            isSelected = isSelected,
            isSelectionMode = state.isSelectionMode,
            onClick = {
                if (state.isSelectionMode) viewModel.toggleTrackSelection(track.uri)
                else viewModel.playTrack(track, tracks)
            },
            onLongClick = { viewModel.toggleTrackSelection(track.uri) },
            onDelete = { viewModel.deleteTrack(track) },
            onAddToPlaylist = { viewModel.addTrackToPlaylist(it, track) },
            onCreatePlaylist = { viewModel.createPlaylist(it) },
            onToggleFavorite = { viewModel.toggleFavorite(track) },
            onDownload = { onDownload(track) },
            onEditTags = { onEditTags(track) },
            playlists = state.playlists,
            searchQuery = searchQuery,
            karaokeEnabled = state.karaokeEnabled,
            onKaraokeClick = {
                if (track.aiLyrics.isNullOrEmpty() && track.sourceUrl == null) {
                    android.widget.Toast.makeText(context, nalwfMsg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.playTrack(track, tracks)
                    viewModel.toggleKaraokeMode()
                    onOpenFullPlayer()
                }
            },
            modifier = modifier
        )
    } else {
        TrackItem(
            track = track,
            isCurrent = track.uri == state.currentTrack?.uri,
            isSelected = isSelected,
            isSelectionMode = state.isSelectionMode,
            onClick = {
                if (state.isSelectionMode) viewModel.toggleTrackSelection(track.uri)
                else viewModel.playTrack(track, tracks)
            },
            onLongClick = { viewModel.toggleTrackSelection(track.uri) },
            onDelete = { viewModel.deleteTrack(track) },
            onAddToPlaylist = { viewModel.addTrackToPlaylist(it, track) },
            onCreatePlaylist = { viewModel.createPlaylist(it) },
            onToggleFavorite = { viewModel.toggleFavorite(track) },
            onDownload = { onDownload(track) },
            onEditTags = { onEditTags(track) },
            playlists = state.playlists,
            searchQuery = searchQuery,
            karaokeEnabled = state.karaokeEnabled,
            onKaraokeClick = {
                if (track.aiLyrics.isNullOrEmpty() && track.sourceUrl == null) {
                    android.widget.Toast.makeText(context, nalwfMsg, android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.playTrack(track, tracks)
                    viewModel.toggleKaraokeMode()
                    onOpenFullPlayer()
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun EmptyMusicPlaceholder(onScan: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Icon(
                Icons.Rounded.MusicOff,
                null,
                modifier = Modifier.size(80.dp).alpha(0.1f),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.st_MusicPlayerScreen_ntf15), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.st_MusicPlayerScreen_sdtm16), color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            ToolzExpressiveButton(onClick = onScan, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.st_MusicPlayerScreen_sn17), fontWeight = FontWeight.Black)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track Item
// ─────────────────────────────────────────────────────────────────────────────

@AnnotationOptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrackItem(
    track: MusicTrack,
    isCurrent: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onDownload: () -> Unit = {},
    onEditTags: () -> Unit = {},
    playlists: List<Playlist>,
    searchQuery: String = "",
    deleteLabel: String = "Delete",
    karaokeEnabled: Boolean = true,
    onKaraokeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // Small, restrained bounce on the favorite toggle — mirrors the full
    // player's treatment but scaled down for a repeating list row. Skips
    // the pop on first composition so it only fires on a real toggle.
    val favScale = remember(track.uri) { Animatable(1f) }
    var isFavInitialized by remember(track.uri) { mutableStateOf(false) }
    LaunchedEffect(track.uri, track.isFavorite) {
        if (!isFavInitialized) {
            isFavInitialized = true
        } else {
            favScale.snapTo(0.75f)
            favScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }
    }

    val cardColors = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        isCurrent  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
        else       -> Color.Transparent
    }
    // M3 Expressive favors asymmetric corner treatments over a uniform
    // radius — leading corners stay tight so the row reads as a rail item,
    // trailing corners open up toward the art. One shape value drives the
    // card, the art clip, and every overlay drawn on the art, so they can't
    // drift out of sync the way three separately-declared radii could.
    val artCorner = if (isCurrent) 18.dp else 14.dp
    val artShape = RoundedCornerShape(
        topStart = artCorner * 0.4f, bottomStart = artCorner * 0.4f,
        topEnd = artCorner, bottomEnd = artCorner
    )
    val cardShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp)

    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    val context = LocalContext.current
    
    ExpressiveCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        containerColor = cardColors,
        elevation = 0.dp,
        border = if (isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Now-playing accent — a slim primary bar instead of relying on
            // tint alone to signal "this is the track playing right now",
            // so it reads at a glance while scanning a long list.
            val barHeight by animateDpAsState(
                targetValue = if (isCurrent) 36.dp else 0.dp,
                animationSpec = if (LocalPerformanceMode.current) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "nowPlayingBar"
            )
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Thumbnail
            Box(modifier = Modifier.size(52.dp)) {
                AlbumArtImage(
                    url = track.thumbnailUri,
                    seed = track.title,
                    modifier = Modifier.fillMaxSize().clip(artShape),
                    iconSize = 22.dp
                )

                // Cloud badge for online (not-yet-downloaded) tracks
                if (track.path == null && track.sourceUrl != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(17.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Cloud,
                                contentDescription = "Streamed from Catalog, not downloaded",
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                if (isCurrent && !isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(artShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingBarsIndicator()
                    }
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(artShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.st_MusicPlayerScreen_sel18), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                val highlightColor = MaterialTheme.colorScheme.secondary
                Text(
                    text = if (searchQuery.isNotBlank()) highlightSearch(track.title, searchQuery, highlightColor) else AnnotatedString(track.title),
                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
                Text(
                    text = artistText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.5.sp
                )
            }

            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Streamed-but-undownloaded tracks get a dedicated download
                    // affordance; everything else is one tap into the overflow,
                    // instead of stacking a third permanent circular button.
                    if (track.path == null && track.sourceUrl != null) {
                        IconButton(onClick = onDownload, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.st_MusicPlayerScreen_d19),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleFavorite()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (track.isFavorite) stringResource(R.string.st_MusicPlayerScreen_rff20) else stringResource(R.string.st_MusicPlayerScreen_atf21),
                            tint = if (track.isFavorite) Color(0xFFE0555C) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { scaleX = favScale.value; scaleY = favScale.value }
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(38.dp)) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.st_MusicPlayerScreen_mo22),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.st_MusicPlayerScreen_atp23), fontWeight = FontWeight.Medium) },
                                onClick = { showPlaylistPicker = true; showMenu = false },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit tags", fontWeight = FontWeight.Medium) },
                                onClick = { onEditTags(); showMenu = false },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            if (karaokeEnabled && (!track.aiLyrics.isNullOrEmpty() || track.sourceUrl != null)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.st_MusicPlayerScreen_oik24), fontWeight = FontWeight.Medium) },
                                    onClick = { onKaraokeClick(); showMenu = false },
                                    leadingIcon = { Icon(Icons.Rounded.MicExternalOn, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                onClick = { onDelete(); showMenu = false },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = playlists,
            onDismiss = { showPlaylistPicker = false },
            onSelect = onAddToPlaylist,
            onCreatePlaylist = onCreatePlaylist
        )
    }
}

// Animated playing bars
@Composable
fun PlayingBarsIndicator() {
    val performanceMode = LocalPerformanceMode.current
    if (performanceMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            listOf(10.dp, 14.dp, 8.dp).forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }
        }
        return
    }

    val inf = rememberInfiniteTransition(label = "playingBars")
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(0, 100, 50).forEach { delay ->
            val h by inf.animateFloat(
                4f, 14f,
                infiniteRepeatable(tween(500, easing = FastOutSlowInEasing, delayMillis = delay), RepeatMode.Reverse),
                label = "bar$delay"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track Card (horizontal scroll)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrackCard(track: MusicTrack, onClick: () -> Unit, cardWidth: Dp = 152.dp) {
    val performanceMode = LocalPerformanceMode.current
    Column(
        modifier = Modifier
            .width(cardWidth)
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.Start
    ) {
        val artSize = cardWidth - 6.dp
        // Corner radius capped at 6dp (was up to 12dp on two corners). At
        // this card width a 12dp radius eats visibly into the square art.
        AlbumArtImage(
            url = track.thumbnailUri,
            seed = track.title,
            modifier = Modifier
                .size(artSize)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 6.dp)),
            iconSize = 32.dp
        )
        Spacer(Modifier.height(12.dp))
        // Text block width matches the art. `softWrap = false` was removed
        // from both Texts below — with `maxLines = 1` it's redundant for
        // preventing wrapping, but inside a horizontally-scrolling carousel
        // it was letting long titles get measured at their natural
        // (unconstrained) width before the ellipsis pass ran, so text
        // could render starting to the left of this Column's own left
        // edge instead of being clipped/ellipsized inside it — visible as
        // the first letter or two of the title/artist getting cut off on
        // the left rather than the end. `.fillMaxWidth()` makes sure each
        // Text is actually laid out to this exact box width so the
        // ellipsis has a real boundary to clip against.
        Column(
            modifier = Modifier
                .width(artSize)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: stringResource(R.string.st_MusicPlayerScreen_ua25)
            Text(
                text = artistText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.3.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Track Carousel Row (keeps the M3 multi-browse scroll/mask feel, but only
// masks the art — title/artist render outside the mask so they can't be
// clipped by it)
// ─────────────────────────────────────────────────────────────────────────────

// Why this exists: the Library tab's Recently Played / Most Played rows used
// to go through ExpressiveCarousel (HorizontalMultiBrowseCarousel), which
// wraps its ENTIRE per-item slot — art and anything else placed inside —
// in `.maskClip(shapes.large)`. That mask is scroll-position-driven, which
// is what gave the nice "items subtly resize/mask as they scroll past
// center" feel — but it also means anything inside that slot, including
// text below the art, gets clipped to the same shape. That mask, not
// TrackCard's own corner radius, was the actual source of the earlier
// text-clipping bug (title/artist losing their leading characters).
//
// This keeps the good scroll feel by letting the carousel mask/animate
// ONLY the art thumbnail — the part that's supposed to look carousel-y —
// and renders title/artist as ordinary text underneath, entirely outside
// the carousel's per-item masked box. The per-item slot width itself is
// animated as it scrolls past center; the text below is rendered at a
// fixed width (preferredItemWidth) rather than tracking that animation,
// which for a two-line caption under a resizing image is visually
// seamless, and guarantees the text can never be clipped by the mask
// regardless of where the item currently sits in the strip.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackCarouselRow(
    tracks: List<MusicTrack>,
    onTrackClick: (MusicTrack) -> Unit,
    preferredItemWidth: Dp = 152.dp,
    itemSpacing: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 2.dp)
) {
    val carouselState = rememberCarouselState { tracks.size }
    val artShape = RoundedCornerShape(topStart = 6.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 6.dp)

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = preferredItemWidth,
        itemSpacing = itemSpacing,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxWidth().height(preferredItemWidth + 62.dp)
    ) { index ->
        val track = tracks[index]
        Column(
            modifier = Modifier.fillMaxHeight().bouncyClick(onClick = { onTrackClick(track) }),
            horizontalAlignment = Alignment.Start
        ) {
            AlbumArtImage(
                url = track.thumbnailUri,
                seed = track.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .maskClip(artShape),
                iconSize = 32.dp
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .width(preferredItemWidth - 6.dp)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                val artistText = track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }?.uppercase() ?: "UNKNOWN ARTIST"
                Text(
                    text = artistText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

fun highlightSearch(text: String, query: String, highlightColor: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val index = text.indexOf(query, ignoreCase = true)
    if (index == -1) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text.substring(0, index))
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Black)) {
            append(text.substring(index, index + query.length))
        }
        append(text.substring(index + query.length))
    }
}
