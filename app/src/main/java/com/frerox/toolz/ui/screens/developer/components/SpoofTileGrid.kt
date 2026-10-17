/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Spoof tab building blocks: one expressive tile per tool (real icon,
 * policy chip, glowing border when sandboxed) plus the section header.
 * Tapping the tile opens its configurator; the trailing switch toggles it.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.developer.DevToolId
import com.frerox.toolz.data.developer.SpoofParams
import com.frerox.toolz.ui.screens.developer.icon
import com.frerox.toolz.ui.screens.developer.openRoute
import com.frerox.toolz.ui.screens.developer.spoofFixtureSummary

@Composable
fun DevSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        modifier = modifier.padding(top = 10.dp, bottom = 6.dp),
        fontWeight = FontWeight.Black,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun rememberPulse(active: Boolean): Float {
    if (!active) return 0f
    val transition = rememberInfiniteTransition(label = "spoofPulse")
    val value by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "spoofPulseValue",
    )
    return value
}

/**
 * A tool tile. Configuration lives in the bottom sheet; the whole tile
 * presses through to the configurator while the switch flips directly.
 */
@Composable
fun SpoofToolTile(
    tool: DevToolId,
    params: SpoofParams,
    active: Boolean,
    devOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit,
) {
    val pulse = rememberPulse(active)
    val container by animateColorAsState(
        if (active && devOn) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        label = "tile",
    )
    val borderMod = if (active && devOn) {
        Modifier.border(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.45f + 0.3f * pulse),
            shape = RoundedCornerShape(22.dp),
        )
    } else {
        Modifier
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = borderMod.semantics { contentDescription = "${tool.title} sandbox tile" },
    ) {
        Column(
            Modifier
                .clickable(enabled = devOn, onClick = onConfigure)
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (active && devOn) MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            tool.icon,
                            contentDescription = null,
                            tint = if (active && devOn) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    tool.title,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                )
                Switch(
                    checked = active,
                    enabled = devOn,
                    onCheckedChange = onToggle,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (active && devOn) "SANDBOX • ${spoofFixtureSummary(tool, params)}"
                else tool.subtitle,
                fontSize = 11.sp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
            ) {
                Text(
                    tool.networkPolicy.name,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (active && devOn) SpoofChip(label = "SPOOF • LOCAL")
                Text(
                    "Configure →",
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (active && devOn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
