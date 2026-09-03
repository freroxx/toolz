/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ColorGood = Color(0xFF4CAF50)
private val ColorWarn = Color(0xFFFFC107)
private val ColorBad = Color(0xFFF44336)

/**
 * Compact status pill summarising the active protections: ad block / NextDNS
 * state, DNS provider (with optional live [latency]), and whether the
 * session is incognito. Tapping opens the full security/privacy sheet.
 */
@Composable
fun SecurityStatusRow(
    adBlockEnabled: Boolean,
    dnsProvider: String,
    isIncognito: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    latency: Long? = null,
) {
    val isNextDns = dnsProvider == "NEXTDNS"
    val isProtected = adBlockEnabled || isNextDns

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecurityDot(color = if (isProtected) ColorGood else ColorBad)

            Text(
                text = if (isNextDns) stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_status_nextdns) else stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_status_adblock),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isProtected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            VerticalDivider(modifier = Modifier.height(14.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Icon(
                imageVector = if (isNextDns) Icons.Rounded.Dns else Icons.Rounded.Shield,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isProtected) ColorGood else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    dnsProvider.lowercase().replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (latency != null) {
                    val color = when {
                        latency < 50 -> ColorGood
                        latency < 150 -> ColorWarn
                        else -> ColorBad
                    }
                    Text(
                        stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_status_latency, latency),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        fontWeight = FontWeight.Bold,
                        color = color.copy(alpha = 0.8f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                    )
                }
            }

            if (isIncognito) {
                SecurityDot(color = MaterialTheme.colorScheme.tertiary)
                Text(
                    stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_status_private),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalFadingEdges(fadeSize = 8.dp, start = false, end = true),
                )
            }

            Spacer(Modifier.weight(1f))

            Icon(
                Icons.Rounded.ChevronRight, null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun SecurityDot(color: Color) {
    Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
}
