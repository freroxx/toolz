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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import com.frerox.toolz.ui.theme.LocalPerformanceMode
import java.util.Locale

/** Split time into whole-second part + centisecond fraction. */
data class PreciseTimeParts(val whole: String, val fraction: String?)

/**
 * Shared centisecond formatter for Timer (countdown) + Stopwatch (count-up).
 * Fraction is always 2-digit centiseconds.
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
 * Shared precision readout for Timer + Stopwatch dials.
 *
 * M3 Expressive, calm by design:
 * - One Row, children aligned by FIRST BASELINE — whole + fraction share the
 *   same baseline at any size pairing, so `.cc` never floats.
 * - Tabular numerals (`tnum`) so `00↔99` never reflows the dial.
 * - ZERO per-tick motion: whole + fraction swap instantly like a real clock.
 *   Motion exists only where it means something — the ms toggle
 *   (fade + width morph, user-initiated) and the running/paused/error color
 *   crossfade (state transitions, never per tick).
 * - Whole stays `onSurface` for readability; only the fraction signals state
 *   (accent while running, muted while paused). Error color only when the
 *   passed accent is the theme error (ringing / finished).
 */
@Composable
fun PrecisionTimerText(
    timeMillis: Long,
    showMillis: Boolean,
    isRunning: Boolean,
    accent: Color,
    style: TextStyle = MaterialTheme.typography.displayMedium,
    fractionStyle: TextStyle = MaterialTheme.typography.titleLarge,
    ceilSeconds: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val performanceMode = LocalPerformanceMode.current
    val locale = remember { Locale.getDefault() }
    val parts = formatPreciseTimeParts(timeMillis, showMillis, locale, ceilSeconds)

    val errorColor = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val isError = accent == errorColor

    val wholeTarget = if (isError) errorColor else onSurface
    val fractionTarget = when {
        isError -> errorColor
        isRunning -> accent
        else -> onVariant.copy(alpha = 0.75f)
    }
    val wholeColor by animateColorAsState(wholeTarget, tween(300), label = "preciseWholeColor")
    val fractionColor by animateColorAsState(fractionTarget, tween(300), label = "preciseFracColor")

    // Theme display type is Default family; enforce it + tabular figures so this
    // never drifts into the mono-clock look and digits never change width.
    val wholeStyle = remember(style) {
        style.copy(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")
    }
    val fracStyle = remember(fractionStyle) {
        fractionStyle.copy(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")
    }
    val readoutDesc = remember(parts.whole, parts.fraction) {
        "Timer ${parts.whole}${parts.fraction ?: ""}"
    }

    Row(
        modifier = modifier.semantics { contentDescription = readoutDesc },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No per-tick animation — digits swap instantly. The only motion here
        // is the ms toggle below (user-initiated) and the color crossfade
        // above (state transitions).
        Text(
            text = parts.whole,
            style = wholeStyle,
            color = wholeColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.alignByBaseline(),
        )
        if (performanceMode) {
            if (parts.fraction != null) {
                Text(
                    text = parts.fraction,
                    style = fracStyle,
                    color = fractionColor,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.alignByBaseline().padding(start = 2.dp),
                )
            }
            return@Row
        }
        AnimatedVisibility(
            visible = parts.fraction != null,
            enter = fadeIn(tween(200)) + expandHorizontally(tween(200), expandFrom = Alignment.Start),
            exit = fadeOut(tween(160)) + shrinkHorizontally(tween(160), shrinkTowards = Alignment.Start),
            modifier = Modifier.alignByBaseline(),
        ) {
            Text(
                text = parts.fraction ?: ".00",
                style = fracStyle,
                color = fractionColor,
                textAlign = TextAlign.Start,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}
