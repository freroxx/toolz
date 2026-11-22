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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ExpressiveSearchField
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.liquidglass.LocalLiquidGlassBackdrop
import com.frerox.toolz.ui.liquidglass.LocalLiquidGlassEnabled
import com.frerox.toolz.ui.liquidglass.liquidGlassEffect
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.ui.screens.media.SortOrder
import com.frerox.toolz.ui.screens.media.catalog.LayoutMode
import com.frerox.toolz.ui.theme.LocalShowTopAppBarDescriptions

// ─────────────────────────────────────────────────────────────────────────────
// Adaptive top bar
// ─────────────────────────────────────────────────────────────────────────────
//
// The bar is the app's navigation instrument, so it morphs with the tab rather
// than swapping whole trees: the title and subtitle slide vertically into one
// another while the action cluster cross-fades and settles with a light scale.
// Layout height is pinned so a longer label never nudges the content below it.

private data class MusicHeader(
    val title: String,
    val subtitle: String,
    val key: String,
)

private fun SortOrder.displayName(): String = when (this) {
    SortOrder.TITLE -> "title"
    SortOrder.ARTIST -> "artist"
    SortOrder.ALBUM -> "album"
    SortOrder.RECENT -> "recently played"
    SortOrder.DATE_ADDED -> "date added"
    SortOrder.MOST_PLAYED -> "most played"
    SortOrder.LISTENING_TIME -> "listening time"
    SortOrder.LONGEST -> "longest"
    SortOrder.SHORTEST -> "shortest"
}

@Composable
private fun rememberMusicHeader(
    state: MusicUiState,
    currentTabLabel: String?,
): MusicHeader {
    return when {
        state.isSelectionMode -> MusicHeader(
            title = "${state.selectedTracks.size} selected",
            subtitle = "Choose an action",
            key = "selection",
        )
        currentTabLabel == "Library" -> MusicHeader(
            title = "Library",
            subtitle = "Playlists, folders and favourites",
            key = "library",
        )
        currentTabLabel == "Karaoke" -> MusicHeader(
            title = "Karaoke",
            subtitle = "Sing along with live lyrics",
            key = "karaoke",
        )
        currentTabLabel == "Catalog" -> MusicHeader(
            title = "Catalog",
            subtitle = "Browse and play",
            key = "catalog",
        )
        else -> MusicHeader(
            title = "Your music",
            subtitle = "${state.tracks.size} tracks, sorted by ${state.sortOrder.displayName()}",
            key = "tracks",
        )
    }
}

