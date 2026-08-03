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

package com.frerox.toolz.ui.screens.time
import androidx.compose.ui.res.stringResource
import com.frerox.toolz.R
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.systemBarsPadding

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.frerox.toolz.ui.components.*
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.toolzBackground
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WorldClockScreen(
    viewModel: WorldClockViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val vibrationManager = LocalVibrationManager.current

    // ── GPS permission launcher ───────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setLocationGranted(granted)
        if (granted) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    loc?.let { viewModel.updateUserLocation(it.latitude, it.longitude) }
                }
        }
    }

    /** Trigger a location fetch, requesting permission if needed. */
    @SuppressLint("MissingPermission")
    fun locateMe() {
        vibrationManager?.vibrateTick()
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            viewModel.setLocationGranted(true)
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    loc?.let { viewModel.updateUserLocation(it.latitude, it.longitude) }
                }
        } else {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            ExpressiveTopAppBar(
                title = stringResource(R.string.st_WorldClockScreen_a1b2),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onBack()
                        },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f),
                                SmallExpressiveShape,
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.st_WorldClockScreen_c3d4))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            WorldClockFloatingActions(
                selected = uiState.selected,
                onSave = {
                    vibrationManager?.vibrateClick()
                    viewModel.addSelectedZone()
                    scope.launch {
                        snackbarHostState.showSnackbar("Saved ${uiState.selected?.location?.city}")
                    }
                },
                onCopy = {
                    vibrationManager?.vibrateClick()
                    val s = uiState.selected ?: return@WorldClockFloatingActions
                    clipboard.setText(AnnotatedString("${s.location.label} – ${s.time}:${s.seconds} ${s.utcOffset}"))
                    scope.launch { snackbarHostState.showSnackbar("Copied ${s.location.city}") }
                },
                onLocate = { locateMe() },
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .toolzBackground()
                .padding(top = padding.calculateTopPadding())
        ) {
            val isLandscape = maxWidth > maxHeight
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left side: Search field + MapSection
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = LargeExpressiveShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ExpressiveSearchField(
                                query = uiState.searchQuery,
                                onQueryChange = viewModel::setSearchQuery,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.st_WorldClockScreen_e5f6)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, null)
                                        }
                                    }
                                },
                                onSearch = {
                                    uiState.searchResults.firstOrNull()?.let(viewModel::selectLocation)
                                },
                            )
                        }

                        AnimatedVisibility(
                            visible = uiState.searchQuery.isNotEmpty() && uiState.searchResults.isNotEmpty(),
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                            ) {
                                items(uiState.searchResults, key = { it.city + it.zoneId }) { location ->
                                    ExpressiveFilterChip(
                                        selected = uiState.selected?.location == location,
                                        onClick = { viewModel.selectLocation(location) },
                                        label = {
                                            Text(location.city, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        leadingIcon = {
                                            if (uiState.selected?.location == location) {
                                                Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        MapSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            onLocate = { locateMe() },
                            onCopySelection = { selection ->
                                vibrationManager?.vibrateClick()
                                clipboard.setText(AnnotatedString("${selection.location.label} – ${selection.time}:${selection.seconds} ${selection.utcOffset}"))
                                scope.launch { snackbarHostState.showSnackbar("Copied ${selection.location.city}") }
                            },
                            modifier = Modifier.weight(1f),
                            mapModifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }

                    // Right side: selected timezone detail card + saved clocks
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .fadingEdges(top = 8.dp, bottom = 80.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Selected timezone detail card
                        item {
                            AnimatedVisibility(
                                visible = uiState.selected != null,
                                enter = fadeIn() + slideInVertically { it / 4 } + scaleIn(initialScale = 0.94f),
                                exit = fadeOut() + scaleOut(targetScale = 0.96f),
                            ) {
                                uiState.selected?.let { selection ->
                                    SelectedTimePanel(
                                        selected = selection,
                                        onSave = viewModel::addSelectedZone,
                                        onCopy = {
                                            vibrationManager?.vibrateClick()
                                            clipboard.setText(
                                                AnnotatedString("${selection.location.label} – ${selection.time}:${selection.seconds} ${selection.utcOffset}")
                                            )
                                            scope.launch { snackbarHostState.showSnackbar("Copied ${selection.location.city}") }
                                        },
                                    )
                                }
                            }
                        }

                        // Section header
                        item {
                            Text(
                                text = stringResource(R.string.st_WorldClockScreen_g7h8),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                            )
                        }

                        // Saved clocks list
                        if (uiState.clocks.isEmpty()) {
                            item {
                                EmptyClockDeck(onPick = { viewModel.selectLocation(viewModel.locations.first()) })
                            }
                        } else {
                            items(uiState.clocks, key = { "${it.zoneId}-${it.isLocal}" }) { clock ->
                                StaggeredEntrance(index = uiState.clocks.indexOf(clock)) {
                                    SavedClockCard(
                                        clock = clock,
                                        onDelete = { viewModel.removeZone(clock.zoneId) },
                                        onCopy = {
                                            vibrationManager?.vibrateClick()
                                            clipboard.setText(
                                                AnnotatedString("${clock.cityName} – ${clock.currentTime}:${clock.seconds} ${clock.utcOffset}")
                                            )
                                            scope.launch { snackbarHostState.showSnackbar("Copied ${clock.cityName}") }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdges(top = 8.dp, bottom = 112.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Search bar
                    item {
                        Surface(
                            shape = LargeExpressiveShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ExpressiveSearchField(
                                query = uiState.searchQuery,
                                onQueryChange = viewModel::setSearchQuery,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.st_WorldClockScreen_e5f6)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, null)
                                        }
                                    }
                                },
                                onSearch = {
                                    uiState.searchResults.firstOrNull()?.let(viewModel::selectLocation)
                                },
                            )
                        }
                    }

                    // Result chips (if any search query)
                    if (uiState.searchQuery.isNotEmpty() && uiState.searchResults.isNotEmpty()) {
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                            ) {
                                items(uiState.searchResults, key = { it.city + it.zoneId }) { location ->
                                    ExpressiveFilterChip(
                                        selected = uiState.selected?.location == location,
                                        onClick = { viewModel.selectLocation(location) },
                                        label = {
                                            Text(location.city, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        leadingIcon = {
                                            if (uiState.selected?.location == location) {
                                                Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(16.dp))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── Interactive 2D Map ───────────────────────────────────────────
                    item {
                        MapSection(
                            uiState = uiState,
                            viewModel = viewModel,
                            onLocate = { locateMe() },
                            onCopySelection = { selection ->
                                vibrationManager?.vibrateClick()
                                clipboard.setText(AnnotatedString("${selection.location.label} – ${selection.time}:${selection.seconds} ${selection.utcOffset}"))
                                scope.launch { snackbarHostState.showSnackbar("Copied ${selection.location.city}") }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    // ── Selected timezone detail card ───────────────────────────────
                    item {
                        AnimatedVisibility(
                            visible = uiState.selected != null,
                            enter = fadeIn() + slideInVertically { it / 4 } + scaleIn(initialScale = 0.94f),
                            exit = fadeOut() + scaleOut(targetScale = 0.96f),
                        ) {
                            uiState.selected?.let { selection ->
                                SelectedTimePanel(
                                    selected = selection,
                                    onSave = viewModel::addSelectedZone,
                                    onCopy = {
                                        vibrationManager?.vibrateClick()
                                        clipboard.setText(
                                            AnnotatedString("${selection.location.label} – ${selection.time}:${selection.seconds} ${selection.utcOffset}")
                                        )
                                        scope.launch { snackbarHostState.showSnackbar("Copied ${selection.location.city}") }
                                    },
                                )
                            }
                        }
                    }

                    // ── Section header ───────────────────────────────────────────────
                    item {
                        Text(
                            text = stringResource(R.string.st_WorldClockScreen_g7h8),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                        )
                    }

                    // ── Saved clocks list ────────────────────────────────────────────
                    if (uiState.clocks.isEmpty()) {
                        item {
                            EmptyClockDeck(onPick = { viewModel.selectLocation(viewModel.locations.first()) })
                        }
                    } else {
                        items(uiState.clocks, key = { "${it.zoneId}-${it.isLocal}" }) { clock ->
                            StaggeredEntrance(index = uiState.clocks.indexOf(clock)) {
                                SavedClockCard(
                                    clock = clock,
                                    onDelete = { viewModel.removeZone(clock.zoneId) },
                                    onCopy = {
                                        vibrationManager?.vibrateClick()
                                        clipboard.setText(
                                            AnnotatedString("${clock.cityName} – ${clock.currentTime}:${clock.seconds} ${clock.utcOffset}")
                                        )
                                        scope.launch { snackbarHostState.showSnackbar("Copied ${clock.cityName}") }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Map section ──────────────────────────────────────────────────────────────

@Composable
private fun MapSection(
    uiState: WorldClockUiState,
    viewModel: WorldClockViewModel,
    onLocate: () -> Unit,
    onCopySelection: (WorldClockSelection) -> Unit,
    modifier: Modifier = Modifier,
    mapModifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1.72f),
) {
    val primary   = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val isDark    = isSystemInDarkTheme()
    val mapColors = remember(primary, secondary, isDark) {
        WorldMapColors.fromTheme(primary, secondary, isDark)
    }

    var isFullScreen by rememberSaveable { mutableStateOf(false) }

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                var fsSearchVisible by remember { mutableStateOf(false) }
                var fsInfoVisible by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Map fills entire screen
                    WorldMap2D(
                        locations         = viewModel.locations,
                        selectedLocation  = uiState.selected?.location,
                        highlightedZones  = uiState.highlightedZones,
                        userLatLon        = uiState.userLatLon,
                        mapMode           = uiState.mapMode,
                        mapColors         = mapColors,
                        modifier          = Modifier.fillMaxSize(),
                        onLocationSelected = viewModel::selectLocation,
                    )

                    // Search bar overlay — slides down from top
                    AnimatedVisibility(
                        visible = fsSearchVisible,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            tonalElevation = 4.dp,
                        ) {
                            Column {
                                ExpressiveSearchField(
                                    query = uiState.searchQuery,
                                    onQueryChange = viewModel::setSearchQuery,
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(stringResource(R.string.st_WorldClockScreen_e5f6)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    trailingIcon = {
                                        if (uiState.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                                Icon(Icons.Rounded.Close, null)
                                            }
                                        }
                                    },
                                    onSearch = {
                                        uiState.searchResults.firstOrNull()?.let(viewModel::selectLocation)
                                    },
                                )
                                AnimatedVisibility(
                                    visible = uiState.searchResults.isNotEmpty() && uiState.searchQuery.isNotEmpty(),
                                ) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(uiState.searchResults, key = { it.city + it.zoneId }) { location ->
                                            ExpressiveFilterChip(
                                                selected = uiState.selected?.location == location,
                                                onClick = {
                                                    viewModel.selectLocation(location)
                                                    fsSearchVisible = false
                                                },
                                                label = { Text(location.city, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                leadingIcon = {
                                                    if (uiState.selected?.location == location)
                                                        Icon(Icons.Rounded.LocationOn, null, modifier = Modifier.size(16.dp))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Info card overlay — slides up from bottom above the pill
                    AnimatedVisibility(
                        visible = fsInfoVisible && uiState.selected != null,
                        enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.94f),
                        exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.96f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 104.dp),
                    ) {
                        uiState.selected?.let { selection ->
                            SelectedTimePanel(
                                selected = selection,
                                onSave = viewModel::addSelectedZone,
                                onCopy = {
                                    onCopySelection(selection)
                                },
                            )
                        }
                    }

                    // Controls pill at the bottom
                    MapControlsPill(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLocate = onLocate,
                        isFullScreen = true,
                        onToggleFullScreen = { isFullScreen = false },
                        showSearchButton = true,
                        onToggleSearch = { fsSearchVisible = !fsSearchVisible },
                        isSearchVisible = fsSearchVisible,
                        showInfoButton = uiState.selected != null,
                        onToggleInfo = { fsInfoVisible = !fsInfoVisible },
                        isInfoVisible = fsInfoVisible,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = SquircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
            modifier = mapModifier,
        ) {
            WorldMap2D(
                locations         = viewModel.locations,
                selectedLocation  = uiState.selected?.location,
                highlightedZones  = uiState.highlightedZones,
                userLatLon        = uiState.userLatLon,
                mapMode           = uiState.mapMode,
                mapColors         = mapColors,
                modifier          = Modifier.fillMaxSize(),
                onLocationSelected= viewModel::selectLocation,
            )
        }

        MapControlsPill(
            uiState = uiState,
            viewModel = viewModel,
            onLocate = onLocate,
            isFullScreen = false,
            onToggleFullScreen = { isFullScreen = true }
        )
    }
}

@Composable
private fun MapControlsPill(
    uiState: WorldClockUiState,
    viewModel: WorldClockViewModel,
    onLocate: () -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    showSearchButton: Boolean = false,
    onToggleSearch: () -> Unit = {},
    isSearchVisible: Boolean = false,
    showInfoButton: Boolean = false,
    onToggleInfo: () -> Unit = {},
    isInfoVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // GPS locate button
            IconButton(onClick = onLocate, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (uiState.userLatLon != null) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn,
                    contentDescription = stringResource(R.string.st_WorldClockScreen_i5j6),
                    tint = if (uiState.userLatLon != null) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            val normalSelected = uiState.mapMode == MapMode.NORMAL
            val normalBgColor by animateColorAsState(
                if (normalSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                label = "normalBg"
            )
            val normalContentColor by animateColorAsState(
                if (normalSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "normalContent"
            )

            Surface(
                onClick = { if (!normalSelected) viewModel.toggleMapMode() },
                shape = MaterialTheme.shapes.extraLarge,
                color = normalBgColor,
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.Map, null, modifier = Modifier.size(15.dp), tint = normalContentColor)
                    Text(stringResource(R.string.st_WorldClockScreen_i9j0), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = normalContentColor)
                }
            }

            val satSelected = uiState.mapMode == MapMode.SATELLITE
            val satBgColor by animateColorAsState(
                if (satSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                label = "satBg"
            )
            val satContentColor by animateColorAsState(
                if (satSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "satContent"
            )

            Surface(
                onClick = { if (!satSelected) viewModel.toggleMapMode() },
                shape = MaterialTheme.shapes.extraLarge,
                color = satBgColor,
                modifier = Modifier.height(32.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.Satellite, null, modifier = Modifier.size(15.dp), tint = satContentColor)
                    Text(stringResource(R.string.st_WorldClockScreen_k1l2), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = satContentColor)
                }
            }

            // Search button (fullscreen only)
            if (showSearchButton) {
                val searchTint by animateColorAsState(
                    if (isSearchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "searchTint"
                )
                IconButton(onClick = onToggleSearch, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (isSearchVisible) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.st_WorldClockScreen_m3n4),
                        tint = searchTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Info button (fullscreen only, only if timezone is selected)
            if (showInfoButton) {
                val infoTint by animateColorAsState(
                    if (isInfoVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "infoTint"
                )
                IconButton(onClick = onToggleInfo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.st_WorldClockScreen_o5p6),
                        tint = infoTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Fullscreen toggle
            IconButton(onClick = onToggleFullScreen, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(R.string.st_WorldClockScreen_q7r8),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}


// ─── Selected time detail panel ───────────────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SelectedTimePanel(
    selected: WorldClockSelection,
    onSave: () -> Unit,
    onCopy: () -> Unit,
) {
    ExpressiveCard(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Header (City, Country & Date, Offset info)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected.location.city,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${selected.location.country.uppercase()} • ${selected.date}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (selected.isNight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = null,
                        tint = if (selected.isNight) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = selected.offset,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Row 2: Large live clock & action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = selected.time,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-2).sp
                        )
                    )
                    AnimatedContent(
                        targetState = selected.seconds,
                        transitionSpec = {
                            (fadeIn(tween(120)) + slideInVertically { -it })
                                .togetherWith(fadeOut(tween(80)) + slideOutVertically { it })
                                .using(SizeTransform(clip = false))
                        },
                        label = "seconds"
                    ) { sec ->
                        Text(
                            text = ":$sec",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy, 
                            contentDescription = stringResource(R.string.st_WorldClockScreen_s9t0),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (!selected.saved) {
                        IconButton(
                            onClick = onSave,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Add, 
                                contentDescription = stringResource(R.string.st_WorldClockScreen_u1v2),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Saved clock card ─────────────────────────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SavedClockCard(
    clock: WorldClockItem,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
) {
    ExpressiveCard(
        onClick = onCopy,
        onLongClick = if (!clock.isLocal) onDelete else null,
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (clock.isLocal) MaterialTheme.colorScheme.primary.copy(0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
        ),
        elevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = clock.cityName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (clock.isLocal) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.st_WorldClockScreen_w3x4),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${clock.country.uppercase()} • ${clock.offset} • ${clock.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = clock.currentTime,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                        ),
                    )
                    AnimatedContent(
                        targetState = clock.seconds,
                        transitionSpec = {
                            (fadeIn(tween(100)) + slideInVertically { -it })
                                .togetherWith(fadeOut(tween(80)) + slideOutVertically { it })
                                .using(SizeTransform(clip = false))
                        },
                        label = "clockSeconds",
                    ) { sec ->
                        Text(
                            text = sec,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
                        )
                    }
                }

                Icon(
                    imageVector = if (clock.isNight) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    contentDescription = null,
                    tint = if (clock.isNight) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                    modifier = Modifier.size(18.dp),
                )

                if (!clock.isLocal) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.st_WorldClockScreen_y5z6),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─── Empty clock placeholder ──────────────────────────────────────────────────

@Composable
private fun EmptyClockDeck(onPick: () -> Unit) {
    ExpressiveCard(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        shape = SquircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        elevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Rounded.Public,
                null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
            Text(
                stringResource(R.string.st_WorldClockScreen_a7b8),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.st_WorldClockScreen_c9d0),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Floating Toolbar ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorldClockFloatingActions(
    selected: WorldClockSelection?,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onLocate: () -> Unit,
) {
    ToolzHorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
        content = {
            ToolzExpressiveIconButton(
                onClick = onSave,
                enabled = selected != null && !selected.saved,
            ) {
                Icon(Icons.Rounded.Add, stringResource(R.string.st_WorldClockScreen_e1f2))
            }
            ToolzExpressiveIconButton(
                onClick = onCopy,
                enabled = selected != null,
            ) {
                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.st_WorldClockScreen_g3h4))
            }
            ToolzExpressiveIconButton(onClick = onLocate) {
                Icon(Icons.Rounded.MyLocation, stringResource(R.string.st_WorldClockScreen_i5j6))
            }
        },
    )
}
