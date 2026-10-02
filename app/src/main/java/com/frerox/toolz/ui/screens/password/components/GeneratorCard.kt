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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.ExpressiveCard
import com.frerox.toolz.ui.components.ExpressiveSlider
import com.frerox.toolz.ui.components.LargeExpressiveShape
import com.frerox.toolz.ui.components.MediumExpressiveShape
import com.frerox.toolz.ui.components.SmallExpressiveShape
import com.frerox.toolz.ui.components.horizontalFadingEdges
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.frerox.toolz.util.password.VaultPasswordEngine
import kotlinx.coroutines.delay

/**
 * One generator card shared by the vault sheet and the random tool.
 * M3 Expressive: full-width password output with fading edges,
 * two big action cards, toggle cards, remade Advanced section.
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
    var justCopied by remember { mutableStateOf(false) }
    var regenSpins by remember { mutableIntStateOf(0) }
    val isElite = report.tier == VaultPasswordEngine.Tier.ELITE

    // Strength tint drives slider, wavy bar, badge and length number as one.
    val strengthTint by animateColorAsState(
        targetValue = tierColor(report.tierIndex),
        animationSpec = tween(450),
        label = "gen_strength_tint"
    )
    val containerTint by animateColorAsState(
        targetValue = if (isElite)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(450),
        label = "gen_container_tint"
    )

    val pwScroll = rememberScrollState()
    LaunchedEffect(password) {
        if (pwScroll.maxValue > 0) {
            try {
                pwScroll.animateScrollTo(pwScroll.maxValue)
            } catch (_: Exception) {
                // Layout not settled yet; tail pin is best-effort.
            }
        } else {
            pwScroll.scrollTo(0)
        }
    }
    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1600)
            justCopied = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = LargeExpressiveShape,
        color = containerTint,
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
            // Zone 1: password occupies the entire row.
            PasswordOutput(
                password = password,
                isElite = isElite,
                scroll = pwScroll
            )

            // Zone 2: two big expressive action cards below the output.
            GeneratorActions(
                onRegenerate = {
                    regenSpins++
                    vibrationManager?.vibrateClick()
                    onRegenerate()
                },
                onCopy = {
                    justCopied = true
                    vibrationManager?.vibrateClick()
                    onCopy()
                },
                justCopied = justCopied,
                regenSpins = regenSpins
            )

            // Strength summary + expandable detail.
            ExpandableStrength(
                report = report,
                strengthTint = strengthTint,
                expanded = detailsOpen,
                onToggle = {
                    vibrationManager?.vibrateTick()
                    detailsOpen = !detailsOpen
                }
            )

            if (emptyPool) {
                Surface(
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.st_PasswordVaultScreen_gen_empty_pool),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }

            // Length with animated number tinted by strength.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.st_PasswordVaultScreen_u3v5),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AnimatedContent(
                    targetState = spec.length,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f)).togetherWith(
                            fadeOut(tween(150)) + scaleOut(targetScale = 0.85f)
                        )
                    },
                    label = "gen_length_number"
                ) { len ->
                    Text(
                        len.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = strengthTint
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
                    thumbColor = strengthTint,
                    activeTrackColor = strengthTint,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            // Character sets as small expressive cards.
            Text(
                stringResource(R.string.st_PasswordVaultScreen_gen_charsets),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GenToggleCard("a-z", spec.includeLowercase, !spec.pinMode) {
                    onSpecChange(spec.copy(includeLowercase = !spec.includeLowercase, pinMode = false))
                }
                GenToggleCard("A-Z", spec.includeUppercase, !spec.pinMode) {
                    onSpecChange(spec.copy(includeUppercase = !spec.includeUppercase, pinMode = false))
                }
                GenToggleCard("0-9", spec.includeNumbers || spec.pinMode, !spec.pinMode) {
                    onSpecChange(spec.copy(includeNumbers = !spec.includeNumbers))
                }
                GenToggleCard("@#!", spec.includeSymbols, !spec.pinMode) {
                    onSpecChange(spec.copy(includeSymbols = !spec.includeSymbols, pinMode = false))
                }
                GenToggleCard(
                    stringResource(R.string.st_PasswordVaultScreen_gen_exclude_ambiguous),
                    spec.excludeAmbiguous, true
                ) {
                    onSpecChange(spec.copy(excludeAmbiguous = !spec.excludeAmbiguous))
                }
                GenToggleCard(
                    stringResource(R.string.st_PasswordVaultScreen_gen_pin),
                    spec.pinMode, true
                ) {
                    onSpecChange(spec.copy(pinMode = !spec.pinMode))
                }
            }

            // Remade Advanced section.
            AdvancedSection(
                spec = spec,
                expanded = advancedOpen,
                onToggle = {
                    vibrationManager?.vibrateTick()
                    advancedOpen = !advancedOpen
                },
                onSpecChange = onSpecChange,
                onPreset = onPreset,
                onMemorable = onMemorable
            )
        }
    }
}

/** Zone 1: full-width output with smooth horizontal fading edges. */
@Composable
private fun PasswordOutput(
    password: String,
    isElite: Boolean,
    scroll: ScrollState
) {
    val leftFade = if (scroll.value > 4) 28.dp else 0.dp
    val rightFade = if (scroll.value < scroll.maxValue - 4) 28.dp else 0.dp
    Surface(
        shape = MediumExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            if (isElite)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .horizontalFadingEdges(left = leftFade, right = rightFade)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

/** Zone 2: two big M3 expressive action cards. */
@Composable
private fun GeneratorActions(
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    justCopied: Boolean,
    regenSpins: Int
) {
    val regenRotation by animateFloatAsState(
        targetValue = regenSpins * 360f,
        animationSpec = tween(650),
        label = "gen_regen_spin"
    )
    val copyContainer by animateColorAsState(
        targetValue = if (justCopied)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(350),
        label = "gen_copy_container"
    )
    val copyContent by animateColorAsState(
        targetValue = if (justCopied)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(350),
        label = "gen_copy_content"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExpressiveCard(
            onClick = onRegenerate,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 68.dp),
            shape = MediumExpressiveShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.st_PasswordVaultScreen_s1t3),
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = regenRotation }
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.st_PasswordVaultScreen_s1t3),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
        ExpressiveCard(
            onClick = onCopy,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 68.dp),
            shape = MediumExpressiveShape,
            containerColor = copyContainer,
            contentColor = copyContent
        ) {
            AnimatedContent(
                targetState = justCopied,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.9f)).togetherWith(
                        fadeOut(tween(150)) + scaleOut(targetScale = 0.9f)
                    )
                },
                label = "gen_copy_swap",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { copied ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.st_PasswordVaultScreen_y7z9),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (copied)
                            stringResource(R.string.st_PasswordVaultScreen_gen_copied)
                        else
                            stringResource(R.string.st_PasswordVaultScreen_gen_copy_short),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Small M3 expressive toggle card replacing filter chips. */
