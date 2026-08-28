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
import com.frerox.toolz.ui.screens.media.SongPickerDialog
import com.frerox.toolz.ui.screens.media.ai.*
import com.frerox.toolz.data.catalog.CatalogTrack

// ─────────────────────────────────────────────────────────────────────────────
// Folder List + Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun FolderList(state: MusicUiState, onFolderClick: (String, List<MusicTrack>) -> Unit) {
    if (state.folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.FolderOff, null, modifier = Modifier.size(72.dp).alpha(0.1f), tint = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.st_MusicPlayerScreen_nff28), color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.st_MusicPlayerScreen_acfutba29), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 130.dp, top = 14.dp, start = 14.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.folders.keys.toList()) { folderName ->
            val tracks = state.folders[folderName] ?: emptyList()
            // Count how many tracks from this folder are favorites or currently playing
            val hasCurrentTrack = state.currentTrack?.let { current -> tracks.any { it.uri == current.uri } } == true
            FolderCard(
                folderName = folderName,
                trackCount = tracks.size,
                isCurrentFolder = hasCurrentTrack,
                onClick = { onFolderClick(folderName, tracks) }
            )
        }
    }
}

@Composable
fun FolderCard(
    folderName: String,
    trackCount: Int,
    isCurrentFolder: Boolean = false,
    onClick: () -> Unit
) {
    val performanceMode = LocalPerformanceMode.current
    val borderAlpha by animateFloatAsState(
        targetValue = if (isCurrentFolder) 0.7f else 0.14f,
        animationSpec = if (performanceMode) snap() else tween(300),
        label = "folderBorder"
    )
    // Special-cased folders (like the app's own download bucket) get a
    // distinct glyph so they read instantly in a scanning grid.
    val isDownloadsFolder = folderName == "Toolz Downloads"
    val folderIcon = when {
        isDownloadsFolder -> Icons.Rounded.DownloadDone
        isCurrentFolder -> Icons.Rounded.FolderOpen
        else -> Icons.Rounded.Folder
    }
    val accentColor = if (isCurrentFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    // Corner radius trimmed down further (16dp max) so the curve never
    // reaches the folder name's baseline, and the icon chip now sits on
    // its own tonal plate rather than floating directly on the card
    // background — gives the tile a clearer two-layer, "chip + content"
    // structure instead of one flat block of color.
    //
    // The container itself stays a neutral surface tint regardless of
    // playing state — previously an active folder swapped to a heavy
    // primaryContainer background AND primary-colored title text AND two
    // separate primary-tinted circles (icon chip + playing-bars badge), so
    // everything on the card read as the same hue with no anchor: the
    // folder name could wash out against its own background, leaving only
    // the icon chip's circle visible as "a blue dot" with the label
    // effectively invisible next to it. The accent color is now reserved
    // for the icon chip and a small trailing indicator only.
    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    val context = LocalContext.current
    
    ExpressiveCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(148.dp).bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 18.dp),
        containerColor = if (isCurrentFolder)
            MaterialTheme.colorScheme.surfaceContainerHigh
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha)),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
                    color = accentColor.copy(alpha = if (isCurrentFolder) 0.18f else 0.1f)
                ) {
                    Box(modifier = Modifier.padding(9.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            folderIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                if (isCurrentFolder) {
                    // Compact playing-bars badge — tonal chip is neutral so
                    // it doesn't compete with the icon chip above it for
                    // "which circle is the accent color" attention.
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)) {
                            PlayingBarsIndicator()
                        }
                    }
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                        modifier = Modifier.size(12.dp).padding(top = 4.dp, end = 2.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = folderName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    // Always the plain onSurface color — the folder name is
                    // the one thing on this card that must never compete
                    // with an accent-tinted background for contrast. Which
                    // folder is playing is already communicated by the
                    // playing-bars badge and the icon color above.
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (trackCount == 1) "1 TRACK" else "$trackCount TRACKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrentFolder)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@AnnotationOptIn(UnstableApi::class)
@Composable
fun FolderTracksDialog(
    folderName: String,
    tracks: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlayTrack: (MusicTrack) -> Unit,
    state: MusicUiState,
    viewModel: MusicPlayerViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist?.contains(searchQuery, ignoreCase = true) == true
        }
    }
    val currentlyPlayingInFolder = state.currentTrack?.let { current -> tracks.any { it.uri == current.uri } } == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(36.dp, 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        },
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

        // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (currentlyPlayingInFolder) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        folderName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (tracks.size == 1) "1 TRACK" else "${tracks.size} TRACKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        if (currentlyPlayingInFolder) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    stringResource(R.string.st_MusicPlayerScreen_p30),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
                // Play-all button — hidden rather than crashing when a
                // (freshly emptied) folder has nothing left to play.
                if (tracks.isNotEmpty()) {
                    ToolzExpressiveIconButton(onClick = { onPlayTrack(tracks.first()) }, shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(26.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Search
            ExpressiveSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.st_MusicPlayerScreen_st_hint31), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.6f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)),
                        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f, animationSpec = tween(120))
                    ) {
                        Surface(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp).bouncyClick { searchQuery = "" },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.st_MusicPlayerScreen_cs5),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.07f))
            Spacer(Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(48.dp).alpha(0.12f), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.st_MusicPlayerScreen_nm32), color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.uri }) { track ->
                        TrackItem(
                            track = track,
                            isCurrent = track.uri == state.currentTrack?.uri,
                            isSelected = state.selectedTracks.contains(track.uri),
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) viewModel.toggleTrackSelection(track.uri)
                                else onPlayTrack(track)
                            },
                            onLongClick = { viewModel.toggleTrackSelection(track.uri) },
                            onDelete = { viewModel.deleteTrack(track) },
                            onAddToPlaylist = { viewModel.addTrackToPlaylist(it, track) },
                            onCreatePlaylist = { viewModel.createPlaylist(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(track) },
                            playlists = state.playlists
                        )
                    }
                }
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Library Section
// ─────────────────────────────────────────────────────────────────────────────

