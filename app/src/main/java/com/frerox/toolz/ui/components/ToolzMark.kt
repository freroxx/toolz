/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.frerox.toolz.data.skin.IconCatalog
import com.frerox.toolz.data.skin.IconShape
import kotlin.math.abs
import kotlin.math.min

/**
 * Toolz mark renderer.
 *
 * Pure vector drawing from [MarkGeometry]; no bitmaps, no tint modes. A preset renders
 * exactly like the reference icon at any size. Shadow/elevation is the caller's business.
 *
 * Frames: OCTAGON is the native silhouette. CIRCLE swaps the octagon for a rounded
 * container (same rim width) and keeps the glyph centred.
 */
@Composable
fun ToolzMark(
    presetId: String,
    modifier: Modifier = Modifier,
    shape: String = "HEXAGON",
    adaptToTheme: Boolean = true,
    followDynamic: Boolean = true,
    bgOverride: Int? = null,
    fgOverride: Int? = null,
    strokeOverride: Int? = null,
    showBackground: Boolean = true,
    iconGradient: Boolean = true,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    ToolzMarkThemed(
        presetId = presetId,
        scheme = scheme,
        isDark = scheme.background.luminance() < 0.5f, // follows the skin, not the system
        dynamicColor = true,
        modifier = modifier,
        shape = shape,
        adaptToTheme = adaptToTheme,
        followDynamic = followDynamic,
        bgOverride = bgOverride,
        fgOverride = fgOverride,
        strokeOverride = strokeOverride,
        showBackground = showBackground,
        iconGradient = iconGradient,
        contentDescription = contentDescription,
    )
}

@Composable
fun ToolzMarkThemed(
    presetId: String,
    scheme: ColorScheme,
    isDark: Boolean,
    dynamicColor: Boolean,
    modifier: Modifier = Modifier,
    shape: String = "HEXAGON",
    adaptToTheme: Boolean = true,
    followDynamic: Boolean = true,
    bgOverride: Int? = null,
    fgOverride: Int? = null,
    strokeOverride: Int? = null,
    showBackground: Boolean = true,
    iconGradient: Boolean = true,
    contentDescription: String? = null,
) {
    val palette = remember(
        presetId, scheme, isDark, dynamicColor, adaptToTheme, followDynamic,
        bgOverride, fgOverride, strokeOverride, iconGradient,
    ) {
        MarkPalettes.resolve(
            spec = IconCatalog.find(presetId),
            scheme = scheme,
            isDark = isDark,
            dynamicColor = dynamicColor,
            followDynamic = followDynamic,
            adaptToTheme = adaptToTheme,
            bgOverride = bgOverride,
            fgOverride = fgOverride,
            strokeOverride = strokeOverride,
            iconGradient = iconGradient,
        )
    }
    ToolzMarkCanvas(
        palette = palette,
        modifier = modifier,
        frame = IconShape.parse(shape) ?: IconShape.OCTAGON,
        showBackground = showBackground,
        contentDescription = contentDescription,
    )
}

/** Palette-driven entry point: icon pickers, previews, anything that already has a [MarkPalette]. */
@Composable
fun ToolzMarkCanvas(
    palette: MarkPalette,
    modifier: Modifier = Modifier,
    frame: IconShape = IconShape.OCTAGON,
    showBackground: Boolean = true,
    contentDescription: String? = null,
) {
    val a11y = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Canvas(modifier.then(a11y)) { drawToolzMark(palette, frame, showBackground) }
}

/**
 * Draws into the largest centred square of the current scope.
 * [showBackground] = false draws only the swoosh, with real transparency where the capsule is.
 */
fun DrawScope.drawToolzMark(
    palette: MarkPalette,
    frame: IconShape = IconShape.OCTAGON,
    showBackground: Boolean = true,
) {
    val side = min(size.width, size.height)
    if (side <= 0f) return
    val scale = side / MarkGeometry.SPAN
    val c = MarkGeometry.CENTER

    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(scale, scale, Offset.Zero)
        translate(-c, -c)
    }) {
        if (!showBackground) {
            drawPath(MarkGeometry.swoosh, palette.disc)
            return@withTransform
        }

        if (frame == IconShape.OCTAGON) {
            drawPath(MarkGeometry.rim, palette.rim)
            drawPath(MarkGeometry.body, palette.octagonBody)
        } else {
            // CIRCLE frame: same rim width as the octagon, glyph stays centred.
            val hw = MarkGeometry.SPAN / 2f
            val hh = MarkGeometry.SPAN / 2f
            drawRoundRect(
                color = palette.rim,
                topLeft = Offset(c - hw, c - hh),
                size = Size(2f * hw, 2f * hh),
                cornerRadius = CornerRadius(min(hw, hh)),
            )
            val bw = hw - MarkGeometry.RIM_WIDTH
            val bh = hh - MarkGeometry.RIM_WIDTH
            val r = min(bw, bh)
            val axis = MarkGeometry.gradientAxis
            // Support function of a rounded rect along the gradient axis.
            val half = abs(axis.x) * (bw - r) + abs(axis.y) * (bh - r) + r
            val centre = Offset(c, c)
            drawRoundRect(
                brush = MarkGradient.brush(palette.bodyStart, palette.bodyEnd, centre - axis * half, centre + axis * half),
                topLeft = Offset(c - bw, c - bh),
                size = Size(2f * bw, 2f * bh),
                cornerRadius = CornerRadius(r),
            )
        }
        drawPath(MarkGeometry.disc, palette.disc)
        drawPath(MarkGeometry.capsule, palette.carve)
    }
}
