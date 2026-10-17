/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Shared expressive widgets for the developer console (cards, buttons,
 * key-value rows, toggles) so tab files and components share one look.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DevCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
fun DevCardTitle(text: String) {
    Text(text.uppercase(), fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun DevDivider() {
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
    Spacer(Modifier.height(10.dp))
}

@Composable
fun DevKv(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(v, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
fun DevToggleLine(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
fun DevBtn(
    label: String,
    icon: ImageVector,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = if (danger) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        else ButtonDefaults.buttonColors(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
