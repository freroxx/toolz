/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The main search/address pill shown at the top of the search screen and
 * embedded browser chrome.
 *
 * Auto-focuses (and raises the keyboard) whenever [active] flips to `true` —
 * e.g. when raised programmatically from [FloatingSearchDock] — and drops
 * focus/hides the keyboard when it flips back to `false`. Becoming focused
 * by direct user tap also reports back through [onActiveChange] so callers
 * stay in sync regardless of which side triggered the state change.
 */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isIncognito: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(active) {
        if (active) {
            delay(80) // let the sheet/nav transition settle before grabbing focus
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(200),
        label = "pillBg",
    )

    Surface(
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        border = if (active) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        tonalElevation = if (active) 0.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isIncognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(if (isIncognito) 17.dp else 18.dp),
                tint = if (isIncognito) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (active) 0.9f else 0.5f)
                },
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Uri),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = if (isIncognito) stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_search_hint_incognito) else stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_search_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        }
                        innerTextField()
                    }
                },
                interactionSource = interactionSource,
            )

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Rounded.Cancel,
                        contentDescription = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_search_clear),
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            IconButton(onClick = onSettingsClick, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_search_options_desc),
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
    }

    // Any focus gained via direct user interaction (not the programmatic
    // path above) should also mark the pill active, so tapping straight
    // into the field works the same as raising it from the dock.
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        if (isFocused && !active) onActiveChange(true)
    }
}
