/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.frerox.toolz.ui.screens.settings.SettingsViewModel

/**
 * Toolz mark bound to the user's persisted icon settings (preset + shape +
 * adapt/follow + overrides). Use this for every in-app brand surface so the
 * whole app follows Icon Catalog picks instantly. Catalog previews keep using
 * [ToolzMark]/[ToolzMarkThemed] with explicit values.
 */
@Composable
fun CurrentToolzMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val presetId by viewModel.appIconPresetId.collectAsState(initial = "grape-haze")
    val shape by viewModel.appIconShape.collectAsState(initial = "HEXAGON")
    val adaptTheme by viewModel.appIconAdaptTheme.collectAsState(initial = true)
    val followDynamic by viewModel.appIconFollowDynamic.collectAsState(initial = true)
    val bgOv by viewModel.appIconBgOverride.collectAsState(initial = null)
    val fgOv by viewModel.appIconFgOverride.collectAsState(initial = null)
    val strokeOv by viewModel.appIconStrokeOverride.collectAsState(initial = null)
    val iconGradient by viewModel.appIconGradientEnabled.collectAsState(initial = true)
    val liquidGlassMode by viewModel.liquidGlassModeEnabled.collectAsState(initial = false)
    val liquidGlassDisabled by viewModel.liquidGlassDisabledIconIds.collectAsState(initial = emptySet())
    val backdrop = com.frerox.toolz.ui.liquidglass.LocalLiquidGlassBackdrop.current

    if (liquidGlassMode && presetId !in liquidGlassDisabled && presetId != com.frerox.toolz.data.skin.IconCatalog.CUSTOM_ID) {
        val spec = androidx.compose.runtime.remember(presetId) { com.frerox.toolz.data.skin.IconCatalog.find(presetId) }
        val parsedShape = androidx.compose.runtime.remember(shape) { com.frerox.toolz.data.skin.IconShape.parse(shape) ?: com.frerox.toolz.data.skin.IconShape.OCTAGON }
        com.frerox.toolz.ui.liquidglass.ToolzMarkGlass(
            spec = spec,
            backdrop = backdrop,
            frame = parsedShape,
            modifier = modifier
        )
    } else {
        ToolzMark(
            presetId = presetId,
            modifier = modifier,
            shape = shape,
            adaptToTheme = adaptTheme,
            followDynamic = followDynamic,
            bgOverride = bgOv,
            fgOverride = fgOv,
            strokeOverride = strokeOv,
            iconGradient = iconGradient,
            contentDescription = contentDescription,
        )
    }
}