@Composable
private fun GenToggleCard(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(300),
        label = "gen_toggle_container"
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(300),
        label = "gen_toggle_content"
    )
    ExpressiveCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = SmallExpressiveShape,
        containerColor = container,
        contentColor = content,
        border = if (selected || !enabled) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun GenPresetCard(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = SmallExpressiveShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Remade Advanced: header card + springy expanding body with sections. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSection(
    spec: VaultPasswordEngine.PasswordSpec,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSpecChange: (VaultPasswordEngine.PasswordSpec) -> Unit,
    onPreset: ((VaultPasswordEngine.PasswordSpec) -> Unit)?,
    onMemorable: (() -> Unit)?
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "gen_advanced_chevron"
    )
    Surface(
        shape = MediumExpressiveShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = SmallExpressiveShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.st_PasswordVaultScreen_advanced),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        stringResource(R.string.st_PasswordVaultScreen_gen_advanced_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.st_PasswordVaultScreen_hide_details
                        else R.string.st_PasswordVaultScreen_show_details
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(280)),
                exit = shrinkVertically(
                    animationSpec = tween(260)
                ) + fadeOut(tween(200))
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    Text(
                        stringResource(R.string.st_PasswordVaultScreen_gen_symbols_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
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
                        Text(
                            stringResource(R.string.st_PasswordVaultScreen_gen_presets),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VaultPasswordEngine.PRESETS.forEach { preset ->
                                GenPresetCard(preset.name) { onPreset(preset.spec) }
                            }
                            if (onMemorable != null) {
                                GenPresetCard(
                                    stringResource(R.string.st_PasswordVaultScreen_gen_memorable)
                                ) { onMemorable() }
                            }
                        }
                    }
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
    modifier: Modifier = Modifier,
    strengthTint: androidx.compose.ui.graphics.Color? = null
) {
    val barTint by animateColorAsState(
        targetValue = strengthTint ?: tierColor(report.tierIndex),
        animationSpec = tween(450),
        label = "gen_bar_tint"
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = SmallExpressiveShape,
                color = barTint.copy(alpha = 0.12f)
            ) {
                Text(
                    stringResource(tierLabel(report.tierIndex)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = barTint,
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
            color = barTint,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(250)) + fadeOut(tween(200))
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
