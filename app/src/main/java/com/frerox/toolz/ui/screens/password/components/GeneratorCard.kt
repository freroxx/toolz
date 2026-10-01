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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
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
 * One generator card shared by the vault sheet and the random tool.
 * Plain M3 Expressive: single container, password + actions, length,
 * one chip row, everything else behind "Advanced".
 */
@OptIn(ExperimentalLayoutApi::class)
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
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val isElite = report.tier == VaultPasswordEngine.Tier.ELITE
    // Slider + accents follow the live strength tier with a smooth M3 fade.
    val strengthTint by animateColorAsState(
        targetValue = tierColor(report.tierIndex),
        animationSpec = tween(450),
        label = "gen_strength_tint"
    )
    // Long outputs stay fully visible: scrollable row pinned to the tail.
    val pwScroll = rememberScrollState()
    LaunchedEffect(password) {
        pwScroll.animateScrollTo(pwScroll.maxValue)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
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
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Password + actions.
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = password,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )).togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.96f))
                    },
                    modifier = Modifier.weight(1f),
                    label = "gen_password_swap"
                ) { pw ->
                    Text(
                        pw.ifEmpty { "—" },
                        style = (if (pw.length > 28) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleLarge)
                            .copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.5.sp),
                        fontWeight = FontWeight.Black,
                        color = if (isElite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.horizontalScroll(pwScroll)
                    )
                }
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

            // Strength summary + expandable detail.
            ExpandableStrength(
                report = report,
                expanded = detailsOpen,
                onToggle = {
                    vibrationManager?.vibrateTick()
                    detailsOpen = !detailsOpen
                }
            )

            if (emptyPool) {
                Text(
                    stringResource(R.string.st_PasswordVaultScreen_gen_empty_pool),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            // Length.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.st_PasswordVaultScreen_u3v5),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    spec.length.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ExpressiveSlider(
                value = spec.length.toFloat(),
                onValueChange = { onSpecChange(spec.copy(length = it.toInt())) },
                onValueChangeFinished = onRegenerate,
                valueRange = VaultPasswordEngine.MIN_LENGTH.toFloat()..
                    VaultPasswordEngine.MAX_LENGTH.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = strengthTint,
                    activeTrackColor = strengthTint,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            // One chip row for the common toggles.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GenChip("a-z", spec.includeLowercase, !spec.pinMode) {
                    onSpecChange(spec.copy(includeLowercase = !spec.includeLowercase, pinMode = false))
                }
                GenChip("A-Z", spec.includeUppercase, !spec.pinMode) {
                    onSpecChange(spec.copy(includeUppercase = !spec.includeUppercase, pinMode = false))
                }
                GenChip("0-9", spec.includeNumbers || spec.pinMode, !spec.pinMode) {
                    onSpecChange(spec.copy(includeNumbers = !spec.includeNumbers))
                }
                GenChip("@#!", spec.includeSymbols, !spec.pinMode) {
                    onSpecChange(spec.copy(includeSymbols = !spec.includeSymbols, pinMode = false))
                }
                GenChip(
                    stringResource(R.string.st_PasswordVaultScreen_gen_exclude_ambiguous),
                    spec.excludeAmbiguous, true
                ) {
                    onSpecChange(spec.copy(excludeAmbiguous = !spec.excludeAmbiguous))
                }
                GenChip(
                    stringResource(R.string.st_PasswordVaultScreen_gen_pin),
                    spec.pinMode, true
                ) {
                    onSpecChange(spec.copy(pinMode = !spec.pinMode))
                }
            }

            // Everything else lives behind Advanced.
            TextButton(
                onClick = {
                    vibrationManager?.vibrateTick()
                    advancedOpen = !advancedOpen
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    stringResource(
                        if (advancedOpen) R.string.st_PasswordVaultScreen_hide_details
                        else R.string.st_PasswordVaultScreen_advanced
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
            AnimatedVisibility(
                visible = advancedOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VaultPasswordEngine.PRESETS.forEach { preset ->
                                GenChip(preset.name, false, true) { onPreset(preset.spec) }
                            }
                            if (onMemorable != null) {
                                GenChip(
                                    stringResource(R.string.st_PasswordVaultScreen_gen_memorable),
                                    false, true
                                ) { onMemorable() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    ExpressiveFilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.Black) },
        shape = MediumExpressiveShape,
        enabled = enabled
    )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
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
