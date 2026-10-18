/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * Shared expressive widgets for the developer console (cards, buttons,
 * key-value rows, toggles) so tab files and components share one look.
 *
 * Card tiers:
 *   DevCard       — default info / read-only surface
 *   DevActionCard — primary control surface (elevated border, slightly warmer tint)
 *   DevHeroCard   — full-bleed primary-tinted card for the power card + hero CTAs
 *   DevDangerCard — error-tinted card with 3dp left border for the Danger Zone
 *
 * Button styles (DevBtn):
 *   HERO    — full-width 52dp filled-primary; "Demo all + relaunch"
 *   PRIMARY — filled-primary, standard height
 *   TONAL   — filled-tonal (default)
 *   GHOST   — text + icon only, no background border
 *   DANGER  — filled-error surface
 *
 * DevMonoBox — shared terminal surface replacing the ad-hoc black Box
 *   background used by both the logcat viewer and the DataStore inspector.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val XXL = 32.dp
    val ICON_SM = 36.dp
    val ICON_MD = 48.dp
}

// ─── Cards ───────────────────────────────────────────────────────────────────

/** Default read-only / info card. */
@Composable
fun DevCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/**
 * Primary control surface — used for cards that contain interactive switches,
 * sliders or action buttons.
 */
@Composable
fun DevActionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/**
 * Primary-tinted card for dashboard highlights.
 */
@Composable
fun DevHeroCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

/**
 * Danger card for risky or destructive actions.
 */
@Composable
fun DevDangerCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

// ─── Terminal surface ─────────────────────────────────────────────────────────

/**
 * Shared terminal/mono surface used by LogcatViewer and the DataStore inspector.
 */
@Composable
fun DevMonoBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F141C))
            .border(1.dp, Color(0xFF28303C), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        content()
    }
}

// ─── Typography helpers ───────────────────────────────────────────────────────

@Composable
fun DevCardTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun DevDivider() {
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
    Spacer(Modifier.height(12.dp))
}

// ─── Live status beacon ───────────────────────────────────────────────────────

/**
 * Clean live status dot: primary when active, subtle outline when inactive.
 */
@Composable
fun DevPulseDot(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            ),
    )
}

// ─── Key-value row ────────────────────────────────────────────────────────────

@Composable
fun DevKv(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(v, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

// ─── Toggle line ──────────────────────────────────────────────────────────────

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
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

// ─── Buttons ──────────────────────────────────────────────────────────────────

enum class DevBtnStyle { HERO, PRIMARY, TONAL, GHOST, DANGER }

/**
 * Console button with five styles:
 * - [DevBtnStyle.HERO]    — full-width 52dp filled-primary hero action
 * - [DevBtnStyle.PRIMARY] — standard filled-primary
 * - [DevBtnStyle.TONAL]   — filled-tonal (default)
 * - [DevBtnStyle.GHOST]   — text+icon, no background
 * - [DevBtnStyle.DANGER]  — filled-error
 *
 * Legacy [primary]/[danger] booleans still work for call-site compat.
 */
@Composable
fun DevBtn(
    label: String,
    icon: ImageVector,
    style: DevBtnStyle = DevBtnStyle.TONAL,
    // Legacy compat — prefer [style] for new call sites
    danger: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Resolve effective style (legacy booleans override if set)
    val effective = when {
        danger -> DevBtnStyle.DANGER
        primary -> DevBtnStyle.PRIMARY
        else -> style
    }

    val iconContent: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }

    when (effective) {
        DevBtnStyle.HERO -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }

        DevBtnStyle.PRIMARY -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(18.dp),
        ) { iconContent() }

        DevBtnStyle.TONAL -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
        ) { iconContent() }

        DevBtnStyle.GHOST -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) { iconContent() }

        DevBtnStyle.DANGER -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            shape = RoundedCornerShape(18.dp),
        ) { iconContent() }
    }
}

// ─── Debounced slider ─────────────────────────────────────────────────────────

/**
 * Slider that debounces persistence: dragging only updates local state;
 * the store write happens 300ms after the last change. Used by the
 * configurator sheets and the Labs "Data lab" card.
 *
 * Optionally shows [minLabel]/[maxLabel] below the track for context.
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
    minLabel: String? = null,
    maxLabel: String? = null,
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
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
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
        if (minLabel != null || maxLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    minLabel ?: "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    maxLabel ?: "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
