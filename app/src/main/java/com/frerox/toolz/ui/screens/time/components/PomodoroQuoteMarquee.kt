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

package com.frerox.toolz.ui.screens.time.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PomodoroQuoteMarquee(
    quotesText: String,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    val quotes = remember(quotesText) {
        quotesText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    }

    if (quotes.isEmpty()) return

    // P-P2-03: key index on quotes so shrink 6->1 resets instead of OOB.
    var currentQuoteIndex by remember(quotes) { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    val safeIndex = currentQuoteIndex.coerceIn(0, quotes.size - 1)
    if (currentQuoteIndex != safeIndex) currentQuoteIndex = safeIndex
    val currentQuote = quotes[safeIndex]

    var showDetails by remember { mutableStateOf(false) }

    // P-P2-03: while(true) + paused-guard (no tight spin, no stuck coroutine).
    LaunchedEffect(quotes, isPaused) {
        while (true) {
            delay(12_000L)
            if (!isPaused && quotes.isNotEmpty()) {
                currentQuoteIndex = (currentQuoteIndex + 1) % quotes.size
            }
        }
    }

    Surface(
        color = activeColor.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    // P-P2-03: onTap present so tap doesn't fight basicMarquee scrolling.
                    onTap = { },
                    onDoubleTap = { isPaused = !isPaused },
                    onLongPress = { showDetails = true }
                )
            }
    ) {
        AnimatedContent(
            targetState = currentQuote,
            transitionSpec = {
                (fadeIn(animationSpec = tween(600, delayMillis = 100)) + 
                 slideInHorizontally(animationSpec = tween(600)) { width -> width / 2 })
                    .togetherWith(fadeOut(animationSpec = tween(400)) + 
                                  slideOutHorizontally(animationSpec = tween(400)) { width -> -width / 2 })
            },
            label = "quoteTransition"
        ) { quote ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = quote,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    ),
                    color = activeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = if (isPaused) 0.dp else 45.dp
                    )
                )
            }
        }
    }

    if (showDetails) {
        // P-P2-03: strict regex with fallback — naive lastIndexOf('(') breaks on
        // quotes containing '(' (e.g. Camus). Fallback keeps full text.
        val parts = remember(currentQuote) {
            parseQuote(currentQuote)
        }

        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close", color = activeColor)
                }
            },
            title = {
                Text(
                    text = "Focus Insight",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column {
                    Text(
                        text = "\"${parts.first}\"",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (parts.second.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                    Text(
                        text = "— ${parts.second}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }
}

/**
 * P-P2-03: strict `"quote" (source)` parse with full-text fallback.
 * Handles inner parens (Camus `(` case) by anchoring to the trailing group.
 */
private val QUOTE_REGEX = Regex("^\"(.*)\"\\s*\\((.*)\\)\\s*$")

internal fun parseQuote(raw: String): Pair<String, String> {
    val trimmed = raw.trim()
    val match = QUOTE_REGEX.find(trimmed)
    if (match != null) {
        val quote = match.groupValues[1].trim()
        val source = match.groupValues[2].trim()
        return quote to source
    }
    // Fallback: keep full text, no crash, no mangling.
    return trimmed.removeSurrounding("\"") to ""
}
