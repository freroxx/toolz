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

package com.frerox.toolz.ui.liquidglass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.data.skin.IconShape
import com.frerox.toolz.data.skin.IconSpec
import com.frerox.toolz.ui.components.MarkGeometry
import com.frerox.toolz.ui.components.drawToolzMark
import com.frerox.toolz.ui.components.MarkPalettes
import com.kyant.backdrop.Backdrop
import kotlin.math.min

/**
 * 3D Glass-rendered version of a [ToolzMark] icon.
 *
 * For the default preset ("grape-haze" / "system-auto"), renders the exact 3D glass jewel
 * matching the user reference image (translucent purple glass octagon, iridescent beveled rim,
 * pearl elevated swoosh with drop shadow and specular catchlights).
 *
 * For all other presets, renders an authentic 3D glass jewel in that preset's colorway
 * using multi-layered Canvas geometry (ambient drop shadow, iridescent rim, glass refraction body,
 * and elevated 3D pearl swoosh).
 */
@Composable
fun ToolzMarkGlass(
    spec: IconSpec,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    frame: IconShape = IconShape.OCTAGON,
    isDark: Boolean = false,
) {
    val isReferencePreset = spec.id == IconCatalog.DEFAULT_ID ||
        spec.id == "system-auto" ||
        spec.id == "liquid-glass"

    if (isReferencePreset) {
        // Pixel-perfect match for the user's reference image
        Image(
            painter = painterResource(R.drawable.ic_liquid_glass_mark),
            contentDescription = null,
            modifier = if (frame == IconShape.CIRCLE) modifier.clip(androidx.compose.foundation.shape.CircleShape) else modifier,
        )
    } else {
        // Render dynamic 3D glass jewel for any preset / shape
        Canvas(modifier = if (frame == IconShape.CIRCLE) modifier.clip(androidx.compose.foundation.shape.CircleShape) else modifier) {
            drawGlassJewelMark(
                spec = spec,
                frame = frame,
                isDark = isDark,
            )
        }
    }
}

/**
 * Full icon tile (matching the reference app launcher icon):
 * Obsidian squircle background + floating 3D glass jewel with ambient shadow.
 */
@Composable
fun ToolzIconTileGlass(
    spec: IconSpec,
    modifier: Modifier = Modifier,
    frame: IconShape = IconShape.OCTAGON,
    isDark: Boolean = true,
) {
    val isReferencePreset = spec.id == IconCatalog.DEFAULT_ID ||
        spec.id == "system-auto" ||
        spec.id == "liquid-glass"

    if (isReferencePreset) {
        Image(
            painter = painterResource(R.drawable.ic_liquid_glass_reference),
            contentDescription = null,
            modifier = if (frame == IconShape.CIRCLE) modifier.clip(androidx.compose.foundation.shape.CircleShape) else modifier.clip(RoundedCornerShape(26.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(26.dp))
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background squircle gradient
                val bgBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(spec.bodyStart.toInt()).copy(alpha = 0.22f),
                        Color(0xFF0F0A18),
                        Color(0xFF07040B),
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension * 0.75f,
                )
                drawRect(brush = bgBrush)

                // 3D Glass Jewel Mark in center (scaled to 72% of tile)
                val jewelScale = 0.72f
                withTransform({
                    scale(jewelScale, jewelScale, Offset(size.width / 2f, size.height / 2f))
                }) {
                    drawGlassJewelMark(spec, frame, isDark)
                }
            }
        }
    }
}

/**
 * Procedural 3D Glass Jewel renderer for any [IconSpec] and [IconShape].
 *
 * Implements:
 * 1. Ambient drop shadow under the floating glass polygon.
 * 2. Iridescent beveled outer rim with dual-tone sweep gradient & top-left specular reflection.
 * 3. Translucent refractive glass body plate with deep jewel saturation.
 * 4. Inner ambient occlusion / shadow along the bevel crease.
 * 5. Elevated 3D satin pearl swoosh glyph with directional drop shadow and crisp top catchlight.
 */