@Composable
fun ScreenTopBar(
    state: MusicUiState,
    currentTab: Int,
    currentTabLabel: String?,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onRefresh: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onClearSelection: () -> Unit,
    onMultiAddPlaylist: () -> Unit,
    onResetCatalogOnboarding: () -> Unit = {},
    catalogLayoutMode: LayoutMode = LayoutMode.GRID,
    onToggleCatalogLayout: () -> Unit = {},
    onCatalogScrollToTop: () -> Unit = {},
) {
    val header = rememberMusicHeader(state, currentTabLabel)
    val liquidGlassEnabled = LocalLiquidGlassEnabled.current
    val glassBackdrop = LocalLiquidGlassBackdrop.current
    val isCatalog = currentTabLabel == "Catalog"
    val isTracks = !state.isSelectionMode && !isCatalog &&
        currentTabLabel != "Library" && currentTabLabel != "Karaoke"

    // A very quiet tab tint behind the bar. It gives the morph a second,
    // continuous channel of change (color) without painting a loud header.
    val accent by androidx.compose.animation.animateColorAsState(
        targetValue = when (header.key) {
            "library" -> MaterialTheme.colorScheme.tertiary
            "catalog" -> MaterialTheme.colorScheme.secondary
            "karaoke" -> MaterialTheme.colorScheme.primary
            "selection" -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(520),
        label = "topBarAccent",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.075f),
                        accent.copy(alpha = 0.02f),
                        Color.Transparent,
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 72.dp)
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolzExpressiveIconButton(
                onClick = if (state.isSelectionMode) onClearSelection else onBack,
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(
                    if (state.isSelectionMode) Icons.Rounded.Close
                    else Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = if (state.isSelectionMode) "Clear selection" else "Back",
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = header.title,
                    transitionSpec = {
                        (fadeIn(tween(240, delayMillis = 60)) +
                            slideInVertically(tween(340), initialOffsetY = { it / 2 }))
                            .togetherWith(
                                fadeOut(tween(130)) +
                                    slideOutVertically(tween(180), targetOffsetY = { -it / 3 })
                            )
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "topBarTitle",
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 30.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                AnimatedVisibility(
                    visible = LocalShowTopAppBarDescriptions.current,
                    enter = fadeIn(tween(240, delayMillis = 40)) +
                        expandVertically(
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = 0.9f
                            ),
                            expandFrom = Alignment.Top,
                        ),
                    exit = fadeOut(tween(140)) +
                        shrinkVertically(
                            animationSpec = tween(200),
                            shrinkTowards = Alignment.Top,
                        ),
                ) {
                    AnimatedContent(
                        targetState = header.subtitle,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 90)) +
                                slideInVertically(tween(300), initialOffsetY = { it / 2 }))
                                .togetherWith(fadeOut(tween(120)))
                        },
                        contentAlignment = Alignment.CenterStart,
                        label = "topBarSubtitle",
                    ) { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.heightIn(min = 17.dp),
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = header.key,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 80)) +
                        scaleIn(tween(320, delayMillis = 80), initialScale = 0.88f))
                        .togetherWith(fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.94f))
                },
                contentAlignment = Alignment.CenterEnd,
                label = "topBarActions",
            ) { key ->
                Row(
                    modifier = Modifier.then(
                        if (liquidGlassEnabled && glassBackdrop != null) {
                            Modifier.liquidGlassEffect(
                                shape = RoundedCornerShape(24.dp),
                                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f),
                                refractionFactor = 0.55f,
                                shadowRadius = 5.dp
                            )
                        } else Modifier
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (key) {
                        "selection" -> ToolzExpressiveIconButton(
                            onClick = onMultiAddPlaylist,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = "Add to playlist",
                            )
                        }

                        "catalog" -> {
                            ToolzExpressiveIconButton(
                                onClick = onRefresh,
                                colors = topBarActionColors(),
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Refresh catalog",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            ToolzExpressiveIconButton(
                                onClick = onToggleCatalogLayout,
                                colors = topBarActionColors(),
                            ) {
                                AnimatedContent(
                                    targetState = catalogLayoutMode,
                                    transitionSpec = {
                                        (fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.7f))
                                            .togetherWith(
                                                fadeOut(tween(120)) +
                                                    scaleOut(tween(160), targetScale = 0.7f)
                                            )
                                    },
                                    label = "catalogLayoutIcon",
                                ) { mode ->
                                    Icon(
                                        if (mode == LayoutMode.GRID) Icons.AutoMirrored.Rounded.ViewList
                                        else Icons.Rounded.GridView,
                                        contentDescription = if (mode == LayoutMode.GRID)
                                            "Switch to list" else "Switch to grid",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            ToolzExpressiveIconButton(
                                onClick = onCatalogScrollToTop,
                                colors = topBarActionColors(),
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "Scroll to top",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        "library" -> ToolzExpressiveIconButton(
                            onClick = onAddPlaylist,
                            colors = topBarActionColors(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = "New playlist",
                            )
                        }

                        else -> {
                            ToolzExpressiveIconButton(
                                onClick = onRefresh,
                                colors = topBarActionColors(),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Rescan library")
                            }
                            Box {
                                ToolzExpressiveIconButton(
                                    onClick = { onShowSortMenu(true) },
                                    colors = topBarActionColors(),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = "Sort songs",
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { onShowSortMenu(false) },
                                    shape = RoundedCornerShape(24.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    SortDropdownItem("Title", Icons.Rounded.Title, state.sortOrder == SortOrder.TITLE) {
                                        onSort(SortOrder.TITLE); onShowSortMenu(false)
                                    }
                                    SortDropdownItem(
                                        "Artist",
                                        Icons.Rounded.Person,
                                        state.sortOrder == SortOrder.ARTIST,
                                    ) {
                                        onSort(SortOrder.ARTIST); onShowSortMenu(false)
                                    }
                                    SortDropdownItem(
                                        "Album",
                                        Icons.Rounded.Album,
                                        state.sortOrder == SortOrder.ALBUM,
                                    ) {
                                        onSort(SortOrder.ALBUM); onShowSortMenu(false)
                                    }
                                    HorizontalDivider()
                                    SortDropdownItem("Recently played", Icons.Rounded.History, state.sortOrder == SortOrder.RECENT) {
                                        onSort(SortOrder.RECENT); onShowSortMenu(false)
                                    }
                                    SortDropdownItem("Date added", Icons.Rounded.NewReleases, state.sortOrder == SortOrder.DATE_ADDED) {
                                        onSort(SortOrder.DATE_ADDED); onShowSortMenu(false)
                                    }
                                    SortDropdownItem("Most played", Icons.Rounded.PlayCircle, state.sortOrder == SortOrder.MOST_PLAYED) {
                                        onSort(SortOrder.MOST_PLAYED); onShowSortMenu(false)
                                    }
                                    SortDropdownItem("Listening time", Icons.Rounded.Timer, state.sortOrder == SortOrder.LISTENING_TIME) {
                                        onSort(SortOrder.LISTENING_TIME); onShowSortMenu(false)
                                    }
                                    HorizontalDivider()
                                    SortDropdownItem("Longest first", Icons.Rounded.KeyboardArrowUp, state.sortOrder == SortOrder.LONGEST) {
                                        onSort(SortOrder.LONGEST); onShowSortMenu(false)
                                    }
                                    SortDropdownItem("Shortest first", Icons.Rounded.KeyboardArrowDown, state.sortOrder == SortOrder.SHORTEST) {
                                        onSort(SortOrder.SHORTEST); onShowSortMenu(false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isTracks,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)) +
                fadeIn(tween(180)),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)) +
                fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
            ) {
                ExpressiveSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    placeholder = {
                        Text(
                            stringResource(R.string.st_MusicPlayerScreen_st_hint4),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter = fadeIn(tween(160)),
                            exit = fadeOut(tween(120)),
                        ) {
                            Surface(
                                onClick = { onSearchChange("") },
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun topBarActionColors() = IconButtonDefaults.filledIconButtonColors(
    containerColor = if (LocalLiquidGlassEnabled.current && LocalLiquidGlassBackdrop.current != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun SortDropdownItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.Medium) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        },
    )
}

// Alias required by task: rename to MusicTopBar if needed — provide both names
@Composable
fun MusicTopBar(
    state: MusicUiState,
    currentTab: Int,
    currentTabLabel: String?,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onRefresh: () -> Unit,
    onSort: (SortOrder) -> Unit,
    onClearSelection: () -> Unit,
    onMultiAddPlaylist: () -> Unit,
    onResetCatalogOnboarding: () -> Unit = {},
    catalogLayoutMode: LayoutMode = LayoutMode.GRID,
    onToggleCatalogLayout: () -> Unit = {},
    onCatalogScrollToTop: () -> Unit = {},
) = ScreenTopBar(
    state = state,
    currentTab = currentTab,
    currentTabLabel = currentTabLabel,
    showSortMenu = showSortMenu,
    onShowSortMenu = onShowSortMenu,
    searchQuery = searchQuery,
    onSearchChange = onSearchChange,
    onBack = onBack,
    onAddPlaylist = onAddPlaylist,
    onRefresh = onRefresh,
    onSort = onSort,
    onClearSelection = onClearSelection,
    onMultiAddPlaylist = onMultiAddPlaylist,
    onResetCatalogOnboarding = onResetCatalogOnboarding,
    catalogLayoutMode = catalogLayoutMode,
    onToggleCatalogLayout = onToggleCatalogLayout,
    onCatalogScrollToTop = onCatalogScrollToTop,
)
