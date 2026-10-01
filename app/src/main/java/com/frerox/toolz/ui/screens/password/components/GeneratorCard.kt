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

package com.frerox.toolz.ui.screens.password.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ExpressiveFilterChip
import com.frerox.toolz.ui.components.ExpressiveSlider
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.ToolzExpressiveIconButton
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.util.password.VaultPasswordEngine

/**
 * One generator card for both surfaces (vault sheet + random tool).
 * Spec is owned by the caller; regen happens on release + explicit button,
 * never on every slider tick.
 */
@Composable
fun GeneratorCard(
    spec: VaultPasswordEngine.PasswordSpec,
    password: String,
    report: VaultPasswordEngine.StrengthReport,
    emptyPool: Boolean,
    onSpecChange: (VaultPasswordEngine.PasswordSpec) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onPreset: ((VaultPasswordEngine.PasswordSpec) -> Unit)? = null,
    onMemorable: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val vibrationManager = LocalVibrationManager.current
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val isElite = report.tier == VaultPasswordEngine.Tier.ELITE

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = LargeExpressiveShape,
            color = if (isElite)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = if (isElite) 2.dp else 1.dp,
                color = if (isElite)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                else
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        password.ifEmpty { "—" },
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Black,
                        color = if (isElite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    ToolzExpressiveIconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onRegenerate()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MediumExpressiveShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.st_PasswordVaultScreen_s1t3),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    ToolzExpressiveIconButton(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            onCopy()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = MediumExpressiveShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.st_PasswordVaultScreen_y7z9),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                ExpandableStrength(
                    report = report,
                    expanded = detailsExpanded,
                    onToggle = {
                        vibrationManager?.vibrateTick()
                        detailsExpanded = !detailsExpanded
                    }
                )
            }
        }

        if (emptyPool) {
            Text(
                stringResource(R.string.st_PasswordVaultScreen_gen_empty_pool),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }

        // Length — live spec update, regen on release only.
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.st_PasswordVaultScreen_u3v5),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AnimatedContent(targetState = spec.length, label = "gen_len") { len ->
                    Text(
                        len.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            ExpressiveSlider(
                value = spec.length.toFloat(),
                onValueChange = { onSpecChange(spec.copy(length = it.toInt())) },
                onValueChangeFinished = onRegenerate,
                valueRange = VaultPasswordEngine.MIN_LENGTH.toFloat()..
                    VaultPasswordEngine.MAX_LENGTH.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }

        // Character classes.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpressiveFilterChip(
                selected = spec.includeLowercase,
                onClick = { onSpecChange(spec.copy(includeLowercase = !spec.includeLowercase, pinMode = false)) },
                label = { Text("a-z", fontWeight = FontWeight.Black) },
                leadingIcon = { Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f),
                shape = MediumExpressiveShape,
                enabled = !spec.pinMode
            )
            ExpressiveFilterChip(
                selected = spec.includeUppercase,
                onClick = { onSpecChange(spec.copy(includeUppercase = !spec.includeUppercase, pinMode = false)) },
                label = { Text("A-Z", fontWeight = FontWeight.Black) },
                leadingIcon = { Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f),
                shape = MediumExpressiveShape,
                enabled = !spec.pinMode
            )
            ExpressiveFilterChip(
                selected = spec.includeNumbers || spec.pinMode,
                onClick = { onSpecChange(spec.copy(includeNumbers = !spec.includeNumbers)) },
                label = { Text("0-9", fontWeight = FontWeight.Black) },
                leadingIcon = { Icon(Icons.Rounded.Numbers, null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f),
                shape = MediumExpressiveShape,
                enabled = !spec.pinMode
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpressiveFilterChip(
                selected = spec.includeSymbols,
                onClick = { onSpecChange(spec.copy(includeSymbols = !spec.includeSymbols, pinMode = false)) },
                label = { Text("@#!", fontWeight = FontWeight.Black) },
                modifier = Modifier.weight(1f),
                shape = MediumExpressiveShape,
                enabled = !spec.pinMode
            )
            ExpressiveFilterChip(
                selected = spec.excludeAmbiguous,
                onClick = { onSpecChange(spec.copy(excludeAmbiguous = !spec.excludeAmbiguous)) },
                label = { Text(stringResource(R.string.st_PasswordVaultScreen_gen_exclude_ambiguous), fontWeight = FontWeight.Black) },
                modifier = Modifier.weight(1f),
                shape = MediumExpressiveShape
            )
            ExpressiveFilterChip(
                selected = spec.pinMode,
                onClick = { onSpecChange(spec.copy(pinMode = !spec.pinMode)) },
                label = { Text(stringResource(R.string.st_PasswordVaultScreen_gen_pin), fontWeight = FontWeight.Black) },
                modifier = Modifier.weight(1f),
                shape = MediumExpressiveShape
            )
        }

        OutlinedTextField(
            value = spec.customSymbols,
            onValueChange = { onSpecChange(spec.copy(customSymbols = it, pinMode = false)) },
            label = { Text(stringResource(R.string.st_PasswordVaultScreen_gen_custom_symbols)) },
            singleLine = true,
            shape = MediumExpressiveShape,
            modifier = Modifier.fillMaxWidth(),
            enabled = !spec.pinMode
        )

        if (onPreset != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaultPasswordEngine.PRESETS.forEach { preset ->
                    ExpressiveFilterChip(
                        selected = false,
                        onClick = { onPreset(preset.spec) },
                        label = { Text(preset.name, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                        shape = SmallExpressiveShape
                    )
                }
                if (onMemorable != null) {
                    ExpressiveFilterChip(
                        selected = false,
                        onClick = onMemorable,
                        label = { Text(stringResource(R.string.st_PasswordVaultScreen_gen_memorable), fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                        shape = SmallExpressiveShape
                    )
                }
            }
        }
    }
}

/** Collapsible strength: badge row always visible, bar + bits + reasons on expand. */
@Composable
fun ExpandableStrength(
    report: VaultPasswordEngine.StrengthReport,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = SmallExpressiveShape,
                color = tierColor(report.tierIndex).copy(alpha = 0.12f)
            ) {
                Text(
                    stringResource(tierLabel(report.tierIndex)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = tierColor(report.tierIndex),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.st_PasswordVaultScreen_bits, report.bits.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.st_PasswordVaultScreen_hide_details
                        else R.string.st_PasswordVaultScreen_show_details
                    ),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        com.frerox.toolz.ui.components.ExpressiveWavyLinearProgressIndicator(
            progress = { (report.tierIndex + 1) / 5f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = tierColor(report.tierIndex),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.st_PasswordVaultScreen_crack_time, report.crackTimeLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                report.reasons.forEach { reason ->
                    Text(
                        "• ${stringResource(reasonLabel(reason))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                report.suggestions.forEach { suggestion ->
                    Text(
                        "→ $suggestion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun tierColor(tierIndex: Int) = when (tierIndex.coerceIn(0, 4)) {
    0 -> MaterialTheme.colorScheme.error
    1 -> MaterialTheme.colorScheme.tertiary
    2 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

private fun tierLabel(tierIndex: Int): Int = when (tierIndex.coerceIn(0, 4)) {
    0 -> R.string.st_PasswordVaultScreen_strength_critical
    1 -> R.string.st_PasswordVaultScreen_strength_weak
    2 -> R.string.st_PasswordVaultScreen_strength_mid
    3 -> R.string.st_PasswordVaultScreen_strength_strong
    else -> R.string.st_PasswordVaultScreen_strength_elite
}

private fun reasonLabel(reason: VaultPasswordEngine.Reason): Int = when (reason) {
    VaultPasswordEngine.Reason.TOO_SHORT -> R.string.st_PasswordVaultScreen_reason_short
    VaultPasswordEngine.Reason.REPEATS -> R.string.st_PasswordVaultScreen_reason_repeats
    VaultPasswordEngine.Reason.SEQUENCE -> R.string.st_PasswordVaultScreen_reason_sequence
    VaultPasswordEngine.Reason.COMMON -> R.string.st_PasswordVaultScreen_reason_common
    VaultPasswordEngine.Reason.GOOD_LENGTH -> R.string.st_PasswordVaultScreen_reason_good_length
    VaultPasswordEngine.Reason.GOOD_MIX -> R.string.st_PasswordVaultScreen_reason_good_mix
}

/** Backwards-compatible helper for non-Compose callers. */
fun tierLabelRes(tierIndex: Int): Int = when (tierIndex.coerceIn(0, 4)) {
    0 -> R.string.st_PasswordVaultScreen_strength_critical
    1 -> R.string.st_PasswordVaultScreen_strength_weak
    2 -> R.string.st_PasswordVaultScreen_strength_mid
    3 -> R.string.st_PasswordVaultScreen_strength_strong
    else -> R.string.st_PasswordVaultScreen_strength_elite
}
