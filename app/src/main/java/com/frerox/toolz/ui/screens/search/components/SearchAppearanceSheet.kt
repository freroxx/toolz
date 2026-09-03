/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DensityMedium
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppearanceSheet(
    onDismiss: () -> Unit,
    isCompact: Boolean,
    onToggleCompact: (Boolean) -> Unit,
    cardRadius: Float,
    onRadiusChange: (Float) -> Unit,
    showGreetingCard: Boolean = false,
    onToggleGreetingCard: ((Boolean) -> Unit)? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp).size(width = 36.dp, height = 4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ) {}
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppearanceHeader()
            GreetingCardPreview(cardRadius = cardRadius)
            AppearanceSettings(
                isCompact = isCompact,
                onToggleCompact = onToggleCompact,
                cardRadius = cardRadius,
                onRadiusChange = onRadiusChange,
                showGreetingCard = showGreetingCard,
                onToggleGreetingCard = onToggleGreetingCard,
            )
        }
    }
}

@Composable
private fun AppearanceHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appearance_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appearance_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GreetingCardPreview(cardRadius: Float) {
    Surface(
        shape = RoundedCornerShape(cardRadius.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Public, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appearance_preview_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appearance_preview_sub), style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.75f))
                }
                Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(18.dp), tint = LocalContentColor.current.copy(alpha = 0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PreviewStat(label = "tabs", value = "4", weight = 1f)
                PreviewStat(label = "saved", value = "12", weight = 1f)
                PreviewStat(label = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appear_preview_engine), value = "Meta", weight = 1.2f)
            }
        }
    }
}

@Composable
private fun RowScope.PreviewStat(label: String, value: String, weight: Float) {
    Surface(modifier = Modifier.weight(weight), shape = RoundedCornerShape(14.dp), color = LocalContentColor.current.copy(alpha = 0.08f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        }
    }
}

@Composable
private fun AppearanceSettings(
    isCompact: Boolean,
    onToggleCompact: (Boolean) -> Unit,
    cardRadius: Float,
    onRadiusChange: (Float) -> Unit,
    showGreetingCard: Boolean,
    onToggleGreetingCard: ((Boolean) -> Unit)?,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onToggleGreetingCard != null) {
                ToggleRow(
                    icon = Icons.Rounded.WavingHand,
                    active = showGreetingCard,
                    title = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appear_pulse_title),
                    subtitle = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appear_pulse_sub),
                    checked = showGreetingCard,
                    onCheckedChange = onToggleGreetingCard,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }

            ToggleRow(
                icon = Icons.Rounded.DensityMedium,
                active = isCompact,
                title = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appear_compact_title),
                subtitle = stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appear_compact_sub),
                checked = isCompact,
                onCheckedChange = onToggleCompact,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.RoundedCorner, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appearance_radius), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(com.frerox.toolz.R.string.st_SearchScreen_ws_appearance_radius_value, cardRadius.toInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Slider(value = cardRadius, onValueChange = onRadiusChange, valueRange = 12f..32f, steps = 9)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
