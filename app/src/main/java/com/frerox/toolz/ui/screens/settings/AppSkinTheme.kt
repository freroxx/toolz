/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frerox.toolz.R
import com.frerox.toolz.ui.components.bouncyClick
import com.frerox.toolz.ui.theme.LocalVibrationManager

/**
 * Theme & display, migrated from the old Settings "Visuals & theme" section.
 * Lives on the App Skin screen now — one home for every visual.
 *
 * Dynamic colors + the custom primary/secondary palette live in the
 * "Icon colors" card above (they repaint the icon too), not here.
 */
@Composable
fun AppSkinThemeSection(viewModel: SettingsViewModel) {
    val vibrationManager = LocalVibrationManager.current

    val themeMode by viewModel.themeMode.collectAsState(initial = "SYSTEM")
    val backgroundGradientEnabled by viewModel.backgroundGradientEnabled.collectAsState(initial = true)
    val performanceMode by viewModel.performanceMode.collectAsState(initial = false)
    val showTopAppBarDescriptions by viewModel.showTopAppBarDescriptions.collectAsState(initial = false)

    SettingsItem(
        title = stringResource(R.string.st_SettingsScreen_o1p2),
        subtitle = "Current: ${themeMode.lowercase()}",
        icon = Icons.Rounded.DarkMode
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("SYSTEM", "LIGHT", "DARK").forEach { mode ->
                val isSelected = themeMode == mode
                Surface(
                    onClick = {
                        vibrationManager?.vibrateClick()
                        viewModel.setThemeMode(mode)
                    },
                    modifier = Modifier.weight(1f).height(48.dp).bouncyClick {},
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) else null
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        @Suppress("DEPRECATION")
                        Text(mode, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    SettingsToggleItem(
        title = stringResource(R.string.st_SettingsScreen_e7f9),
        subtitle = stringResource(R.string.st_SettingsScreen_g9h1),
        icon = Icons.Rounded.Gradient,
        checked = backgroundGradientEnabled,
        onCheckedChange = { viewModel.setBackgroundGradientEnabled(it) }
    )

    SettingsToggleItem(
        title = stringResource(R.string.st_SettingsScreen_k7l8),
        subtitle = stringResource(R.string.st_SettingsScreen_m9n0),
        icon = Icons.Rounded.Speed,
        checked = performanceMode,
        onCheckedChange = { viewModel.setPerformanceMode(it) }
    )

    SettingsToggleItem(
        title = stringResource(R.string.st_SettingsScreen_top_bar_desc_title),
        subtitle = stringResource(R.string.st_SettingsScreen_top_bar_desc_subtitle),
        icon = Icons.Rounded.Subtitles,
        checked = showTopAppBarDescriptions,
        onCheckedChange = { viewModel.setShowTopAppBarDescriptions(it) }
    )
}