@AnnotationOptIn(UnstableApi::class)
@Composable
fun LibrarySection(
    state: MusicUiState,
    viewModel: MusicPlayerViewModel,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onUpdateThumb: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit = {},  // wire to ViewModel
    onRenamePlaylist: (Playlist, String) -> Unit = { _, _ -> }, // wire to ViewModel
    onFolderClick: (String, List<MusicTrack>) -> Unit = { _, _ -> },
    onAddFolder: () -> Unit = {},
    onDownload: (MusicTrack) -> Unit
) {
    var showFavoritesDetail by remember { mutableStateOf(false) }
    var showRecentDetail by remember { mutableStateOf(false) }
    var showMostPlayedDetail by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).fadingEdges(top = 16.dp, bottom = 24.dp),
        contentPadding = PaddingValues(bottom = 130.dp, top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        // The top app bar already reads "STUDIO PLAYER / Library", so this
        // doesn't repeat that title in a decorative gradient card — it just
        // states the one fact that matters (the count) plainly.
        item {
            Text(
                text = "${state.tracks.size} tracks · ${state.playlists.size} playlists",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 2.dp)
            )
        }

        // ── Stats header row ──────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favorites
                QuickAccessCard(
                    modifier = Modifier.weight(1f).height(138.dp),
                    icon = Icons.Rounded.Favorite,
                    label = stringResource(R.string.st_MusicPlayerScreen_f33),
                    count = state.favoriteTracks.size,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { showFavoritesDetail = true }
                )
                // Recently Played
                QuickAccessCard(
                    modifier = Modifier.weight(1f).height(138.dp),
                    icon = Icons.Rounded.History,
                    label = stringResource(R.string.st_MusicPlayerScreen_r34),
                    count = state.recentlyPlayed.size,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { showRecentDetail = true }
                )
                // Most Played
                QuickAccessCard(
                    modifier = Modifier.weight(1f).height(138.dp),
                    icon = Icons.Rounded.TrendingUp,
                    label = stringResource(R.string.st_MusicPlayerScreen_tp35),
                    count = state.mostPlayed.size,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { showMostPlayedDetail = true }
                )
            }
        }

        // ── Recently Played carousel ──────────────────────────────────────────
        if (state.recentlyPlayed.isNotEmpty()) {
            item {
                RowSectionHeader(stringResource(R.string.st_MusicPlayerScreen_rp36)) { showRecentDetail = true }
                Spacer(Modifier.height(12.dp))
                TrackCarouselRow(
                    tracks = state.recentlyPlayed.take(12),
                    onTrackClick = { viewModel.playTrack(it, state.recentlyPlayed) }
                )
            }
        }

        // ── Most Played carousel ──────────────────────────────────────────────
        if (state.mostPlayed.isNotEmpty()) {
            item {
                RowSectionHeader(stringResource(R.string.st_MusicPlayerScreen_mop37)) { showMostPlayedDetail = true }
                Spacer(Modifier.height(12.dp))
                TrackCarouselRow(
                    tracks = state.mostPlayed.take(12),
                    onTrackClick = { viewModel.playTrack(it, state.mostPlayed) }
                )
            }
        }

        // ── Playlists ─────────────────────────────────────────────────────────
        // Header is always shown now — previously the whole section
        // (header, create button, everything) vanished when there were no
        // playlists yet, which meant a first-time user had no way to find
        // the create action inside the Library tab itself. The count in
        // the label doubles as a quiet confirmation that "0" really is
        // the current state, not a loading gap.
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(if (state.playlists.isEmpty()) stringResource(R.string.st_MusicPlayerScreen_pl39) else stringResource(R.string.st_MusicPlayerScreen_pl39) + " · ${state.playlists.size}")
                Surface(
                    onClick = onCreatePlaylist,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.bouncyClick(onClick = onCreatePlaylist)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.st_MusicPlayerScreen_n9),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.playlists.isEmpty()) {
            item { PlaylistEmptyCard(onCreatePlaylist = onCreatePlaylist) }
        } else {
            items(state.playlists.chunked(2)) { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    chunk.forEach { playlist ->
                        Box(modifier = Modifier.weight(1f)) {
                            val thumbs = remember(playlist, state.tracks) {
                                playlist.trackUris
                                    .take(4)
                                    .mapNotNull { uri -> state.tracks.find { it.uri == uri }?.thumbnailUri }
                            }
                            PlaylistCard(
                                playlist = playlist,
                                firstTrackThumbnails = thumbs,
                                onClick = { onPlaylistClick(playlist) },
                                onPlay = { viewModel.playPlaylist(playlist) },
                                onDelete = { onDeletePlaylist(playlist) },
                                onRename = { playlistToRename = playlist },
                                onUpdateThumb = { onUpdateThumb(playlist) }
                            )
                        }
                    }
                    if (chunk.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        // ── Folders ───────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(if (state.folders.isEmpty()) stringResource(R.string.st_MusicPlayerScreen_fl42) else stringResource(R.string.st_MusicPlayerScreen_fl42) + " · ${state.folders.size}")
                Surface(
                    onClick = onAddFolder,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.bouncyClick(onClick = onAddFolder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(R.string.st_MusicPlayerScreen_a43),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.folders.isEmpty()) {
            item {
                FolderEmptyCard(onAddFolder = onAddFolder)
            }
        } else {
            // All folder rows now live inside ONE LazyColumn item, wrapped
            // in their own Column with a 12dp spacedBy. Previously each row
            // was a separate item, so the LazyColumn's own top-level
            // spacedBy(20dp) ran *between* every row in addition to the
            // 12dp spacer this composable added itself — rows ended up
            // ~32dp apart instead of the intended tight 12dp rhythm, which
            // read as a much bigger gap than every other section.
            item {
                val folderRows = state.folders.keys.toList().chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    folderRows.forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            chunk.forEach { folderName ->
                                val tracks = state.folders[folderName] ?: emptyList()
                                val hasCurrentTrack = state.currentTrack?.let { current -> tracks.any { it.uri == current.uri } } == true
                                Box(modifier = Modifier.weight(1f)) {
                                    FolderCard(
                                        folderName = folderName,
                                        trackCount = tracks.size,
                                        isCurrentFolder = hasCurrentTrack,
                                        onClick = { onFolderClick(folderName, tracks) }
                                    )
                                }
                            }
                            if (chunk.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // Detail overlays
    if (showFavoritesDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = stringResource(R.string.st_MusicPlayerScreen_f33), trackUris = state.favoriteTracks.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showFavoritesDetail = false },
            onPlayPlaylist = { _, shuffle -> if (state.favoriteTracks.isNotEmpty()) {
                val list = if (shuffle) state.favoriteTracks.shuffled() else state.favoriteTracks
                viewModel.playTrack(list.first(), list)
            } },
            onDeletePlaylist = {},
            onAddTrack = { viewModel.toggleFavorite(it) },
            onRemoveTrack = { uri -> state.favoriteTracks.find { it.uri == uri }?.let { viewModel.toggleFavorite(it) } },
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDownload = onDownload
        )
    }
    if (showRecentDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = stringResource(R.string.st_MusicPlayerScreen_r34), trackUris = state.recentlyPlayed.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showRecentDetail = false },
            onPlayPlaylist = { _, shuffle -> if (state.recentlyPlayed.isNotEmpty()) {
                val list = if (shuffle) state.recentlyPlayed.shuffled() else state.recentlyPlayed
                viewModel.playTrack(list.first(), list)
            } },
            onDeletePlaylist = {}, onAddTrack = {}, onRemoveTrack = {},
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDownload = onDownload
        )
    }
    if (showMostPlayedDetail) {
        PlaylistDetailView(
            playlist = Playlist(name = stringResource(R.string.st_MusicPlayerScreen_tp35), trackUris = state.mostPlayed.map { it.uri }),
            allTracks = state.tracks,
            onDismiss = { showMostPlayedDetail = false },
            onPlayPlaylist = { _, shuffle -> if (state.mostPlayed.isNotEmpty()) {
                val list = if (shuffle) state.mostPlayed.shuffled() else state.mostPlayed
                viewModel.playTrack(list.first(), list)
            } },
            onDeletePlaylist = {}, onAddTrack = {}, onRemoveTrack = {},
            onPlayTrack = { track, tracks -> viewModel.playTrack(track, tracks) },
            isEditable = false,
            currentTrackUri = state.currentTrack?.uri,
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onDownload = onDownload
        )
    }

    // Rename dialog
    playlistToRename?.let { playlist ->
        var newName by remember(playlist.id) { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text(stringResource(R.string.st_MusicPlayerScreen_rp46), fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.st_MusicPlayerScreen_n47)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            },
            confirmButton = {
                ToolzExpressiveButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenamePlaylist(playlist, newName.trim())
                            playlistToRename = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    enabled = newName.isNotBlank()
                ) { Text(stringResource(R.string.st_MusicPlayerScreen_rn48), fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) { Text(stringResource(R.string.st_MusicPlayerScreen_c49)) }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun RowSectionHeader(title: String, onViewAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(title)
        TextButton(onClick = onViewAll) {
            Text("SEE ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun QuickAccessCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    // Asymmetric M3 Expressive corner, trimmed down from the previous
    // 24dp max — at this tile width a corner that large curved directly
    // under the label baseline and clipped the descenders of "Recent" /
    // "Top Played". 16dp still reads as a distinct expressive shape while
    // leaving the label column flat, unrounded ground to sit on. The
    // bottom padding is also given its own (larger) inset so the text
    // block clears the curve with margin instead of hugging it.
    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    val context = LocalContext.current
    
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier.bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 12.dp),
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor
                )
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor.copy(alpha = 0.75f),
                letterSpacing = 0.8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Playlist Card
// ─────────────────────────────────────────────────────────────────────────────

@Suppress("EXPERIMENTAL_API_USAGE")
@Composable
fun PlaylistCard(
    playlist: Playlist,
    firstTrackThumbnails: List<String?> = emptyList(),
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit = {},
    onRename: () -> Unit = {},
    onUpdateThumb: () -> Unit = {}
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val performanceMode = LocalPerformanceMode.current

    val nalwfMsg = stringResource(R.string.st_MusicPlayerScreen_nalwf14)
    val context = LocalContext.current
    
    ExpressiveCard(
        onClick = onClick,
        onLongClick = { showContextMenu = true },
        modifier = Modifier.fillMaxWidth().height(220.dp),
        shape = RoundedCornerShape(36.dp),
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ── Background: custom thumb > mosaic > solid gradient ────────────
            when {
                playlist.thumbnailUri != null -> {
                    AsyncImage(
                        model = playlist.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(if (performanceMode) 0.8f else 0.55f)
                    )
                    if (!performanceMode) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)), startY = 60f)
                            )
                        )
                    }
                }
                firstTrackThumbnails.isNotEmpty() && !performanceMode -> {
                    // Always show mosaic when we have any thumbnails, unless in performance mode
                    val thumbs = (firstTrackThumbnails + List(4) { null }).take(4)
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(model = thumbs[0], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                            AsyncImage(model = thumbs[1], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(model = thumbs[2], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                            AsyncImage(model = thumbs[3], contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight(), error = rememberVectorPainter(Icons.Rounded.MusicNote))
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                ),
                                startY = 0f
                            )
                        )
                    )
                }
                else -> {
                    // Solid gradient fallback when empty playlist or in performance mode with no explicit thumb
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            if (performanceMode) {
                                SolidColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        )
                    )
                    // Music note icon centered
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        modifier = Modifier.size(72.dp).align(Alignment.Center)
                    )
                }
            }

            // Info and Play button
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (playlist.trackUris.size == 1) "1 track" else "${playlist.trackUris.size} tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                
                Surface(
                    onClick = onPlay,
                    modifier = Modifier.size(46.dp).bouncyClick {},
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // ── Long-press context menu ───────────────────────────────────────
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                shape = RoundedCornerShape(20.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Play", fontWeight = FontWeight.Bold) },
                    onClick = { showContextMenu = false; onPlay() },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Rename", fontWeight = FontWeight.Bold) },
                    onClick = { showContextMenu = false; onRename() },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                )
                DropdownMenuItem(
                    text = { Text("Set cover image", fontWeight = FontWeight.Bold) },
                    onClick = { showContextMenu = false; onUpdateThumb() },
                    leadingIcon = { Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                )
                HorizontalDivider(modifier = Modifier.alpha(0.1f).padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("Delete", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error) },
                    onClick = { showContextMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Playlist Detail View
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    allTracks: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlayPlaylist: (Playlist, Boolean) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onAddTrack: (MusicTrack) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    isEditable: Boolean = true,
    currentTrackUri: String? = null,
    onToggleFavorite: (MusicTrack) -> Unit = {},
    onDownload: (MusicTrack) -> Unit
) {
    var showAddTrack by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val playlistTracks = remember(allTracks, playlist.trackUris) {
        playlist.trackUris.mapNotNull { uri -> allTracks.find { it.uri == uri } }
    }

    val filteredPlaylistTracks = remember(playlistTracks, searchQuery) {
        if (searchQuery.isBlank()) playlistTracks
        else playlistTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    val playingInThisPlaylist = currentTrackUri != null && playlistTracks.any { it.uri == currentTrackUri }
    val performanceMode = LocalPerformanceMode.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Enhanced Dynamic Header ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                // Background blurred artwork
                if (!performanceMode && playlistTracks.isNotEmpty()) {
                    AsyncImage(
                        model = playlist.thumbnailUri ?: playlistTracks.firstOrNull()?.thumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.2f)
                            .blur(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Artwork with elevation and better shape
                    Surface(
                        modifier = Modifier.size(160.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 16.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        if (playlist.thumbnailUri != null) {
                            AsyncImage(
                                model = playlist.thumbnailUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (playlistTracks.isNotEmpty()) {
                            val thumbs = (playlistTracks.map { it.thumbnailUri } + List(4) { null }).take(4)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = false
                            ) {
                                items(thumbs) { thumb ->
                                    AsyncImage(
                                        model = thumb,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.aspectRatio(1f).fillMaxSize(),
                                        error = rememberVectorPainter(Icons.Rounded.MusicNote)
                                    )
                                }
                            }
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    if (playingInThisPlaylist) {
                        Spacer(Modifier.height(8.dp))
                        PlayingBarsIndicator()
                    }
                }

                // Top buttons
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolzExpressiveIconButton(onClick = onDismiss, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)), shape = CircleShape) {
                        Icon(Icons.Rounded.Close, null)
                    }

                    if (isEditable) {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            ToolzExpressiveIconButton(onClick = { showMoreMenu = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)), shape = CircleShape) {
                                Icon(Icons.Rounded.MoreVert, null)
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        onDeletePlaylist(playlist)
                                        showMoreMenu = false
                                        onDismiss()
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }

            // ── Action Buttons ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolzExpressiveButton(
                    onClick = { onPlayPlaylist(playlist, false) },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("PLAY ALL", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }

                ToolzExpressiveButton(
                    onClick = { onPlayPlaylist(playlist, true) },
                    modifier = Modifier.weight(0.7f).height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("MIX", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Search Bar & Track Count ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search tracks…") }
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "${playlistTracks.size}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Track List ──────────────────────────────────────────────────
            if (filteredPlaylistTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.LibraryMusic,
                            null,
                            modifier = Modifier.size(64.dp).alpha(0.1f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Empty Playlist" else "No matches found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                        if (isEditable && searchQuery.isEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            ToolzExpressiveButton(onClick = { showAddTrack = true }, shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Tracks")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fadingEdges(top = 16.dp, bottom = 32.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredPlaylistTracks, key = { _, t -> t.uri }) { index, track ->
                        val isCurrent = track.uri == currentTrackUri

                        ListItem(
                            headlineContent = {
                                Text(
                                    track.title,
                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text(
                                    track.artist?.uppercase() ?: "UNKNOWN ARTIST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            },
                            leadingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        modifier = Modifier.width(28.dp),
                                        textAlign = TextAlign.Center
                                    )

                                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        if (isCurrent) {
                                            PlayingBarsIndicator()
                                        } else {
                                            AlbumArtImage(
                                                url = track.thumbnailUri,
                                                seed = track.uri,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                            )
                                        }

                                        // Cloud icon for online tracks
                                        if (track.path == null && track.sourceUrl != null) {
                                            Surface(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-4).dp)
                                                    .size(16.dp),
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                                shape = CircleShape
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Cloud,
                                                        contentDescription = "Online",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Filled tonal container instead of a bare glyph — matches the
                                    // favorite toggle used in the full player, so the "loved" state
                                    // is legible against any card background, not just a red tint
                                    // floating on transparency.
                                    val haptic = LocalHapticFeedback.current
                                    ToolzExpressiveIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite(track)
                },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (track.isFavorite) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (track.isFavorite) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (track.isFavorite) stringResource(R.string.st_MusicPlayerScreen_rff20) else stringResource(R.string.st_MusicPlayerScreen_atf21),
                    modifier = Modifier.size(18.dp)
                )
            }

                                    if (track.path == null && track.sourceUrl != null) {
                                        ToolzExpressiveIconButton(
                                            onClick = { onDownload(track) },
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                            )
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.st_MusicPlayerScreen_d19), modifier = Modifier.size(19.dp))
                                        }
                                    }

                                    if (isEditable) {
                                        ToolzExpressiveIconButton(
                                            onClick = { onRemoveTrack(track.uri) },
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                                            )
                                        ) {
                                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove from playlist", modifier = Modifier.size(19.dp))
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                            ),
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPlayTrack(track, filteredPlaylistTracks) }
                        )
                    }
                }
            }

            // Floating Add Button for editable playlists
            if (isEditable && filteredPlaylistTracks.isNotEmpty()) {
                ToolzExpressiveButton(
                    onClick = { showAddTrack = true },
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.End),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ADD SONGS", fontWeight = FontWeight.Black)
                }
            }
        }
    }

    if (showAddTrack) {
        val available = allTracks.filter { it.uri !in playlist.trackUris }
        SongPickerDialog(
            allTracks = available,
            onTrackSelected = { onAddTrack(it); showAddTrack = false },
            onDismiss = { showAddTrack = false }
        )
    }
}

@Composable
fun PlaylistEmptyCard(onCreatePlaylist: () -> Unit) {
    // Mirrors FolderEmptyCard's language (bordered icon plate, left-aligned
    // copy, circular action chip) so the two empty states in the same tab
    // read as one designed family instead of two unrelated fallbacks.
    Surface(
        onClick = onCreatePlaylist,
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onCreatePlaylist),
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 8.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "No playlists yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Group your favorite tracks into a set",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Create playlist",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun FolderEmptyCard(onAddFolder: () -> Unit) {
    // A left-aligned row with an explicit action button reads as an
    // invitation to act, not a decorative dead-end. The icon now sits on
    // its own bordered plate — echoing the same "chip + content" language
    // as the populated FolderCard grid — so the empty state feels like
    // part of the same family instead of a generic fallback block.
    Surface(
        onClick = onAddFolder,
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onAddFolder),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "No custom folders yet",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Pick a music directory to track it here",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add folder",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistPickerRow(playlist: Playlist, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClick(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(playlist.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("${playlist.trackUris.size} tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
