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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.frerox.toolz.ui.screens.media.MusicUiState
import com.frerox.toolz.ui.screens.media.SortOrder
import com.frerox.toolz.ui.components.*

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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
    onGoToTop: () -> Unit = {}
) {
    Column(modifier = Modifier.background(Color.Transparent)) {
    ExpressiveTopAppBar(
        // "STUDIO PLAYER" was decorative product branding that duplicated
        // nothing useful and a vague "Precision playback" tagline filled
        // the subtitle slot when idle. The subtitle slot is more useful
        // showing the one thing that actually changes as you navigate:
        // which tab you're on. Selection mode still overrides both with
        // the live count, since that's the more urgent piece of state.
        title = if (state.isSelectionMode) "${state.selectedTracks.size} Selected" else stringResource(R.string.st_MusicPlayerScreen_mp3),
        subtitle = if (state.isSelectionMode) null else currentTabLabel,
        navigationIcon = {
            ToolzExpressiveIconButton(
                onClick = if (state.isSelectionMode) onClearSelection else onBack,
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(if (state.isSelectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, null)
            }
        },
        actions = {
            if (state.isSelectionMode) {
                ToolzExpressiveIconButton(
                    onClick = onMultiAddPlaylist,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
                }
                } else {
                    if (currentTabLabel == "Catalog") {
                        ToolzExpressiveIconButton(
                            onClick = onGoToTop,
                            modifier = Modifier.padding(end = 4.dp).size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    when (currentTab) {
                            0 -> { // Tracks
                                ToolzExpressiveIconButton(onClick = onRefresh) {
                                    Icon(Icons.Rounded.Refresh, null)
                                }
                                Box {
                                    ToolzExpressiveIconButton(onClick = { onShowSortMenu(true) }) {
                                        Icon(Icons.AutoMirrored.Rounded.Sort, null)
                                    }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { onShowSortMenu(false) },
                                    shape = RoundedCornerShape(24.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    SortDropdownItem("By Title", Icons.Rounded.Title) {
                                        onSort(SortOrder.TITLE); onShowSortMenu(false)
                                    }
                                    SortDropdownItem(stringResource(R.string.st_MusicPlayerScreen_ba7), Icons.Rounded.Person) {
                                        onSort(SortOrder.ARTIST); onShowSortMenu(false)
                                    }
                                    SortDropdownItem(stringResource(R.string.st_MusicPlayerScreen_br8), Icons.Rounded.Schedule) {
                                        onSort(SortOrder.RECENT); onShowSortMenu(false)
                                    }
                                }
                            }
                        }
                        1 -> { // Library
                                // Was "add folder" — folders already have their
                                // own add action inside the Folders section
                                // itself, so the header action is more useful
                                // as the higher-frequency "new playlist" shortcut.
                                ToolzExpressiveIconButton(onClick = onAddPlaylist) {
                                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                                }
                            }
                            2 -> { // Catalog
                                ToolzExpressiveIconButton(onClick = onRefresh) {
                                    Icon(Icons.Rounded.Refresh, null)
                                }
                            }
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
            ),
            modifier = Modifier.statusBarsPadding()
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f),
            modifier = Modifier.padding(top = 4.dp)
        )

        // Search bar — using M3 ExpressiveSearchField
        AnimatedVisibility(
            visible = currentTab == 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    ExpressiveSearchField(
                        query = searchQuery,
                        onQueryChange = onSearchChange,
                        placeholder = {
                            Text(
                                stringResource(R.string.st_MusicPlayerScreen_st_hint4),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter = fadeIn(tween(160)),
                                exit = fadeOut(tween(120))
                            ) {
                                Surface(
                                    onClick = { onSearchChange("") },
                                    modifier = Modifier.size(28.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SortDropdownItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontWeight = FontWeight.Medium) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
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
    onGoToTop: () -> Unit = {}
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
    onGoToTop = onGoToTop
)
