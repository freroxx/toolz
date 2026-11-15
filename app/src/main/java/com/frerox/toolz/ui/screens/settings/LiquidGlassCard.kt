/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Uses backdrop library © Kyant, Apache License 2.0
 * https://github.com/Kyant0/AndroidLiquidGlass
 */

package com.frerox.toolz.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.data.skin.IconShape
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.liquidglass.GlassCard
import com.frerox.toolz.ui.liquidglass.LocalLiquidGlassBackdrop
import com.frerox.toolz.ui.liquidglass.ToolzMarkGlass
import com.frerox.toolz.ui.theme.LocalVibrationManager
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle

/**
 * The authentic Liquid Glass settings card in [AppSkinScreen].
 *
 * Features:
 * - 1-to-1 [LiquidToggle] for enable/disable with cinematic transition
 * - Performance Mode toggle (uses standard M3 components instead of glass shaders)
 * - Three customization sliders: Glass Blur, Opacity, Lens Refraction
 * - All sliders wrapped with 20dp horizontal padding so the glass thumb never clips
 */
@Composable
fun LiquidGlassCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEnableAnimation: () -> Unit = {},
    onToggleAnimation: ((Boolean) -> Unit)? = null,
    performanceMode: Boolean = false,
    onPerformanceModeChange: (Boolean) -> Unit = {},
    blurRadius: Float = 8f,
    onBlurRadiusChange: (Float) -> Unit = {},
    tintAlpha: Float = 0.25f,
    onTintAlphaChange: (Float) -> Unit = {},
    refraction: Float = 24f,
    onRefractionChange: (Float) -> Unit = {},
    onResetCustomization: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vibrationManager = LocalVibrationManager.current
    val scheme = MaterialTheme.colorScheme
    val backdrop = LocalLiquidGlassBackdrop.current

    // Use theme-aware surface color so sliders look correct on both light & dark themes
    val isDark = isSystemInDarkTheme()
    val surfaceColor = if (isDark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)
    val effectiveBackdrop = backdrop ?: rememberCanvasBackdrop {
        drawRect(surfaceColor)
    }

    GlassCard(
        backdrop = backdrop,
        shape = RoundedCornerShape(28.dp),
        tint = if (enabled) scheme.surfaceVariant.copy(alpha = 0.35f) else scheme.surfaceVariant.copy(alpha = 0.20f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Main toggle row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClick(
                        onClick = {
                            vibrationManager?.vibrateClick()
                            val next = !enabled
                            onToggle(next)
                            if (onToggleAnimation != null) {
                                onToggleAnimation(next)
                            } else if (next) {
                                onEnableAnimation()
                            }
                        },
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // ── 3D Liquid Glass Jewel Icon Preview ───────────────────────
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ToolzMarkGlass(
                        spec = IconCatalog.find(IconCatalog.DEFAULT_ID),
                        backdrop = effectiveBackdrop,
                        frame = IconShape.OCTAGON,
                        modifier = Modifier.size(42.dp),
                    )
                }

                // ── Title, Badge & Subtitle ──────────────────────────────────
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Liquid Glass",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (enabled)
                                Color(0xFF9D5FFF).copy(alpha = 0.18f)
                            else
                                scheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                0.5.dp,
                                if (enabled) Color(0xFF9D5FFF).copy(alpha = 0.45f) else scheme.outlineVariant.copy(alpha = 0.25f),
                            ),
                        ) {
                            Text(
                                text = "12+",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (enabled) Color(0xFFC084FC) else scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Text(
                        text = if (enabled)
                            "Physical lens refraction, chromatic dispersion & liquid components"
                        else
                            "Refractive glass optics across all components · Android 12+",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                    )
                }

                // ── 1-to-1 Kyant LiquidToggle ───────────────────────────────
                LiquidToggle(
                    selected = { enabled },
                    onSelect = { next ->
                        vibrationManager?.vibrateClick()
                        onToggle(next)
                        if (onToggleAnimation != null) {
                            onToggleAnimation(next)
                        } else if (next) {
                            onEnableAnimation()
                        }
                    },
                    backdrop = effectiveBackdrop,
                )
            }

            // ── Expandable Customization Sliders ──────────────────────────────
            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.10f),
                        thickness = 0.5.dp,
                    )

                    // ── Performance Mode Row ─────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClick(onClick = {
                                vibrationManager?.vibrateClick()
                                onPerformanceModeChange(!performanceMode)
                            })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Performance Mode",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                            )
                            Text(
                                text = if (performanceMode)
                                    "Using simplified shaders · better on older devices"
                                else
                                    "Full glass rendering · disable if UI feels slow",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                lineHeight = 15.sp,
                            )
                        }
                        // Use LiquidToggle for the performance toggle (it's inside an enabled LG card)
                        LiquidToggle(
                            selected = { performanceMode },
                            onSelect = { next ->
                                vibrationManager?.vibrateClick()
                                onPerformanceModeChange(next)
                            },
                            backdrop = effectiveBackdrop,
                        )
                    }

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                    )

                    // ── 1. Blur Radius Slider ────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Glass Blur",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (performanceMode) scheme.onSurfaceVariant else scheme.onSurface,
                            )
                            Text(
                                text = "${blurRadius.toInt()} dp",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                            )
                        }
                        // Wrap with Box + horizontal padding to prevent thumb clipping
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidSlider(
                                value = { blurRadius },
                                onValueChange = onBlurRadiusChange,
                                valueRange = 0f..32f,
                                visibilityThreshold = 0.05f,
                                backdrop = effectiveBackdrop,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }

                    // ── 2. Tint / Opacity Slider ─────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Glass Opacity",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (performanceMode) scheme.onSurfaceVariant else scheme.onSurface,
                            )
                            Text(
                                text = "${(tintAlpha * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidSlider(
                                value = { tintAlpha },
                                onValueChange = onTintAlphaChange,
                                valueRange = 0.05f..0.80f,
                                visibilityThreshold = 0.005f,
                                backdrop = effectiveBackdrop,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }

                    // ── 3. Refraction Slider ─────────────────────────────────
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Lens Refraction",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (performanceMode) scheme.onSurfaceVariant else scheme.onSurface,
                            )
                            Text(
                                text = "${refraction.toInt()} dp",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LiquidSlider(
                                value = { refraction },
                                onValueChange = onRefractionChange,
                                valueRange = 4f..48f,
                                visibilityThreshold = 0.05f,
                                backdrop = effectiveBackdrop,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }

                    // ── Reset button ─────────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        LiquidButton(
                            onClick = {
                                vibrationManager?.vibrateClick()
                                onResetCustomization()
                            },
                            backdrop = effectiveBackdrop,
                            tint = scheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = "Reset Defaults",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
