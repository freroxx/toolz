/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Shared expressive widgets for the developer console (cards, buttons,
 * key-value rows, toggles) so tab files and components share one look.
 */

package com.frerox.toolz.ui.screens.developer.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Spacing scale used across the developer console so every tab stays visually consistent. */
object DevSpacing {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 12.dp
    val LG = 16.dp
    val XL = 24.dp
}

@Composable
fun DevCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            // Console polish: a hairline outline so cards read as surfaces on
            // tinted backgrounds instead of flat grey blurs.
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                RoundedCornerShape(28.dp),
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
fun DevCardTitle(text: String) {
    Text(
        text.uppercase(),
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
fun DevDivider() {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
    Spacer(Modifier.height(12.dp))
}

/**
 * Tiny live beacon for status lines: a dim grey dot when idle, a breathing
 * primary dot while the console/sandboxes are armed. Lets Home feel alive
 * without adding noise.
 */
@Composable
fun DevPulseDot(active: Boolean, modifier: Modifier = Modifier) {
    if (!active) {
        Box(
            modifier = modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "devPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "devPulseAlpha",
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape),
    )
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

/**
 * Console button. [primary] renders the filled hero style (e.g. "Spoof all"),
 * [danger] the error style; default is the softer filled-tonal look.
 */
@Composable
fun DevBtn(
    label: String,
    icon: ImageVector,
    danger: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = when {
            danger -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
            primary -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            else -> ButtonDefaults.buttonColors()
        },
        shape = RoundedCornerShape(18.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/**
 * Slider that debounces persistence: dragging only updates local state;
 * the store write happens 300ms after the last change. Used by the
 * configurator sheets and the Labs "Data lab" card.
 */
@Composable
fun DebouncedSlider(
    label: String,
    current: Float,
    min: Float,
    max: Float,
    format: (Float) -> String,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    var pending by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(pending, current) {
        val v = pending ?: return@LaunchedEffect
        delay(300)
        onCommit(v)
        pending = null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                format(pending ?: current),
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = (pending ?: current).coerceIn(min, max),
            onValueChange = { pending = it },
            valueRange = min..max,
            enabled = enabled,
        )
    }
}
