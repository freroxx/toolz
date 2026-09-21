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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import java.util.Locale

/** Split time into whole-second part + centisecond fraction. */
data class PreciseTimeParts(val whole: String, val fraction: String?)

/**
 * Shared centisecond formatter for Timer (countdown) + Stopwatch (count-up).
 * Fraction is always 2-digit centiseconds, matching the stopwatch convention.
 */
fun formatPreciseTimeParts(
    timeMillis: Long,
    showMillis: Boolean,
    locale: Locale,
    ceilSeconds: Boolean = true,
): PreciseTimeParts {
    val safe = timeMillis.coerceAtLeast(0L)
    val totalSeconds = if (ceilSeconds) {
        // Countdown: 59.9s remaining reads 01:00, not 00:59.
        ((safe + 999) / 1000).coerceAtLeast(0)
    } else {
        // Count-up: 59.9s elapsed reads 00:59.
        (safe / 1000).coerceAtLeast(0)
    }
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val whole = if (hours >= 24) {
        val days = hours / 24
        val h = hours % 24
        String.format(locale, "%dd %02d:%02d:%02d", days, h, minutes, seconds)
    } else if (hours > 0) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(locale, "%02d:%02d", minutes, seconds)
    }
    if (!showMillis) return PreciseTimeParts(whole, null)
    val centis = (safe % 1000) / 10
    return PreciseTimeParts(whole, String.format(locale, ".%02d", centis))
}

/**
 * Shared precision counter for Timer + Stopwatch dials.
 *
 * - Whole-second part swaps with a subtle fade/scale (never per-tick churn).
 * - Fraction (.cc) rolls: quick vertical slide + alpha, like a drum digit.
 *   The slide also stretches scaleY briefly, which reads as motion blur
 *   without the cost of a real blur pass.
 * - Monospace + fixed layout so digits never reflow; static fallback under
 *   performance mode or when paused.
 */
@Composable
fun PrecisionTimerText(
    timeMillis: Long,
    showMillis: Boolean,
    isRunning: Boolean,
    accent: Color,
    style: TextStyle = MaterialTheme.typography.displayMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
    ),
    fractionStyle: TextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
    ),
    ceilSeconds: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val performanceMode = LocalPerformanceMode.current
    val locale = remember { Locale.getDefault() }
    val parts = formatPreciseTimeParts(timeMillis, showMillis, locale, ceilSeconds)
    val contentColor = if (isRunning) accent else MaterialTheme.colorScheme.onSurface
    val animateFraction = showMillis && isRunning && !performanceMode

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
    ) {
        AnimatedContent(
            targetState = parts.whole,
            transitionSpec = { (fadeIn(tween(150)) + scaleIn(initialScale = 0.97f)).togetherWith(fadeOut(tween(150))) },
            label = "preciseWhole",
            modifier = Modifier.weight(1f, fill = false),
        ) { whole ->
            Text(
                text = whole,
                style = style,
                color = contentColor,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )
        }
        if (parts.fraction != null) {
            if (animateFraction) {
                RollingFractionText(
                    fraction = parts.fraction,
                    color = accent,
                    style = fractionStyle,
                )
            } else {
                Text(
                    text = parts.fraction,
                    style = fractionStyle,
                    color = if (isRunning) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RollingFractionText(
    fraction: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var lastFraction by remember { mutableStateOf(fraction) }
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val slidePx = remember(density) { with(density) { 6.dp.toPx() } }
    LaunchedEffect(fraction) {
        if (fraction != lastFraction) {
            lastFraction = fraction
            // Drum roll: start slightly below + transparent, settle up.
            offset.snapTo(1f)
            offset.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }
    // Stretch while moving => motion-blur read; alpha dip sells the speed.
    val blurAlpha = (1f - 0.45f * offset.value).coerceIn(0.55f, 1f)
    Text(
        text = fraction,
        style = style,
        color = color,
        textAlign = TextAlign.Start,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                translationY = offset.value * slidePx
                scaleY = 1f + 0.18f * offset.value
                alpha = blurAlpha
            }
            .alpha(blurAlpha),
    )
}
