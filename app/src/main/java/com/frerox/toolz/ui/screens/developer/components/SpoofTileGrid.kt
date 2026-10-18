/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Spoof tab building blocks: spacious, high-usability sandbox cards.
 * Provides clear live simulated values, parameter configuration,
 * and direct one-tap "Launch Screen" navigation to test mock behavior in real time.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
        title,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}



/**
 * A spacious, dashboard-grade tool sandbox card.
 */
@Composable
fun SpoofToolTile(
    tool: DevToolId,
    params: SpoofParams,
    active: Boolean,
    devOn: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit,
    onLaunchScreen: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isEnabled = devOn
    val isMockActive = active && isEnabled

    val cardBorder = if (isMockActive) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    val containerColor = if (isMockActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = cardBorder,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = isEnabled, onClick = onConfigure)
            .semantics { contentDescription = "${tool.title} sandbox card" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMockActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        tool.icon,
                        contentDescription = null,
                        tint = if (isMockActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tool.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isMockActive) {
                    Text(
                        spoofFixtureSummary(tool, params),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        tool.subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            val route = tool.openRoute
            if (route != null && onLaunchScreen != null) {
                IconButton(
                    onClick = { onLaunchScreen(route) },
                    enabled = isEnabled,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Launch tool",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Switch(
                checked = isMockActive,
                enabled = isEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}
