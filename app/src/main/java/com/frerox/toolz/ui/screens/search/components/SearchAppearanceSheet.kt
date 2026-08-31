/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.ui.screens.search.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppearanceSheet(onDismiss: () -> Unit, isCompact: Boolean, onToggleCompact: (Boolean)->Unit, cardRadius: Float, onRadiusChange: (Float)->Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Compact density", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isCompact, onCheckedChange = onToggleCompact)
            }
            Text("Card corner radius: ${cardRadius.toInt()}dp", style = MaterialTheme.typography.labelLarge)
            Slider(value = cardRadius, onValueChange = onRadiusChange, valueRange = 12f..24f, steps = 6)
        }
    }
}
