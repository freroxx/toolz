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
