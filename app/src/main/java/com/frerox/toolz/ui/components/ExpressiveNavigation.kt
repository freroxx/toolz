package com.frerox.toolz.ui.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlinx.coroutines.launch

@Composable
fun ExpressiveNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 0.dp,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit
) {
    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        windowInsets = windowInsets,
        content = content
    )
}

@Composable
fun RowScope.ExpressiveNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedIcon: @Composable () -> Unit = icon,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    interactionSource: MutableInteractionSource? = null,
) {
    val performanceMode = LocalPerformanceMode.current
    val haptic = rememberToolzHapticFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = when {
            performanceMode -> 1f
            isPressed -> 0.92f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = if (performanceMode) tween(durationMillis = 90) else spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "navItemScale",
    )

    NavigationBarItem(
        selected = selected,
        onClick = {
            haptic.tick()
            currentOnClick()
        },
        icon = {
            Box(modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }) {
                if (selected) selectedIcon() else icon()
            }
        },
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
        colors = colors,
        interactionSource = resolvedInteractionSource
    )
}

/**
 * Premium Wide Navigation Rail with expanded/collapsed expressive transitions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzWideNavigationRail(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    items: List<Pair<String, ImageVector>>,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    expandedHeaderTopPadding: Dp = 64.dp,
) {
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val haptic = rememberToolzHapticFeedback()

    WideNavigationRail(
        modifier = modifier,
        state = state,
        header = {
            Column(modifier = Modifier.padding(start = 24.dp, top = expandedHeaderTopPadding)) {
                if (header != null) {
                    header()
                    Spacer(Modifier.height(16.dp))
                }
                IconButton(
                    onClick = {
                        haptic.tick()
                        scope.launch {
                            if (state.targetValue == WideNavigationRailValue.Expanded) {
                                state.collapse()
                            } else {
                                state.expand()
                            }
                        }
                    },
                ) {
                    Icon(
                        if (state.targetValue == WideNavigationRailValue.Expanded) 
                            Icons.Rounded.KeyboardDoubleArrowLeft else Icons.Rounded.Menu,
                        contentDescription = "Toggle Rail"
                    )
                }
            }
        },
    ) {
        WideRailItems(
            items = items,
            selectedItem = selectedItem,
            railExpanded = state.targetValue == WideNavigationRailValue.Expanded,
            onItemSelected = onItemSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzModalWideNavigationRail(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    items: List<Pair<String, ImageVector>>,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
    expandedHeaderTopPadding: Dp = 64.dp,
) {
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val haptic = rememberToolzHapticFeedback()

    ModalWideNavigationRail(
        modifier = modifier,
        state = state,
        expandedHeaderTopPadding = expandedHeaderTopPadding,
        header = {
            Column(modifier = Modifier.padding(start = 24.dp)) {
                if (header != null) {
                    header()
                    Spacer(Modifier.height(16.dp))
                }
                IconButton(
                    onClick = {
                        haptic.tick()
                        scope.launch {
                            if (state.targetValue == WideNavigationRailValue.Expanded) {
                                state.collapse()
                            } else {
                                state.expand()
                            }
                        }
                    },
                ) {
                    Icon(
                        if (state.targetValue == WideNavigationRailValue.Expanded)
                            Icons.Rounded.KeyboardDoubleArrowLeft else Icons.Rounded.Menu,
                        contentDescription = "Toggle Rail",
                    )
                }
            }
        },
    ) {
        WideRailItems(
            items = items,
            selectedItem = selectedItem,
            railExpanded = state.targetValue == WideNavigationRailValue.Expanded,
            onItemSelected = onItemSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzFloatingToolbar(
    expanded: Boolean,
    selectedTab: com.frerox.toolz.ui.screens.dashboard.DashboardTab,
    onTabSelected: (com.frerox.toolz.ui.screens.dashboard.DashboardTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberToolzHapticFeedback()

    HorizontalFloatingToolbar(
        modifier = modifier
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        expanded = expanded,
        shape = ExtraLargeExpressiveShape,
        content = {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.frerox.toolz.ui.screens.dashboard.DashboardTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    val color by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        animationSpec = tween(300),
                        label = "tabColor"
                    )
                    
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "tabScale"
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .bouncyClick {
                                haptic.tick()
                                onTabSelected(tab)
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = color,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Adaptive Navigation Suite Scaffold for Toolz.
 */
@Composable
fun ToolzNavigationSuiteScaffold(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
    content: @Composable () -> Unit,
) {
    val layoutType = NavigationSuiteScaffoldDefaults
        .calculateFromAdaptiveInfo(windowAdaptiveInfo)
    
    NavigationSuiteScaffold(
        navigationSuiteItems = navigationSuiteItems,
        layoutType = layoutType,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        modifier = modifier,
        content = content
    )
}

/**
 * Floating Action Button Menu for complex multi-action entries.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveFabMenu(
    items: List<Triple<String, ImageVector, () -> Unit>>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberToolzHapticFeedback()

    FloatingActionButtonMenu(
        modifier = modifier,
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { 
                    haptic.tick()
                    expanded = it 
                },
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                    }
                }
                Icon(
                    painter = rememberVectorPainter(imageVector),
                    contentDescription = contentDescription,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = { 
                    haptic.click()
                    expanded = false
                    item.third()
                },
                icon = { Icon(item.second, contentDescription = null) },
                text = { 
                    Text(
                        text = item.first,
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WideRailItems(
    items: List<Pair<String, ImageVector>>,
    selectedItem: Int,
    railExpanded: Boolean,
    onItemSelected: (Int) -> Unit,
) {
    val haptic = rememberToolzHapticFeedback()
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)
    items.forEachIndexed { index, item ->
        WideNavigationRailItem(
            railExpanded = railExpanded,
            icon = { Icon(item.second, contentDescription = item.first) },
            label = { Text(item.first) },
            selected = selectedItem == index,
            onClick = {
                haptic.click()
                currentOnItemSelected(index)
            },
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpressiveNavigationPreview() {
    ToolzTheme(dynamicColor = false) {
        Row(modifier = Modifier.fillMaxSize()) {
            ToolzWideNavigationRail(
                selectedItem = 0,
                onItemSelected = {},
                items = listOf("Dashboard" to Icons.Rounded.Dashboard, "Settings" to Icons.Rounded.Settings),
                header = { Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(32.dp)) }
            )
            
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExpressiveFabMenu(
                        items = listOf(
                            Triple("Add Item", Icons.Rounded.Add, {}),
                            Triple("Save", Icons.Rounded.Save, {})
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    ExpressiveNavigationBar {
                        ExpressiveNavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                            label = { Text("Profile") }
                        )
                        ExpressiveNavigationBarItem(
                            selected = false,
                            onClick = {},
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        }
    }
}
