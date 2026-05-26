package com.frerox.toolz.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import com.frerox.toolz.ui.theme.ToolzTheme

/**
 * Premium top app bar with dynamic scaling and expressive typography.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit) = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
    ),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    largeFlexible: Boolean = false,
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    if (largeFlexible) {
        LargeFlexibleTopAppBar(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            actions = actions,
            titleHorizontalAlignment = titleHorizontalAlignment,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
        return
    }

    val performanceMode = LocalPerformanceMode.current
    val collapsedFraction = scrollBehavior?.state?.collapsedFraction?.coerceIn(0f, 1f) ?: 0f
    val scaleTarget = if (performanceMode) {
        1f
    } else {
        1.15f + (0.85f - 1.15f) * collapsedFraction
    }
    val titleScale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = if (performanceMode) tween(120) else spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "expressiveTopBarScale",
    )

    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = titleScale
                        scaleY = titleScale
                    },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    title()
                    subtitle?.invoke()
                }
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit) = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
    ),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    largeFlexible: Boolean = false,
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    ExpressiveTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        subtitle = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        scrollBehavior = scrollBehavior,
        largeFlexible = largeFlexible,
        titleHorizontalAlignment = titleHorizontalAlignment,
    )
}

/**
 * Premium Horizontal Floating Toolbar using official Material 3 Expressive APIs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzHorizontalFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (AppBarRowScope.() -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    content: @Composable RowScope.() -> Unit,
) {
    HorizontalFloatingToolbar(
        modifier = modifier,
        expanded = expanded,
        colors = FloatingToolbarColors(
            toolbarContainerColor = containerColor,
            toolbarContentColor = contentColor,
            fabContainerColor = MaterialTheme.colorScheme.primaryContainer,
            fabContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        leadingContent = leadingContent,
        trailingContent = trailingContent?.let {
            {
                AppBarRow(
                    overflowIndicator = {
                        IconButton(onClick = { /* menu state check */ }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                    }
                ) {
                    it()
                }
            }
        },
        scrollBehavior = scrollBehavior,
        content = content
    )
}

/**
 * Premium Vertical Floating Toolbar using official Material 3 Expressive APIs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolzVerticalFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (AppBarColumnScope.() -> Unit)? = null,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    VerticalFloatingToolbar(
        modifier = modifier,
        expanded = expanded,
        colors = FloatingToolbarColors(
            toolbarContainerColor = containerColor,
            toolbarContentColor = contentColor,
            fabContainerColor = MaterialTheme.colorScheme.primaryContainer,
            fabContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        leadingContent = leadingContent,
        trailingContent = trailingContent?.let {
            {
                AppBarColumn(
                    overflowIndicator = {
                        IconButton(onClick = { /* menu state check */ }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                        }
                    }
                ) {
                    it()
                }
            }
        },
        scrollBehavior = scrollBehavior,
        content = content
    )
}

/**
 * Premium Flexible Bottom App Bar with expressive actions.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveFlexibleBottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    actions: AppBarRowScope.() -> Unit
) {
    FlexibleBottomAppBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        contentPadding = contentPadding,
        horizontalArrangement = BottomAppBarDefaults.FlexibleFixedHorizontalArrangement,
    ) {
        AppBarRow(
            overflowIndicator = {
                IconButton(
                    onClick = {
                        /* menu state check omitted due to API differences */
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                    )
                }
            },
            content = actions
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun ExpressiveBarsPreview() {
    ToolzTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ExpressiveTopAppBar(
                title = "Toolz",
                subtitle = "Expressive Design",
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
            
            ToolzHorizontalFloatingToolbar(
                expanded = true,
                content = {
                    FilledIconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    }
                },
                trailingContent = {
                    clickableItem(onClick = {}, icon = { Icon(Icons.Rounded.Favorite, null) }, label = "Fav")
                    clickableItem(onClick = {}, icon = { Icon(Icons.Rounded.Person, null) }, label = "Profile")
                }
            )
            
            ToolzVerticalFloatingToolbar(
                expanded = true,
                content = {
                    FilledIconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                    }
                },
                trailingContent = {
                    clickableItem(onClick = {}, icon = { Icon(Icons.Rounded.MoreVert, null) }, label = "More")
                }
            )
        }
    }
}