private fun DrawScope.drawGlassJewelMark(
    spec: IconSpec,
    frame: IconShape,
    isDark: Boolean,
) {
    val side = min(size.width, size.height)
    if (side <= 0f) return

    val scale = side / MarkGeometry.SPAN
    val c = MarkGeometry.CENTER
    val centerOffset = Offset(size.width / 2f, size.height / 2f)

    val rimColor = Color(spec.rim.toInt())
    val bodyColor1 = Color(spec.bodyStart.toInt())
    val bodyColor2 = Color(spec.bodyEnd?.toInt() ?: spec.bodyStart.toInt())

    withTransform({
        translate(centerOffset.x, centerOffset.y)
        scale(scale, scale, Offset.Zero)
        translate(-c, -c)
    }) {
        if (frame == IconShape.OCTAGON) {
            // ── 1. Ambient Drop Shadow ─────────────────────────────────────────
            val shadowOffset = Offset(0f, 24f)
            withTransform({ translate(shadowOffset.x, shadowOffset.y) }) {
                drawPath(
                    path = MarkGeometry.rim,
                    color = Color.Black.copy(alpha = 0.38f),
                )
            }

            // ── 2. Iridescent Beveled Rim ──────────────────────────────────────
            // Multi-stop sweep gradient creating chromatic edge highlights
            val rimBrush = Brush.sweepGradient(
                0.00f to rimColor.copy(alpha = 0.95f),
                0.20f to Color.White.copy(alpha = 0.90f), // top-right specular
                0.35f to bodyColor1.copy(alpha = 0.85f),
                0.60f to rimColor.copy(alpha = 0.95f),
                0.78f to Color.White.copy(alpha = 0.70f), // bottom-left reflection
                1.00f to rimColor.copy(alpha = 0.95f),
                center = Offset(c, c),
            )
            drawPath(path = MarkGeometry.rim, brush = rimBrush)

            // ── 3. Translucent Glass Body Plate ────────────────────────────────
            val bodyBrush = Brush.radialGradient(
                0.00f to bodyColor1.copy(alpha = 0.92f),
                0.65f to bodyColor2.copy(alpha = 0.95f),
                1.00f to Color(0xFF0D0618).copy(alpha = 0.98f),
                center = Offset(c - 60f, c - 60f),
                radius = 420f,
            )
            drawPath(path = MarkGeometry.body, brush = bodyBrush)

            // ── 4. Inner Bevel Highlight & Shadow ──────────────────────────────
            // Subtle top-left rim highlight
            drawPath(
                path = MarkGeometry.body,
                brush = Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.28f),
                    0.5f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.35f),
                    start = Offset(c - 300f, c - 300f),
                    end = Offset(c + 300f, c + 300f),
                ),
            )
        } else {
            // Circular frame
            val hw = MarkGeometry.SPAN / 2f
            val hh = MarkGeometry.SPAN / 2f
            val r = min(hw, hh)

            // Ambient Shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.38f),
                radius = r,
                center = Offset(c, c + 24f),
            )

            // Iridescent Rim
            val rimBrush = Brush.sweepGradient(
                0.00f to rimColor.copy(alpha = 0.95f),
                0.22f to Color.White.copy(alpha = 0.90f),
                0.50f to bodyColor1.copy(alpha = 0.85f),
                0.75f to rimColor.copy(alpha = 0.95f),
                1.00f to rimColor.copy(alpha = 0.95f),
                center = Offset(c, c),
            )
            drawCircle(brush = rimBrush, radius = r, center = Offset(c, c))

            // Body
            val bw = hw - MarkGeometry.RIM_WIDTH
            val bodyBrush = Brush.radialGradient(
                0.00f to bodyColor1.copy(alpha = 0.92f),
                0.65f to bodyColor2.copy(alpha = 0.95f),
                1.00f to Color(0xFF0D0618).copy(alpha = 0.98f),
                center = Offset(c - 60f, c - 60f),
                radius = bw,
            )
            drawCircle(brush = bodyBrush, radius = bw, center = Offset(c, c))
        }

        // ── 5. Elevated 3D Pearl Swoosh ────────────────────────────────────────
        // Step 5a: Soft drop shadow directly underneath swoosh
        withTransform({ translate(0f, 18f) }) {
            drawPath(
                path = MarkGeometry.swoosh,
                color = Color.Black.copy(alpha = 0.45f),
            )
        }

        // Step 5b: Pearl / Satin Metallic gradient fill
        val swooshBrush = Brush.linearGradient(
            0.00f to Color(0xFFFFFFFF),
            0.45f to Color(0xFFF0EBF8),
            1.00f to Color(0xFFC7BDD6),
            start = Offset(c - 200f, c - 200f),
            end = Offset(c + 200f, c + 200f),
        )
        drawPath(path = MarkGeometry.swoosh, brush = swooshBrush)

        // Step 5c: Crisp top-left specular bevel catchlight on swoosh
        drawPath(
            path = MarkGeometry.swoosh,
            brush = Brush.linearGradient(
                0.00f to Color.White.copy(alpha = 0.85f),
                0.35f to Color.White.copy(alpha = 0.20f),
                1.00f to Color.Transparent,
                start = Offset(c - 160f, c - 180f),
                end = Offset(c, c),
            ),
            style = Stroke(width = 6f),
        )
    }
}

/**
 * Returns whether a given icon spec should render with the glass variant
 * when liquid glass mode is enabled.
 */
fun IconSpec.isGlassEnabled(glassDisabledIconIds: Set<String>): Boolean =
    id !in glassDisabledIconIds
