package com.frerox.toolz.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.theme.ToolzTheme
import kotlinx.coroutines.launch

/**
 * Premium carousel using HorizontalMultiBrowseCarousel for featured content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ExpressiveCarousel(
    items: List<T>,
    modifier: Modifier = Modifier,
    preferredItemWidth: Dp = 220.dp,
    itemSpacing: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    itemContent: @Composable (T) -> Unit,
) {
    val carouselState = rememberCarouselState { items.size }
    
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = preferredItemWidth,
        itemSpacing = itemSpacing,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxWidth()
    ) { index ->
        val item = items[index]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.large)
        ) {
            itemContent(item)
        }
    }
}

/**
 * Premium adaptive scaffold for List-Detail patterns.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExpressiveListDetailPaneScaffold(
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch {
            navigator.navigateBack()
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            Box(modifier = Modifier.fillMaxSize()) {
                listPane()
            }
        },
        detailPane = {
            Box(modifier = Modifier.fillMaxSize()) {
                detailPane()
            }
        },
        modifier = modifier
    )
}

/**
 * Premium adaptive scaffold using official Material 3 Adaptive APIs.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ExpressiveSupportingPaneScaffold(
    mainPane: @Composable () -> Unit,
    supportingPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberSupportingPaneScaffoldNavigator()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch {
            navigator.navigateBack()
        }
    }

    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        mainPane = {
            Box(modifier = Modifier.fillMaxSize()) {
                mainPane()
            }
        },
        supportingPane = {
            Box(modifier = Modifier.fillMaxSize()) {
                supportingPane()
            }
        },
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun ExpressiveLayoutsPreview() {
    ToolzTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ExpressiveCarousel(items = listOf(1, 2, 3)) { item ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Item $item")
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            ExpressiveSupportingPaneScaffold(
                mainPane = { Text("Main Content") },
                supportingPane = { Text("Supporting Content") }
            )
        }
    }
}
