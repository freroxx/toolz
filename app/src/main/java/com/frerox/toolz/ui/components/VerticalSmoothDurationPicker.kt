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

package com.frerox.toolz.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.ui.theme.toolzBackground
import com.frerox.toolz.util.VibrationManager
import com.frerox.toolz.ui.theme.LocalVibrationManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalSmoothDurationPicker(
    minutes: Int,
    seconds: Int,
    accent: Color,
    enabled: Boolean,
    onChange: (mins: Int, secs: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vibrationManager = LocalVibrationManager.current
    val itemCountMins = 60
    val itemCountSecs = 60  // 0-59
    val ITEM_HEIGHT = 58.dp

    val initialPageMins = (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % itemCountMins + minutes
    val initialPageSecs = (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % itemCountSecs + seconds

    val minsPagerState = rememberPagerState(initialPage = initialPageMins) { Int.MAX_VALUE }
    val secsPagerState = rememberPagerState(initialPage = initialPageSecs) { Int.MAX_VALUE }

    // Sync external minutes changes to pager (only if not scrolling)
    LaunchedEffect(minutes) {
        if (!minsPagerState.isScrollInProgress) {
            val currentVal = (minsPagerState.currentPage % itemCountMins)
            if (currentVal != minutes) {
                val target = minsPagerState.currentPage + (minutes - currentVal)
                minsPagerState.scrollToPage(target)
            }
        }
    }

    // Sync external seconds changes to pager (only if not scrolling)
    LaunchedEffect(seconds) {
        if (!secsPagerState.isScrollInProgress) {
            val currentVal = (secsPagerState.currentPage % itemCountSecs)
            if (currentVal != seconds) {
                val target = secsPagerState.currentPage + (seconds - currentVal)
                secsPagerState.scrollToPage(target)
            }
        }
    }

    // Report minutes changes
    LaunchedEffect(minsPagerState.settledPage) {
        val newVal = minsPagerState.settledPage % itemCountMins
        if (newVal != minutes) {
            vibrationManager?.vibrateTick()
            onChange(newVal, seconds)
        }
    }

    // Report seconds changes
    LaunchedEffect(secsPagerState.settledPage) {
        val newVal = secsPagerState.settledPage % itemCountSecs
        if (newVal != seconds) {
            vibrationManager?.vibrateTick()
            onChange(minutes, newVal)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Minutes column
        DurationColumn(
            pagerState = minsPagerState,
            itemCount = itemCountMins,
            itemHeight = ITEM_HEIGHT,
            label = "Mins",
            accent = accent,
            enabled = enabled,
        )

        // Separator
        Text(
            text = ":",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = accent,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Seconds column
        DurationColumn(
            pagerState = secsPagerState,
            itemCount = itemCountSecs,
            itemHeight = ITEM_HEIGHT,
            label = "Secs",
            accent = accent,
            enabled = enabled,
        )
    }
}

@Composable
private fun DurationColumn(
    pagerState: androidx.compose.foundation.pager.PagerState,
    itemCount: Int,
    itemHeight: androidx.compose.ui.unit.Dp,
    label: String,
    accent: Color,
    enabled: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    val visibleItems = 3 // Reduced to 3 for cleaner look

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.height(itemHeight * visibleItems),
            contentAlignment = Alignment.Center
        ) {
            // Background / border
            Surface(
                modifier = Modifier.width(110.dp).fillMaxHeight(),
                shape = SquircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.8f else 0.4f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.12f)),
            ) {}

            // Central focus bar (BEHIND the pager to avoid blocking touches)
            Surface(
                modifier = Modifier
                    .width(96.dp)
                    .height(itemHeight * 0.95f),
                shape = SmallExpressiveShape,
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.5.dp, accent.copy(alpha = 0.22f)),
            ) {}

            // The Pager (TOP layer for interaction)
            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight()
                    .alpha(if (enabled) 1f else 0.4f),
                userScrollEnabled = enabled,
                contentPadding = PaddingValues(vertical = itemHeight),
            ) { index ->
                val itemValue = index % itemCount
                val isSelected = pagerState.currentPage == index

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.25f else 0.75f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "itemScale",
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            alpha = if (isSelected) 1f else 0.25f,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = String.format(locale, "%02d", itemValue),
                        style = if (isSelected) {
                            MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black)
                        } else {
                            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                        },
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accent.copy(alpha = 0.7f),
            letterSpacing = 2.sp,
        )
    }
}

