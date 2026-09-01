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

package com.frerox.toolz.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.frerox.toolz.ui.navigation.Screen
import com.frerox.toolz.ui.theme.LocalVibrationManager

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickActionHub(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val vibrationManager = LocalVibrationManager.current

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + 
                slideInVertically(spring(stiffness = Spring.StiffnessLow)) { it } +
                scaleIn(initialScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(),
        modifier = modifier
    ) {
        ToolzHorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
            content = {
                ToolzExpressiveIconButton(
                    onClick = { 
                        vibrationManager?.vibrateClick()
                        onNavigate(Screen.AiAssistant.createRoute()) 
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.AutoAwesome, "AI")
                }
            },
            trailingContent = {
                clickableItem(
                    onClick = { 
                        vibrationManager?.vibrateClick()
                        onNavigate(Screen.Search.homeRoute)
                    },
                    icon = { Icon(Icons.Rounded.Search, "Search") },
                    label = "SEARCH"
                )
                clickableItem(
                    onClick = { 
                        vibrationManager?.vibrateClick()
                        onNavigate(Screen.Settings.route) 
                    },
                    icon = { Icon(Icons.Rounded.Palette, "Appearance") },
                    label = "STYLE"
                )
            }
        )
    }
}
